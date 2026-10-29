import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Conexão com o MySQL remoto (Aiven), agora com um POOL LEVE de conexões.
 *
 * Por que isso existe: abrir uma conexão nova com um servidor remoto (handshake
 * TCP + SSL + login) leva de centenas de milissegundos a alguns segundos, e as
 * telas abriam uma conexão nova a cada consulta. Era isso que deixava a abertura
 * das telas lenta.
 *
 * Como funciona: o código das telas continua IGUAL (Conexao.conectar() dentro de
 * try-with-resources). Só que o close() não derruba mais a conexão: ela volta para
 * uma fila e é reaproveitada pela próxima chamada.
 */
public class Conexao {

    // Altere as credenciais de acordo com a sua base de dados MySQL remoto
    // connectTimeout: se estiver sem internet, desiste em 10s em vez de ficar travado
    private static final String URL = "jdbc:mysql://mysql-14e29d2-integradorarcade.b.aivencloud.com:17853/integradorarcade?sslMode=REQUIRED&connectTimeout=10000";
    private static final String USER = "avnadmin";
    private static final String PASSWORD = "AVNS_OE5PEonim4HMG18g_M9";

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

    /**
     * Devolve uma conexão pronta para uso (ou null se não conseguir conectar).
     * Use SEMPRE em try-with-resources: o close() devolve a conexão ao pool.
     */
    public static Connection conectar() {
        try {
            Connection real = pegarOciosa();
            if (real == null) {
                real = DriverManager.getConnection(URL, USER, PASSWORD);
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
            try {
                c.close(); // devolve ao pool
            } catch (SQLException ignorar) {
            }
        }
        return true;
    }

    // ---------------------------------------------------------------
    // INTERNO
    // ---------------------------------------------------------------

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
}