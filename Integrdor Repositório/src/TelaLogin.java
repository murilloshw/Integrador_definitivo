import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.net.URL;

public class TelaLogin extends JFrame {

    private JTextField email;
    private JPasswordField senha;

    private JButton olho;
    private JButton login;

    private boolean mostrarSenha = false;

    // Tela de carregamento (fica por cima do login enquanto o sistema abre)
    private CarregandoOverlay overlay;
    private boolean carregando = false;

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
                        68,
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
                        68,
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

        // Animação de clique do botão (hover, afundar ao pressionar e ondulação)
        tela.instalarAnimacaoBotao(login);

        // Tela de carregamento: um "vidro" por cima do login
        overlay = new CarregandoOverlay();
        setGlassPane(overlay);

        // =====================================================
        // ENTER
        // =====================================================

        // No Enter o botão também "afunda", como se tivesse sido clicado
        email.addActionListener(
                e -> {
                    tela.animarCliqueTeclado();
                    fazerLogin();
                });

        senha.addActionListener(
                e -> {
                    tela.animarCliqueTeclado();
                    fazerLogin();
                });

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

        // Evita clique duplo enquanto o sistema já está carregando
        if (carregando) {

            return;
        }

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
                    "Preencha o usuário e a senha.",
                    "Atenção",
                    JOptionPane.WARNING_MESSAGE);

            return;
        }

        // =====================================================
        // LOGIN
        // =====================================================

        // Não existe tabela separada de login: quem entra é um funcionário
        // cadastrado (o "admin" é o Manager Master, fixo no sistema).
        Funcionario funcionario =
                Repositoriofuncionarios.autenticar(
                        usuario,
                        senhaDigitada);

        if (funcionario == null) {

            JOptionPane.showMessageDialog(
                    this,
                    "Usuário ou senha incorretos.",
                    "Erro",
                    JOptionPane.ERROR_MESSAGE);

            return;
        }

        if (!funcionario.isAtivo()) {

            JOptionPane.showMessageDialog(
                    this,
                    "Este funcionário está bloqueado.\nProcure o Manager Master.",
                    "Acesso bloqueado",
                    JOptionPane.WARNING_MESSAGE);

            return;
        }

        // Guarda quem entrou (usado para liberar as telas restritas)
        Sessao.iniciar(funcionario);

        iniciarCarregamento(funcionario);
    }

    // =========================================================
    // CARREGAMENTO (tela de "Entrando no sistema...")
    // =========================================================

    /*
     * Etapas:
     *   1. Credenciais verificadas (já feito acima)
     *   2. Conectando ao banco de dados -> em SEGUNDO PLANO (a animação não trava)
     *   3. Carregando telas do sistema  -> abre o App
     *
     * Na etapa 2 já deixamos algumas conexões abertas e prontas (Conexao.aquecer),
     * então as telas do App abrem muito mais rápido na etapa 3.
     */
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

                // Pequena pausa para a etapa 3 aparecer antes de o App ocupar a interface
                javax.swing.Timer pausa = new javax.swing.Timer(
                        150,
                        ev -> abrirApp());

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

            JOptionPane.showMessageDialog(
                    this,
                    "Erro ao abrir o sistema:\n" + ex.getMessage(),
                    "Erro",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void falhaNaConexao() {

        cancelarCarregamento();

        String[] opcoes = { "Tentar novamente", "Cancelar" };

        int escolha = JOptionPane.showOptionDialog(
                this,
                "Não foi possível conectar ao banco de dados.\n"
                        + "Verifique sua conexão com a internet e tente novamente.",
                "Sem conexão",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.ERROR_MESSAGE,
                null,
                opcoes,
                opcoes[0]);

        if (escolha == 0) {

            fazerLogin();
        }
    }

    /** Volta para a tela de login normal (usado quando o carregamento falha). */
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

        // =====================================================
        // ANIMAÇÃO DO BOTÃO LOGIN
        // =====================================================

        // Valores animados de 0 a 1: "correm" suavemente até o alvo
        private float hover = 0f;
        private float pressao = 0f;

        // Ondulação (ripple) que nasce no ponto do clique; -1 = inativa
        private float ripple = -1f;
        private float rippleX = 831f;
        private float rippleY = 648f;

        private boolean mouseSobre = false;
        private boolean mousePressionado = false;

        private final javax.swing.Timer animador =
                new javax.swing.Timer(
                        16,
                        e -> animarBotao());

        /*
         * Liga o botão invisível à animação:
         *   - passar o mouse  -> brilho (hover)
         *   - pressionar      -> o botão afunda, escurece e nasce uma ondulação
         *   - soltar / sair   -> volta ao normal suavemente
         */
        void instalarAnimacaoBotao(JButton botao) {

            botao.addMouseListener(
                    new MouseAdapter() {

                        @Override
                        public void mouseEntered(
                                MouseEvent e) {

                            mouseSobre = true;

                            animador.start();
                        }

                        @Override
                        public void mouseExited(
                                MouseEvent e) {

                            mouseSobre = false;

                            mousePressionado = false;

                            animador.start();
                        }

                        @Override
                        public void mousePressed(
                                MouseEvent e) {

                            if (!botao.isEnabled()) {

                                return;
                            }

                            mousePressionado = true;

                            ripple = 0f;

                            // converte o clique para as coordenadas do desenho (1664x936)
                            rippleX = 664f + (float) (e.getX() / escala);

                            rippleY = 617f + (float) (e.getY() / escala);

                            animador.start();
                        }

                        @Override
                        public void mouseReleased(
                                MouseEvent e) {

                            mousePressionado = false;

                            animador.start();
                        }
                    });
        }

        // Enter nos campos: simula o clique no centro do botão
        void animarCliqueTeclado() {

            mousePressionado = true;

            ripple = 0f;

            rippleX = 831f;

            rippleY = 648f;

            animador.start();

            javax.swing.Timer soltar =
                    new javax.swing.Timer(
                            140,
                            e -> {

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

                if (ripple >= 1f) {

                    ripple = -1f;
                }
            }

            // repinta só a região do botão (com folga para o brilho)
            repaint(
                    login.getX() - 30,
                    login.getY() - 30,
                    login.getWidth() + 60,
                    login.getHeight() + 60);

            if (hover == alvoHover
                    && pressao == alvoPressao
                    && ripple < 0f) {

                animador.stop();
            }
        }

        private float aproximar(
                float atual,
                float alvo,
                float velocidade) {

            float novo = atual + (alvo - atual) * velocidade;

            // encosta no alvo quando já está bem perto (para a animação poder parar)
            return Math.abs(alvo - novo) < 0.01f ? alvo : novo;
        }

        private Color misturar(
                Color a,
                Color b,
                float t) {

            t = Math.max(0f, Math.min(1f, t));

            return new Color(
                    (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                    (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                    (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
        }

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
            // LOGO ARCADE (IMAGEM)
            // =================================================

            desenharLogoArcade(
                    g2,
                    650,
                    120,
                    360,
                    105);
                                g2.setColor(
                    AZUL_TEXTO);
        // =================================================
        // ARCADE TEXTO
        // =================================================

            g2.setFont(
                    new Font(
                            "Arial",
                            Font.PLAIN,
                            40));

            g2.drawString(
                    "ARCADE",
                    750,+
                    300);

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
                    "Usuário",
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

            desenharBotaoLogin(g2);

            g2.dispose();
        }

        // =====================================================
        // DESENHAR BOTÃO LOGIN (animado)
        // =====================================================

        private void desenharBotaoLogin(
                Graphics2D g2) {

            Graphics2D g = (Graphics2D) g2.create();

            // Centro do botão, em coordenadas do desenho
            float cx = 664f + 334f / 2f;

            float cy = 617f + 62f / 2f;

            // Ao pressionar, o botão "afunda" (encolhe um pouco a partir do centro)
            float encolher = 1f - 0.045f * pressao;

            g.translate(cx, cy);

            g.scale(encolher, encolher);

            g.translate(-cx, -cy);

            // Brilho azul em volta quando o mouse está em cima (some ao pressionar)
            float brilho = hover * (1f - pressao);

            if (brilho > 0.01f) {

                for (int i = 10; i >= 2; i -= 4) {

                    g.setColor(
                            new Color(
                                    32,
                                    139,
                                    248,
                                    (int) (34 * brilho)));

                    g.fillRoundRect(
                            664 - i,
                            617 - i,
                            334 + i * 2,
                            62 + i * 2,
                            10 + i * 2,
                            10 + i * 2);
                }
            }

            // Cor: mais clara no hover, mais escura ao pressionar
            Color cor = misturar(
                    AZUL,
                    new Color(66, 162, 255),
                    hover * 0.65f);

            cor = misturar(
                    cor,
                    new Color(16, 92, 182),
                    pressao);

            g.setColor(cor);

            g.fillRoundRect(
                    664,
                    617,
                    334,
                    62,
                    10,
                    10);

            // Ondulação que se espalha a partir do ponto do clique
            if (ripple >= 0f) {

                Graphics2D gr = (Graphics2D) g.create();

                gr.clip(
                        new RoundRectangle2D.Float(
                                664,
                                617,
                                334,
                                62,
                                10,
                                10));

                float raio = ripple * 360f;

                int alfa = (int) (95 * (1f - ripple));

                gr.setColor(
                        new Color(
                                255,
                                255,
                                255,
                                Math.max(0, alfa)));

                gr.fill(
                        new Ellipse2D.Float(
                                rippleX - raio,
                                rippleY - raio,
                                raio * 2,
                                raio * 2));

                gr.dispose();
            }

            g.setColor(
                    misturar(
                            new Color(70, 166, 255),
                            new Color(140, 200, 255),
                            hover));

            g.setStroke(
                    new BasicStroke(1));

            g.drawRoundRect(
                    664,
                    617,
                    334,
                    62,
                    10,
                    10);

            g.setColor(Color.WHITE);

            g.setFont(
                    new Font(
                            "Arial",
                            Font.PLAIN,
                            22));

            String texto = "Log in";

            FontMetrics fm =
                    g.getFontMetrics();

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

            g.drawString(
                    texto,
                    tx,
                    ty);

            g.dispose();
        }

        // =====================================================
        // DESENHAR LOGO ARCADE
        // =====================================================

        private void desenharLogoArcade(
                Graphics2D g,
                int x,
                int y,
                int larguraMax,
                int alturaMax) {

            ImageIcon logo = carregarLogo();

            if (logo == null) {
                return;
            }

            Image imagem = logo.getImage();

            int larguraOriginal = logo.getIconWidth();
            int alturaOriginal = logo.getIconHeight();

            if (larguraOriginal <= 0 || alturaOriginal <= 0) {
                return;
            }

            // Mantém a proporção original da imagem.
            double escalaLogo = Math.min(
                    (double) larguraMax / larguraOriginal,
                    (double) alturaMax / alturaOriginal);

            int largura = (int) (larguraOriginal * escalaLogo);
            int altura = (int) (alturaOriginal * escalaLogo);

            // Centraliza a logo na área reservada.
            int posX = x + (larguraMax - largura) / 2;
            int posY = y + (alturaMax - altura) / 2;

            g.drawImage(
                    imagem,
                    posX,
                    posY,
                    largura,
                    altura,
                    null);
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

    private ImageIcon carregarLogo() {

        // 1) Tenta pelo classpath (funciona quando a pasta images
        //    foi configurada como recurso do projeto).
        URL resource = getClass().getResource("/images/Logo_arcade.png");
        if (resource != null) {
            return new ImageIcon(resource);
        }

        // 2) No VS Code/Java comum, a imagem normalmente fica em:
        //    src/images/Logo_arcade.png
        String[] caminhos = {
                "src/images/Logo_arcade.png",
                "images/Logo_arcade.png",
                "./src/images/Logo_arcade.png",
                "./images/Logo_arcade.png"
        };

        for (String caminho : caminhos) {
            java.io.File arquivo = new java.io.File(caminho);
            if (arquivo.exists()) {
                return new ImageIcon(arquivo.getAbsolutePath());
            }
        }

        System.out.println("ERRO: Logo_arcade.png não encontrada.");
        return null;
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
    // TELA DE CARREGAMENTO ("Entrando no sistema...")
    // =========================================================

    /*
     * Fica no "vidro" da janela: escurece o login, mostra um cartão central
     * com as etapas (feita / em andamento / pendente), um spinner girando
     * e uma barra de progresso animada.
     */
    class CarregandoOverlay extends JComponent {

        private final String[] etapas = {
                "Credenciais verificadas",
                "Conectando ao banco de dados",
                "Carregando telas do sistema"
        };

        private int etapaAtual = 1;

        private float fade = 0f;

        private float angulo = 0f;

        private float barra = 0f;

        private String saudacao = "";

        private final javax.swing.Timer timer =
                new javax.swing.Timer(
                        20,
                        e -> avancar());

        CarregandoOverlay() {

            setOpaque(false);

            setVisible(false);

            // Engole cliques e teclas: nada do login reage enquanto carrega
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

            fade = 0f;

            angulo = 0f;

            barra = 0f;

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

            if (barra > 1f) {

                barra = 0f;
            }

            if (fade < 1f) {

                fade = Math.min(1f, fade + 0.09f);

                repaint();

            } else {

                // depois do fade, só o cartão precisa ser repintado
                repaint(areaCartao());
            }
        }

        private Rectangle areaCartao() {

            int largura = 480;

            int altura = 340;

            return new Rectangle(
                    (getWidth() - largura) / 2 - 20,
                    (getHeight() - altura) / 2 - 20,
                    largura + 40,
                    altura + 40);
        }

        @Override
        protected void paintComponent(
                Graphics g) {

            Graphics2D g2 =
                    (Graphics2D) g.create();

            g2.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            g2.setRenderingHint(
                    RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g2.setComposite(
                    AlphaComposite.SrcOver.derive(fade));

            // Véu escuro sobre o login
            g2.setColor(
                    new Color(1, 7, 18, 215));

            g2.fillRect(
                    0,
                    0,
                    getWidth(),
                    getHeight());

            // Cartão central
            int cw = 480;

            int ch = 340;

            int x = (getWidth() - cw) / 2;

            int y = (getHeight() - ch) / 2;

            g2.setColor(
                    new Color(32, 139, 248, 28));

            g2.fillRoundRect(
                    x - 6,
                    y - 6,
                    cw + 12,
                    ch + 12,
                    26,
                    26);

            g2.setColor(PAINEL);

            g2.fillRoundRect(
                    x,
                    y,
                    cw,
                    ch,
                    20,
                    20);

            g2.setColor(AZUL_BORDA);

            g2.drawRoundRect(
                    x,
                    y,
                    cw,
                    ch,
                    20,
                    20);

            // Título e saudação
            g2.setColor(BRANCO);

            g2.setFont(
                    new Font("Arial", Font.BOLD, 24));

            centralizar(
                    g2,
                    "Entrando no sistema",
                    x + cw / 2,
                    y + 62);

            g2.setColor(AZUL_TEXTO);

            g2.setFont(
                    new Font("Arial", Font.PLAIN, 15));

            centralizar(
                    g2,
                    saudacao,
                    x + cw / 2,
                    y + 92);

            // Etapas
            for (int i = 0; i < etapas.length; i++) {

                int ex = x + 70;

                int ey = y + 150 + i * 46;

                int icx = ex + 11;

                int icy = ey;

                if (i < etapaAtual) {

                    // feita: círculo verde com "check"
                    g2.setColor(
                            new Color(34, 197, 94));

                    g2.fillOval(
                            icx - 11,
                            icy - 11,
                            22,
                            22);

                    g2.setColor(Color.WHITE);

                    g2.setStroke(
                            new BasicStroke(
                                    2.4f,
                                    BasicStroke.CAP_ROUND,
                                    BasicStroke.JOIN_ROUND));

                    g2.drawLine(icx - 5, icy, icx - 1, icy + 4);

                    g2.drawLine(icx - 1, icy + 4, icx + 5, icy - 4);

                } else if (i == etapaAtual) {

                    // em andamento: spinner girando
                    g2.setStroke(
                            new BasicStroke(
                                    3f,
                                    BasicStroke.CAP_ROUND,
                                    BasicStroke.JOIN_ROUND));

                    g2.setColor(
                            new Color(32, 139, 248, 50));

                    g2.drawOval(
                            icx - 10,
                            icy - 10,
                            20,
                            20);

                    g2.setColor(AZUL);

                    g2.drawArc(
                            icx - 10,
                            icy - 10,
                            20,
                            20,
                            (int) -angulo,
                            110);

                } else {

                    // pendente: círculo vazio
                    g2.setColor(
                            new Color(51, 65, 85));

                    g2.setStroke(
                            new BasicStroke(2f));

                    g2.drawOval(
                            icx - 10,
                            icy - 10,
                            20,
                            20);
                }

                boolean ativa = i == etapaAtual;

                g2.setFont(
                        new Font(
                                "Arial",
                                ativa ? Font.BOLD : Font.PLAIN,
                                17));

                g2.setColor(
                        i < etapaAtual
                                ? new Color(148, 163, 184)
                                : ativa
                                        ? BRANCO
                                        : new Color(88, 103, 125));

                String texto = etapas[i];

                if (ativa) {

                    // reticências animadas: "Conectando ao banco de dados..."
                    texto += ".".repeat(1 + (int) (angulo / 120f) % 3);
                }

                g2.drawString(
                        texto,
                        ex + 38,
                        ey + 6);
            }

            // Barra de progresso (indeterminada)
            int bx = x + 40;

            int by = y + ch - 42;

            int bw = cw - 80;

            int bh = 6;

            g2.setColor(
                    new Color(15, 30, 50));

            g2.fillRoundRect(
                    bx,
                    by,
                    bw,
                    bh,
                    bh,
                    bh);

            Graphics2D gb =
                    (Graphics2D) g2.create();

            gb.clip(
                    new RoundRectangle2D.Float(
                            bx,
                            by,
                            bw,
                            bh,
                            bh,
                            bh));

            int seg = (int) (bw * 0.35f);

            int sx = bx + (int) (barra * (bw + seg)) - seg;

            gb.setPaint(
                    new GradientPaint(
                            sx,
                            0,
                            AZUL_BORDA,
                            sx + seg,
                            0,
                            AZUL));

            gb.fillRoundRect(
                    sx,
                    by,
                    seg,
                    bh,
                    bh,
                    bh);

            gb.dispose();

            g2.dispose();
        }

        private void centralizar(
                Graphics2D g2,
                String texto,
                int cx,
                int baseY) {

            FontMetrics fm =
                    g2.getFontMetrics();

            g2.drawString(
                    texto,
                    cx - fm.stringWidth(texto) / 2,
                    baseY);
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