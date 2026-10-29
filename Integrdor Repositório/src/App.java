import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.net.URL;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

//entony vittor´peaswrwlemshnrsdf
public class App extends JFrame {

    private TelaClientes telaClientes;
    private TelaCartao telaCartao;
    private TelaFuncionarios telaFuncionarios;
    private JPanel conteudoCentralPadrao;

    public App() {
        setTitle("Sistema Arcade");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // 1. Remove a barra de título superior do Windows
        setUndecorated(true);

        // 2. Tela cheia real (esconde a barra de tarefas do Windows)
        GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (gd.isFullScreenSupported()) {
            gd.setFullScreenWindow(this);
        } else {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }

        // colocando o icone da janela
        setIconImage(carregarIconeJanela());

        // Tecla ESC para fechar o programa rapidamente
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    System.exit(0);
                }
            }
        });
        setFocusable(true);

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

        // Instância da Tela de Clientes (Inicia Oculta)
        telaClientes = new TelaClientes();
        telaClientes.setBounds(70, 0, larguraTela - 70, alturaTela);
        telaClientes.setVisible(false);
        layeredPane.add(telaClientes, JLayeredPane.DEFAULT_LAYER);

        // Instância da Tela de Cartões (Inicia Oculta)
        telaCartao = new TelaCartao(telaClientes);
        telaCartao.setBounds(70, 0, larguraTela - 70, alturaTela);
        telaCartao.setVisible(false);
        layeredPane.add(telaCartao, JLayeredPane.DEFAULT_LAYER);

        // Instância da Tela de Funcionários (Inicia Oculta) - acesso só do Manager
        // Master
        telaFuncionarios = new TelaFuncionarios();
        telaFuncionarios.setBounds(70, 0, larguraTela - 70, alturaTela);
        telaFuncionarios.setVisible(false);
        layeredPane.add(telaFuncionarios, JLayeredPane.DEFAULT_LAYER);

        // Sidebar Fixa de Fundo
        JPanel sidebar = new JPanel();
        sidebar.setBackground(new Color(24, 75, 150));
        sidebar.setLayout(null);
        sidebar.setBounds(0, 0, 70, alturaTela);
        layeredPane.add(sidebar, JLayeredPane.DEFAULT_LAYER);

        // Logo no topo da Sidebar
        JPanel logo = criarLogo();
        logo.setBounds(0, 0, 70, 70);
        sidebar.add(logo);

        // Informações dos botões da Sidebar
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

        // Criar os botões extensíveis
        int yAtual = 70;
        for (String[] btn : botoesInfo) {
            String texto = btn[0];
            String imagem = btn[1];

            // O botão "Funcionários" só aparece para o Manager Master
            if (texto.equals("Funcionários") && !Sessao.isManagerMaster()) {
                continue;
            }

            PainelExtensor botaoExtensivel = new PainelExtensor(texto, imagem, yAtual, 70, 180);

            // Ação ao clicar no botão
            botaoExtensivel.setAcaoClique(() -> {

                if (texto.equalsIgnoreCase("Clientes")) {
                    mostrarTela(telaClientes);
                } else if (texto.equalsIgnoreCase("Cartões")) {
                    telaCartao.atualizarNomesClientes();
                    mostrarTela(telaCartao);
                } else if (texto.equalsIgnoreCase("Funcionários")) {
                    // Só o Manager Master pode abrir o cadastro de funcionários
                    if (!Sessao.isManagerMaster()) {
                        JOptionPane.showMessageDialog(this,
                                "Apenas o Manager Master pode acessar Funcionários.",
                                "Acesso negado", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    mostrarTela(telaFuncionarios);
                } else if (texto.equalsIgnoreCase("Início")) {
                    mostrarTela(null);
                } else if (texto.equalsIgnoreCase("Sair")) {
                    // Encerra a sessão, sai da tela cheia e volta para uma tela de login nova
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

        // Redimensionamento dinâmico
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = getWidth();
                int h = getHeight();

                sidebar.setBounds(0, 0, 70, h);
                conteudoCentralPadrao.setBounds(70, 0, w - 70, h);
                telaClientes.setBounds(70, 0, w - 70, h);
                telaCartao.setBounds(70, 0, w - 70, h);
                telaFuncionarios.setBounds(70, 0, w - 70, h);
            }
        });
    }

    // Mostra apenas a tela informada (null = tela de início)
    private void mostrarTela(JPanel alvo) {
        telaClientes.setVisible(alvo == telaClientes);
        telaCartao.setVisible(alvo == telaCartao);
        telaFuncionarios.setVisible(alvo == telaFuncionarios);
        conteudoCentralPadrao.setVisible(alvo == null);
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
        // Busca a imagem no classpath (/images/Logo_arcade.png)
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
}
