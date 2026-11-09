import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Cadastro de CARGOS (tabela `cargos`), gerenciado pelo administrador na aba
 * "Cargos" da tela de Funcionários. No cadastro de funcionário o cargo é escolhido
 * numa lista, em vez de digitado.
 *
 * - A tabela é criada sozinha na primeira vez, já com "Funcionário", "Gerente" e
 *   "Bibliotecário", mais qualquer cargo que os funcionários atuais já tenham.
 * - O funcionário guarda o NOME do cargo (coluna funcionários.Cargo). Por isso, ao
 *   renomear um cargo, todos os funcionários com ele são atualizados junto.
 * - O cargo "Bibliotecário" é protegido (não pode ser excluído nem renomeado),
 *   porque é ele que libera a tela da Biblioteca (ver Sessao.isBibliotecario).
 */
public final class RepositorioCargos {

    /** Linha da aba Cargos. */
    public static final class Cargo {
        public final long id;
        public final String nome;
        public final boolean ativo;
        public final int funcionarios; // quantos funcionários usam este cargo

        Cargo(long id, String nome, boolean ativo, int funcionarios) {
            this.id = id;
            this.nome = nome;
            this.ativo = ativo;
            this.funcionarios = funcionarios;
        }
    }

    /** Regra de negócio violada (mensagem pronta para mostrar ao usuário). */
    public static final class CargoException extends Exception {
        public CargoException(String mensagem) {
            super(mensagem);
        }
    }

    private static final String SQL_CRIAR = "CREATE TABLE IF NOT EXISTS cargos ("
            + "ID_Cargo INT AUTO_INCREMENT PRIMARY KEY, "
            + "Nome VARCHAR(50) NOT NULL UNIQUE, "
            + "Ativo BOOLEAN NOT NULL DEFAULT TRUE)";

    private static volatile boolean tabelaPronta = false;

    private RepositorioCargos() {
    }

    // =========================================================================
    // CONSULTAS
    // =========================================================================

    /** Todos os cargos, com quantos funcionários usam cada um (aba Cargos). */
    public static List<Cargo> listarTodos() throws SQLException {
        String sql = "SELECT c.ID_Cargo, c.Nome, c.Ativo, "
                + "(SELECT COUNT(*) FROM funcionários f WHERE TRIM(f.Cargo) = c.Nome) AS Qtd "
                + "FROM cargos c ORDER BY c.Nome";
        List<Cargo> lista = new ArrayList<>();
        try (Connection con = conectar()) {
            garantirTabela(con);
            try (PreparedStatement ps = con.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new Cargo(rs.getLong("ID_Cargo"), rs.getString("Nome"),
                            rs.getBoolean("Ativo"), rs.getInt("Qtd")));
                }
            }
        }
        return lista;
    }

    /** Nomes dos cargos ativos, para a lista de escolha do cadastro de funcionário. */
    public static List<String> listarAtivos() throws SQLException {
        List<String> nomes = new ArrayList<>();
        try (Connection con = conectar()) {
            garantirTabela(con);
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT Nome FROM cargos WHERE Ativo = TRUE ORDER BY Nome");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    nomes.add(rs.getString(1));
                }
            }
        }
        return nomes;
    }

    // =========================================================================
    // GRAVAÇÃO
    // =========================================================================

    /**
     * Cadastra (id < 0) ou edita um cargo. Na edição, se o nome mudou, os
     * funcionários com o nome antigo passam a ter o novo (mesma transação).
     */
    public static void salvar(long id, String nome, boolean ativo) throws SQLException, CargoException {
        String novoNome = nome == null ? "" : nome.trim();
        if (novoNome.isEmpty()) {
            throw new CargoException("Informe o nome do cargo.");
        }
        if (novoNome.length() > 50) {
            throw new CargoException("O nome do cargo pode ter no máximo 50 caracteres.");
        }

        try (Connection con = conectar()) {
            garantirTabela(con);
            if (id < 0) {
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO cargos (Nome, Ativo) VALUES (?, ?)")) {
                    ps.setString(1, novoNome);
                    ps.setBoolean(2, ativo);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    throw traduzirDuplicado(e, novoNome);
                }
                return;
            }

            con.setAutoCommit(false);
            try {
                String nomeAntigo = nomeDoCargo(con, id);
                if (Sessao.cargoEhBibliotecario(nomeAntigo) && !Sessao.cargoEhBibliotecario(novoNome)) {
                    throw new CargoException("O cargo \"" + nomeAntigo + "\" não pode ser renomeado:\n"
                            + "é ele que dá acesso à tela da Biblioteca.");
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE cargos SET Nome = ?, Ativo = ? WHERE ID_Cargo = ?")) {
                    ps.setString(1, novoNome);
                    ps.setBoolean(2, ativo);
                    ps.setLong(3, id);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    throw traduzirDuplicado(e, novoNome);
                }
                if (!nomeAntigo.equals(novoNome)) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE funcionários SET Cargo = ? WHERE TRIM(Cargo) = ?")) {
                        ps.setString(1, novoNome);
                        ps.setString(2, nomeAntigo);
                        ps.executeUpdate();
                    }
                }
                con.commit();
            } catch (SQLException | CargoException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Exclui um cargo que nenhum funcionário usa. Devolve o nome excluído (para a auditoria). */
    public static String excluir(long id) throws SQLException, CargoException {
        try (Connection con = conectar()) {
            garantirTabela(con);
            String nome = nomeDoCargo(con, id);
            if (Sessao.cargoEhBibliotecario(nome)) {
                throw new CargoException("O cargo \"" + nome + "\" não pode ser excluído:\n"
                        + "é ele que dá acesso à tela da Biblioteca.");
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT COUNT(*) FROM funcionários WHERE TRIM(Cargo) = ?")) {
                ps.setString(1, nome);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    int qtd = rs.getInt(1);
                    if (qtd > 0) {
                        throw new CargoException("Não é possível excluir: " + qtd + " funcionário(s) têm o cargo \""
                                + nome + "\".\n\nTroque o cargo deles antes, ou desative o cargo\n"
                                + "(ele some da lista de novos cadastros).");
                    }
                }
            }
            try (PreparedStatement ps = con.prepareStatement("DELETE FROM cargos WHERE ID_Cargo = ?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
            return nome;
        }
    }

    // =========================================================================
    // INTERNOS
    // =========================================================================

    /** Cria a tabela na primeira vez com os cargos básicos + os que já existem nos funcionários. */
    private static void garantirTabela(Connection con) throws SQLException {
        if (tabelaPronta) {
            return;
        }
        try (Statement st = con.createStatement()) {
            st.executeUpdate(SQL_CRIAR);
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM cargos")) {
                if (rs.next() && rs.getLong(1) == 0) {
                    st.executeUpdate("INSERT IGNORE INTO cargos (Nome) VALUES "
                            + "('Funcionário'), ('Gerente'), ('Bibliotecário')");
                    // cargos já digitados nos funcionários atuais (para nenhum sumir da lista)
                    st.executeUpdate("INSERT IGNORE INTO cargos (Nome) "
                            + "SELECT DISTINCT TRIM(Cargo) FROM funcionários "
                            + "WHERE Cargo IS NOT NULL AND TRIM(Cargo) <> ''");
                }
            }
        }
        tabelaPronta = true;
    }

    private static String nomeDoCargo(Connection con, long id) throws SQLException, CargoException {
        try (PreparedStatement ps = con.prepareStatement("SELECT Nome FROM cargos WHERE ID_Cargo = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new CargoException("Cargo não encontrado (pode ter sido excluído por outra pessoa).");
                }
                return rs.getString(1);
            }
        }
    }

    /** Nome repetido (Nome é UNIQUE) vira uma mensagem amigável. */
    private static SQLException traduzirDuplicado(SQLException e, String nome) {
        if (e.getErrorCode() == 1062) {
            return new SQLException("Já existe um cargo com o nome \"" + nome + "\".", e.getSQLState(), 1062, e);
        }
        return e;
    }

    private static Connection conectar() throws SQLException {
        Connection con = Conexao.conectar();
        if (con == null) {
            throw new SQLException("Não foi possível conectar ao banco de dados.");
        }
        return con;
    }
}
