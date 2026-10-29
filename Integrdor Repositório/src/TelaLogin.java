import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class TelaLogin extends JFrame {

    private JTextField email;
    private JPasswordField senha;
    private JButton olho;
    private JButton login;
    private boolean mostrarSenha = false;
    private CarregandoOverlay overlay;
    private boolean carregando = false;

    private static final Color FUNDO = new Color(1, 7, 18);
    private static final Color FUNDO_AZUL = new Color(2, 12, 27);
    private static final Color PAINEL = new Color(5, 16, 31);
    private static final Color CAMPO = new Color(3, 13, 27);
    private static final Color AZUL = new Color(32, 139, 248);
    private static final Color AZUL_BORDA = new Color(39, 105, 168);
    private static final Color AZUL_TEXTO = new Color(119, 182, 242);
    private static final Color BRANCO = new Color(245, 247, 250);

    private static final double BASE_W = 1664.0;
    private static final double BASE_H = 936.0;

    // ---------------------------------------------------------------
    // Resultado da autenticação (cada caso vira uma mensagem diferente)
    // ---------------------------------------------------------------
    private enum Resultado {
        SUCESSO,
        CREDENCIAIS_INVALIDAS,
        BLOQUEADO,
        SEM_SENHA,
        SEM_CONEXAO,
        ERRO_BANCO
    }

    private static class ResultadoLogin {
        final Resultado tipo;
        final Funcionario funcionario;

        ResultadoLogin(Resultado tipo, Funcionario funcionario) {
            this.tipo = tipo;
            this.funcionario = funcionario;
        }
    }

    public TelaLogin() {
        setTitle("Arcade");
        setUndecorated(true);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        SairComEsc.instalar(); // ESC encerra o sistema também nesta tela
        verificarEstruturaBanco();

        criarTela();
        setVisible(true);
    }

    private void verificarEstruturaBanco() {
        try (Connection con = Conexao.conectar()) {
            if (con != null) {
                try (Statement st = con.createStatement()) {
                    String sql = "CREATE TABLE IF NOT EXISTS Seguranca_Senhas (" +
                                 "Usuario VARCHAR(50) PRIMARY KEY, " +
                                 "Salt VARCHAR(50) NOT NULL)";
                    st.executeUpdate(sql);
                } catch (Exception e) {
                    System.err.println("Aviso: não foi possível verificar a tabela Seguranca_Senhas: " + e.getMessage());
                }
            } else {
                System.err.println("Aviso: sem conexão com o banco ao iniciar a tela de login.");
            }
        } catch (Exception ex) {
            System.err.println("Erro de conexão: " + ex.getMessage());
        }
    }

    private void criarTela() {
        Tela tela = new Tela();
        setContentPane(tela);

        email = new JTextField();
        email.setFont(new Font("Arial", Font.PLAIN, 16));
        email.setForeground(BRANCO);
        email.setCaretColor(BRANCO);
        email.setOpaque(false);
        email.setBorder(new EmptyBorder(0, 68, 0, 10));
        tela.add(email);

        senha = new JPasswordField();
        senha.setFont(new Font("Arial", Font.PLAIN, 16));
        senha.setForeground(BRANCO);
        senha.setCaretColor(BRANCO);
        senha.setOpaque(false);
        senha.setEchoChar('●');
        senha.setBorder(new EmptyBorder(0, 68, 0, 60));
        tela.add(senha);

        olho = new JButton();
        olho.setOpaque(false);
        olho.setContentAreaFilled(false);
        olho.setBorderPainted(false);
        olho.setFocusPainted(false);
        olho.setText("");
        olho.setCursor(new Cursor(Cursor.HAND_CURSOR));
        olho.addActionListener(e -> alternarSenha());
        tela.add(olho);

        login = new JButton();
        login.setOpaque(false);
        login.setContentAreaFilled(false);
        login.setBorderPainted(false);
        login.setFocusPainted(false);
        login.setCursor(new Cursor(Cursor.HAND_CURSOR));
        login.addActionListener(e -> fazerLogin());
        tela.add(login);

        tela.instalarAnimacaoBotao(login);

        overlay = new CarregandoOverlay();
        setGlassPane(overlay);

        email.addActionListener(e -> { tela.animarCliqueTeclado(); fazerLogin(); });
        senha.addActionListener(e -> { tela.animarCliqueTeclado(); fazerLogin(); });

    }

    private void alternarSenha() {
        mostrarSenha = !mostrarSenha;
        senha.setEchoChar(mostrarSenha ? (char) 0 : '●');
        senha.requestFocus();
        repaint();
    }

    // ---------------------------------------------------------------
    // LOGIN
    // ---------------------------------------------------------------

    private void fazerLogin() {
        if (carregando) return;

        String usuario = email.getText().trim();
        String senhaDigitada = new String(senha.getPassword());

        if (usuario.isEmpty() || senhaDigitada.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Preencha o usuário e a senha.", "Atenção", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ResultadoLogin resultado = autenticar(usuario, senhaDigitada);

        if (resultado.tipo == Resultado.SUCESSO) {
            Funcionario funcionario = resultado.funcionario;
            Sessao.iniciar(funcionario); // a permissão de admin vem do campo Permissão_ADM
            iniciarCarregamento(funcionario);
            return;
        }

        switch (resultado.tipo) {
            case CREDENCIAIS_INVALIDAS:
                JOptionPane.showMessageDialog(this, "Usuário ou senha incorretos.", "Erro", JOptionPane.ERROR_MESSAGE);
                senha.setText("");
                senha.requestFocus();
                break;

            case BLOQUEADO:
                JOptionPane.showMessageDialog(this,
                        "Este funcionário está bloqueado.\nProcure o Manager Master.",
                        "Acesso bloqueado", JOptionPane.WARNING_MESSAGE);
                break;

            case SEM_SENHA:
                JOptionPane.showMessageDialog(this,
                        "Este usuário ainda não tem senha configurada.\nPeça ao Manager Master para definir uma.",
                        "Acesso indisponível", JOptionPane.WARNING_MESSAGE);
                break;

            case SEM_CONEXAO:
                falhaNaConexao();
                break;

            case ERRO_BANCO:
            default:
                JOptionPane.showMessageDialog(this,
                        "Erro ao consultar o banco de dados.\nVeja o console para mais detalhes.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
                break;
        }
    }

    /**
     * Confere usuário e senha direto na tabela Funcionários.
     * Ordem das regras:
     *  1. o usuário existe?            -> senão: credenciais inválidas
     *  2. o usuário tem salt/senha?    -> senão: sem senha configurada
     *  3. a senha confere?             -> senão: credenciais inválidas
     *  4. o funcionário está ativo?    -> senão: bloqueado
     *  5. ok -> sucesso (o campo Permissão_ADM vai junto no objeto)
     */
    private ResultadoLogin autenticar(String usuario, String senhaDigitada) {
        String sql = "SELECT f.ID_Funcionário, f.Nome_Completo, f.CPF, f.Telefone, f.Email, " +
                     "f.Cargo, f.Usuário, f.Senha, f.Permissão_ADM, f.Ativo, s.Salt " +
                     "FROM funcionários f " +
                     "LEFT JOIN Seguranca_Senhas s ON f.Usuário = s.Usuario " +
                     "WHERE f.Usuário = ?";

        try (Connection con = Conexao.conectar()) {
            if (con == null) {
                return new ResultadoLogin(Resultado.SEM_CONEXAO, null);
            }

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, usuario);

                try (ResultSet rs = ps.executeQuery()) {
                    // 1. usuário existe?
                    if (!rs.next()) {
                        return new ResultadoLogin(Resultado.CREDENCIAIS_INVALIDAS, null);
                    }

                    String salt = rs.getString("Salt");
                    String hash = rs.getString("Senha");

                    // 2. tem senha configurada?
                    if (salt == null || salt.isEmpty() || hash == null || hash.isEmpty()) {
                        return new ResultadoLogin(Resultado.SEM_SENHA, null);
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
                            false
                    );

                    // 3. senha confere?
                    if (!f.senhaConfere(senhaDigitada)) {
                        return new ResultadoLogin(Resultado.CREDENCIAIS_INVALIDAS, null);
                    }

                    // 4. ativo?
                    if (!f.isAtivo()) {
                        return new ResultadoLogin(Resultado.BLOQUEADO, null);
                    }

                    // 5. sucesso
                    return new ResultadoLogin(Resultado.SUCESSO, f);
                }
            }
        } catch (SQLException ex) {
            System.err.println("Erro de banco no login: " + ex.getMessage());
            ex.printStackTrace();
            return new ResultadoLogin(Resultado.ERRO_BANCO, null);
        } catch (RuntimeException ex) {
            // ex.: salt que não está em Base64
            System.err.println("Erro ao validar a senha: " + ex.getMessage());
            ex.printStackTrace();
            return new ResultadoLogin(Resultado.ERRO_BANCO, null);
        }
    }

    private void iniciarCarregamento(Funcionario funcionario) {
        carregando = true;
        bloquearEntrada(true);
        overlay.mostrar(funcionario.getNome());

        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return Conexao.aquecer(4);
            }

            @Override
            protected void done() {
                boolean conectou;
                try {
                    conectou = get();
                } catch (Exception ex) {
                    conectou = false;
                }

                if (!conectou) {
                    falhaNaConexao();
                    return;
                }

                overlay.irParaEtapa(2);
                javax.swing.Timer pausa = new javax.swing.Timer(150, ev -> abrirApp());
                pausa.setRepeats(false);
                pausa.start();
            }
        }.execute();
    }

    private void abrirApp() {
        try {
            App app = new App();
            app.setVisible(true);
            overlay.ocultar();
            dispose();
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            cancelarCarregamento();
            JOptionPane.showMessageDialog(this, "Erro ao abrir o sistema:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void falhaNaConexao() {
        cancelarCarregamento();
        String[] opcoes = { "Tentar novamente", "Cancelar" };
        int escolha = JOptionPane.showOptionDialog(this,
                "Não foi possível conectar ao banco de dados.\nVerifique sua conexão e tente novamente.",
                "Sem conexão", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE,
                null, opcoes, opcoes[0]);

        if (escolha == 0) {
            fazerLogin();
        }
    }

    private void cancelarCarregamento() {
        overlay.ocultar();
        bloquearEntrada(false);
        Sessao.encerrar();
        carregando = false;
    }

    private void bloquearEntrada(boolean bloquear) {
        email.setEnabled(!bloquear);
        senha.setEnabled(!bloquear);
        login.setEnabled(!bloquear);
        olho.setEnabled(!bloquear);
    }

    // =========================================================================
    // PAINEL PRINCIPAL E ANIMAÇÕES VISUAIS
    // =========================================================================
    class Tela extends JPanel {
        private double escala = 1.0;
        private int deslocamentoX, deslocamentoY;
        private float hover = 0f, pressao = 0f, ripple = -1f, rippleX = 831f, rippleY = 648f;
        private boolean mouseSobre = false, mousePressionado = false;
        private final javax.swing.Timer animador = new javax.swing.Timer(16, e -> animarBotao());

        void instalarAnimacaoBotao(JButton botao) {
            botao.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { mouseSobre = true; animador.start(); }
                @Override public void mouseExited(MouseEvent e) { mouseSobre = false; mousePressionado = false; animador.start(); }
                @Override public void mousePressed(MouseEvent e) {
                    if (!botao.isEnabled()) return;
                    mousePressionado = true;
                    ripple = 0f;
                    rippleX = 664f + (float) (e.getX() / escala);
                    rippleY = 617f + (float) (e.getY() / escala);
                    animador.start();
                }
                @Override public void mouseReleased(MouseEvent e) { mousePressionado = false; animador.start(); }
            });
        }

        void animarCliqueTeclado() {
            mousePressionado = true;
            ripple = 0f; rippleX = 831f; rippleY = 648f;
            animador.start();
            javax.swing.Timer soltar = new javax.swing.Timer(140, e -> { mousePressionado = false; animador.start(); });
            soltar.setRepeats(false); soltar.start();
        }

        private void animarBotao() {
            float alvoHover = mouseSobre ? 1f : 0f;
            float alvoPressao = mousePressionado ? 1f : 0f;
            hover = aproximar(hover, alvoHover, 0.22f);
            pressao = aproximar(pressao, alvoPressao, 0.40f);
            if (ripple >= 0f) { ripple += 0.05f; if (ripple >= 1f) ripple = -1f; }
            repaint(login.getX() - 30, login.getY() - 30, login.getWidth() + 60, login.getHeight() + 60);
            if (hover == alvoHover && pressao == alvoPressao && ripple < 0f) animador.stop();
        }

        private float aproximar(float atual, float alvo, float velocidade) {
            float novo = atual + (alvo - atual) * velocidade;
            return Math.abs(alvo - novo) < 0.01f ? alvo : novo;
        }

        private Color misturar(Color a, Color b, float t) {
            t = Math.max(0f, Math.min(1f, t));
            return new Color(
                    (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                    (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                    (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
        }

        public Tela() { setLayout(null); setBackground(FUNDO); }

        private void calcularEscala() {
            double escalaX = getWidth() / BASE_W;
            double escalaY = getHeight() / BASE_H;
            escala = Math.min(escalaX, escalaY);
            int larguraFinal = (int) (BASE_W * escala);
            int alturaFinal = (int) (BASE_H * escala);
            deslocamentoX = (getWidth() - larguraFinal) / 2;
            deslocamentoY = (getHeight() - alturaFinal) / 2;
        }

        private int PX(double x) { return deslocamentoX + (int) (x * escala); }
        private int PY(double y) { return deslocamentoY + (int) (y * escala); }
        private int PS(double tamanho) { return (int) (tamanho * escala); }

        @Override
        public void doLayout() {
            calcularEscala();
            email.setBounds(PX(640), PY(421), PS(382), PS(54));
            senha.setBounds(PX(640), PY(526), PS(382), PS(55));
            olho.setBounds(PX(1039), PY(526), PS(57), PS(55));
            login.setBounds(PX(664), PY(617), PS(334), PS(62));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            calcularEscala();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setPaint(new GradientPaint(0, 0, FUNDO_AZUL, getWidth(), getHeight(), FUNDO));
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.translate(deslocamentoX, deslocamentoY);
            g2.scale(escala, escala);

            g2.setColor(PAINEL);
            g2.fillRoundRect(585, 172, 492, 582, 17, 17);
            g2.setColor(AZUL_BORDA);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(585, 172, 492, 582, 17, 17);

            desenharLogoArcade(g2, 650, 120, 360, 105);
            g2.setColor(AZUL_TEXTO);
            g2.setFont(new Font("Arial", Font.PLAIN, 40));
            g2.drawString("ARCADE", 750, 300);

            g2.setColor(AZUL_TEXTO);
            g2.setFont(new Font("Arial", Font.PLAIN, 17));
            g2.drawString("Usuário", 640, 408);
            desenharCampo(g2, 640, 421, 382, 54);
            desenharEmail(g2, 657, 438);

            g2.drawString("Password", 640, 513);
            desenharCampo(g2, 640, 526, 382, 55);
            desenharCadeado(g2, 658, 542);

            desenharOlho(g2, 1039, 544);
            desenharBotaoLogin(g2);
            g2.dispose();
        }

        private void desenharBotaoLogin(Graphics2D g2) {
            Graphics2D g = (Graphics2D) g2.create();
            float cx = 664f + 334f / 2f;
            float cy = 617f + 62f / 2f;
            float encolher = 1f - 0.045f * pressao;
            g.translate(cx, cy); g.scale(encolher, encolher); g.translate(-cx, -cy);

            float brilho = hover * (1f - pressao);
            if (brilho > 0.01f) {
                for (int i = 10; i >= 2; i -= 4) {
                    g.setColor(new Color(32, 139, 248, (int) (34 * brilho)));
                    g.fillRoundRect(664 - i, 617 - i, 334 + i * 2, 62 + i * 2, 10 + i * 2, 10 + i * 2);
                }
            }

            Color cor = misturar(AZUL, new Color(66, 162, 255), hover * 0.65f);
            cor = misturar(cor, new Color(16, 92, 182), pressao);
            g.setColor(cor);
            g.fillRoundRect(664, 617, 334, 62, 10, 10);

            if (ripple >= 0f) {
                Graphics2D gr = (Graphics2D) g.create();
                gr.clip(new RoundRectangle2D.Float(664, 617, 334, 62, 10, 10));
                float raio = ripple * 360f;
                int alfa = (int) (95 * (1f - ripple));
                gr.setColor(new Color(255, 255, 255, Math.max(0, alfa)));
                gr.fill(new Ellipse2D.Float(rippleX - raio, rippleY - raio, raio * 2, raio * 2));
                gr.dispose();
            }

            g.setColor(misturar(new Color(70, 166, 255), new Color(140, 200, 255), hover));
            g.setStroke(new BasicStroke(1));
            g.drawRoundRect(664, 617, 334, 62, 10, 10);

            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.PLAIN, 22));
            String texto = "Log in";
            FontMetrics fm = g.getFontMetrics();
            g.drawString(texto, 831 - fm.stringWidth(texto) / 2, 617 + (62 - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }

        private void desenharLogoArcade(Graphics2D g, int x, int y, int larguraMax, int alturaMax) {
            ImageIcon logo = carregarLogo();
            if (logo == null) return;
            Image imagem = logo.getImage();
            int lOrig = logo.getIconWidth();
            int aOrig = logo.getIconHeight();
            if (lOrig <= 0 || aOrig <= 0) return;
            double escalaLogo = Math.min((double) larguraMax / lOrig, (double) alturaMax / aOrig);
            int l = (int) (lOrig * escalaLogo);
            int a = (int) (aOrig * escalaLogo);
            g.drawImage(imagem, x + (larguraMax - l) / 2, y + (alturaMax - a) / 2, l, a, null);
        }

        private void desenharCampo(Graphics2D g, int x, int y, int w, int h) {
            g.setColor(CAMPO);
            g.fillRoundRect(x, y, w, h, 7, 7);
            g.setColor(new Color(39, 91, 145));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(x, y, w, h, 7, 7);
        }

        private ImageIcon carregarLogo() {
            URL resource = getClass().getResource("/images/Logo_arcade.png");
            if (resource != null) return new ImageIcon(resource);
            String[] caminhos = { "src/images/Logo_arcade.png", "images/Logo_arcade.png", "./src/images/Logo_arcade.png", "./images/Logo_arcade.png" };
            for (String caminho : caminhos) {
                java.io.File arquivo = new java.io.File(caminho);
                if (arquivo.exists()) return new ImageIcon(arquivo.getAbsolutePath());
            }
            return null;
        }

        private void desenharEmail(Graphics2D g, int x, int y) {
            g.setColor(AZUL_TEXTO); g.setStroke(new BasicStroke(2));
            g.drawRoundRect(x, y, 23, 18, 3, 3);
            g.drawLine(x + 1, y + 1, x + 11, y + 10);
            g.drawLine(x + 22, y + 1, x + 11, y + 10);
        }

        private void desenharCadeado(Graphics2D g, int x, int y) {
            g.setColor(AZUL_TEXTO); g.setStroke(new BasicStroke(2));
            g.drawRoundRect(x, y + 9, 20, 16, 3, 3);
            g.drawArc(x + 3, y, 14, 17, 0, 180);
        }

        private void desenharOlho(Graphics2D g, int x, int y) {
            g.setColor(AZUL_TEXTO); g.setStroke(new BasicStroke(2));
            if (mostrarSenha) {
                g.drawOval(x, y, 22, 15); g.fillOval(x + 7, y + 4, 8, 8);
            } else {
                g.drawOval(x, y, 22, 15); g.fillOval(x + 7, y + 4, 8, 8);
                g.drawLine(x - 3, y - 3, x + 25, y + 19);
            }
        }
    }

    class CarregandoOverlay extends JComponent {
        private final String[] etapas = { "Credenciais verificadas", "Conectando ao banco de dados", "Carregando telas do sistema" };
        private int etapaAtual = 1;
        private float fade = 0f, angulo = 0f, barra = 0f;
        private String saudacao = "";
        private final javax.swing.Timer timer = new javax.swing.Timer(20, e -> avancar());

        CarregandoOverlay() {
            setOpaque(false); setVisible(false);
            addMouseListener(new MouseAdapter() {});
            addMouseMotionListener(new MouseMotionAdapter() {});
            addKeyListener(new KeyAdapter() {});
        }

        void mostrar(String nome) {
            saudacao = "Bem-vindo(a), " + nome;
            etapaAtual = 1; fade = 0f; angulo = 0f; barra = 0f;
            setVisible(true); timer.start();
        }

        void ocultar() { timer.stop(); setVisible(false); }
        void irParaEtapa(int etapa) { etapaAtual = etapa; repaint(); }

        private void avancar() {
            angulo = (angulo + 10f) % 360f; barra += 0.014f;
            if (barra > 1f) barra = 0f;
            if (fade < 1f) { fade = Math.min(1f, fade + 0.09f); repaint(); }
            else repaint(areaCartao());
        }

        private Rectangle areaCartao() {
            int largura = 480, altura = 340;
            return new Rectangle((getWidth() - largura) / 2 - 20, (getHeight() - altura) / 2 - 20, largura + 40, altura + 40);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setComposite(AlphaComposite.SrcOver.derive(fade));
            g2.setColor(new Color(1, 7, 18, 215));
            g2.fillRect(0, 0, getWidth(), getHeight());

            int cw = 480, ch = 340;
            int x = (getWidth() - cw) / 2, y = (getHeight() - ch) / 2;

            g2.setColor(new Color(32, 139, 248, 28));
            g2.fillRoundRect(x - 6, y - 6, cw + 12, ch + 12, 26, 26);
            g2.setColor(PAINEL);
            g2.fillRoundRect(x, y, cw, ch, 20, 20);
            g2.setColor(AZUL_BORDA);
            g2.drawRoundRect(x, y, cw, ch, 20, 20);

            g2.setColor(BRANCO);
            g2.setFont(new Font("Arial", Font.BOLD, 24));
            centralizar(g2, "Entrando no sistema", x + cw / 2, y + 62);

            g2.setColor(AZUL_TEXTO);
            g2.setFont(new Font("Arial", Font.PLAIN, 15));
            centralizar(g2, saudacao, x + cw / 2, y + 92);

            for (int i = 0; i < etapas.length; i++) {
                int ex = x + 70, ey = y + 150 + i * 46, icx = ex + 11, icy = ey;
                if (i < etapaAtual) {
                    g2.setColor(new Color(34, 197, 94)); g2.fillOval(icx - 11, icy - 11, 22, 22);
                    g2.setColor(Color.WHITE); g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(icx - 5, icy, icx - 1, icy + 4); g2.drawLine(icx - 1, icy + 4, icx + 5, icy - 4);
                } else if (i == etapaAtual) {
                    g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setColor(new Color(32, 139, 248, 50)); g2.drawOval(icx - 10, icy - 10, 20, 20);
                    g2.setColor(AZUL); g2.drawArc(icx - 10, icy - 10, 20, 20, (int) -angulo, 110);
                } else {
                    g2.setColor(new Color(51, 65, 85)); g2.setStroke(new BasicStroke(2f));
                    g2.drawOval(icx - 10, icy - 10, 20, 20);
                }

                boolean ativa = i == etapaAtual;
                g2.setFont(new Font("Arial", ativa ? Font.BOLD : Font.PLAIN, 17));
                g2.setColor(i < etapaAtual ? new Color(148, 163, 184) : ativa ? BRANCO : new Color(88, 103, 125));
                String texto = etapas[i] + (ativa ? ".".repeat(1 + (int) (angulo / 120f) % 3) : "");
                g2.drawString(texto, ex + 38, ey + 6);
            }

            int bx = x + 40, by = y + ch - 42, bw = cw - 80, bh = 6;
            g2.setColor(new Color(15, 30, 50)); g2.fillRoundRect(bx, by, bw, bh, bh, bh);
            Graphics2D gb = (Graphics2D) g2.create();
            gb.clip(new RoundRectangle2D.Float(bx, by, bw, bh, bh, bh));
            int seg = (int) (bw * 0.35f);
            int sx = bx + (int) (barra * (bw + seg)) - seg;
            gb.setPaint(new GradientPaint(sx, 0, AZUL_BORDA, sx + seg, 0, AZUL));
            gb.fillRoundRect(sx, by, seg, bh, bh, bh);
            gb.dispose(); g2.dispose();
        }

        private void centralizar(Graphics2D g2, String texto, int cx, int baseY) {
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(texto, cx - fm.stringWidth(texto) / 2, baseY);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TelaLogin::new);
    }
}