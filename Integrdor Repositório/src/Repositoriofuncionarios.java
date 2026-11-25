import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Consultas de funcionários usadas fora da tela de login (ex.: saída do autoatendimento).
 * Toda a gestão de funcionários fica no MySQL (tabelas funcionários + Seguranca_Senhas).
 */
public final class Repositoriofuncionarios {

    private Repositoriofuncionarios() {
    }

    /**
     * Confere usuário e senha no banco.
     *
     * NÃO verifica se o funcionário está ativo nem se tem permissão de administrador:
     * quem chama decide o que fazer com o resultado.
     *
     * @return o funcionário, se usuário e senha conferem;
     *         null se o usuário não existe, não tem senha configurada ou a senha está errada.
     * @throws SQLException se o banco não pôde ser consultado.
     */
    public static Funcionario autenticar(String usuario, String senha) throws SQLException {
        String sql = "SELECT f.ID_Funcionário, f.Nome_Completo, f.CPF, f.Telefone, f.Email, "
                + "f.Cargo, f.Usuário, f.Senha, f.Permissão_ADM, f.Ativo, s.Salt "
                + "FROM funcionários f "
                + "LEFT JOIN Seguranca_Senhas s ON f.Usuário = s.Usuario "
                + "WHERE f.Usuário = ?";

        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, usuario);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                String salt = rs.getString("Salt");
                String hash = rs.getString("Senha");
                if (salt == null || salt.isEmpty() || hash == null || hash.isEmpty()) {
                    return null; // sem senha configurada
                }

                Funcionario f = new Funcionario(
                        String.format("%03d", rs.getLong("ID_Funcionário")),
                        rs.getString("Nome_Completo"),
                        rs.getString("CPF"),
                        rs.getString("Telefone"),
                        rs.getString("Email"),
                        rs.getString("Cargo"),
                        rs.getString("Usuário"),
                        salt,
                        hash,
                        rs.getBoolean("Permissão_ADM"),
                        rs.getBoolean("Ativo"),
                        false);

                return f.senhaConfere(senha) ? f : null;
            }
        }
    }

    /** Conexao.conectar() devolve null quando falha; aqui vira exceção para quem chamou tratar. */
    private static Connection conectar() throws SQLException {
        Connection con = Conexao.conectar();
        if (con == null) {
            throw new SQLException("Não foi possível conectar ao banco de dados.");
        }
        return con;
    }
}