import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Registro de auditoria: guarda QUEM fez O QUÊ e QUANDO (recargas, resgates,
 * partidas, exclusões, login/logout...).
 *
 * Uso (depois que a operação deu certo, ou seja, depois do commit):
 *
 *   Auditoria.registrar("RECARGA", "Cartão 007 | R$ 10,00 | PIX");
 *
 * Como funciona:
 * - O funcionário logado (Sessao) é capturado NA HORA da chamada.
 * - A gravação acontece em segundo plano, numa fila de uma thread só: não trava a tela
 *   e mantém a ordem dos registros.
 * - É "melhor esforço": se o log falhar (sem internet, por exemplo), a operação
 *   principal NÃO é desfeita; o erro só aparece no console.
 * - A tabela é criada automaticamente na primeira gravação (o mesmo script está em
 *   sql/auditoria.sql, para quem preferir criar manualmente).
 */
public final class Auditoria {

    /** Tamanho máximo da coluna Detalhes. */
    static final int MAX_DETALHES = 500;

    static final String SQL_CRIAR_TABELA = "CREATE TABLE IF NOT EXISTS auditoria ("
            + "ID_Log BIGINT AUTO_INCREMENT PRIMARY KEY, "
            + "Data_Hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
            + "ID_Funcionario BIGINT NULL, "
            + "Usuario VARCHAR(50) NULL, "
            + "Acao VARCHAR(40) NOT NULL, "
            + "Detalhes VARCHAR(" + MAX_DETALHES + ") NULL, "
            + "INDEX idx_auditoria_data (Data_Hora), "
            + "INDEX idx_auditoria_acao (Acao))";

    private static final String SQL_INSERIR =
            "INSERT INTO auditoria (ID_Funcionario, Usuario, Acao, Detalhes) VALUES (?, ?, ?, ?)";

    /** Fila de gravação: uma thread "daemon" (não impede o programa de fechar). */
    private static final ExecutorService FILA = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "auditoria");
        t.setDaemon(true);
        return t;
    });

    /** A tabela já foi criada/verificada nesta execução? (fila de gravação e tela de Log) */
    private static volatile boolean tabelaVerificada = false;

    /** Uma linha do log, já com o nome do funcionário (usada pela TelaLog). */
    public static final class Registro {
        public final long id;
        public final java.time.LocalDateTime dataHora;
        public final String nome;     // nome completo do funcionário (ou o login, se ele foi excluído)
        public final String usuario;  // login usado no momento da ação
        public final String acao;
        public final String detalhes;

        Registro(long id, java.time.LocalDateTime dataHora, String nome, String usuario, String acao, String detalhes) {
            this.id = id;
            this.dataHora = dataHora;
            this.nome = nome;
            this.usuario = usuario;
            this.acao = acao;
            this.detalhes = detalhes;
        }
    }

    static {
        // Ao fechar o programa (inclusive pelo ESC), espera até 3s para gravar o que falta.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            FILA.shutdown();
            try {
                FILA.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignorar) {
                Thread.currentThread().interrupt();
            }
        }, "auditoria-fim"));
    }

    private Auditoria() {
    }

    /**
     * Registra uma ação feita pelo funcionário logado.
     *
     * @param acao     código curto em MAIÚSCULAS (ex.: "RECARGA", "EXCLUSAO_CLIENTE").
     * @param detalhes texto livre com os dados importantes (pode ser null).
     */
    public static void registrar(String acao, String detalhes) {
        Funcionario logado = Sessao.getLogado();
        registrar(logado == null ? null : logado.getId(),
                logado == null ? null : logado.getUsuario(),
                acao, detalhes);
    }

    /** Versão com o responsável explícito (ex.: login, quando a sessão ainda não começou). */
    public static void registrar(String idFuncionario, String usuario, String acao, String detalhes) {
        final Long id = idNumerico(idFuncionario);
        final String acaoFinal = limitar(acao == null ? "DESCONHECIDA" : acao.trim().toUpperCase(), 40);
        final String detalhesFinal = limitar(detalhes, MAX_DETALHES);
        try {
            FILA.execute(() -> gravar(id, usuario, acaoFinal, detalhesFinal));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            // programa já está fechando
            System.err.println("Auditoria ignorada (sistema encerrando): " + acaoFinal);
        }
    }

    private static void gravar(Long idFuncionario, String usuario, String acao, String detalhes) {
        try (Connection con = Conexao.conectar()) {
            if (con == null) {
                System.err.println("Auditoria não gravada (sem conexão): " + acao + " | " + detalhes);
                return;
            }
            garantirTabela(con);
            try (PreparedStatement ps = con.prepareStatement(SQL_INSERIR)) {
                if (idFuncionario == null) {
                    ps.setNull(1, Types.BIGINT);
                } else {
                    ps.setLong(1, idFuncionario);
                }
                ps.setString(2, limitar(usuario, 50));
                ps.setString(3, acao);
                ps.setString(4, detalhes);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Auditoria não gravada: " + acao + " (" + e.getMessage() + ")");
        }
    }

    private static void garantirTabela(Connection con) throws SQLException {
        if (tabelaVerificada) {
            return;
        }
        try (Statement st = con.createStatement()) {
            st.executeUpdate(SQL_CRIAR_TABELA);
        }
        tabelaVerificada = true;
    }

    // =========================================================================
    // LEITURA (TELA DE LOG)
    // =========================================================================

    /**
     * Registros com ID maior que {@code depoisDoId}, do mais antigo para o mais novo.
     * Com depoisDoId = 0, traz os {@code limite} mais recentes (carga inicial da tela).
     * Roda fora da thread da interface.
     */
    public static java.util.List<Registro> consultar(long depoisDoId, int limite) throws SQLException {
        String sql = "SELECT a.ID_Log, a.Data_Hora, a.Usuario, a.Acao, a.Detalhes, f.Nome_Completo "
                + "FROM auditoria a LEFT JOIN funcionários f ON f.ID_Funcionário = a.ID_Funcionario "
                + "WHERE a.ID_Log > ? ORDER BY a.ID_Log DESC LIMIT ?";
        java.util.List<Registro> lista = new java.util.ArrayList<>();
        try (Connection con = Conexao.conectar()) {
            if (con == null) {
                throw new SQLException("Não foi possível conectar ao banco de dados.");
            }
            garantirTabela(con);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setLong(1, depoisDoId);
                ps.setInt(2, limite);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String nome = rs.getString("Nome_Completo");
                        String usuario = rs.getString("Usuario");
                        java.sql.Timestamp ts = rs.getTimestamp("Data_Hora");
                        lista.add(new Registro(rs.getLong("ID_Log"),
                                ts == null ? null : ts.toLocalDateTime(),
                                nome != null ? nome : usuario,
                                usuario,
                                rs.getString("Acao"),
                                rs.getString("Detalhes")));
                    }
                }
            }
        }
        java.util.Collections.reverse(lista); // veio do mais novo para o mais antigo
        return lista;
    }

    // =========================================================================
    // AJUDANTES
    // =========================================================================

    /** Corta o texto no tamanho máximo da coluna ("..." no final quando cortar). */
    static String limitar(String texto, int max) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim();
        if (t.length() <= max) {
            return t;
        }
        return t.substring(0, Math.max(0, max - 3)) + "...";
    }

    /** "007" -> 7. Devolve null se vazio ou não numérico. */
    static Long idNumerico(String id) {
        if (id == null || id.trim().isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
