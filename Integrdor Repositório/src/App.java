import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.net.URL;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;

public class App extends JFrame {

    /** De quanto em quanto tempo a tela aberta é atualizada com os dados do banco. */
    private static final int INTERVALO_ATUALIZACAO_MS = 3000;

    private TelaClientes telaClientes;
    private TelaCartao telaCartao;
    private TelaPremios telaPremios;
    private TelaMaquinas telaMaquinas;
    private TelaFuncionarios telaFuncionarios;
    private TelaRecargas telaRecargas;
<<<<<<< HEAD
    private TelaPremios telaPremios; // Nova tela adicionada
    private TelaPartidas telapartidas;
=======
    private TelaAutoatendimento telaAutoatendimento;
>>>>>>> 9df62e8372717dac44a1c1b4f9652ee285563f33
    private JPanel conteudoCentralPadrao;
    private Timer timerAtualizacao;

    public App() {
        setTitle("Sistema Arcade");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setUndecorated(true);

        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (gd.isFullScreenSupported()) {
            gd.setFullScreenWindow(this);
        } else {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }

        setIconImage(carregarIconeJanela());

        // ESC encerra o sistema em qualquer tela (ver SairComEsc)
        SairComEsc.instalar();

        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setLayout(null);
        setContentPane(layeredPane);

        int larguraTela = getWidth();
        int alturaTela = getHeight();

        // Painel Central Padrão
        conteudoCentralPadrao = new JPanel();
        conteudoCentralPadrao.setBackground(new Color(2, 12, 27));
        conteudoCentralPadrao.setBounds(70, 0, larguraTela - 70, alturaTela);
        layeredPane.add(conteudoCentralPadrao, JLayeredPane.DEFAULT_LAYER);

        // Instâncias das Telas
        telaClientes = new TelaClientes();
        telaClientes.setBounds(70, 0, larguraTela - 70, alturaTela);
        telaClientes.setVisible(false);
        layeredPane.add(telaClientes, JLayeredPane.DEFAULT_LAYER);

        telaCartao = new TelaCartao(telaClientes);
        telaCartao.setBounds(70, 0, larguraTela - 70, alturaTela);
        telaCartao.setVisible(false);
        layeredPane.add(telaCartao, JLayeredPane.DEFAULT_LAYER);

        telaMaquinas = new TelaMaquinas();
        telaMaquinas.setBounds(70, 0, larguraTela - 70, alturaTela);
        telaMaquinas.setVisible(false);
        layeredPane.add(telaMaquinas, JLayeredPane.DEFAULT_LAYER);

        telaFuncionarios = new TelaFuncionarios();
        telaFuncionarios.setBounds(70, 0, larguraTela - 70, alturaTela);
        telaFuncionarios.setVisible(false);
        layeredPane.add(telaFuncionarios, JLayeredPane.DEFAULT_LAYER);

        telaRecargas = new TelaRecargas();
        telaRecargas.setBounds(70, 0, larguraTela - 70, alturaTela);
        telaRecargas.setVisible(false);
        layeredPane.add(telaRecargas, JLayeredPane.DEFAULT_LAYER);

        telaPremios = new TelaPremios();
        telaPremios.setBounds(70, 0, larguraTela - 70, alturaTela);
        telaPremios.setVisible(false);
        layeredPane.add(telaPremios, JLayeredPane.DEFAULT_LAYER);

<<<<<<< HEAD
        telapartidas = new TelaPartidas();
        telapartidas.setBounds(70, 0, larguraTela - 70, alturaTela);
        telapartidas.setVisible(false);
        layeredPane.add(telapartidas, JLayeredPane.DEFAULT_LAYER);
=======
        // Autoatendimento: tela "presa" do cliente, por cima de tudo (inclusive da barra lateral)
        telaAutoatendimento = new TelaAutoatendimento(telaRecargas, this::fecharAutoatendimento);
        telaAutoatendimento.setBounds(0, 0, larguraTela, alturaTela);
        telaAutoatendimento.setVisible(false);
        layeredPane.add(telaAutoatendimento, JLayeredPane.DRAG_LAYER);
        telaRecargas.setAcaoAutoatendimento(this::abrirAutoatendimento);
>>>>>>> 9df62e8372717dac44a1c1b4f9652ee285563f33

        // Sidebar Fixa
        JPanel sidebar = new JPanel();
        sidebar.setBackground(new Color(24, 75, 150));
        sidebar.setLayout(null);
        sidebar.setBounds(0, 0, 70, alturaTela);
        layeredPane.add(sidebar, JLayeredPane.DEFAULT_LAYER);

        JPanel logo = criarLogo();
        logo.setBounds(0, 0, 70, 70);
        sidebar.add(logo);

        String[][] botoesInfo = {
                { "Início", "botao-de-inicio.png" },
                { "Clientes", "botao cliente.png" },
                { "Cartões", "botao cartao.png" },
                { "Partidas", "botao partidas.png" },
                { "Máquinas", "botao maquinas.png" },
                { "Recargas", "botao recarga.png" },
                { "Prêmios", "botao premio.png" },
                { "Funcionários", "botao funcionario.png" },
                { "Sair", "botao saida.png" }
        };

        int yAtual = 70;
        for (String[] btn : botoesInfo) {
            String texto = btn[0];
            String imagem = btn[1];

            if (texto.equals("Funcionários") && !Sessao.isManagerMaster()) {
                continue;
            }

            PainelExtensor botaoExtensivel = new PainelExtensor(texto, imagem, yAtual, 70, 180);

            botaoExtensivel.setAcaoClique(() -> {
                if (texto.equalsIgnoreCase("Clientes")) {
                    mostrarTela(telaClientes);
                } else if (texto.equalsIgnoreCase("Cartões")) {
                    telaCartao.atualizarNomesClientes();
                    telaCartao.recarregar();
                    mostrarTela(telaCartao);
                } else if (texto.equalsIgnoreCase("Partidas")) {
                    telapartidas.recarregar();
                    mostrarTela(telapartidas);
                } else if (texto.equalsIgnoreCase("Máquinas")) {
                    mostrarTela(telaMaquinas);
                } else if (texto.equalsIgnoreCase("Recargas")) {
                    telaRecargas.carregarDadosRecargas();
                    mostrarTela(telaRecargas);
                } else if (texto.equalsIgnoreCase("Prêmios")) {
                    telaPremios.recarregar();
                    mostrarTela(telaPremios);
                } else if (texto.equalsIgnoreCase("Funcionários")) {
                    if (!Sessao.isManagerMaster()) {
                        JOptionPane.showMessageDialog(this, "Apenas o Manager Master pode acessar Funcionários.",
                                "Acesso negado", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    telaFuncionarios.recarregar();
                    mostrarTela(telaFuncionarios);
                } else if (texto.equalsIgnoreCase("Início")) {
                    mostrarTela(null);
                } else if (texto.equalsIgnoreCase("Sair")) {

                    Sessao.encerrar();
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                            .setFullScreenWindow(null);
                    dispose();
                    new TelaLogin();

                }
            });

            layeredPane.add(botaoExtensivel, JLayeredPane.POPUP_LAYER);
            yAtual += 70;
        }

        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = getWidth();
                int h = getHeight();

                sidebar.setBounds(0, 0, 70, h);
                conteudoCentralPadrao.setBounds(70, 0, w - 70, h);
                telaClientes.setBounds(70, 0, w - 70, h);
                telaCartao.setBounds(70, 0, w - 70, h);
                telaMaquinas.setBounds(70, 0, w - 70, h);
                telaFuncionarios.setBounds(70, 0, w - 70, h);
                telaRecargas.setBounds(70, 0, w - 70, h);
                telaPremios.setBounds(70, 0, w - 70, h);
<<<<<<< HEAD
                telapartidas.setBounds(70, 0, w - 70, h);
=======
                telaAutoatendimento.setBounds(0, 0, w, h);
>>>>>>> 9df62e8372717dac44a1c1b4f9652ee285563f33
            }
        });

        // Tempo real: a tela aberta busca no banco as alterações feitas em outros lugares
        // (recargas no balcão, no autoatendimento ou em outra máquina).
        timerAtualizacao = new Timer(INTERVALO_ATUALIZACAO_MS, e -> atualizarTelaVisivel());
        timerAtualizacao.start();
    }

    private void mostrarTela(JPanel alvo) {
        telaClientes.setVisible(alvo == telaClientes);
        telaCartao.setVisible(alvo == telaCartao);
        telaMaquinas.setVisible(alvo == telaMaquinas);
        telaFuncionarios.setVisible(alvo == telaFuncionarios);
        telaRecargas.setVisible(alvo == telaRecargas);
        telaPremios.setVisible(alvo == telaPremios);
        telapartidas.setVisible(alvo == telapartidas);
        conteudoCentralPadrao.setVisible(alvo == null);

        getContentPane().revalidate();
        getContentPane().repaint();
    }

    /** Atualização periódica: só mexe na tela que está aberta e, sem o autoatendimento na frente. */
    private void atualizarTelaVisivel() {
        if (telaAutoatendimento.isVisible()) {
            return;
        }
        if (telaCartao.isVisible()) {
            telaCartao.atualizarSilenciosamente();
        } else if (telaRecargas.isVisible()) {
            telaRecargas.atualizarSilenciosamente();
        }
    }

    /** Mostra o autoatendimento do cliente por cima de todo o sistema. */
    private void abrirAutoatendimento() {
        telaAutoatendimento.setBounds(0, 0, getWidth(), getHeight());
        telaAutoatendimento.iniciar();
        telaAutoatendimento.setVisible(true);
        getContentPane().repaint();
    }

    /** Fecha o autoatendimento (após a saída de um administrador) e atualiza os dados na hora. */
    private void fecharAutoatendimento() {
        telaAutoatendimento.encerrar();
        telaAutoatendimento.setVisible(false);
        atualizarTelaVisivel();
        getContentPane().repaint();
    }

    /** Sair do sistema: volta para a tela de login. */
    private void sair() {
        timerAtualizacao.stop();
        telaAutoatendimento.encerrar();
        Sessao.encerrar();
        GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().setFullScreenWindow(null);
        dispose();
        new TelaLogin();
    }

    private JPanel criarLogo() {
        JPanel blocoLogo = new JPanel(new BorderLayout());
        blocoLogo.setBackground(new Color(10, 52, 102));
        blocoLogo.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        ImageIcon logo = carregarLogo();
        JLabel labelLogo;

        if (logo != null) {
            Image imagemRedimensionada = logo.getImage().getScaledInstance(52, 52, Image.SCALE_SMOOTH);
            labelLogo = new JLabel(new ImageIcon(imagemRedimensionada));
        } else {
            labelLogo = new JLabel("A", SwingConstants.CENTER);
            labelLogo.setForeground(Color.WHITE);
            labelLogo.setFont(getFont().deriveFont(24f));
        }

        labelLogo.setHorizontalAlignment(SwingConstants.CENTER);
        labelLogo.setVerticalAlignment(SwingConstants.CENTER);
        blocoLogo.add(labelLogo, BorderLayout.CENTER);

        return blocoLogo;
    }

    private ImageIcon carregarLogo() {
        URL resource = getClass().getResource("/images/Logo_arcade.png");
        if (resource != null) {
            return new ImageIcon(resource);
        }
        return null;
    }

    private Image carregarIconeJanela() {
        ImageIcon logo = carregarLogo();
        return logo != null ? logo.getImage() : null;
    }
}//app java ta dando jumpscares, aparecendo vermelhinho... igual ao bolder
