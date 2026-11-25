import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.net.URL;
import java.sql.*;

/**
 * Tela de login: confere a senha no banco (PBKDF2 + salt) e abre o App com uma
 * animação de carregamento.
 */
public class TelaLogin extends JFrame {

    private static final Color FUNDO = new Color(1, 7, 18), FUNDO_AZUL = new Color(2, 12, 27),
            PAINEL = new Color(5, 16, 31), CAMPO = new Color(3, 13, 27), AZUL = new Color(32, 139, 248),
            AZUL_BORDA = new Color(39, 105, 168), AZUL_TEXTO = new Color(119, 182, 242),
            BRANCO = new Color(245, 247, 250);
    private static final double BASE_W = 1664, BASE_H = 936; // layout de referência, escalado para a tela

    private JTextField email;
    private JPasswordField senha;
    private JButton olho, login;
    private boolean mostrarSenha, carregando;
    private CarregandoOverlay overlay;

    private enum Resultado {
        SUCESSO, CREDENCIAIS_INVALIDAS, BLOQUEADO, SEM_SENHA, SEM_CONEXAO, ERRO_BANCO
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
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setExtendedState(MAXIMIZED_BOTH);

        SairComEsc.instalar();
        verificarEstruturaBanco();
        criarTela();
        setVisible(true);
    }

    private void verificarEstruturaBanco() {
        try (Connection con = Conexao.conectar()) {
            if (con == null) {
                System.err.println("Aviso: sem conexão com o banco ao iniciar a tela de login.");
                return;
            }
            try (Statement st = con.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS Seguranca_Senhas ("
                        + "Usuario VARCHAR(50) PRIMARY KEY, Salt VARCHAR(50) NOT NULL)");
            }
        } catch (Exception ex) {
            System.err.println("Erro ao verificar a estrutura do banco: " + ex.getMessage());
        }
    }

    private void criarTela() {
        Tela tela = new Tela();
        setContentPane(tela);

        email = campoTransparente(new EmptyBorder(0, 68, 0, 10));
        senha = new JPasswordField();
        estilizarCampo(senha, new EmptyBorder(0, 68, 0, 60));
        senha.setEchoChar('●');

        olho = botaoInvisivel(e -> alternarSenha());
        login = botaoInvisivel(e -> fazerLogin());

        tela.add(email);
        tela.add(senha);
        tela.add(olho);
        tela.add(login);
        tela.instalarAnimacaoBotao(login);

        overlay = new CarregandoOverlay();
        setGlassPane(overlay);

        ActionListener enter = e -> {
            tela.animarCliqueTeclado();
            fazerLogin();
        };
        email.addActionListener(enter);
        senha.addActionListener(enter);
    }

    private JTextField campoTransparente(EmptyBorder borda) {
        JTextField t = new JTextField();
        estilizarCampo(t, borda);
        return t;
    }

    private void estilizarCampo(JTextField t, EmptyBorder borda) {
        t.setFont(new Font("Arial", Font.PLAIN, 16));
        t.setForeground(BRANCO);
        t.setCaretColor(BRANCO);
        t.setOpaque(false);
        t.setBorder(borda);
    }

    private JButton botaoInvisivel(java.awt.event.ActionListener acao) {
        JButton b = new JButton();
        b.setOpaque(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addActionListener(acao);
        return b;
    }

    private void alternarSenha() {
        mostrarSenha = !mostrarSenha;
        senha.setEchoChar(mostrarSenha ? (char) 0 : '●');
        senha.requestFocus();
        repaint();
    }

    // ===================== LOGIN =====================

    private void fazerLogin() {
        if (carregando) {
            return;
        }
        String usuario = email.getText().trim();
        String senhaDigitada = new String(senha.getPassword());

        if (usuario.isEmpty() || senhaDigitada.isEmpty()) {
            aviso("Preencha o usuário e a senha.", "Atenção", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ResultadoLogin r = autenticar(usuario, senhaDigitada);
        switch (r.tipo) {
            case SUCESSO:
                Sessao.iniciar(r.funcionario);
                Auditoria.registrar("LOGIN", null);
                iniciarCarregamento(r.funcionario);
                break;
            case CREDENCIAIS_INVALIDAS:
                Auditoria.registrar(null, usuario, "LOGIN_FALHOU", "Usuário ou senha incorretos");
                aviso("Usuário ou senha incorretos.", "Erro", JOptionPane.ERROR_MESSAGE);
                senha.setText("");
                senha.requestFocus();
                break;
            case BLOQUEADO:
                Auditoria.registrar(null, usuario, "LOGIN_BLOQUEADO", "Tentativa de funcionário inativo");
                aviso("Este funcionário está bloqueado.\nProcure o Manager Master.",
                        "Acesso bloqueado", JOptionPane.WARNING_MESSAGE);
                break;
            case SEM_SENHA:
                aviso("Este usuário ainda não tem senha configurada.\nPeça ao Manager Master para definir uma.",
                        "Acesso indisponível", JOptionPane.WARNING_MESSAGE);
                break;
            case SEM_CONEXAO:
                falhaNaConexao();
                break;
            default:
                aviso("Erro ao consultar o banco de dados.\nVeja o console para mais detalhes.",
                        "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Ordem: usuário existe → tem senha/salt → senha confere → ativo.
     * O campo Permissão_ADM vai junto no objeto Funcionário.
     */
    private ResultadoLogin autenticar(String usuario, String senhaDigitada) {
        String sql = "SELECT f.ID_Funcionário, f.Nome_Completo, f.CPF, f.Telefone, f.Email, f.Cargo, "
                + "f.Usuário, f.Senha, f.Permissão_ADM, f.Ativo, s.Salt "
                + "FROM funcionários f LEFT JOIN Seguranca_Senhas s ON f.Usuário = s.Usuario "
                + "WHERE f.Usuário = ?";

        try (Connection con = Conexao.conectar()) {
            if (con == null) {
                return new ResultadoLogin(Resultado.SEM_CONEXAO, null);
            }
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, usuario);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return new ResultadoLogin(Resultado.CREDENCIAIS_INVALIDAS, null);
                    }

                    String salt = rs.getString("Salt");
                    String hash = rs.getString("Senha");
                    if (vazio(salt) || vazio(hash)) {
                        return new ResultadoLogin(Resultado.SEM_SENHA, null);
                    }

                    Funcionario f = new Funcionario(
                            String.format("%03d", rs.getLong("ID_Funcionário")),
                            rs.getString("Nome_Completo"), rs.getString("CPF"), rs.getString("Telefone"),
                            rs.getString("Email"), rs.getString("Cargo"), rs.getString("Usuário"),
                            salt, hash, rs.getBoolean("Permissão_ADM"), rs.getBoolean("Ativo"), false);

                    if (!f.senhaConfere(senhaDigitada)) {
                        return new ResultadoLogin(Resultado.CREDENCIAIS_INVALIDAS, null);
                    }
                    if (!f.isAtivo()) {
                        return new ResultadoLogin(Resultado.BLOQUEADO, null);
                    }
                    return new ResultadoLogin(Resultado.SUCESSO, f);
                }
            }
        } catch (SQLException ex) {
            System.err.println("Erro de banco no login: " + ex.getMessage());
            return new ResultadoLogin(Resultado.ERRO_BANCO, null);
        } catch (RuntimeException ex) {
            // ex.: salt que não está em Base64
            System.err.println("Erro ao validar a senha: " + ex.getMessage());
            return new ResultadoLogin(Resultado.ERRO_BANCO, null);
        }
    }

    private static boolean vazio(String s) {
        return s == null || s.isEmpty();
    }

    private void aviso(String msg, String titulo, int tipo) {
        JOptionPane.showMessageDialog(this, msg, titulo, tipo);
    }

    // ===================== CARREGAMENTO PÓS-LOGIN =====================

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
            new App().setVisible(true);
            overlay.ocultar();
            dispose();
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            cancelarCarregamento();
            aviso("Erro ao abrir o sistema:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void falhaNaConexao() {
        cancelarCarregamento();
        String[] opcoes = { "Tentar novamente", "Cancelar" };
        int escolha = JOptionPane.showOptionDialog(this,
                "Não foi possível conectar ao banco de dados.\nVerifique sua conexão e tente novamente.",
                "Sem conexão", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, opcoes, opcoes[0]);
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

    // ===================== PAINEL PRINCIPAL =====================

    class Tela extends JPanel {
        private double escala = 1.0;
        private int offX, offY;
        private float hover, pressao, ripple = -1f, rippleX = 831f, rippleY = 648f;
        private boolean mouseSobre, mousePressionado;
        private final javax.swing.Timer animador = new javax.swing.Timer(16, e -> animarBotao());

        Tela() {
            setLayout(null);
            setBackground(FUNDO);
        }

        void instalarAnimacaoBotao(JButton botao) {
            botao.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    mouseSobre = true;
                    animador.start();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    mouseSobre = false;
                    mousePressionado = false;
                    animador.start();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    if (!botao.isEnabled())
                        return;
                    mousePressionado = true;
                    ripple = 0f;
                    rippleX = 664f + (float) (e.getX() / escala);
                    rippleY = 617f + (float) (e.getY() / escala);
                    animador.start();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    mousePressionado = false;
                    animador.start();
                }
            });
        }

        void animarCliqueTeclado() {
            mousePressionado = true;
            ripple = 0f;
            rippleX = 831f;
            rippleY = 648f;
            animador.start();
            javax.swing.Timer soltar = new javax.swing.Timer(140, e -> {
                mousePressionado = false;
                animador.start();
            });
            soltar.setRepeats(false);
            soltar.start();
        }

        private void animarBotao() {
            float alvoHover = mouseSobre ? 1f : 0f;
            float alvoPressao = mousePressionado ? 1f : 0f;
            hover = aproximar(hover, alvoHover, 0.22f);
            pressao = aproximar(pressao, alvoPressao, 0.40f);
            if (ripple >= 0f) {
                ripple += 0.05f;
                if (ripple >= 1f)
                    ripple = -1f;
            }
            repaint(login.getX() - 30, login.getY() - 30, login.getWidth() + 60, login.getHeight() + 60);
            if (hover == alvoHover && pressao == alvoPressao && ripple < 0f) {
                animador.stop();
            }
        }

        private float aproximar(float atual, float alvo, float vel) {
            float novo = atual + (alvo - atual) * vel;
            return Math.abs(alvo - novo) < 0.01f ? alvo : novo;
        }

        private Color misturar(Color a, Color b, float t) {
            t = Math.max(0f, Math.min(1f, t));
            return new Color(
                    (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                    (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                    (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
        }

        private void calcularEscala() {
            escala = Math.min(getWidth() / BASE_W, getHeight() / BASE_H);
            offX = (getWidth() - (int) (BASE_W * escala)) / 2;
            offY = (getHeight() - (int) (BASE_H * escala)) / 2;
        }

        private int px(double x) {
            return offX + (int) (x * escala);
        }

        private int py(double y) {
            return offY + (int) (y * escala);
        }

        private int ps(double t) {
            return (int) (t * escala);
        }

        @Override
        public void doLayout() {
            calcularEscala();
            email.setBounds(px(640), py(421), ps(382), ps(54));
            senha.setBounds(px(640), py(526), ps(382), ps(55));
            olho.setBounds(px(1039), py(526), ps(57), ps(55));
            login.setBounds(px(664), py(617), ps(334), ps(62));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            calcularEscala();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setPaint(new GradientPaint(0, 0, FUNDO_AZUL, getWidth(), getHeight(), FUNDO));
            g2.fillRect(0, 0, getWidth(), getHeight());

            g2.translate(offX, offY);
            g2.scale(escala, escala);

            g2.setColor(PAINEL);
            g2.fillRoundRect(585, 172, 492, 582, 17, 17);
            g2.setColor(AZUL_BORDA);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(585, 172, 492, 582, 17, 17);

            desenharLogo(g2, 650, 120, 360, 105);
            g2.setColor(AZUL_TEXTO);
            g2.setFont(new Font("Arial", Font.PLAIN, 40));
            g2.drawString("ARCADE", 750, 300);

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
            float cx = 664f + 167f, cy = 617f + 31f;
            float encolher = 1f - 0.045f * pressao;
            g.translate(cx, cy);
            g.scale(encolher, encolher);
            g.translate(-cx, -cy);

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
                gr.setColor(new Color(255, 255, 255, Math.max(0, (int) (95 * (1f - ripple)))));
                gr.fill(new Ellipse2D.Float(rippleX - raio, rippleY - raio, raio * 2, raio * 2));
                gr.dispose();
            }

            g.setColor(misturar(new Color(70, 166, 255), new Color(140, 200, 255), hover));
            g.setStroke(new BasicStroke(1));
            g.drawRoundRect(664, 617, 334, 62, 10, 10);

            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.PLAIN, 22));
            FontMetrics fm = g.getFontMetrics();
            g.drawString("Log in", 831 - fm.stringWidth("Log in") / 2,
                    617 + (62 - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }

        private void desenharLogo(Graphics2D g, int x, int y, int maxL, int maxA) {
            ImageIcon logo = carregarLogo();
            if (logo == null || logo.getIconWidth() <= 0)
                return;
            double k = Math.min((double) maxL / logo.getIconWidth(), (double) maxA / logo.getIconHeight());
            int l = (int) (logo.getIconWidth() * k), a = (int) (logo.getIconHeight() * k);
            g.drawImage(logo.getImage(), x + (maxL - l) / 2, y + (maxA - a) / 2, l, a, null);
        }

        private ImageIcon carregarLogo() {
            URL res = getClass().getResource("/images/Logo_arcade.png");
            if (res != null)
                return new ImageIcon(res);
            for (String c : new String[] { "src/images/Logo_arcade.png", "images/Logo_arcade.png",
                    "./src/images/Logo_arcade.png", "./images/Logo_arcade.png" }) {
                File f = new File(c);
                if (f.exists())
                    return new ImageIcon(f.getAbsolutePath());
            }
            return null;
        }

        private void desenharCampo(Graphics2D g, int x, int y, int w, int h) {
            g.setColor(CAMPO);
            g.fillRoundRect(x, y, w, h, 7, 7);
            g.setColor(new Color(39, 91, 145));
            g.setStroke(new BasicStroke(1.5f));
            g.drawRoundRect(x, y, w, h, 7, 7);
        }

        private void desenharEmail(Graphics2D g, int x, int y) {
            g.setColor(AZUL_TEXTO);
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(x, y, 23, 18, 3, 3);
            g.drawLine(x + 1, y + 1, x + 11, y + 10);
            g.drawLine(x + 22, y + 1, x + 11, y + 10);
        }

        private void desenharCadeado(Graphics2D g, int x, int y) {
            g.setColor(AZUL_TEXTO);
            g.setStroke(new BasicStroke(2));
            g.drawRoundRect(x, y + 9, 20, 16, 3, 3);
            g.drawArc(x + 3, y, 14, 17, 0, 180);
        }

        private void desenharOlho(Graphics2D g, int x, int y) {
            g.setColor(AZUL_TEXTO);
            g.setStroke(new BasicStroke(2));
            g.drawOval(x, y, 22, 15);
            g.fillOval(x + 7, y + 4, 8, 8);
            if (!mostrarSenha) {
                g.drawLine(x - 3, y - 3, x + 25, y + 19); // riscado = senha escondida
            }
        }
    }

    // ===================== OVERLAY "ENTRANDO NO SISTEMA" =====================

    class CarregandoOverlay extends JComponent {
        private static final int CW = 480, CH = 340;
        private final String[] etapas = { "Credenciais verificadas", "Conectando ao banco de dados",
                "Carregando telas do sistema" };
        private int etapaAtual = 1;
        private float fade, angulo, barra;
        private String saudacao = "";
        private final javax.swing.Timer timer = new javax.swing.Timer(20, e -> avancar());

        CarregandoOverlay() {
            setOpaque(false);
            setVisible(false);
            // consome cliques e teclas enquanto o overlay está ativo
            addMouseListener(new MouseAdapter() {
            });
            addMouseMotionListener(new MouseMotionAdapter() {
            });
            addKeyListener(new KeyAdapter() {
            });
        }

        void mostrar(String nome) {
            saudacao = "Bem-vindo(a), " + nome;
            etapaAtual = 1;
            fade = angulo = barra = 0f;
            setVisible(true);
            timer.start();
        }

        void ocultar() {
            timer.stop();
            setVisible(false);
        }

        void irParaEtapa(int etapa) {
            etapaAtual = etapa;
            repaint();
        }

        private void avancar() {
            angulo = (angulo + 10f) % 360f;
            barra += 0.014f;
            if (barra > 1f)
                barra = 0f;
            if (fade < 1f) {
                fade = Math.min(1f, fade + 0.09f);
                repaint();
            } else {
                repaint(new Rectangle((getWidth() - CW) / 2 - 20, (getHeight() - CH) / 2 - 20, CW + 40, CH + 40));
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setComposite(AlphaComposite.SrcOver.derive(fade));
            g2.setColor(new Color(1, 7, 18, 215));
            g2.fillRect(0, 0, getWidth(), getHeight());

            int x = (getWidth() - CW) / 2, y = (getHeight() - CH) / 2;

            g2.setColor(new Color(32, 139, 248, 28));
            g2.fillRoundRect(x - 6, y - 6, CW + 12, CH + 12, 26, 26);
            g2.setColor(PAINEL);
            g2.fillRoundRect(x, y, CW, CH, 20, 20);
            g2.setColor(AZUL_BORDA);
            g2.drawRoundRect(x, y, CW, CH, 20, 20);

            g2.setColor(BRANCO);
            g2.setFont(new Font("Arial", Font.BOLD, 24));
            centralizar(g2, "Entrando no sistema", x + CW / 2, y + 62);

            g2.setColor(AZUL_TEXTO);
            g2.setFont(new Font("Arial", Font.PLAIN, 15));
            centralizar(g2, saudacao, x + CW / 2, y + 92);

            for (int i = 0; i < etapas.length; i++) {
                desenharEtapa(g2, i, x + 70, y + 150 + i * 46);
            }

            desenharBarra(g2, x + 40, y + CH - 42, CW - 80, 6);
            g2.dispose();
        }

        private void desenharEtapa(Graphics2D g2, int i, int ex, int ey) {
            int icx = ex + 11;
            if (i < etapaAtual) {
                g2.setColor(new Color(34, 197, 94));
                g2.fillOval(icx - 11, ey - 11, 22, 22);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(icx - 5, ey, icx - 1, ey + 4);
                g2.drawLine(icx - 1, ey + 4, icx + 5, ey - 4);
            } else if (i == etapaAtual) {
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(32, 139, 248, 50));
                g2.drawOval(icx - 10, ey - 10, 20, 20);
                g2.setColor(AZUL);
                g2.drawArc(icx - 10, ey - 10, 20, 20, (int) -angulo, 110);
            } else {
                g2.setColor(new Color(51, 65, 85));
                g2.setStroke(new BasicStroke(2f));
                g2.drawOval(icx - 10, ey - 10, 20, 20);
            }

            boolean ativa = i == etapaAtual;
            g2.setFont(new Font("Arial", ativa ? Font.BOLD : Font.PLAIN, 17));
            g2.setColor(i < etapaAtual ? new Color(148, 163, 184) : ativa ? BRANCO : new Color(88, 103, 125));
            String pontos = ativa ? ".".repeat(1 + (int) (angulo / 120f) % 3) : "";
            g2.drawString(etapas[i] + pontos, ex + 38, ey + 6);
        }

        private void desenharBarra(Graphics2D g2, int bx, int by, int bw, int bh) {
            g2.setColor(new Color(15, 30, 50));
            g2.fillRoundRect(bx, by, bw, bh, bh, bh);
            Graphics2D gb = (Graphics2D) g2.create();
            gb.clip(new RoundRectangle2D.Float(bx, by, bw, bh, bh, bh));
            int seg = (int) (bw * 0.35f);
            int sx = bx + (int) (barra * (bw + seg)) - seg;
            gb.setPaint(new GradientPaint(sx, 0, AZUL_BORDA, sx + seg, 0, AZUL));
            gb.fillRoundRect(sx, by, seg, bh, bh, bh);
            gb.dispose();
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