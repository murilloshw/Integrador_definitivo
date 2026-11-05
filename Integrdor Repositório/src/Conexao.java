import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Conexão com o banco MySQL remoto (Aiven), com um POOL LEVE de conexões.
 *
 * Por que o pool existe: abrir uma conexão com um servidor remoto (TCP + SSL + login)
 * demora de centenas de milissegundos a alguns segundos. Em vez de abrir uma nova a
 * cada consulta, as conexões fechadas voltam para uma fila e são reaproveitadas.
 *
 * Como usar nas telas (sempre em try-with-resources):
 *
 *   try (Connection con = Conexao.conectar()) { ... }
 *
 * O close() não derruba a conexão: ele a devolve ao pool.
 *
 * As credenciais ficam no arquivo .env (veja .env.example), fora do código.
 */
public class Conexao {

    // =========================================================================
    // CONFIGURAÇÃO
    // Credenciais lidas do .env (ou de variáveis de ambiente) e limites do pool.
    // =========================================================================

    private static final Map<String, String> ENV = carregarEnv();

    private static final String URL = config("DB_URL");
    private static final String USER = config("DB_USER");
    private static final String PASSWORD = config("DB_PASSWORD");

    /**
     * Fuso horário usado pelo banco nesta conexão. O servidor (Aiven) roda em UTC;
     * com isto, NOW(), CURDATE() e DEFAULT CURRENT_TIMESTAMP gravam o horário de Brasília.
     */
    private static final String FUSO_BANCO = "America/Sao_Paulo";

    /** Quantas conexões ficam guardadas prontas para uso. */
    private static final int MAX_OCIOSAS = 4;

    /** Conexão parada há menos que isso é considerada boa; acima disso testamos antes de usar. */
    private static final long CONFIAR_ATE_MS = 8_000;

    /** Conexão real guardada no pool + quando foi devolvida. */
    private static class Ociosa {
        final Connection con;
        final long desde = System.currentTimeMillis();

        Ociosa(Connection con) {
            this.con = con;
        }
    }

    private static final LinkedBlockingQueue<Ociosa> OCIOSAS = new LinkedBlockingQueue<>();

    // =========================================================================
    // USO PELAS TELAS
    // =========================================================================

    /**
     * Devolve uma conexão pronta para uso (ou null se não conseguir conectar).
     * Use SEMPRE em try-with-resources: o close() devolve a conexão ao pool.
     */
    public static Connection conectar() {
        try {
            Connection real = pegarOciosa();
            if (real == null) {
                real = DriverManager.getConnection(URL, USER, PASSWORD);
                definirFuso(real);
                System.out.println("Conexao com o MySQL estabelecida com sucesso!");
            }
            return embrulhar(real);
        } catch (SQLException e) {
            System.err.println("Erro ao conectar com a base de dados: " + e.getMessage());
            return null;
        }
    }

    /**
     * Abre algumas conexões ANTES (em paralelo) e as deixa no pool, para que as
     * telas abram rápido. Usado na tela de carregamento do login.
     *
     * @return false se não foi possível conectar ao banco.
     */
    public static boolean aquecer(int quantidade) {
        Connection primeira = conectar(); // valida banco, rede e credenciais
        if (primeira == null) {
            return false;
        }

        List<Connection> abertas = new ArrayList<>();
        abertas.add(primeira);

        if (quantidade > 1) {
            ExecutorService executor = Executors.newFixedThreadPool(quantidade - 1);
            List<Future<Connection>> futuros = new ArrayList<>();
            for (int i = 1; i < quantidade; i++) {
                futuros.add(executor.submit(Conexao::conectar));
            }
            for (Future<Connection> f : futuros) {
                try {
                    Connection c = f.get();
                    if (c != null) {
                        abertas.add(c);
                    }
                } catch (Exception ignorar) {
                    // se alguma falhar, as telas abrem a sua própria quando precisarem
                }
            }
            executor.shutdown();
        }

        for (Connection c : abertas) {
            fecharSilencioso(c); // devolve ao pool
        }
        return true;
    }

    // =========================================================================
    // FUNCIONAMENTO INTERNO DO POOL
    // =========================================================================

    /**
     * Coloca a conexão no fuso de Brasília. Vale para a conexão inteira, inclusive
     * quando ela volta ao pool e é reaproveitada.
     */
    private static void definirFuso(Connection real) throws SQLException {
        try (Statement st = real.createStatement()) {
            st.execute("SET time_zone = '" + FUSO_BANCO + "'");
        } catch (SQLException e) {
            fecharSilencioso(real);
            throw e;
        }
    }

    /** Pega uma conexão guardada que ainda esteja viva; null se não houver. */
    private static Connection pegarOciosa() {
        Ociosa o;
        while ((o = OCIOSAS.poll()) != null) {
            try {
                boolean recente = System.currentTimeMillis() - o.desde < CONFIAR_ATE_MS;
                if (!o.con.isClosed() && (recente || o.con.isValid(2))) {
                    return o.con;
                }
            } catch (SQLException ignorar) {
            }
            fecharSilencioso(o.con);
        }
        return null;
    }

    /** Chamado no close(): limpa qualquer transação pendente e guarda a conexão. */
    private static void devolver(Connection real) {
        try {
            if (real.isClosed()) {
                return;
            }
            if (!real.getAutoCommit()) {
                real.rollback(); // sobra de transação não finalizada
                real.setAutoCommit(true); // próxima tela recebe a conexão no modo normal
            }
            if (OCIOSAS.size() < MAX_OCIOSAS) {
                OCIOSAS.offer(new Ociosa(real));
            } else {
                real.close();
            }
        } catch (SQLException e) {
            fecharSilencioso(real);
        }
    }

    /** Conexão "de mentirinha": tudo vai para a real, exceto close(), que devolve ao pool. */
    private static Connection embrulhar(Connection real) {
        final boolean[] devolvida = { false };

        return (Connection) Proxy.newProxyInstance(
                Conexao.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, metodo, args) -> {
                    String nome = metodo.getName();

                    if (nome.equals("close")) {
                        synchronized (devolvida) {
                            if (!devolvida[0]) {
                                devolvida[0] = true;
                                devolver(real);
                            }
                        }
                        return null;
                    }
                    if (nome.equals("isClosed")) {
                        return devolvida[0] || real.isClosed();
                    }
                    if (devolvida[0]) {
                        throw new SQLException("Esta conexão já foi fechada.");
                    }
                    try {
                        return metodo.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause(); // devolve o SQLException original
                    }
                });
    }

    private static void fecharSilencioso(Connection c) {
        try {
            c.close();
        } catch (SQLException ignorar) {
        }
    }

    // =========================================================================
    // LEITURA DO .env
    // Procura o arquivo na pasta atual e nas pastas próximas (o programa pode ser
    // executado pela raiz do repositório ou por dentro de "Integrdor Repositório").
    // =========================================================================

    /** Valor de uma chave: primeiro a variável de ambiente do sistema, depois o .env. */
    private static String config(String chave) {
        String valor = System.getenv(chave);
        if (valor == null || valor.isEmpty()) {
            valor = ENV.get(chave);
        }
        if (valor == null) {
            System.err.println("Configuração ausente: " + chave + " (crie o arquivo .env a partir do .env.example)");
        }
        return valor;
    }

    /** Lê linhas "CHAVE=valor" do primeiro .env encontrado. Ignora linhas vazias e comentários (#). */
    private static Map<String, String> carregarEnv() {
        String[] locais = { ".env", "../.env", "../../.env", "Integrdor Repositório/.env" };
        Map<String, String> valores = new HashMap<>();
        for (String local : locais) {
            Path arquivo = Paths.get(local);
            if (!Files.isRegularFile(arquivo)) {
                continue;
            }
            try {
                for (String linha : Files.readAllLines(arquivo, StandardCharsets.UTF_8)) {
                    linha = linha.trim();
                    int igual = linha.indexOf('=');
                    if (linha.isEmpty() || linha.startsWith("#") || igual < 0) {
                        continue;
                    }
                    String chave = linha.substring(0, igual).trim();
                    String valor = linha.substring(igual + 1).trim();
                    if (valor.length() >= 2 && valor.startsWith("\"") && valor.endsWith("\"")) {
                        valor = valor.substring(1, valor.length() - 1); // aceita valor entre aspas
                    }
                    valores.put(chave, valor);
                }
            } catch (IOException e) {
                System.err.println("Não foi possível ler " + arquivo.toAbsolutePath() + ": " + e.getMessage());
            }
            break;
        }
        return valores;
    }
}
