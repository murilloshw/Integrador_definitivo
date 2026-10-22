/** Guarda quem está logado no momento. */
public final class Sessao {

    private static Funcionario logado;

    private Sessao() {
    }

    public static void iniciar(Funcionario f) {
        logado = f;
    }

    public static void encerrar() {
        logado = null;
    }

    public static Funcionario getLogado() {
        return logado;
    }

    /** Só o Manager Master pode cadastrar/editar funcionários e outras ações restritas. */
    public static boolean isManagerMaster() {
        return logado != null && logado.isManagerMaster();
    }
}
