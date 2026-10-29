import java.awt.AWTEvent;
import java.awt.image.BufferedImage;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;

/**
 * Autoatendimento de recargas: a tela "presa" que o CLIENTE usa sozinho nos balcões.
 *
 * Fluxo (4 etapas):
 *   1. Cartão     -> cliente digita o número do cartão (teclado na tela ou teclado físico)
 *   2. Valor      -> escolhe R$ 10, 20, 30, 50 ou 100 (vê os créditos antes de pagar)
 *   3. Pagamento  -> escolhe Pix, débito ou crédito (lista vem do cadastro) e confirma
 *   4. Confirmação-> mostra o novo saldo e volta sozinha ao início
 *
 * Regras de segurança e de uso:
 *   - Não há como sair pelo sistema: a barra lateral fica coberta e o ESC é ignorado.
 *   - Para sair é preciso o usuário e a senha de um funcionário cadastrado e ATIVO.
 *     Depois de 3 senhas erradas, a tentativa fica bloqueada por 30 segundos.
 *   - Sem uso por 60 segundos, a tela volta ao início e limpa o que foi digitado
 *     (para o próximo cliente não ver o cartão do anterior).
 *   - A recarga é gravada SEM funcionário (campo vazio), conforme a documentação.
 *   - Mensagens para o cliente são simples; detalhes técnicos vão só para o console.
 *   - Botões grandes, sem foco de teclado (pensado para toque).
 */
public class TelaAutoatendimento extends JPanel {

    // ---------------------------------------------------------------
    // REGRAS E CONSTANTES
    // ---------------------------------------------------------------
    private static final int[] VALORES_REAIS = { 10, 20, 30, 50, 100 };
    private static final int DIGITOS_CARTAO = 6;
    private static final int INATIVIDADE_MS = 60_000;
    private static final int SEGUNDOS_ATE_INICIO = 8;
    private static final int MAX_TENTATIVAS_SAIDA = 3;
    private static final int BLOQUEIO_SAIDA_S = 30;

    private static final String ETAPA_CARTAO = "cartao";
    private static final String ETAPA_VALOR = "valor";
    private static final String ETAPA_PAGAMENTO = "pagamento";
    private static final String ETAPA_CONFIRMACAO = "confirmacao";

    private static final String MSG_INDISPONIVEL =
            "O sistema está indisponível no momento. Tente novamente ou procure a recepção.";

    // ---------------------------------------------------------------
    // CORES (mesma paleta das outras telas)
    // ---------------------------------------------------------------
    private static final Color FUNDO = new Color(2, 12, 27);
    private static final Color CARD = new Color(10, 22, 40);
    private static final Color BORDA = new Color(23, 42, 69);
    private static final Color CAMPO = new Color(13, 27, 46);
    private static final Color AZUL = new Color(14, 116, 233);
    private static final Color AZUL_PRESSIONADO = new Color(10, 90, 185);
    private static final Color AZUL_CLARO = new Color(125, 211, 252);
    private static final Color BOTAO_SECUNDARIO = new Color(23, 42, 69);
    private static final Color DESABILITADO = new Color(30, 41, 59);
    private static final Color TEXTO_SUAVE = new Color(148, 163, 184);
    private static final Color ERRO = new Color(248, 113, 113);
    private static final Color SUCESSO = new Color(74, 222, 128);
    private static final Color COR_ICONE_CLARO = new Color(226, 232, 240);

    private static final NumberFormat MOEDA = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

    // ---------------------------------------------------------------
    // ESTADO
    // ---------------------------------------------------------------
    private final TelaRecargas recargas;
    private final Runnable aoSair;

    private final CardLayout cards = new CardLayout();
    private JPanel painelEtapas;
    private JLabel lblPasso;
    private String etapaAtual = ETAPA_CARTAO;

    // Etapa 1 - cartão
    private final StringBuilder digitos = new StringBuilder();
    private JLabel lblCartaoDigitado;
    private JLabel lblErroCartao;
    private JButton btnContinuar;

    // Etapa 2 - valor
    private JLabel lblOla;
    private JLabel lblSaldo;

    // Etapa 3 - pagamento
    private JLabel lblResumo;
    private JPanel painelFormas;
    private JLabel lblErroPagamento;
    private JButton btnConfirmar;
    private ButtonGroup grupoFormas = new ButtonGroup();
    private List<String> formas = new ArrayList<>();
    private boolean formasCarregadas = false;

    // Etapa 4 - confirmação
    private JLabel lblConfirmacaoDetalhe;
    private JLabel lblContagem;
    private int segundosRestantes;

    // Dados da recarga em andamento
    private TelaRecargas.CartaoInfo cartao;
    private int valorReais;
    private String formaEscolhida;

    /** true enquanto consulta ou grava no banco (bloqueia novos toques). */
    private boolean ocupado = false;
    /** true enquanto a janela de saída de funcionário está aberta. */
    private boolean saidaAberta = false;
    private int tentativasSaidaFalhas = 0;
    /** Até quando a saída fica bloqueada depois de muitas senhas erradas (ms desde 1970). */
    private long bloqueioSaidaAte = 0;
    /**
     * Número da "sessão" do cliente atual. Sobe a cada volta ao início ou ao fechar a tela:
     * respostas do banco que chegam depois disso são descartadas (não mexem na tela nova).
     */
    private int geracao = 0;

    private final Timer timerInatividade = new Timer(INATIVIDADE_MS, e -> aoInatividade());
    private final Timer timerContagem = new Timer(1000, e -> contarRegressiva());

    /** Qualquer toque ou tecla reinicia o relógio de inatividade (mesmo dentro da janela de saída). */
    private final AWTEventListener atividade = e -> {
        if (e.getID() == MouseEvent.MOUSE_PRESSED || e.getID() == KeyEvent.KEY_PRESSED) {
            reiniciarInatividade();
        }
    };

    // ---------------------------------------------------------------
    // CONSTRUTOR E CICLO DE VIDA
    // ---------------------------------------------------------------

    /**
     * @param recargas a tela de recargas (dá acesso ao banco pelas regras já existentes)
     * @param aoSair   chamado quando um funcionário confirma a saída (o App fecha esta tela)
     */
    public TelaAutoatendimento(TelaRecargas recargas, Runnable aoSair) {
        this.recargas = recargas;
        this.aoSair = aoSair;

        timerInatividade.setRepeats(false); // dispara uma vez; é reiniciado a cada toque

        setLayout(new BorderLayout(0, 22));
        setBackground(FUNDO);
        setOpaque(true); // cobre tudo o que está por baixo
        setBorder(BorderFactory.createEmptyBorder(26, 36, 26, 36));

        add(criarCabecalho(), BorderLayout.NORTH);

        painelEtapas = new JPanel(cards);
        painelEtapas.setOpaque(false);
        painelEtapas.add(criarEtapaCartao(), ETAPA_CARTAO);
        painelEtapas.add(criarEtapaValor(), ETAPA_VALOR);
        painelEtapas.add(criarEtapaPagamento(), ETAPA_PAGAMENTO);
        painelEtapas.add(criarEtapaConfirmacao(), ETAPA_CONFIRMACAO);
        add(painelEtapas, BorderLayout.CENTER);

        instalarTeclado();
        mostrarEtapa(ETAPA_CARTAO);
    }

    /** Chamado pelo App ao abrir a tela: liga o controle de inatividade e carrega as formas de pagamento. */
    public void iniciar() {
        Toolkit.getDefaultToolkit().addAWTEventListener(atividade,
                AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        voltarAoInicio();
        carregarFormas();
    }

    /** Chamado pelo App ao fechar a tela: desliga timers e listeners globais. */
    public void encerrar() {
        geracao++;
        Toolkit.getDefaultToolkit().removeAWTEventListener(atividade);
        timerInatividade.stop();
        timerContagem.stop();
    }

    // ---------------------------------------------------------------
    // CABEÇALHO (logo, título, passo atual e botão de saída)
    // ---------------------------------------------------------------
    private JPanel criarCabecalho() {
        JPanel topo = new JPanel(new BorderLayout(20, 0));
        topo.setOpaque(false);

        JPanel marca = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 0));
        marca.setOpaque(false);
        JLabel logo = new JLabel(carregarIcone("Logo_arcade.png", 64, null));
        if (logo.getIcon() == null) {
            logo = new JLabel("A", SwingConstants.CENTER);
            logo.setForeground(Color.WHITE);
            logo.setFont(new Font("Segoe UI", Font.BOLD, 30));
            logo.setPreferredSize(new Dimension(64, 64));
        }
        marca.add(logo);

        JPanel textos = new JPanel(new GridLayout(2, 1, 0, 2));
        textos.setOpaque(false);
        JLabel titulo = new JLabel("Autoatendimento de recargas");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 28));
        titulo.setForeground(Color.WHITE);
        lblPasso = new JLabel();
        lblPasso.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        lblPasso.setForeground(TEXTO_SUAVE);
        textos.add(titulo);
        textos.add(lblPasso);
        marca.add(textos);

        topo.add(marca, BorderLayout.CENTER);
        topo.add(criarBotaoSaida(), BorderLayout.EAST);
        return topo;
    }

    /** Botão de saída com o ícone que já existe no projeto (botao saida.png). Exige login de funcionário. */
    private JButton criarBotaoSaida() {
        // Mesmo desenho do projeto, clareado para aparecer no fundo escuro
        JButton b = new JButton("Sair", carregarIcone("botao saida.png", 34, COR_ICONE_CLARO));
        b.setFont(new Font("Segoe UI", Font.BOLD, 15));
        b.setForeground(TEXTO_SUAVE);
        b.setHorizontalTextPosition(SwingConstants.LEFT);
        b.setIconTextGap(12);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setFocusable(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.setToolTipText("Saída (somente funcionários)");
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDA, 2, true),
                BorderFactory.createEmptyBorder(10, 18, 10, 18)));
        b.addActionListener(e -> abrirSaidaFuncionario());
        return b;
    }

    // ---------------------------------------------------------------
    // ETAPA 1: CARTÃO
    // ---------------------------------------------------------------
    private JPanel criarEtapaCartao() {
        JPanel etapa = new JPanel(new GridLayout(1, 2, 36, 0));
        etapa.setOpaque(false);

        // Esquerda: instruções + número digitado
        JPanel esquerda = painelArredondado(new BorderLayout(0, 18), CARD);
        esquerda.setBorder(BorderFactory.createEmptyBorder(30, 34, 30, 34));

        JPanel textos = new JPanel(new GridLayout(2, 1, 0, 8));
        textos.setOpaque(false);
        JLabel pergunta = new JLabel("Qual é o número do seu cartão?");
        pergunta.setFont(new Font("Segoe UI", Font.BOLD, 26));
        pergunta.setForeground(Color.WHITE);
        JLabel dica = new JLabel("Digite os números que estão no seu cartão e toque em Continuar.");
        dica.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        dica.setForeground(TEXTO_SUAVE);
        textos.add(pergunta);
        textos.add(dica);
        esquerda.add(textos, BorderLayout.NORTH);

        JPanel visor = painelArredondado(new BorderLayout(), CAMPO);
        lblCartaoDigitado = new JLabel("—", SwingConstants.CENTER);
        lblCartaoDigitado.setFont(new Font("Segoe UI", Font.BOLD, 64));
        lblCartaoDigitado.setForeground(AZUL_CLARO);
        visor.add(lblCartaoDigitado, BorderLayout.CENTER);
        esquerda.add(visor, BorderLayout.CENTER);

        JPanel rodapeEsq = new JPanel(new BorderLayout(0, 12));
        rodapeEsq.setOpaque(false);
        lblErroCartao = new JLabel(" ", SwingConstants.CENTER);
        lblErroCartao.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblErroCartao.setForeground(ERRO);
        btnContinuar = criarBotao("Continuar", true, 22, new Dimension(0, 84));
        btnContinuar.addActionListener(e -> continuarComCartao());
        rodapeEsq.add(lblErroCartao, BorderLayout.NORTH);
        rodapeEsq.add(btnContinuar, BorderLayout.CENTER);
        esquerda.add(rodapeEsq, BorderLayout.SOUTH);

        // Direita: teclado numérico
        JPanel direita = painelArredondado(new BorderLayout(), CARD);
        direita.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        JPanel teclado = new JPanel(new GridLayout(4, 3, 14, 14));
        teclado.setOpaque(false);
        for (String t : new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9" }) {
            teclado.add(criarTecla(t, () -> digitar(t)));
        }
        teclado.add(criarTecla("Limpar", this::limparDigitos));
        teclado.add(criarTecla("0", () -> digitar("0")));
        teclado.add(criarTecla("Apagar", this::apagarUltimo));
        direita.add(teclado, BorderLayout.CENTER);

        etapa.add(esquerda);
        etapa.add(direita);
        return etapa;
    }

    private JButton criarTecla(String texto, Runnable acao) {
        JButton b = criarBotao(texto, false, 30, null); // tamanho vem da grade
        b.addActionListener(e -> acao.run());
        return b;
    }

    private void digitar(String d) {
        if (ocupado || digitos.length() >= DIGITOS_CARTAO) {
            return;
        }
        digitos.append(d);
        lblErroCartao.setText(" ");
        atualizarVisorCartao();
    }

    private void apagarUltimo() {
        if (ocupado || digitos.length() == 0) {
            return;
        }
        digitos.deleteCharAt(digitos.length() - 1);
        atualizarVisorCartao();
    }

    private void limparDigitos() {
        if (ocupado) {
            return;
        }
        digitos.setLength(0);
        atualizarVisorCartao();
    }

    private void atualizarVisorCartao() {
        lblCartaoDigitado.setText(digitos.length() == 0 ? "—" : digitos.toString());
    }

    private void mostrarErroCartao(String msg) {
        lblErroCartao.setText(msg);
    }

    /** Confere o cartão no banco (em segundo plano) antes de avançar. */
    private void continuarComCartao() {
        if (ocupado) {
            return;
        }
        if (digitos.length() == 0) {
            mostrarErroCartao("Digite o número do seu cartão.");
            return;
        }
        final long idCartao = Long.parseLong(digitos.toString());
        final int geracaoAtual = geracao;

        ocupado = true;
        btnContinuar.setEnabled(false);
        btnContinuar.setText("Verificando...");

        emBackground(() -> recargas.buscarCartao(idCartao),
                info -> {
                    ocupado = false;
                    liberarBotaoContinuar();
                    if (geracaoAtual != geracao) {
                        return; // o cliente já voltou ao início (ou a tela foi fechada)
                    }
                    if (info == null) {
                        mostrarErroCartao("Cartão não encontrado. Confira o número ou procure a recepção.");
                        return;
                    }
                    if (!info.ativo) {
                        mostrarErroCartao("Este cartão está bloqueado. Procure a recepção.");
                        return;
                    }
                    cartao = info;
                    prepararValores();
                    mostrarEtapa(ETAPA_VALOR);
                },
                ex -> {
                    ocupado = false;
                    liberarBotaoContinuar();
                    registrarFalha("consultar cartão", ex);
                    if (geracaoAtual == geracao) {
                        mostrarErroCartao(MSG_INDISPONIVEL);
                    }
                });
    }

    /** Só devolve o aspecto normal ao botão (quem controla "ocupado" é quem chamou). */
    private void liberarBotaoContinuar() {
        btnContinuar.setEnabled(true);
        btnContinuar.setText("Continuar");
    }

    // ---------------------------------------------------------------
    // ETAPA 2: VALOR
    // ---------------------------------------------------------------
    private JPanel criarEtapaValor() {
        JPanel etapa = new JPanel(new BorderLayout(0, 26));
        etapa.setOpaque(false);

        JPanel cabecalho = new JPanel(new GridLayout(2, 1, 0, 6));
        cabecalho.setOpaque(false);
        lblOla = new JLabel(" ");
        lblOla.setFont(new Font("Segoe UI", Font.BOLD, 34));
        lblOla.setForeground(Color.WHITE);
        lblSaldo = new JLabel(" ");
        lblSaldo.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        lblSaldo.setForeground(TEXTO_SUAVE);
        cabecalho.add(lblOla);
        cabecalho.add(lblSaldo);
        etapa.add(cabecalho, BorderLayout.NORTH);

        JPanel opcoesBox = painelArredondado(new BorderLayout(0, 20), CARD);
        opcoesBox.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        JLabel instrucao = new JLabel("Quanto você quer recarregar?");
        instrucao.setFont(new Font("Segoe UI", Font.BOLD, 22));
        instrucao.setForeground(Color.WHITE);
        opcoesBox.add(instrucao, BorderLayout.NORTH);

        JPanel grade = new JPanel(new GridLayout(1, VALORES_REAIS.length, 18, 0));
        grade.setOpaque(false);
        for (int reais : VALORES_REAIS) {
            int creditos = reais * TelaRecargas.CREDITOS_POR_REAL;
            String html = "<html><center>"
                    + "<span style='font-size:22px'><b>" + MOEDA.format(reais) + "</b></span><br>"
                    + "<span style='font-size:14px'>" + creditos + " créditos</span>"
                    + "</center></html>";
            JButton b = criarBotao(html, false, 18, new Dimension(0, 150));
            b.addActionListener(e -> escolherValor(reais));
            grade.add(b);
        }
        opcoesBox.add(grade, BorderLayout.CENTER);
        etapa.add(opcoesBox, BorderLayout.CENTER);

        JButton voltar = criarBotao("Voltar", false, 18, new Dimension(220, 70));
        voltar.addActionListener(e -> voltarAoInicio());
        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rodape.setOpaque(false);
        rodape.add(voltar);
        etapa.add(rodape, BorderLayout.SOUTH);
        return etapa;
    }

    private void prepararValores() {
        lblOla.setText("Olá, " + cartao.nome + "!");
        lblSaldo.setText("Cartão " + String.format("%03d", cartao.id)
                + "  ·  Saldo atual: " + cartao.creditos + " créditos");
    }

    private void escolherValor(int reais) {
        valorReais = reais;
        prepararPagamento();
        mostrarEtapa(ETAPA_PAGAMENTO);
    }

    // ---------------------------------------------------------------
    // ETAPA 3: PAGAMENTO
    // ---------------------------------------------------------------
    private JPanel criarEtapaPagamento() {
        JPanel etapa = new JPanel(new BorderLayout(0, 26));
        etapa.setOpaque(false);

        lblResumo = new JLabel(" ");
        lblResumo.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        lblResumo.setForeground(Color.WHITE);
        etapa.add(lblResumo, BorderLayout.NORTH);

        JPanel formasBox = painelArredondado(new BorderLayout(0, 20), CARD);
        formasBox.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        JLabel instrucao = new JLabel("Como você vai pagar?");
        instrucao.setFont(new Font("Segoe UI", Font.BOLD, 22));
        instrucao.setForeground(Color.WHITE);
        formasBox.add(instrucao, BorderLayout.NORTH);

        painelFormas = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 18));
        painelFormas.setOpaque(false);
        formasBox.add(painelFormas, BorderLayout.CENTER);

        lblErroPagamento = new JLabel(" ", SwingConstants.CENTER);
        lblErroPagamento.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblErroPagamento.setForeground(ERRO);
        formasBox.add(lblErroPagamento, BorderLayout.SOUTH);
        etapa.add(formasBox, BorderLayout.CENTER);

        JButton voltar = criarBotao("Voltar", false, 18, new Dimension(220, 76));
        voltar.addActionListener(e -> mostrarEtapa(ETAPA_VALOR));
        btnConfirmar = criarBotao("Confirmar recarga", true, 22, new Dimension(340, 76));
        btnConfirmar.setEnabled(false);
        btnConfirmar.addActionListener(e -> confirmarRecarga());

        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setOpaque(false);
        rodape.add(voltar, BorderLayout.WEST);
        rodape.add(btnConfirmar, BorderLayout.EAST);
        etapa.add(rodape, BorderLayout.SOUTH);
        return etapa;
    }

    private void prepararPagamento() {
        formaEscolhida = null;
        grupoFormas.clearSelection();
        btnConfirmar.setEnabled(false);
        lblErroPagamento.setText(" ");
        int creditos = valorReais * TelaRecargas.CREDITOS_POR_REAL;
        lblResumo.setText("Cartão " + String.format("%03d", cartao.id) + " — recarga de "
                + MOEDA.format(valorReais) + " = " + creditos + " créditos");
    }

    /** Carrega as formas de pagamento do banco (em segundo plano) e monta os botões. */
    private void carregarFormas() {
        emBackground(() -> recargas.formasAtivasAutoatendimento(),
                lista -> {
                    formas = lista;
                    formasCarregadas = true;
                    preencherFormas();
                },
                ex -> {
                    formas = new ArrayList<>();
                    formasCarregadas = true;
                    preencherFormas();
                    registrarFalha("carregar formas de pagamento", ex);
                });
    }

    private void preencherFormas() {
        painelFormas.removeAll();
        grupoFormas = new ButtonGroup();
        formaEscolhida = null;

        if (!formasCarregadas) {
            painelFormas.add(criarRotuloAviso("Carregando formas de pagamento..."));
        } else if (formas.isEmpty()) {
            painelFormas.add(criarRotuloAviso("Nenhuma forma de pagamento disponível. Procure a recepção."));
        } else {
            for (String forma : formas) {
                JToggleButton b = criarOpcaoForma(forma);
                b.addActionListener(e -> selecionarForma(forma));
                grupoFormas.add(b);
                painelFormas.add(b);
            }
        }
        painelFormas.revalidate();
        painelFormas.repaint();
    }

    private JLabel criarRotuloAviso(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        l.setForeground(TEXTO_SUAVE);
        return l;
    }

    private void selecionarForma(String forma) {
        formaEscolhida = forma;
        lblErroPagamento.setText(" ");
        btnConfirmar.setEnabled(true);
    }

    /** Grava a recarga (em segundo plano). Só mostra sucesso depois que o banco confirmou. */
    private void confirmarRecarga() {
        if (ocupado || formaEscolhida == null || cartao == null) {
            return;
        }
        ocupado = true;
        final long idCartao = cartao.id;
        final BigDecimal valor = BigDecimal.valueOf(valorReais);
        final String forma = formaEscolhida;
        final String nomeCliente = cartao.nome;
        final int geracaoAtual = geracao;
        bloquearPagamento(true);

        emBackground(() -> {
            long creditos = recargas.registrarRecargaAutoatendimento(idCartao, valor, forma);
            long saldo;
            try {
                TelaRecargas.CartaoInfo atual = recargas.buscarCartao(idCartao);
                saldo = atual != null ? atual.creditos : -1;
            } catch (SQLException e) {
                saldo = -1; // a recarga já foi gravada; só não conseguimos ler o saldo agora
            }
            return new long[] { creditos, saldo };
        },
                r -> {
                    ocupado = false;
                    bloquearPagamento(false);
                    if (geracaoAtual != geracao) {
                        return; // a recarga já foi gravada; a tela já foi fechada/reiniciada
                    }
                    mostrarConfirmacao(nomeCliente, idCartao, r[0], r[1]);
                },
                ex -> {
                    ocupado = false;
                    bloquearPagamento(false);
                    if (ex instanceof TelaRecargas.RecargaException) {
                        // mensagem de regra de negócio (ex.: cartão inativo), já escrita para o usuário
                        lblErroPagamento.setText(ex.getMessage());
                    } else {
                        lblErroPagamento.setText("Não foi possível registrar a recarga. Nenhum crédito foi adicionado."
                                + " Procure a recepção.");
                    }
                    registrarFalha("registrar recarga", ex);
                });
    }

    private void bloquearPagamento(boolean bloquear) {
        btnConfirmar.setEnabled(!bloquear && formaEscolhida != null);
        btnConfirmar.setText(bloquear ? "Registrando..." : "Confirmar recarga");
        for (java.util.Enumeration<javax.swing.AbstractButton> e = grupoFormas.getElements(); e.hasMoreElements();) {
            e.nextElement().setEnabled(!bloquear);
        }
    }

    // ---------------------------------------------------------------
    // ETAPA 4: CONFIRMAÇÃO
    // ---------------------------------------------------------------
    private JPanel criarEtapaConfirmacao() {
        JPanel etapa = painelArredondado(new GridLayout(0, 1, 0, 18), CARD);
        etapa.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));

        JLabel titulo = new JLabel("Recarga confirmada!", SwingConstants.CENTER);
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 44));
        titulo.setForeground(SUCESSO);

        lblConfirmacaoDetalhe = new JLabel(" ", SwingConstants.CENTER);
        lblConfirmacaoDetalhe.setFont(new Font("Segoe UI", Font.PLAIN, 22));
        lblConfirmacaoDetalhe.setForeground(Color.WHITE);

        lblContagem = new JLabel(" ", SwingConstants.CENTER);
        lblContagem.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        lblContagem.setForeground(TEXTO_SUAVE);

        JButton novaRecarga = criarBotao("Nova recarga", true, 22, new Dimension(300, 80));
        novaRecarga.addActionListener(e -> voltarAoInicio());
        JPanel botaoBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        botaoBox.setOpaque(false);
        botaoBox.add(novaRecarga);

        etapa.add(titulo);
        etapa.add(lblConfirmacaoDetalhe);
        etapa.add(lblContagem);
        etapa.add(botaoBox);
        return etapa;
    }

    private void mostrarConfirmacao(String nomeCliente, long idCartao, long creditos, long saldo) {
        String saldoTexto = saldo >= 0 ? saldo + " créditos" : "consulte a recepção";
        lblConfirmacaoDetalhe.setText("<html><div style='text-align:center'>"
                + "+" + creditos + " créditos adicionados ao cartão " + String.format("%03d", idCartao) + "."
                + "<br>Saldo atual: " + saldoTexto + "</div></html>");
        segundosRestantes = SEGUNDOS_ATE_INICIO;
        atualizarContagem();
        mostrarEtapa(ETAPA_CONFIRMACAO);
        timerContagem.restart();
    }

    private void contarRegressiva() {
        segundosRestantes--;
        if (segundosRestantes <= 0) {
            voltarAoInicio();
            return;
        }
        atualizarContagem();
    }

    private void atualizarContagem() {
        lblContagem.setText("Voltando ao início em " + segundosRestantes + " segundos...");
    }

    // ---------------------------------------------------------------
    // NAVEGAÇÃO, INATIVIDADE E TECLADO
    // ---------------------------------------------------------------

    /** Volta ao início e limpa tudo o que foi digitado ou consultado. */
    private void voltarAoInicio() {
        geracao++;
        timerContagem.stop();
        digitos.setLength(0);
        atualizarVisorCartao();
        lblErroCartao.setText(" ");
        liberarBotaoContinuar();
        cartao = null;
        valorReais = 0;
        formaEscolhida = null;
        grupoFormas.clearSelection();
        mostrarEtapa(ETAPA_CARTAO);
        reiniciarInatividade();
    }

    private void mostrarEtapa(String etapa) {
        etapaAtual = etapa;
        cards.show(painelEtapas, etapa);
        lblPasso.setText(textoPasso(etapa));
        revalidate();
        repaint();
    }

    private String textoPasso(String etapa) {
        switch (etapa) {
            case ETAPA_VALOR:
                return "Passo 2 de 3 · Valor";
            case ETAPA_PAGAMENTO:
                return "Passo 3 de 3 · Pagamento";
            case ETAPA_CONFIRMACAO:
                return "Recarga concluída";
            default:
                return "Passo 1 de 3 · Cartão";
        }
    }

    private void reiniciarInatividade() {
        timerInatividade.restart();
    }

    /** Sem uso por um tempo: volta ao início, a menos que haja operação ou janela de saída aberta. */
    private void aoInatividade() {
        if (ocupado || saidaAberta) {
            reiniciarInatividade();
            return;
        }
        voltarAoInicio();
    }

    /** Teclado físico: dígitos, Backspace (apagar) e Enter (continuar), só na etapa do cartão. */
    private void instalarTeclado() {
        InputMap im = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = getActionMap();

        for (char c = '0'; c <= '9'; c++) {
            final String d = String.valueOf(c);
            String nome = "digito" + c;
            im.put(KeyStroke.getKeyStroke(c), nome);
            am.put(nome, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (podeUsarTeclado()) {
                        digitar(d);
                    }
                }
            });
        }

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "apagar");
        am.put("apagar", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (podeUsarTeclado()) {
                    apagarUltimo();
                }
            }
        });

        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "continuar");
        am.put("continuar", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (podeUsarTeclado()) {
                    continuarComCartao();
                }
            }
        });
    }

    private boolean podeUsarTeclado() {
        return ETAPA_CARTAO.equals(etapaAtual) && !saidaAberta;
    }

    // ---------------------------------------------------------------
    // SAÍDA (somente funcionário cadastrado e ativo)
    // ---------------------------------------------------------------
    private void abrirSaidaFuncionario() {
        if (saidaAberta) {
            return;
        }
        saidaAberta = true;

        JDialog dialogo = new JDialog(SwingUtilities.getWindowAncestor(this),
                "Saída do autoatendimento", Dialog.ModalityType.APPLICATION_MODAL);
        dialogo.setResizable(false);
        dialogo.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);

        JPanel c = new JPanel(new BorderLayout(0, 16));
        c.setBackground(CARD);
        c.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 6));
        form.setOpaque(false);
        JLabel titulo = new JLabel("Área de funcionários");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titulo.setForeground(Color.WHITE);
        JLabel aviso = new JLabel("Digite o usuário e a senha de um administrador ativo.");
        aviso.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        aviso.setForeground(TEXTO_SUAVE);

        JTextField txtUsuario = new JTextField();
        JPasswordField txtSenha = new JPasswordField();
        for (JTextField campo : new JTextField[] { txtUsuario, txtSenha }) {
            campo.setPreferredSize(new Dimension(340, 40));
            campo.setBackground(CAMPO);
            campo.setForeground(Color.WHITE);
            campo.setCaretColor(Color.WHITE);
            campo.setFont(new Font("Segoe UI", Font.PLAIN, 16));
            campo.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(30, 48, 80)),
                    BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        }

        JLabel lblErro = new JLabel(" ");
        lblErro.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblErro.setForeground(ERRO);

        form.add(titulo);
        form.add(aviso);
        form.add(criarRotuloCampo("Usuário"));
        form.add(txtUsuario);
        form.add(criarRotuloCampo("Senha"));
        form.add(txtSenha);
        form.add(lblErro);
        c.add(form, BorderLayout.CENTER);

        JButton btnCancelar = criarBotao("Cancelar", false, 16, new Dimension(130, 50));
        JButton btnEntrar = criarBotao("Sair", true, 16, new Dimension(130, 50));

        // true enquanto a conferência no banco está em andamento
        final boolean[] verificando = { false };

        // Mantém o botão desligado e a contagem certa enquanto durar o bloqueio por tentativas
        Runnable atualizarBloqueio = () -> {
            long restante = (bloqueioSaidaAte - System.currentTimeMillis() + 999) / 1000;
            if (restante > 0) {
                btnEntrar.setEnabled(false);
                lblErro.setText("Muitas tentativas. Aguarde " + restante + " s.");
            } else if (!verificando[0]) {
                btnEntrar.setEnabled(true);
                if (lblErro.getText().startsWith("Muitas")) {
                    lblErro.setText(" ");
                }
            }
        };
        Timer relogio = new Timer(1000, e -> atualizarBloqueio.run());

        Runnable fechar = () -> {
            relogio.stop();
            dialogo.dispose();
            saidaAberta = false;
            reiniciarInatividade();
        };
        btnCancelar.addActionListener(e -> fechar.run());
        dialogo.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                fechar.run();
            }
        });

        // Toda tentativa que não libera a saída conta para o bloqueio (inclusive usuário sem permissão)
        Consumer<String> falha = msg -> {
            tentativasSaidaFalhas++;
            if (tentativasSaidaFalhas >= MAX_TENTATIVAS_SAIDA) {
                tentativasSaidaFalhas = 0;
                bloqueioSaidaAte = System.currentTimeMillis() + BLOQUEIO_SAIDA_S * 1000L;
                atualizarBloqueio.run();
            } else {
                lblErro.setText(msg + " Tentativa " + tentativasSaidaFalhas + " de " + MAX_TENTATIVAS_SAIDA + ".");
                btnEntrar.setEnabled(true);
                txtSenha.requestFocusInWindow();
            }
        };

        btnEntrar.addActionListener(e -> {
            String usuario = txtUsuario.getText().trim();
            String senha = new String(txtSenha.getPassword());
            txtSenha.setText("");

            if (usuario.isEmpty() || senha.isEmpty()) {
                lblErro.setText("Preencha usuário e senha.");
                return;
            }
            if (System.currentTimeMillis() < bloqueioSaidaAte) {
                atualizarBloqueio.run();
                return;
            }

            // A conferência vai ao banco: roda em segundo plano, com o botão desligado enquanto isso
            verificando[0] = true;
            btnEntrar.setEnabled(false);
            lblErro.setText("Verificando...");

            emBackground(() -> Repositoriofuncionarios.autenticar(usuario, senha),
                    funcionario -> {
                        verificando[0] = false;
                        if (funcionario == null) {
                            falha.accept("Usuário ou senha incorretos.");
                        } else if (!funcionario.isAtivo()) {
                            falha.accept("Este funcionário está bloqueado. Procure o Manager Master.");
                        } else if (!funcionario.isPermissaoAdmin()) {
                            falha.accept("Só funcionários administrativos podem sair por aqui.");
                        } else {
                            tentativasSaidaFalhas = 0;
                            System.out.println("Autoatendimento: saída liberada por " + funcionario.getNome());
                            fechar.run();
                            aoSair.run();
                        }
                    },
                    ex -> {
                        verificando[0] = false;
                        btnEntrar.setEnabled(true);
                        lblErro.setText("Não foi possível verificar agora. Tente novamente.");
                        registrarFalha("verificar saída", ex);
                    });
        });

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        botoes.setOpaque(false);
        botoes.add(btnCancelar);
        botoes.add(btnEntrar);
        c.add(botoes, BorderLayout.SOUTH);

        dialogo.getRootPane().setDefaultButton(btnEntrar);
        dialogo.setContentPane(c);
        dialogo.pack();
        dialogo.setLocationRelativeTo(SwingUtilities.getWindowAncestor(this));
        atualizarBloqueio.run(); // se ainda estiver bloqueado da vez anterior, já mostra
        relogio.start();
        dialogo.setVisible(true); // modal: só volta quando a janela fechar
    }

    private JLabel criarRotuloCampo(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        l.setForeground(TEXTO_SUAVE);
        return l;
    }

    // ---------------------------------------------------------------
    // SEGUNDO PLANO E LOG
    // ---------------------------------------------------------------

    /** Roda a tarefa (banco) fora da interface e devolve o resultado na thread da interface. */
    private <T> void emBackground(Callable<T> tarefa, Consumer<T> aoConcluir, Consumer<Exception> aoFalhar) {
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return tarefa.call();
            }

            @Override
            protected void done() {
                try {
                    aoConcluir.accept(get());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ee) {
                    Throwable causa = ee.getCause();
                    aoFalhar.accept(causa instanceof Exception ? (Exception) causa : new Exception(causa));
                }
            }
        }.execute();
    }

    /** Detalhe técnico vai para o console (o cliente vê só a mensagem simples). */
    private void registrarFalha(String acao, Exception ex) {
        System.err.println("Autoatendimento - falha ao " + acao + ": " + ex.getMessage());
    }

    // ---------------------------------------------------------------
    // VISUAL
    // ---------------------------------------------------------------

    /** Painel com cantos arredondados (cartões da tela). */
    private JPanel painelArredondado(LayoutManager layout, Color cor) {
        JPanel p = new JPanel(layout) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(cor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22);
                g2.setColor(BORDA);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 22, 22);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        return p;
    }

    /** Botão grande, pensado para toque. primario = azul; senão, cinza. */
    private JButton criarBotao(String texto, boolean primario, int fonte, Dimension tamanho) {
        JButton b = new JButton(texto) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color cor;
                if (!isEnabled()) {
                    cor = DESABILITADO;
                } else if (getModel().isPressed()) {
                    cor = primario ? AZUL_PRESSIONADO : new Color(30, 55, 90);
                } else {
                    cor = primario ? AZUL : BOTAO_SECUNDARIO;
                }
                g2.setColor(cor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        estilizarBotao(b, fonte, tamanho);
        return b;
    }

    /** Opção de forma de pagamento: fica azul quando escolhida. */
    private JToggleButton criarOpcaoForma(String texto) {
        JToggleButton b = new JToggleButton(texto) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color cor;
                if (!isEnabled()) {
                    cor = DESABILITADO;
                } else if (isSelected()) {
                    cor = AZUL;
                } else {
                    cor = BOTAO_SECUNDARIO;
                }
                g2.setColor(cor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        estilizarBotao(b, 20, new Dimension(260, 96));
        return b;
    }

    private static void estilizarBotao(AbstractButton b, int fonte, Dimension tamanho) {
        b.setFont(new Font("Segoe UI", Font.BOLD, fonte));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setFocusable(false); // tela de toque: sem navegação por Tab
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setOpaque(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        if (tamanho != null) {
            b.setPreferredSize(tamanho);
        }
    }

    /**
     * Carrega um ícone de src/images, redimensionado. Se recolorir não for null, troca a cor
     * do desenho mantendo a transparência. Se o arquivo não existir, devolve null.
     */
    private static ImageIcon carregarIcone(String nomeArquivo, int tamanho, Color recolorir) {
        URL url = TelaAutoatendimento.class.getResource("/images/" + nomeArquivo);
        if (url == null) {
            return null;
        }
        BufferedImage img = new BufferedImage(tamanho, tamanho, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(new ImageIcon(url).getImage(), 0, 0, tamanho, tamanho, null);
        g2.dispose();

        if (recolorir != null) {
            int rgb = recolorir.getRGB() & 0xFFFFFF;
            for (int y = 0; y < tamanho; y++) {
                for (int x = 0; x < tamanho; x++) {
                    int alfa = (img.getRGB(x, y) >>> 24) & 0xFF;
                    if (alfa > 0) {
                        img.setRGB(x, y, (alfa << 24) | rgb);
                    }
                }
            }
        }
        return new ImageIcon(img);
    }
}