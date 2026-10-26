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

public class App extends JFrame {

    private TelaClientes telaClientes;
    private TelaCartao telaCartao;
    private TelaMaquinas telaMaquinas;
    private TelaFuncionarios telaFuncionarios;
    private TelaRecargas telaRecargas; // Declarada a tela de recargas
    private JPanel conteudoCentralPadrao;

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

        // Instância da Tela de Clientes
        telaClientes = new TelaClientes();
        telaClientes.setBounds(70, 0, larguraTela - 70, alturaTela);
        telaClientes.setVisible(false);
        layeredPane.add(telaClientes, JLayeredPane.DEFAULT_LAYER);

        // Instância da Tela de Cartões
        telaCartao = new TelaCartao(telaClientes);
        telaCartao.setBounds(70, 0, larguraTela - 70, alturaTela);
        telaCartao.setVisible(false);
        layeredPane.add(telaCartao, JLayeredPane.DEFAULT_LAYER);

        // Instância da Tela de Máquinas
        telaMaquinas = new TelaMaquinas();
        telaMaquinas.setBounds(70, 0, larguraTela - 70, alturaTela);
        telaMaquinas.setVisible(false);
        layeredPane.add(telaMaquinas, JLayeredPane.DEFAULT_LAYER);

        // Instância da Tela de Funcionários
        telaFuncionarios = new TelaFuncionarios();
        telaFuncionarios.setBounds(70, 0, larguraTela - 70, alturaTela);
        telaFuncionarios.setVisible(false);
        layeredPane.add(telaFuncionarios, JLayeredPane.DEFAULT_LAYER);

        // Instância da Tela de Recargas (Adicionada)
        telaRecargas = new TelaRecargas();
        telaRecargas.setBounds(70, 0, larguraTela - 70, alturaTela);
        telaRecargas.setVisible(false);
        layeredPane.add(telaRecargas, JLayeredPane.DEFAULT_LAYER);

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

            // Ação ao clicar no botão
            botaoExtensivel.setAcaoClique(() -> {

                if (texto.equalsIgnoreCase("Clientes")) {
                    mostrarTela(telaClientes);
                } else if (texto.equalsIgnoreCase("Cartões")) {
                    telaCartao.atualizarNomesClientes();
                    mostrarTela(telaCartao);
                } else if (texto.equalsIgnoreCase("Máquinas")) {
                    mostrarTela(telaMaquinas);
                } else if (texto.equalsIgnoreCase("Recargas")) { // Redirecionamento configurado
                    telaRecargas.carregarDadosRecargas();
                    System.out.println("Clicou em recargas!");
                    mostrarTela(telaRecargas);
                } else if (texto.equalsIgnoreCase("Funcionários")) {
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
                    Sessao.encerrar();
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().setFullScreenWindow(null);
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
                telaRecargas.setBounds(70, 0, w - 70, h); // Redimensionamento adicionado
            }
        });
    }

 private void mostrarTela(JPanel alvo) {
    telaClientes.setVisible(alvo == telaClientes);
    telaCartao.setVisible(alvo == telaCartao);
    telaMaquinas.setVisible(alvo == telaMaquinas);
    telaFuncionarios.setVisible(alvo == telaFuncionarios);
    telaRecargas.setVisible(alvo == telaRecargas);
    conteudoCentralPadrao.setVisible(alvo == null);

    // Força o contentor a atualizar e redesenhar os componentes visíveis
    getContentPane().revalidate();
    getContentPane().repaint();
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
    
}