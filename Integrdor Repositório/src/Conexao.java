import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class Conexao {
    // Altere as credenciais de acordo com a sua base de dados MySQL remoto
       private static final String URL = "jdbc:mysql://mysql-14e29d2-integradorarcade.b.aivencloud.com:17853/integradorarcade?sslMode=REQUIRED";
    private static final String USER = "avnadmin";
    private static final String PASSWORD = "AVNS_OE5PEonim4HMG18g_M9";

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