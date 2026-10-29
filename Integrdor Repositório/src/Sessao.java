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

    /** Agora a permissão vem do banco de dados (Permissão_ADM). */
    public static boolean isManagerMaster() {
        return logado != null && logado.isPermissaoAdmin();
    }
}