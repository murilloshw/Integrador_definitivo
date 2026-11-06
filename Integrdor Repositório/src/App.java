import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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

/**
 * Janela principal do sistema (aberta depois do login).
 *
 * Layout: uma barra lateral fixa à esquerda (70px) com os botões de navegação e,
 * à direita, a área onde fica a tela escolhida. Todas as telas são criadas uma vez
 * só e ficam empilhadas; trocar de tela é apenas esconder umas e mostrar outra.
 *
 * O autoatendimento fica numa camada acima de tudo (inclusive da barra lateral).
 */
public class App extends JFrame {

    // =========================================================================
    // CONSTANTES E CAMPOS
    // =========================================================================

    /** De quanto em quanto tempo a tela aberta é atualizada com os dados do banco. */
    private static final int INTERVALO_ATUALIZACAO_MS = 3000;

    /** Largura da barra lateral (e altura de cada botão dela). */
    private static final int LARGURA_SIDEBAR = 70;

    private TelaDashboard telaDashboard;
    private TelaClientes telaClientes;
    private TelaCartao telaCartao;
    private TelaMaquinas telaMaquinas;
    private TelaFuncionarios telaFuncionarios;
    private TelaRecargas telaRecargas;
    private TelaPremios telaPremios;
    private TelaPartidas telaPartidas;
    private TelaBiblioteca telaBiblioteca;
    private TelaLog telaLog;
    private TelaAutoatendimento telaAutoatendimento;

    /** Todas as telas que ocupam a área à direita da barra lateral. */
    private JPanel[] telas;

    private Timer timerAtualizacao;

    // =========================================================================
    // CONSTRUTOR
    // Monta a janela em tela cheia, cria as telas, a barra lateral e o timer.
    // =========================================================================

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

        // ESC pergunta se quer sair; no autoatendimento pede login de administrador (ver SairComEsc)
        SairComEsc.instalar();

        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setLayout(null);
        setContentPane(layeredPane);

        criarTelas(layeredPane);

        JPanel sidebar = criarSidebar(layeredPane);

        // Ao redimensionar a janela, tudo acompanha o novo tamanho
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int w = getWidth();
                int h = getHeight();
                sidebar.setBounds(0, 0, LARGURA_SIDEBAR, h);
                posicionarTelas(w, h);
            }
        });

        // Tempo real: a tela aberta busca no banco as alterações feitas em outros
        // lugares (recargas no balcão, no autoatendimento ou em outra máquina).
        timerAtualizacao = new Timer(INTERVALO_ATUALIZACAO_MS, e -> atualizarTelaVisivel());
        timerAtualizacao.start();
    }

    // =========================================================================
    // CRIAÇÃO DAS TELAS
    // =========================================================================

    /** Cria todas as telas, deixa só o Início visível e as adiciona à janela. */
    private void criarTelas(JLayeredPane layeredPane) {
        telaDashboard = new TelaDashboard();
        telaClientes = new TelaClientes();
        telaCartao = new TelaCartao(telaClientes); // usa a tela de clientes para listar os nomes
        telaMaquinas = new TelaMaquinas();
        telaFuncionarios = new TelaFuncionarios();
        telaRecargas = new TelaRecargas();
        telaPremios = new TelaPremios();
        telaPartidas = new TelaPartidas();
        telaBiblioteca = new TelaBiblioteca();
        telaLog = new TelaLog();

        telas = new JPanel[] { telaDashboard, telaClientes, telaCartao, telaMaquinas, telaFuncionarios,
                telaRecargas, telaPremios, telaPartidas, telaBiblioteca, telaLog };

        for (JPanel tela : telas) {
            tela.setVisible(tela == telaDashboard);
            layeredPane.add(tela, JLayeredPane.DEFAULT_LAYER);
        }

        // Autoatendimento: quando um administrador confirma a saída, a tela é fechada
        telaAutoatendimento = new TelaAutoatendimento(telaRecargas, this::fecharAutoatendimento);
        telaAutoatendimento.setVisible(false);
        layeredPane.add(telaAutoatendimento, JLayeredPane.POPUP_LAYER);

        posicionarTelas(getWidth(), getHeight());

        // Botão "Autoatendimento" da tela de Recargas: só funcionários com
        // permissão administrativa podem abrir (o mesmo vale para sair dele).
        telaRecargas.setAcaoAutoatendimento(() -> {
            if (!Sessao.isManagerMaster()) {
                JOptionPane.showMessageDialog(this,
                        "Apenas funcionários com permissão administrativa podem abrir o autoatendimento.",
                        "Acesso negado", JOptionPane.WARNING_MESSAGE);
                return;
            }
            abrirAutoatendimento();
        });
    }

    /** Telas normais ficam à direita da barra lateral; o autoatendimento ocupa a janela inteira. */
    private void posicionarTelas(int w, int h) {
        for (JPanel tela : telas) {
            tela.setBounds(LARGURA_SIDEBAR, 0, w - LARGURA_SIDEBAR, h);
        }
        if (telaAutoatendimento != null) {
            telaAutoatendimento.setBounds(0, 0, w, h);
        }
    }

    // =========================================================================
    // BARRA LATERAL (MENU)
    // Logo no topo e um botão expansível (PainelExtensor) para cada tela.
    // Funcionários só aparece para administradores; Biblioteca só para bibliotecários.
    // =========================================================================

    private JPanel criarSidebar(JLayeredPane layeredPane) {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(new Color(24, 75, 150));
        sidebar.setLayout(null);
        sidebar.setBounds(0, 0, LARGURA_SIDEBAR, getHeight());
        layeredPane.add(sidebar, JLayeredPane.DEFAULT_LAYER);

        JPanel logo = criarLogo();
        logo.setBounds(0, 0, LARGURA_SIDEBAR, LARGURA_SIDEBAR);
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
                { "Biblioteca", "botao biblioteca.png" },
                { "Log", "botao log.png" },
                { "Sair", "botao saida.png" }
        };

        int yAtual = LARGURA_SIDEBAR;
        for (String[] btn : botoesInfo) {
            String texto = btn[0];
            String imagem = btn[1];

            if ((texto.equals("Funcionários") || texto.equals("Log")) && !Sessao.isManagerMaster()) {
                continue;
            }
            if (texto.equals("Biblioteca") && !Sessao.isBibliotecario()) {
                continue;
            }

            PainelExtensor botao = new PainelExtensor(texto, imagem, yAtual, LARGURA_SIDEBAR, 180);
            botao.setAcaoClique(() -> aoClicarMenu(texto));
            layeredPane.add(botao, JLayeredPane.POPUP_LAYER);
            yAtual += LARGURA_SIDEBAR;
        }
        return sidebar;
    }

    /** O que cada botão do menu faz: recarrega os dados da tela (quando preciso) e a mostra. */
    private void aoClicarMenu(String texto) {
        switch (texto) {
            case "Início":
                telaDashboard.recarregar();
                mostrarTela(telaDashboard);
                break;
            case "Clientes":
                mostrarTela(telaClientes);
                break;
            case "Cartões":
                telaCartao.atualizarNomesClientes();
                telaCartao.recarregar();
                mostrarTela(telaCartao);
                break;
            case "Partidas":
                telaPartidas.recarregar();
                mostrarTela(telaPartidas);
                break;
            case "Máquinas":
                mostrarTela(telaMaquinas);
                break;
            case "Recargas":
                telaRecargas.carregarDadosRecargas();
                mostrarTela(telaRecargas);
                break;
            case "Prêmios":
                telaPremios.recarregar();
                mostrarTela(telaPremios);
                break;
            case "Funcionários":
                if (!Sessao.isManagerMaster()) {
                    JOptionPane.showMessageDialog(this, "Apenas o Manager Master pode acessar Funcionários.",
                            "Acesso negado", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                telaFuncionarios.recarregar();
                mostrarTela(telaFuncionarios);
                break;
            case "Biblioteca":
                if (!Sessao.isBibliotecario()) {
                    JOptionPane.showMessageDialog(this,
                            "Apenas funcionários com cargo bibliotecário podem acessar a Biblioteca.",
                            "Acesso negado", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                telaBiblioteca.recarregar();
                mostrarTela(telaBiblioteca);
                break;
            case "Log":
                if (!Sessao.isManagerMaster()) {
                    JOptionPane.showMessageDialog(this, "Apenas administradores podem ver o log do sistema.",
                            "Acesso negado", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                telaLog.recarregar();
                mostrarTela(telaLog);
                break;
            case "Sair":
                sair();
                break;
        }
    }

    // =========================================================================
    // NAVEGAÇÃO E ATUALIZAÇÃO
    // =========================================================================

    /** Mostra só a tela escolhida e esconde as outras. */
    private void mostrarTela(JPanel alvo) {
        for (JPanel tela : telas) {
            tela.setVisible(tela == alvo);
        }
        getContentPane().revalidate();
        getContentPane().repaint();
    }

    /**
     * Chamado pelo timer: atualiza só a tela que está aberta, e nada enquanto o
     * autoatendimento estiver na frente.
     */
    private void atualizarTelaVisivel() {
        if (telaAutoatendimento.isVisible()) {
            return;
        }
        if (telaDashboard.isVisible()) {
            telaDashboard.atualizarSilenciosamente();
        } else if (telaCartao.isVisible()) {
            telaCartao.atualizarSilenciosamente();
        } else if (telaRecargas.isVisible()) {
            telaRecargas.atualizarSilenciosamente();
        } else if (telaLog.isVisible()) {
            telaLog.atualizarSilenciosamente();
        }
    }

    /** Sair do sistema: encerra a sessão e volta para a tela de login. */
    private void sair() {
        timerAtualizacao.stop();
        Auditoria.registrar("LOGOUT", null); // antes de encerrar, para saber quem saiu
        Sessao.encerrar();
        SairComEsc.desviarPara(null); // ESC volta a perguntar "Deseja sair?"
        GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().setFullScreenWindow(null);
        dispose();
        new TelaLogin();
    }

    // =========================================================================
    // AUTOATENDIMENTO
    // =========================================================================

    /** Mostra o autoatendimento do cliente por cima de todo o sistema. */
    private void abrirAutoatendimento() {
        telaAutoatendimento.setBounds(0, 0, getWidth(), getHeight());
        telaAutoatendimento.iniciar();
        telaAutoatendimento.setVisible(true);
        SairComEsc.desviarPara(telaAutoatendimento::abrirSaidaFuncionario); // ESC pede login de administrador
        Auditoria.registrar("AUTOATENDIMENTO_ABERTO", null);
        getContentPane().repaint();
    }

    /** Fecha o autoatendimento (após a saída de um administrador) e atualiza os dados na hora. */
    private void fecharAutoatendimento() {
        telaAutoatendimento.encerrar();
        telaAutoatendimento.setVisible(false);
        SairComEsc.desviarPara(null); // ESC volta a perguntar "Deseja sair?"
        Auditoria.registrar("AUTOATENDIMENTO_FECHADO", null);
        atualizarTelaVisivel();
        getContentPane().repaint();
    }

    // =========================================================================
    // LOGO E ÍCONE DA JANELA
    // =========================================================================

    /** Bloco do topo da barra lateral com o logo (ou a letra "A" se a imagem não existir). */
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
        return resource != null ? new ImageIcon(resource) : null;
    }

    private Image carregarIconeJanela() {
        ImageIcon logo = carregarLogo();
        return logo != null ? logo.getImage() : null;
    }
}
