import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.SQLException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
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
 *   1. Cartão      -> cliente digita o número do cartão (teclado na tela ou teclado físico)
 *   2. Valor       -> escolhe R$ 10, 20, 30, 50 ou 100 (vê os créditos antes de pagar)
 *   3. Pagamento   -> vê o resumo (cartão, créditos, valor) e escolhe Pix, crédito ou débito
 *                     (dinheiro só no balcão; cada opção precisa estar ativa no cadastro)
 *   4. Confirmação -> mostra o novo saldo e volta sozinha ao início
 *
 * Regras de segurança e de uso:
 *   - Não há como sair pelo sistema: a barra lateral fica coberta e o ESC abre a
 *     mesma janela de login de administrador do botão de saída.
 *   - Para sair é preciso o usuário e a senha de um funcionário ADMINISTRADOR e ATIVO.
 *     Depois de 3 tentativas erradas, a saída fica bloqueada por 30 segundos.
 *   - Sem uso por 60 segundos, a tela volta ao início e limpa o que foi digitado
 *     (para o próximo cliente não ver o cartão do anterior).
 *   - A recarga é gravada SEM funcionário (campo vazio), conforme a documentação.
 *   - Mensagens para o cliente são simples; detalhes técnicos vão só para o console.
 *   - Botões grandes, sem foco de teclado (pensado para toque).
 *
 * Uso (pelo App): new TelaAutoatendimento(telaRecargas, aoSair); depois iniciar() ao abrir
 * e encerrar() ao fechar. Todo acesso ao banco passa pelos métodos da TelaRecargas.
 */
public class TelaAutoatendimento extends JPanel {

    // =========================================================================
    // REGRAS, CONSTANTES E CORES
    // Valores de recarga, tempos de inatividade/bloqueio, nomes das etapas e a
    // paleta de cores (a mesma das outras telas).
    // =========================================================================
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

    // =========================================================================
    // ESTADO DA TELA
    // Componentes que mudam durante o uso e os dados da recarga em andamento.
    // =========================================================================
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
    // As três opções do autoatendimento (dinheiro só no balcão). Cada uma é ligada ao
    // nome cadastrado em formas_pagamento; se não houver (ou estiver inativa), fica indisponível.
    private static final String[] TIPOS_FORMA = { "pix", "credito", "debito" };
    private static final String[] TITULOS_FORMA = { "Pix", "Cartão de crédito", "Cartão de débito" };
    private static final String[] DICAS_FORMA = { "Aprovação na hora", "Na maquininha ao lado", "Na maquininha ao lado" };

    private JLabel lblResumoCartao;
    private JLabel lblResumoCreditos;
    private JLabel lblResumoValor;
    private JLabel lblResumoSaldo;
    private JLabel lblErroPagamento;
    private JButton btnConfirmar;
    private ButtonGroup grupoFormas = new ButtonGroup();
    private final JToggleButton[] botoesForma = new JToggleButton[TIPOS_FORMA.length];
    /** Nome da forma no banco para cada opção (null = indisponível). */
    private final String[] nomeFormaNoBanco = new String[TIPOS_FORMA.length];
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
    /** Até quando a saída fica bloqueada depois de muitas tentativas erradas (ms desde 1970). */
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

    // =========================================================================
    // CONSTRUTOR E CICLO DE VIDA
    // Monta as 4 etapas num CardLayout. iniciar()/encerrar() são chamados pelo
    // App ao abrir e fechar o autoatendimento.
    // =========================================================================

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

    // =========================================================================
    // CABEÇALHO
    // Logo, título, indicação do passo atual e o botão "Sair" (só funcionários).
    // =========================================================================
    private JPanel criarCabecalho() {
        JPanel topo = new JPanel(new BorderLayout(20, 0));
        topo.setOpaque(false);

        JPanel marca = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 0));
        marca.setOpaque(false);
        JLabel logo = new JLabel(carregarIcone("Logo_arcade.png", 64, null));
        if (logo.getIcon() == null) { // sem imagem: mostra um "A" no lugar
            logo = rotulo("A", SwingConstants.CENTER, Font.BOLD, 30, Color.WHITE);
            logo.setPreferredSize(new Dimension(64, 64));
        }
        marca.add(logo);

        JPanel textos = new JPanel(new GridLayout(2, 1, 0, 2));
        textos.setOpaque(false);
        lblPasso = rotulo("", Font.PLAIN, 16, TEXTO_SUAVE);
        textos.add(rotulo("Autoatendimento de recargas", Font.BOLD, 28, Color.WHITE));
        textos.add(lblPasso);
        marca.add(textos);

        topo.add(marca, BorderLayout.CENTER);
        topo.add(criarBotaoSaida(), BorderLayout.EAST);
        return topo;
    }

    /** Botão de saída com o ícone do projeto (botao saida.png), clareado para o fundo escuro. */
    private JButton criarBotaoSaida() {
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

    // =========================================================================
    // ETAPA 1: CARTÃO
    // Visor com o número digitado + teclado numérico. Ao continuar, o cartão é
    // conferido no banco (existe? está ativo?).
    // =========================================================================
    private JPanel criarEtapaCartao() {
        JPanel etapa = new JPanel(new GridLayout(1, 2, 36, 0));
        etapa.setOpaque(false);

        // Esquerda: instruções + número digitado + botão Continuar
        JPanel esquerda = painelArredondado(new BorderLayout(0, 18), CARD);
        esquerda.setBorder(BorderFactory.createEmptyBorder(30, 34, 30, 34));

        JPanel textos = new JPanel(new GridLayout(2, 1, 0, 8));
        textos.setOpaque(false);
        textos.add(rotulo("Qual é o número do seu cartão?", Font.BOLD, 26, Color.WHITE));
        textos.add(rotulo("Digite os números que estão no seu cartão e toque em Continuar.",
                Font.PLAIN, 16, TEXTO_SUAVE));
        esquerda.add(textos, BorderLayout.NORTH);

        JPanel visor = painelArredondado(new BorderLayout(), CAMPO);
        lblCartaoDigitado = rotulo("—", SwingConstants.CENTER, Font.BOLD, 64, AZUL_CLARO);
        visor.add(lblCartaoDigitado, BorderLayout.CENTER);
        esquerda.add(visor, BorderLayout.CENTER);

        JPanel rodapeEsq = new JPanel(new BorderLayout(0, 12));
        rodapeEsq.setOpaque(false);
        lblErroCartao = rotulo(" ", SwingConstants.CENTER, Font.BOLD, 16, ERRO);
        btnContinuar = criarBotao("Continuar", true, 22, new Dimension(0, 84));
        btnContinuar.addActionListener(e -> continuarComCartao());
        rodapeEsq.add(lblErroCartao, BorderLayout.NORTH);
        rodapeEsq.add(btnContinuar, BorderLayout.CENTER);
        esquerda.add(rodapeEsq, BorderLayout.SOUTH);

        // Direita: teclado numérico (1-9, Limpar, 0, Apagar)
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

    /** Confere o cartão no banco (em segundo plano) antes de avançar para a escolha do valor. */
    private void continuarComCartao() {
        if (ocupado) {
            return;
        }
        if (digitos.length() == 0) {
            lblErroCartao.setText("Digite o número do seu cartão.");
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
                        lblErroCartao.setText("Cartão não encontrado. Confira o número ou procure a recepção.");
                        return;
                    }
                    if (!info.ativo) {
                        lblErroCartao.setText("Este cartão está bloqueado. Procure a recepção.");
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
                        lblErroCartao.setText(MSG_INDISPONIVEL);
                    }
                });
    }

    /** Só devolve o aspecto normal ao botão (quem controla "ocupado" é quem chamou). */
    private void liberarBotaoContinuar() {
        btnContinuar.setEnabled(true);
        btnContinuar.setText("Continuar");
    }

    // =========================================================================
    // ETAPA 2: VALOR
    // Saudação com o saldo atual e os botões de valor (R$ -> créditos).
    // =========================================================================
    private JPanel criarEtapaValor() {
        JPanel etapa = new JPanel(new BorderLayout(0, 26));
        etapa.setOpaque(false);

        JPanel cabecalho = new JPanel(new GridLayout(2, 1, 0, 6));
        cabecalho.setOpaque(false);
        lblOla = rotulo(" ", Font.BOLD, 34, Color.WHITE);
        lblSaldo = rotulo(" ", Font.PLAIN, 18, TEXTO_SUAVE);
        cabecalho.add(lblOla);
        cabecalho.add(lblSaldo);
        etapa.add(cabecalho, BorderLayout.NORTH);

        JPanel opcoesBox = painelArredondado(new BorderLayout(0, 20), CARD);
        opcoesBox.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));
        opcoesBox.add(rotulo("Quanto você quer recarregar?", Font.BOLD, 22, Color.WHITE), BorderLayout.NORTH);

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

    // =========================================================================
    // ETAPA 3: PAGAMENTO
    // Esquerda: resumo da recarga (cartão, créditos, valor, saldo depois).
    // Direita: Pix / Cartão de crédito / Cartão de débito e, embaixo, Confirmar.
    // =========================================================================
    private JPanel criarEtapaPagamento() {
        JPanel etapa = new JPanel(new GridLayout(1, 2, 36, 0));
        etapa.setOpaque(false);

        // --- Esquerda: resumo ---
        JPanel resumo = painelArredondado(new BorderLayout(0, 20), CARD);
        resumo.setBorder(BorderFactory.createEmptyBorder(30, 34, 30, 34));
        resumo.add(rotulo("Resumo da recarga", Font.BOLD, 26, Color.WHITE), BorderLayout.NORTH);

        // Blocos empilhados de cima para baixo (cartão / créditos / valor / saldo)
        JPanel linhas = new JPanel();
        linhas.setLayout(new javax.swing.BoxLayout(linhas, javax.swing.BoxLayout.Y_AXIS));
        linhas.setOpaque(false);
        lblResumoCartao = rotulo(" ", Font.PLAIN, 20, TEXTO_SUAVE);
        lblResumoCreditos = rotulo(" ", Font.BOLD, 96, AZUL_CLARO);
        lblResumoValor = rotulo(" ", Font.BOLD, 28, Color.WHITE);
        lblResumoSaldo = rotulo(" ", Font.PLAIN, 18, TEXTO_SUAVE);
        java.awt.Component[] ordem = {
                lblResumoCartao, javax.swing.Box.createVerticalStrut(34),
                rotulo("Créditos que serão adicionados", Font.PLAIN, 17, TEXTO_SUAVE), lblResumoCreditos,
                javax.swing.Box.createVerticalStrut(26),
                lblResumoValor, javax.swing.Box.createVerticalStrut(10), lblResumoSaldo };
        for (java.awt.Component comp : ordem) {
            if (comp instanceof JComponent) {
                ((JComponent) comp).setAlignmentX(LEFT_ALIGNMENT);
            }
            linhas.add(comp);
        }
        resumo.add(linhas, BorderLayout.CENTER);

        JButton voltar = criarBotao("Voltar", false, 18, new Dimension(220, 70));
        voltar.addActionListener(e -> mostrarEtapa(ETAPA_VALOR));
        JPanel rodapeEsq = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        rodapeEsq.setOpaque(false);
        rodapeEsq.add(voltar);
        resumo.add(rodapeEsq, BorderLayout.SOUTH);

        // --- Direita: as três formas + Confirmar ---
        JPanel pagamento = painelArredondado(new BorderLayout(0, 18), CARD);
        pagamento.setBorder(BorderFactory.createEmptyBorder(30, 30, 30, 30));

        JPanel titulos = new JPanel(new GridLayout(2, 1, 0, 6));
        titulos.setOpaque(false);
        titulos.add(rotulo("Como você vai pagar?", Font.BOLD, 26, Color.WHITE));
        titulos.add(rotulo("Toque em uma opção e depois em Confirmar.", Font.PLAIN, 16, TEXTO_SUAVE));
        pagamento.add(titulos, BorderLayout.NORTH);

        JPanel opcoes = new JPanel(new GridLayout(TIPOS_FORMA.length, 1, 0, 16));
        opcoes.setOpaque(false);
        for (int i = 0; i < TIPOS_FORMA.length; i++) {
            final int indice = i;
            botoesForma[i] = criarOpcaoForma(TIPOS_FORMA[i], TITULOS_FORMA[i], DICAS_FORMA[i]);
            botoesForma[i].addActionListener(e -> selecionarForma(indice));
            grupoFormas.add(botoesForma[i]);
            opcoes.add(botoesForma[i]);
        }
        pagamento.add(opcoes, BorderLayout.CENTER);

        JPanel rodapeDir = new JPanel(new BorderLayout(0, 12));
        rodapeDir.setOpaque(false);
        lblErroPagamento = rotulo(" ", SwingConstants.CENTER, Font.BOLD, 16, ERRO);
        btnConfirmar = criarBotao("Confirmar", true, 24, new Dimension(0, 84));
        btnConfirmar.setEnabled(false);
        btnConfirmar.addActionListener(e -> confirmarRecarga());
        rodapeDir.add(lblErroPagamento, BorderLayout.NORTH);
        rodapeDir.add(btnConfirmar, BorderLayout.CENTER);
        pagamento.add(rodapeDir, BorderLayout.SOUTH);

        etapa.add(resumo);
        etapa.add(pagamento);
        return etapa;
    }

    private void prepararPagamento() {
        formaEscolhida = null;
        grupoFormas.clearSelection();
        btnConfirmar.setEnabled(false);
        lblErroPagamento.setText(" ");
        int creditos = valorReais * TelaRecargas.CREDITOS_POR_REAL;
        lblResumoCartao.setText("Cartão " + String.format("%03d", cartao.id) + "  ·  " + cartao.nome);
        lblResumoCreditos.setText("+" + creditos);
        lblResumoValor.setText("Valor a pagar: " + MOEDA.format(valorReais));
        lblResumoSaldo.setText("Saldo depois da recarga: " + (cartao.creditos + creditos) + " créditos");
        atualizarDisponibilidadeFormas();
    }

    /** Carrega do banco (em segundo plano) quais das três formas estão ativas. */
    private void carregarFormas() {
        emBackground(() -> recargas.formasAtivasAutoatendimento(),
                lista -> {
                    java.util.Arrays.fill(nomeFormaNoBanco, null);
                    for (String nome : lista) {
                        String tipo = TelaRecargas.tipoFormaAutoatendimento(nome);
                        for (int i = 0; i < TIPOS_FORMA.length; i++) {
                            if (TIPOS_FORMA[i].equals(tipo) && nomeFormaNoBanco[i] == null) {
                                nomeFormaNoBanco[i] = nome;
                            }
                        }
                    }
                    formasCarregadas = true;
                    atualizarDisponibilidadeFormas();
                },
                ex -> {
                    java.util.Arrays.fill(nomeFormaNoBanco, null);
                    formasCarregadas = true;
                    atualizarDisponibilidadeFormas();
                    registrarFalha("carregar formas de pagamento", ex);
                });
    }

    /** Liga só as opções que existem e estão ativas no cadastro; as outras ficam "Indisponível". */
    private void atualizarDisponibilidadeFormas() {
        boolean alguma = false;
        for (int i = 0; i < TIPOS_FORMA.length; i++) {
            boolean disponivel = nomeFormaNoBanco[i] != null;
            alguma |= disponivel;
            botoesForma[i].setEnabled(disponivel && !ocupado);
            botoesForma[i].setText(textoOpcao(TITULOS_FORMA[i],
                    !formasCarregadas ? "Carregando..." : disponivel ? DICAS_FORMA[i] : "Indisponível no momento"));
        }
        if (formasCarregadas && !alguma) {
            lblErroPagamento.setText("Pagamento indisponível no momento. Procure a recepção.");
        }
        if (formaEscolhida != null && !botoesForma[indiceDaForma(formaEscolhida)].isEnabled()) {
            formaEscolhida = null;
            grupoFormas.clearSelection();
            btnConfirmar.setEnabled(false);
        }
    }

    private int indiceDaForma(String nomeNoBanco) {
        for (int i = 0; i < nomeFormaNoBanco.length; i++) {
            if (nomeNoBanco.equals(nomeFormaNoBanco[i])) {
                return i;
            }
        }
        return 0;
    }

    private void selecionarForma(int indice) {
        formaEscolhida = nomeFormaNoBanco[indice]; // gravado na recarga com o nome do cadastro
        lblErroPagamento.setText(" ");
        btnConfirmar.setEnabled(formaEscolhida != null);
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

    /** Desliga (ou religa) o botão Confirmar e as opções de pagamento durante a gravação. */
    private void bloquearPagamento(boolean bloquear) {
        btnConfirmar.setEnabled(!bloquear && formaEscolhida != null);
        btnConfirmar.setText(bloquear ? "Registrando..." : "Confirmar");
        for (int i = 0; i < botoesForma.length; i++) {
            botoesForma[i].setEnabled(!bloquear && nomeFormaNoBanco[i] != null);
        }
    }

    // =========================================================================
    // ETAPA 4: CONFIRMAÇÃO
    // Mostra os créditos adicionados e o novo saldo; volta ao início sozinha
    // depois de alguns segundos (contagem regressiva).
    // =========================================================================
    private JPanel criarEtapaConfirmacao() {
        JPanel etapa = painelArredondado(new GridLayout(0, 1, 0, 18), CARD);
        etapa.setBorder(BorderFactory.createEmptyBorder(40, 40, 40, 40));

        lblConfirmacaoDetalhe = rotulo(" ", SwingConstants.CENTER, Font.PLAIN, 22, Color.WHITE);
        lblContagem = rotulo(" ", SwingConstants.CENTER, Font.PLAIN, 16, TEXTO_SUAVE);

        JButton novaRecarga = criarBotao("Nova recarga", true, 22, new Dimension(300, 80));
        novaRecarga.addActionListener(e -> voltarAoInicio());
        JPanel botaoBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        botaoBox.setOpaque(false);
        botaoBox.add(novaRecarga);

        etapa.add(rotulo("Recarga bem-sucedida!", SwingConstants.CENTER, Font.BOLD, 44, SUCESSO));
        etapa.add(lblConfirmacaoDetalhe);
        etapa.add(lblContagem);
        etapa.add(botaoBox);
        return etapa;
    }

    private void mostrarConfirmacao(String nomeCliente, long idCartao, long creditos, long saldo) {
        String saldoTexto = saldo >= 0 ? saldo + " créditos" : "consulte a recepção";
        lblConfirmacaoDetalhe.setText("<html><div style='text-align:center'>"
                + "Recarga finalizada" + (formaEscolhida != null ? " no " + formaEscolhida : "") + ".<br>"
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

    // =========================================================================
    // NAVEGAÇÃO, INATIVIDADE E TECLADO FÍSICO
    // Troca de etapa, volta ao início (limpando tudo), relógio de 60s sem uso e
    // atalhos do teclado físico na etapa do cartão.
    // =========================================================================

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
        for (char c = '0'; c <= '9'; c++) {
            final String d = String.valueOf(c);
            atalho(KeyStroke.getKeyStroke(c), "digito" + c, () -> digitar(d));
        }
        atalho(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "apagar", this::apagarUltimo);
        atalho(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "continuar", this::continuarComCartao);
    }

    /** Liga uma tecla a uma ação, que só roda quando o teclado pode ser usado. */
    private void atalho(KeyStroke tecla, String nome, Runnable acao) {
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(tecla, nome);
        getActionMap().put(nome, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (podeUsarTeclado()) {
                    acao.run();
                }
            }
        });
    }

    private boolean podeUsarTeclado() {
        return ETAPA_CARTAO.equals(etapaAtual) && !saidaAberta;
    }

    // =========================================================================
    // SAÍDA DO AUTOATENDIMENTO (somente administrador ativo)
    // Janela modal que pede usuário e senha. 3 tentativas erradas bloqueiam a
    // saída por 30 segundos.
    // =========================================================================
    /** Também chamado pelo ESC (ver SairComEsc / App.abrirAutoatendimento). */
    void abrirSaidaFuncionario() {
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

        // --- Formulário (título, aviso, usuário, senha, mensagem de erro) ---
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
        JLabel lblErro = rotulo(" ", Font.BOLD, 14, ERRO);

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 6));
        form.setOpaque(false);
        form.add(rotulo("Área de funcionários", Font.BOLD, 20, Color.WHITE));
        form.add(rotulo("Digite o usuário e a senha de um administrador ativo.", Font.PLAIN, 14, TEXTO_SUAVE));
        form.add(rotulo("Usuário", Font.PLAIN, 14, TEXTO_SUAVE));
        form.add(txtUsuario);
        form.add(rotulo("Senha", Font.PLAIN, 14, TEXTO_SUAVE));
        form.add(txtSenha);
        form.add(lblErro);
        c.add(form, BorderLayout.CENTER);

        JButton btnCancelar = criarBotao("Cancelar", false, 16, new Dimension(130, 50));
        JButton btnEntrar = criarBotao("Sair", true, 16, new Dimension(130, 50));

        // true enquanto a conferência no banco está em andamento
        final boolean[] verificando = { false };

        // --- Bloqueio por tentativas: mantém o botão desligado e mostra a contagem ---
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

        // --- Fechar a janela (Cancelar ou X) ---
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

        // --- Botão "Sair": confere usuário/senha no banco (em segundo plano) ---
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

    // =========================================================================
    // SEGUNDO PLANO E LOG
    // Consultas ao banco rodam fora da thread da interface para a tela não travar.
    // =========================================================================

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

    // =========================================================================
    // COMPONENTES VISUAIS
    // Fábricas de rótulos, painéis arredondados, botões grandes para toque e
    // carregamento de ícones.
    // =========================================================================

    /** Rótulo com fonte Segoe UI, alinhado à esquerda. */
    private static JLabel rotulo(String texto, int estilo, int tamanho, Color cor) {
        return rotulo(texto, SwingConstants.LEADING, estilo, tamanho, cor);
    }

    /** Rótulo com fonte Segoe UI e alinhamento escolhido. */
    private static JLabel rotulo(String texto, int alinhamento, int estilo, int tamanho, Color cor) {
        JLabel l = new JLabel(texto, alinhamento);
        l.setFont(new Font("Segoe UI", estilo, tamanho));
        l.setForeground(cor);
        return l;
    }

    /** Painel com cantos arredondados (os "cartões" da tela). */
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
                Color cor;
                if (!isEnabled()) {
                    cor = DESABILITADO;
                } else if (getModel().isPressed()) {
                    cor = primario ? AZUL_PRESSIONADO : new Color(30, 55, 90);
                } else {
                    cor = primario ? AZUL : BOTAO_SECUNDARIO;
                }
                pintarFundo(g, this, cor);
                super.paintComponent(g);
            }
        };
        estilizarBotao(b, fonte, tamanho);
        return b;
    }

    /**
     * Opção de forma de pagamento (Pix / crédito / débito): ícone à esquerda, nome e dica.
     * Fica azul com um "✓" quando escolhida.
     */
    private JToggleButton criarOpcaoForma(String tipo, String titulo, String dica) {
        JToggleButton b = new JToggleButton(textoOpcao(titulo, dica), new IconeForma(tipo)) {
            @Override
            protected void paintComponent(Graphics g) {
                Color cor = !isEnabled() ? DESABILITADO : isSelected() ? AZUL : BOTAO_SECUNDARIO;
                pintarFundo(g, this, cor);
                super.paintComponent(g);
                if (isSelected() && isEnabled()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int d = 34, x = getWidth() - d - 24, y = (getHeight() - d) / 2;
                    g2.setColor(Color.WHITE);
                    g2.fillOval(x, y, d, d);
                    g2.setColor(AZUL);
                    g2.setStroke(new java.awt.BasicStroke(4f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                    g2.drawPolyline(new int[] { x + 9, x + 15, x + 26 }, new int[] { y + 18, y + 24, y + 11 }, 3);
                    g2.dispose();
                }
            }
        };
        estilizarBotao(b, 20, new Dimension(0, 104));
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setIconTextGap(22);
        b.setBorder(BorderFactory.createEmptyBorder(0, 26, 0, 70));
        return b;
    }

    private static String textoOpcao(String titulo, String dica) {
        return "<html><span style='font-size:21px'><b>" + titulo + "</b></span><br>"
                + "<span style='font-size:13px; font-weight:normal'>" + dica + "</span></html>";
    }

    /** Ícones em line art (mesmo estilo dos botões do sistema) para as formas de pagamento. */
    private static final class IconeForma implements javax.swing.Icon {
        private static final int TAM = 48;
        private final String tipo;

        IconeForma(String tipo) {
            this.tipo = tipo;
        }

        @Override public int getIconWidth() { return TAM; }
        @Override public int getIconHeight() { return TAM; }

        @Override
        public void paintIcon(java.awt.Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(c.isEnabled() ? Color.WHITE : TEXTO_SUAVE);
            g2.setStroke(new java.awt.BasicStroke(3f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            g2.translate(x, y);
            if ("pix".equals(tipo)) {
                // losango com outro losango dentro
                g2.drawPolygon(new int[] { 24, 44, 24, 4 }, new int[] { 4, 24, 44, 24 }, 4);
                g2.drawPolygon(new int[] { 24, 33, 24, 15 }, new int[] { 15, 24, 33, 24 }, 4);
            } else {
                // cartão: retângulo arredondado
                g2.drawRoundRect(3, 10, 42, 28, 7, 7);
                if ("credito".equals(tipo)) {
                    g2.drawLine(3, 18, 45, 18);           // tarja
                    g2.drawLine(10, 30, 22, 30);          // número
                } else {
                    g2.drawRoundRect(9, 17, 10, 8, 3, 3); // chip
                    g2.drawLine(9, 31, 27, 31);           // número
                }
            }
            g2.dispose();
        }
    }

    /** Pinta o fundo arredondado de um botão na cor indicada. */
    private static void pintarFundo(Graphics g, AbstractButton b, Color cor) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(cor);
        g2.fillRoundRect(0, 0, b.getWidth(), b.getHeight(), 18, 18);
        g2.dispose();
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
