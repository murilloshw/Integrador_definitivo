import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Conexao {
    // Altere as credenciais de acordo com a sua base de dados MySQL local
    private static final String URL = "jdbc:mysql://localhost:3306/IntegradorArcade";
    private static final String USER = "root";
    private static final String PASSWORD = "sua_senha";

    public static Connection conectar() {
        try {
            Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("Conexao com o MySQL estabelecida com sucesso!");
            return conn;
        } catch (SQLException e) {
            System.err.println("Erro ao conectar com a base de dados: " + e.getMessage());
            return null;
        }
    }
}