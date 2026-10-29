import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Base64;

public class AtualizarSenhaMaster {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.out.println("Uso: java AtualizarSenhaMaster <usuario> <nova_senha>");
            return;
        }
        String usuario = args[0];
        String senha = args[1];

        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        String hash = Funcionario.gerarHash(senha, saltBytes);

        try (Connection con = Conexao.conectar()) {
            if (con == null) {
                System.out.println("Sem conexão com o banco.");
                return;
            }
            con.setAutoCommit(false);
            try {
                int linhas;
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE funcionários SET Senha = ? WHERE Usuário = ?")) {
                    ps.setString(1, hash);
                    ps.setString(2, usuario);
                    linhas = ps.executeUpdate();
                }

                if (linhas != 1) {
                    con.rollback();
                    System.out.println("Usuário não encontrado na tabela Funcionários. Nada foi alterado.");
                    return;
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO Seguranca_Senhas (Usuario, Salt) VALUES (?, ?) " +
                        "ON DUPLICATE KEY UPDATE Salt = ?")) {
                    ps.setString(1, usuario);
                    ps.setString(2, salt);
                    ps.setString(3, salt);
                    ps.executeUpdate();
                }

                con.commit();
                System.out.println("Senha do usuário '" + usuario + "' atualizada com sucesso.");
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }
}