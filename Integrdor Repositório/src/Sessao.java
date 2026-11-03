import java.text.Normalizer;

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

    /** Acesso à Biblioteca: só funcionários com cargo "bibliotecário". */
    public static boolean isBibliotecario() {
        return logado != null && cargoEhBibliotecario(logado.getCargo());
    }

    /** Ignora maiúsculas, minúsculas e acentos ("Bibliotecário", "BIBLIOTECARIO" etc.). */
    static boolean cargoEhBibliotecario(String cargo) {
        if (cargo == null) {
            return false;
        }
        String semAcento = Normalizer.normalize(cargo, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.trim().equalsIgnoreCase("bibliotecario");
    }
}