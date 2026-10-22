import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Guarda os funcionários em memória enquanto o programa está aberto.
 *
 * É estático de propósito: a tela de login e a tela principal (App) são
 * recriadas a cada "Sair"/login, então os funcionários cadastrados precisam
 * continuar existindo entre elas.
 *
 * O Manager Master é fixo (admin / 1234) e sempre existe.
 */
public final class Repositoriofuncionarios {

    public static final String MASTER_USUARIO = "admin";
    private static final String MASTER_SENHA = "1234";

    private static final List<Funcionario> FUNCIONARIOS = new ArrayList<>();
    private static int proximoId = 2; // o 001 é o Manager Master

    static {
        FUNCIONARIOS.add(new Funcionario(
                "001", "Manager Master", "", "", "",
                "Manager Master", MASTER_USUARIO, MASTER_SENHA,
                true, true, true));
    }

    private Repositoriofuncionarios() {
    }

    public static List<Funcionario> listar() {
        return Collections.unmodifiableList(FUNCIONARIOS);
    }

    public static String gerarProximoId() {
        return String.format("%03d", proximoId++);
    }

    public static void adicionar(Funcionario f) {
        FUNCIONARIOS.add(f);
    }

    public static Funcionario buscarPorId(String id) {
        for (Funcionario f : FUNCIONARIOS) {
            if (f.getId().equals(id)) {
                return f;
            }
        }
        return null;
    }

    public static Funcionario buscarPorUsuario(String usuario) {
        for (Funcionario f : FUNCIONARIOS) {
            if (f.getUsuario().equalsIgnoreCase(usuario)) {
                return f;
            }
        }
        return null;
    }

    /** Usuário já usado por OUTRO funcionário (ignorarId = quem está sendo editado, ou null). */
    public static boolean usuarioEmUso(String usuario, String ignorarId) {
        Funcionario f = buscarPorUsuario(usuario);
        return f != null && !f.getId().equals(ignorarId);
    }

    /** CPF já usado por OUTRO funcionário. */
    public static boolean cpfEmUso(String cpf, String ignorarId) {
        for (Funcionario f : FUNCIONARIOS) {
            if (!f.getId().equals(ignorarId) && !f.getCpf().isEmpty() && f.getCpf().equals(cpf)) {
                return true;
            }
        }
        return false;
    }

    /** Retorna o funcionário se usuário e senha conferem; senão null. (Não olha o status.) */
    public static Funcionario autenticar(String usuario, String senha) {
        Funcionario f = buscarPorUsuario(usuario);
        if (f != null && f.senhaConfere(senha)) {
            return f;
        }
        return null;
    }
}