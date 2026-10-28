import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class Funcionario {

    private static final int ITERACOES = 65536;
    private static final int TAMANHO_CHAVE = 256;

    private final String id;
    private String nome;
    private String cpf;
    private String telefone;
    private String email;
    private String cargo;
    private String usuario;
    private String salt;
    private String senhaHash;
    private boolean permissaoAdmin;
    private boolean ativo;

    // Construtor para novo funcionário
    public Funcionario(String id, String nome, String cpf, String telefone, String email,
            String cargo, String usuario, String senha, boolean permissaoAdmin,
            boolean ativo, boolean managerMasterIgnorado) {
        this.id = id;
        this.nome = nome;
        this.cpf = cpf;
        this.telefone = telefone;
        this.email = email;
        this.cargo = cargo;
        this.usuario = usuario;
        this.permissaoAdmin = permissaoAdmin;
        this.ativo = ativo;
        definirSenha(senha);
    }

    // Construtor para uso do banco (onde já temos os hashes)
    public Funcionario(String id, String nome, String cpf, String telefone, String email,
            String cargo, String usuario, String salt, String senhaHash, boolean permissaoAdmin,
            boolean ativo, boolean managerMasterIgnorado) {
        this.id = id;
        this.nome = nome;
        this.cpf = cpf;
        this.telefone = telefone;
        this.email = email;
        this.cargo = cargo;
        this.usuario = usuario;
        this.salt = salt;
        this.senhaHash = senhaHash;
        this.permissaoAdmin = permissaoAdmin;
        this.ativo = ativo;
    }

    public final void definirSenha(String senha) {
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        this.salt = Base64.getEncoder().encodeToString(saltBytes);
        this.senhaHash = gerarHash(senha, saltBytes);
    }

    public boolean senhaConfere(String senhaDigitada) {
        if (salt == null || salt.isEmpty()) return false;
        byte[] saltBytes = Base64.getDecoder().decode(salt);
        String hashDigitado = gerarHash(senhaDigitada, saltBytes);
        return MessageDigest.isEqual(
                hashDigitado.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                senhaHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static String gerarHash(String senha, byte[] saltBytes) {
        try {
            PBEKeySpec spec = new PBEKeySpec(senha.toCharArray(), saltBytes, ITERACOES, TAMANHO_CHAVE);
            SecretKeyFactory fabrica = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return Base64.getEncoder().encodeToString(fabrica.generateSecret(spec).getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Não foi possível proteger a senha", e);
        }
    }

    // Getters e Setters
    public String getId() { return id; }
    public String getNome() { return nome; }
    public String getCpf() { return cpf; }
    public String getTelefone() { return telefone; }
    public String getEmail() { return email; }
    public String getCargo() { return cargo; }
    public String getUsuario() { return usuario; }
    public boolean isPermissaoAdmin() { return permissaoAdmin; }
    public boolean isAtivo() { return ativo; }

    public void setNome(String nome) { this.nome = nome; }
    public void setCpf(String cpf) { this.cpf = cpf; }
    public void setTelefone(String telefone) { this.telefone = telefone; }
    public void setEmail(String email) { this.email = email; }
    public void setCargo(String cargo) { this.cargo = cargo; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public void setPermissaoAdmin(boolean permissaoAdmin) { this.permissaoAdmin = permissaoAdmin; }
    public void setAtivo(boolean ativo) { this.ativo = ativo; }
}