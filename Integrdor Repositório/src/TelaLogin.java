
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

public class TelaLogin extends JFrame {

    private JTextField email;
    private JPasswordField senha;

    private JButton olho;
    private JButton login;

    private boolean mostrarSenha = false;

    // =========================================================
    // CORES
    // =========================================================

    private static final Color FUNDO = new Color(1, 7, 18);
    private static final Color FUNDO_AZUL = new Color(2, 12, 27);
    private static final Color PAINEL = new Color(5, 16, 31);
    private static final Color CAMPO = new Color(3, 13, 27);

    private static final Color AZUL = new Color(32, 139, 248);
    private static final Color AZUL_BORDA = new Color(39, 105, 168);
    private static final Color AZUL_TEXTO = new Color(119, 182, 242);

    private static final Color BRANCO = new Color(245, 247, 250);

    // =========================================================
    // RESOLUÇÃO DE REFERÊNCIA
    // =========================================================

    /*
     * Todo o design original foi criado pensando em:
     *
     * Largura:  1664
     * Altura:    936
     *
     * As coordenadas abaixo são convertidas automaticamente
     * para porcentagens dentro das funções PX() e PY().
     */

    private static final double BASE_W = 1664.0;
    private static final double BASE_H = 936.0;

    // =========================================================
    // CONSTRUTOR
    // =========================================================

    public TelaLogin() {

        setTitle("Arcade");

        setUndecorated(true);

        setDefaultCloseOperation(
                JFrame.EXIT_ON_CLOSE);

        // Tela inteira
        setExtendedState(
                JFrame.MAXIMIZED_BOTH);

        criarTela();

        setVisible(true);
    }

    // =========================================================
    // CRIAR TELA
    // =========================================================

    private void criarTela() {

        Tela tela = new Tela();

        setContentPane(tela);

        // =====================================================
        // EMAIL
        // =====================================================

        email = new JTextField();

        email.setFont(
                new Font(
                        "Arial",
                        Font.PLAIN,
                        16));

        email.setForeground(BRANCO);

        email.setCaretColor(BRANCO);

        email.setOpaque(false);

        email.setBorder(
                new EmptyBorder(
                        0,
                        48,
                        0,
                        10));

        tela.add(email);

        // =====================================================
        // SENHA
        // =====================================================

        senha = new JPasswordField();

        senha.setFont(
                new Font(
                        "Arial",
                        Font.PLAIN,
                        16));

        senha.setForeground(BRANCO);

        senha.setCaretColor(BRANCO);

        senha.setOpaque(false);

        senha.setEchoChar('●');

        senha.setBorder(
                new EmptyBorder(
                        0,
                        55,
                        0,
                        60));

        tela.add(senha);

        // =====================================================
        // BOTÃO OLHO
        // =====================================================

        olho = new JButton();

        olho.setOpaque(false);

        olho.setContentAreaFilled(false);

        olho.setBorderPainted(false);

        olho.setFocusPainted(false);

        olho.setText("");

        olho.setCursor(
                new Cursor(
                        Cursor.HAND_CURSOR));

        olho.addActionListener(
                e -> alternarSenha());

        tela.add(olho);

        // =====================================================
        // BOTÃO LOGIN
        // =====================================================

        login = new JButton();

        login.setOpaque(false);

        login.setContentAreaFilled(false);

        login.setBorderPainted(false);

        login.setFocusPainted(false);

        login.setCursor(
                new Cursor(
                        Cursor.HAND_CURSOR));

        login.addActionListener(
                e -> fazerLogin());

        tela.add(login);

        // =====================================================
        // ENTER
        // =====================================================

        email.addActionListener(
                e -> fazerLogin());

        senha.addActionListener(
                e -> fazerLogin());

        // =====================================================
        // ESC
        // =====================================================

        tela.getInputMap(
                JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(
                        KeyStroke.getKeyStroke(
                                KeyEvent.VK_ESCAPE,
                                0),
                        "fechar");

        tela.getActionMap().put(
                "fechar",
                new AbstractAction() {

                    @Override
                    public void actionPerformed(
                            ActionEvent e) {

                        dispose();
                    }
                });
    }

    // =========================================================
    // MOSTRAR / ESCONDER SENHA
    // =========================================================

    private void alternarSenha() {

        mostrarSenha = !mostrarSenha;

        if (mostrarSenha) {

            senha.setEchoChar(
                    (char) 0);

        } else {

            senha.setEchoChar(
                    '●');
        }

        senha.requestFocus();

        repaint();
    }

    // =========================================================
    // LOGIN
    // =========================================================

    private void fazerLogin() {

        String usuario = email.getText().trim();

        String senhaDigitada = new String(
                senha.getPassword());

        // =====================================================
        // VERIFICA CAMPOS VAZIOS
        // =====================================================

        if (usuario.isEmpty()
                ||
                senhaDigitada.isEmpty()) {

            JOptionPane.showMessageDialog(
                    this,
                    "Preencha o email e a senha.",
                    "Atenção",
                    JOptionPane.WARNING_MESSAGE);

            return;
        }

        // =====================================================
        // LOGIN
        // =====================================================

        if (usuario.equals("admin")
                &&
                senhaDigitada.equals("1234")) {

            App app = new App();

            app.setVisible(true);

            dispose();

        } else {

            JOptionPane.showMessageDialog(
                    this,
                    "Email ou senha incorretos.",
                    "Erro",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // =========================================================
    // PAINEL PRINCIPAL
    // =========================================================

    class Tela extends JPanel {

        /*
         * Escala usada para manter a proporção original
         * do design.
         */
        private double escala = 1.0;

        /*
         * Espaço restante quando a proporção da tela
         * é diferente de 1664x936.
         */
        private int deslocamentoX;
        private int deslocamentoY;

        public Tela() {

            setLayout(null);

            setBackground(FUNDO);
        }

        // =====================================================
        // CALCULA ESCALA
        // =====================================================

        private void calcularEscala() {

            double escalaX =
                    getWidth() / BASE_W;

            double escalaY =
                    getHeight() / BASE_H;

            /*
             * Usa a menor escala para impedir que o layout
             * seja deformado.
             */
            escala = Math.min(
                    escalaX,
                    escalaY);

            int larguraFinal =
                    (int) (BASE_W * escala);

            int alturaFinal =
                    (int) (BASE_H * escala);

            deslocamentoX =
                    (getWidth() - larguraFinal) / 2;

            deslocamentoY =
                    (getHeight() - alturaFinal) / 2;
        }

        // =====================================================
        // CONVERTE X
        // =====================================================

        /*
         * Recebe a coordenada original e converte para
         * a posição proporcional da tela.
         */
        private int PX(double x) {

            return deslocamentoX
                    +
                    (int) (x * escala);
        }

        // =====================================================
        // CONVERTE Y
        // =====================================================

        private int PY(double y) {

            return deslocamentoY
                    +
                    (int) (y * escala);
        }

        // =====================================================
        // CONVERTE TAMANHO
        // =====================================================

        private int PS(double tamanho) {

            return (int) (tamanho * escala);
        }

        // =====================================================
        // POSIÇÃO DOS COMPONENTES
        // =====================================================

        @Override
        public void doLayout() {

            calcularEscala();

            // =================================================
            // EMAIL
            // =================================================

            email.setBounds(
                    PX(640),
                    PY(421),
                    PS(382),
                    PS(54));

            // =================================================
            // SENHA
            // =================================================

            senha.setBounds(
                    PX(640),
                    PY(526),
                    PS(382),
                    PS(55));

            // =================================================
            // OLHO
            // =================================================

            olho.setBounds(
                    PX(1039),
                    PY(526),
                    PS(57),
                    PS(55));

            // =================================================
            // BOTÃO LOGIN
            // =================================================

            login.setBounds(
                    PX(664),
                    PY(617),
                    PS(334),
                    PS(62));
        }

        // =====================================================
        // DESENHO
        // =====================================================

        @Override
        protected void paintComponent(
                Graphics g) {

            super.paintComponent(g);

            calcularEscala();

            Graphics2D g2 =
                    (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            // =================================================
            // FUNDO
            // =================================================

            GradientPaint fundo =
                    new GradientPaint(
                            0,
                            0,
                            FUNDO_AZUL,
                            getWidth(),
                            getHeight(),
                            FUNDO);

            g2.setPaint(fundo);

            g2.fillRect(
                    0,
                    0,
                    getWidth(),
                    getHeight());

            // =================================================
            // DESENHO ESCALADO
            // =================================================

            g2.translate(
                    deslocamentoX,
                    deslocamentoY);

            g2.scale(
                    escala,
                    escala);

            // =================================================
            // PAINEL
            // =================================================

            g2.setColor(PAINEL);

            g2.fillRoundRect(
                    585,
                    172,
                    492,
                    582,
                    17,
                    17);

            g2.setColor(
                    AZUL_BORDA);

            g2.setStroke(
                    new BasicStroke(1.5f));

            g2.drawRoundRect(
                    585,
                    172,
                    492,
                    582,
                    17,
                    17);

            // =================================================
            // LOGO
            // =================================================

            desenharLogo(
                    g2,
                    657,
                    232);

            // =================================================
            // ARCADE
            // =================================================

            g2.setColor(BRANCO);

            g2.setFont(
                    new Font(
                            "Arial",
                            Font.PLAIN,
                            47));

            g2.drawString(
                    "Arcade",
                    837,
                    307);

            // =================================================
            // EMAIL
            // =================================================

            g2.setColor(
                    AZUL_TEXTO);

            g2.setFont(
                    new Font(
                            "Arial",
                            Font.PLAIN,
                            17));

            g2.drawString(
                    "Email",
                    640,
                    408);

            desenharCampo(
                    g2,
                    640,
                    421,
                    382,
                    54);

            desenharEmail(
                    g2,
                    657,
                    438);

            // =================================================
            // PASSWORD
            // =================================================

            g2.setColor(
                    AZUL_TEXTO);

            g2.drawString(
                    "Password",
                    640,
                    513);

            desenharCampo(
                    g2,
                    640,
                    526,
                    382,
                    55);

            desenharCadeado(
                    g2,
                    658,
                    542);

            // =================================================
            // OLHO
            // =================================================

            desenharOlho(
                    g2,
                    1039,
                    544);

            // =================================================
            // BOTÃO LOGIN
            // =================================================

            g2.setColor(AZUL);

            g2.fillRoundRect(
                    664,
                    617,
                    334,
                    62,
                    10,
                    10);

            g2.setColor(
                    new Color(
                            70,
                            166,
                            255));

            g2.setStroke(
                    new BasicStroke(1));

            g2.drawRoundRect(
                    664,
                    617,
                    334,
                    62,
                    10,
                    10);

            g2.setColor(Color.WHITE);

            g2.setFont(
                    new Font(
                            "Arial",
                            Font.PLAIN,
                            22));

            String texto = "Log in";

            FontMetrics fm =
                    g2.getFontMetrics();

            int tx =
                    831
                    -
                    fm.stringWidth(texto) / 2;

            int ty =
                    617
                    +
                    (62 - fm.getHeight()) / 2
                    +
                    fm.getAscent();

            g2.drawString(
                    texto,
                    tx,
                    ty);

            g2.dispose();
        }

        // =====================================================
        // DESENHAR CAMPO
        // =====================================================

        private void desenharCampo(
                Graphics2D g,
                int x,
                int y,
                int w,
                int h) {

            g.setColor(CAMPO);

            g.fillRoundRect(
                    x,
                    y,
                    w,
                    h,
                    7,
                    7);

            g.setColor(
                    new Color(
                            39,
                            91,
                            145));

            g.setStroke(
                    new BasicStroke(1.5f));

            g.drawRoundRect(
                    x,
                    y,
                    w,
                    h,
                    7,
                    7);
        }

        // =====================================================
        // LOGO
        // =====================================================

        private void desenharLogo(
                Graphics2D g,
                int x,
                int y) {

            Graphics2D d =
                    (Graphics2D) g.create();

            d.translate(
                    x,
                    y);

            Path2D a =
                    new Path2D.Double();

            a.moveTo(
                    83,
                    3);

            a.lineTo(
                    30,
                    100);

            a.lineTo(
                    55,
                    100);

            a.lineTo(
                    83,
                    47);

            a.lineTo(
                    110,
                    100);

            a.lineTo(
                    137,
                    100);

            a.closePath();

            d.setColor(BRANCO);

            d.fill(a);

            Path2D parte =
                    new Path2D.Double();

            parte.moveTo(
                    78,
                    47);

            parte.lineTo(
                    94,
                    47);

            parte.lineTo(
                    116,
                    100);

            parte.lineTo(
                    91,
                    100);

            parte.closePath();

            d.setColor(AZUL);

            d.fill(parte);

            d.setColor(AZUL);

            d.setStroke(
                    new BasicStroke(
                            7,
                            BasicStroke.CAP_ROUND,
                            BasicStroke.JOIN_ROUND));

            d.drawArc(
                    8,
                    25,
                    145,
                    72,
                    190,
                    205);

            d.dispose();
        }

        // =====================================================
        // EMAIL
        // =====================================================

        private void desenharEmail(
                Graphics2D g,
                int x,
                int y) {

            g.setColor(
                    AZUL_TEXTO);

            g.setStroke(
                    new BasicStroke(2));

            g.drawRoundRect(
                    x,
                    y,
                    23,
                    18,
                    3,
                    3);

            g.drawLine(
                    x + 1,
                    y + 1,
                    x + 11,
                    y + 10);

            g.drawLine(
                    x + 22,
                    y + 1,
                    x + 11,
                    y + 10);
        }

        // =====================================================
        // CADEADO
        // =====================================================

        private void desenharCadeado(
                Graphics2D g,
                int x,
                int y) {

            g.setColor(
                    AZUL_TEXTO);

            g.setStroke(
                    new BasicStroke(2));

            g.drawRoundRect(
                    x,
                    y + 9,
                    20,
                    16,
                    3,
                    3);

            g.drawArc(
                    x + 3,
                    y,
                    14,
                    17,
                    0,
                    180);
        }

        // =====================================================
        // OLHO
        // =====================================================

        private void desenharOlho(
                Graphics2D g,
                int x,
                int y) {

            g.setColor(
                    AZUL_TEXTO);

            g.setStroke(
                    new BasicStroke(2));

            // =================================================
            // OLHO ABERTO
            // =================================================

            if (mostrarSenha) {

                g.drawOval(
                        x,
                        y,
                        22,
                        15);

                g.fillOval(
                        x + 7,
                        y + 4,
                        8,
                        8);

            }

            // =================================================
            // OLHO FECHADO
            // =================================================

            else {

                g.drawOval(
                        x,
                        y,
                        22,
                        15);

                g.fillOval(
                        x + 7,
                        y + 4,
                        8,
                        8);

                g.drawLine(
                        x - 3,
                        y - 3,
                        x + 25,
                        y + 19);
            }
        }
    }

    // =========================================================
    // MAIN
    // =========================================================

    public static void main(
            String[] args) {

        SwingUtilities.invokeLater(
                TelaLogin::new);
    }
}

