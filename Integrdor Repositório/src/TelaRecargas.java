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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Component;
import java.awt.geom.Point2D;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

/**
 * Tela de Recargas, com duas abas:
 *
 *  - Recargas: histórico de recargas + botão "+ Nova Recarga".
 *      Regra: 1 real = 5 créditos. Ao salvar, a recarga é gravada na tabela
 *      `recargas` E os créditos são somados ao cartão (`cartões`.Creditos),
 *      tudo na mesma transação (ou grava as duas coisas, ou nenhuma).
 *
 *  Carregamento: as consultas rodam em SEGUNDO PLANO (SwingWorker) e, enquanto
 *  isso, a tabela mostra um "ghost loading" (linhas fantasma com brilho).
 *  A tela abre na hora e a interface nunca trava esperando o banco.
 *
 *  - Formas de Pagamento (só o Manager Master enxerga): cadastro das formas
 *      aceitas nas recargas. Ficam salvas na tabela `formas_pagamento`,
 *      que é criada automaticamente na primeira vez que a tela é aberta.
 *      Formas não são apagadas (para não perder o histórico); são desativadas.
 */
public class TelaRecargas extends JPanel {

    // ---------------------------------------------------------------
    // REGRAS E CONSTANTES
    // ---------------------------------------------------------------
    static final int CREDITOS_POR_REAL = 5; // visível no pacote: o autoatendimento usa a mesma regra

    private static final Color FUNDO = new Color(2, 12, 27);
    private static final Color CARD = new Color(10, 22, 40);
    private static final Color BORDA = new Color(23, 42, 69);
    private static final Color AZUL = new Color(14, 116, 233);
    private static final Color TEXTO_SUAVE = new Color(148, 163, 184);
    private static final Color CAMPO = new Color(13, 27, 46);

    private static final String CARD_RECARGAS = "recargas";
    private static final String CARD_FORMAS = "formas";

    private static final NumberFormat MOEDA = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Criada sozinha na primeira vez (não precisa rodar SQL na mão)
    private static final String SQL_CRIAR_FORMAS = "CREATE TABLE IF NOT EXISTS formas_pagamento ("
            + "ID_Forma INT AUTO_INCREMENT PRIMARY KEY, "
            + "Nome VARCHAR(50) NOT NULL UNIQUE, "
            + "Ativo BOOLEAN NOT NULL DEFAULT TRUE)";
    private static boolean tabelaFormasPronta = false;

    // O ghost fica visível pelo menos este tempo, para não "piscar" quando o banco responde rápido
    private static final int GHOST_MIN_MS = 350;

    // ---------------------------------------------------------------
    // COMPONENTES
    // ---------------------------------------------------------------
    private final CardLayout cards = new CardLayout();
    private JPanel painelCards;
    private JButton abaRecargas;
    private JButton abaFormas;
    private boolean abaFormasAtiva = false;

    /** Ação do botão "Autoatendimento" (definida pelo App, que mostra a tela presa por cima de tudo). */
    private Runnable acaoAutoatendimento;

    private JTable tabelaRecargas;
    private DefaultTableModel modeloRecargas;
    private TableRowSorter<DefaultTableModel> sorterRecargas;

    private JTable tabelaFormas;
    private DefaultTableModel modeloFormas;
    private TableRowSorter<DefaultTableModel> sorterFormas;

    private JLabel lblTotal;

    // ghost loading
    private JPanel cardTabelaRecargas;
    private JPanel cardTabelaFormas;
    private GhostPanel ghostRecargas;
    private GhostPanel ghostFormas;
    private boolean carregandoRecargas = false;
    private boolean carregandoFormas = false;
    private int cargaRecargasId = 0; // descarta respostas antigas se o usuário recarregar várias vezes
    private int cargaFormasId = 0;

    /** Formas ativas da última carga (null = ainda não carregou). Deixa o modal abrir instantâneo. */
    private List<String> formasAtivasCache = null;

    /** Erro de regra de negócio (mensagem já pronta para mostrar ao usuário). */
    public static class RecargaException extends Exception {
        RecargaException(String msg) {
            super(msg);
        }
    }

    /** Dados de leitura de um cartão (usado pelo autoatendimento para mostrar saldo e nome). */
    public static class CartaoInfo {
        public final long id;
        public final String nome;
        public final long creditos;
        public final boolean ativo;

        CartaoInfo(long id, String nome, long creditos, boolean ativo) {
            this.id = id;
            this.nome = nome;
            this.creditos = creditos;
            this.ativo = ativo;
        }
    }

    public TelaRecargas() {
        setBackground(FUNDO);
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        // 1. TOPO: título + abas
        JPanel topo = new JPanel(new BorderLayout(0, 10));
        topo.setOpaque(false);

        JLabel lblTitulo = new JLabel("Recargas");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitulo.setForeground(Color.WHITE);

        JPanel abas = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 0));
        abas.setOpaque(false);
        abaRecargas = criarAba("Recargas");
        abaFormas = criarAba("Formas de Pagamento");
        abaFormas.setVisible(false); // só aparece para o Manager Master
        abaRecargas.addActionListener(e -> mostrarAba(false));
        abaFormas.addActionListener(e -> mostrarAba(true));
        abas.add(abaRecargas);
        abas.add(abaFormas);

        topo.add(lblTitulo, BorderLayout.NORTH);
        topo.add(abas, BorderLayout.CENTER);
        add(topo, BorderLayout.NORTH);

        // 2. CENTRO: conteúdo de cada aba
        painelCards = new JPanel(cards);
        painelCards.setOpaque(false);
        painelCards.add(criarPainelRecargas(), CARD_RECARGAS);
        painelCards.add(criarPainelFormas(), CARD_FORMAS);
        add(painelCards, BorderLayout.CENTER);

        // 3. RODAPÉ: total + regra de conversão
        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setOpaque(false);
        rodape.setPreferredSize(new Dimension(0, 35));

        lblTotal = new JLabel();
        lblTotal.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTotal.setForeground(TEXTO_SUAVE);

        JLabel lblRegra = new JLabel("1 real = " + CREDITOS_POR_REAL + " créditos");
        lblRegra.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblRegra.setForeground(TEXTO_SUAVE);

        rodape.add(lblTotal, BorderLayout.WEST);
        rodape.add(lblRegra, BorderLayout.EAST);
        add(rodape, BorderLayout.SOUTH);

        mostrarAba(false);
    }

    // ---------------------------------------------------------------
    // API PÚBLICA (chamada pelo App ao clicar em "Recargas")
    // ---------------------------------------------------------------

    /** Atualiza as permissões e recarrega os dados do banco (em segundo plano, com ghost loading). */
    public void carregarDadosRecargas() {
        atualizarPermissoes();
        carregar(true, Sessao.isManagerMaster());
    }

    /** Define o que o botão "Autoatendimento" faz (o App abre a tela do cliente). */
    public void setAcaoAutoatendimento(Runnable acao) {
        this.acaoAutoatendimento = acao;
    }

    // ---------------------------------------------------------------
    // API DO AUTOATENDIMENTO
    // Chamadas em SEGUNDO PLANO pela tela do cliente (bloqueiam até o banco responder).
    // ---------------------------------------------------------------

    /** Lê o cartão. Devolve null se não existir. Não expõe dados do cliente além do nome de exibição. */
    public CartaoInfo buscarCartao(long idCartao) throws SQLException {
        String sql = "SELECT `Usuário` AS usuario, Creditos AS creditos, Ativo AS ativo "
                + "FROM `cartões` WHERE `ID_Cartão` = ?";
        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, idCartao);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String nome = rs.getString("usuario");
                if (nome == null || nome.trim().isEmpty()) {
                    nome = "cliente";
                }
                return new CartaoInfo(idCartao, nome.trim(), rs.getLong("creditos"), rs.getBoolean("ativo"));
            }
        }
    }

    /** Formas de pagamento ativas, buscadas agora (o cliente sempre vê a lista atual). */
    public List<String> formasAtivasAutoatendimento() throws SQLException {
        try (Connection con = conectar()) {
            garantirTabelaFormas(con);
            return consultarFormasAtivas(con);
        }
    }

    /**
     * Registra uma recarga feita pelo cliente no autoatendimento.
     * Igual à recarga da recepção, mas SEM funcionário: o campo ID_Funcionário fica vazio (NULL).
     * Retorna quantos créditos foram adicionados.
     */
    public long registrarRecargaAutoatendimento(long idCartao, BigDecimal valor, String forma)
            throws SQLException, RecargaException {
        return registrarRecarga(idCartao, valor, forma, null);
    }

    // ---------------------------------------------------------------
    // ABAS
    // ---------------------------------------------------------------
    private JButton criarAba(String texto) {
        JButton b = new JButton(texto);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void estilizarAba(JButton b, boolean ativa) {
        b.setForeground(ativa ? Color.WHITE : TEXTO_SUAVE);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, ativa ? AZUL : new Color(0, 0, 0, 0)),
                BorderFactory.createEmptyBorder(6, 4, 6, 4)));
    }

    private void mostrarAba(boolean formas) {
        abaFormasAtiva = formas;
        cards.show(painelCards, formas ? CARD_FORMAS : CARD_RECARGAS);
        estilizarAba(abaRecargas, !formas);
        estilizarAba(abaFormas, formas);
        atualizarTotal();
    }

    private void atualizarPermissoes() {
        boolean master = Sessao.isManagerMaster();
        abaFormas.setVisible(master);
        if (!master && abaFormasAtiva) {
            mostrarAba(false);
        }
    }

    private void atualizarTotal() {
        if (abaFormasAtiva) {
            if (carregandoFormas) {
                lblTotal.setText("Carregando...");
                return;
            }
            int n = tabelaFormas.getRowCount();
            lblTotal.setText("Total: " + n + (n == 1 ? " forma de pagamento" : " formas de pagamento"));
        } else {
            if (carregandoRecargas) {
                lblTotal.setText("Carregando...");
                return;
            }
            int n = tabelaRecargas.getRowCount();
            lblTotal.setText("Total: " + n + (n == 1 ? " recarga" : " recargas"));
        }
    }

    // ---------------------------------------------------------------
    // ABA 1: RECARGAS
    // ---------------------------------------------------------------
    private JPanel criarPainelRecargas() {
        JPanel painel = new JPanel(new BorderLayout(0, 15));
        painel.setOpaque(false);

        JPanel acoes = new JPanel(new BorderLayout(15, 0));
        acoes.setOpaque(false);
        acoes.add(criarBusca("Buscar por cartão, funcionário ou forma de pagamento...",
                texto -> filtrar(sorterRecargas, texto)), BorderLayout.WEST);

        JButton btnAuto = criarBotao("Autoatendimento", false, 190);
        btnAuto.addActionListener(e -> {
            if (acaoAutoatendimento != null) {
                acaoAutoatendimento.run();
            }
        });

        JButton btnNova = criarBotao("+ Nova Recarga", true, 175);
        btnNova.addActionListener(e -> abrirModalNovaRecarga());
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        botoes.setOpaque(false);
        botoes.add(btnAuto);
        botoes.add(btnNova);
        acoes.add(botoes, BorderLayout.EAST);

        String[] colunas = { "ID", "Cartão", "Funcionário", "Valor", "Créditos", "Forma de Pagamento", "Data e Hora" };
        modeloRecargas = new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tabelaRecargas = criarTabela(modeloRecargas);
        sorterRecargas = new TableRowSorter<>(modeloRecargas);
        tabelaRecargas.setRowSorter(sorterRecargas);

        painel.add(acoes, BorderLayout.NORTH);
        ghostRecargas = new GhostPanel(7);
        cardTabelaRecargas = criarCardTabela(tabelaRecargas, ghostRecargas);
        painel.add(cardTabelaRecargas, BorderLayout.CENTER);
        return painel;
    }

    // ---------------------------------------------------------------
    // CARREGAMENTO EM SEGUNDO PLANO (com ghost loading)
    // ---------------------------------------------------------------

    /** Resultado de uma carga: linhas já prontas para as tabelas + o erro de cada parte (se houver). */
    private static class DadosCarga {
        List<Object[]> recargas;
        List<Object[]> formas;
        List<String> formasAtivas; // null = não conseguiu carregar
        String erroRecargas;
        String erroFormas;
    }

    /**
     * Mostra o ghost nas tabelas pedidas, busca os dados fora da thread da interface
     * e, quando chegam, troca o ghost pela tabela.
     */
    private void carregar(boolean recargas, boolean formas) {
        final long inicio = System.currentTimeMillis();
        final int idRecargas = recargas ? ++cargaRecargasId : cargaRecargasId;
        final int idFormas = formas ? ++cargaFormasId : cargaFormasId;

        if (recargas) {
            carregandoRecargas = true;
            mostrarGhost(cardTabelaRecargas, ghostRecargas, true);
        }
        if (formas) {
            carregandoFormas = true;
            mostrarGhost(cardTabelaFormas, ghostFormas, true);
        }
        atualizarTotal();

        async(() -> buscarDados(recargas, formas),
                dados -> {
                    Runnable aplicar = () -> aplicarDados(dados,
                            recargas && idRecargas == cargaRecargasId,
                            formas && idFormas == cargaFormasId);
                    long resto = GHOST_MIN_MS - (System.currentTimeMillis() - inicio);
                    if (resto > 0) {
                        Timer t = new Timer((int) resto, ev -> aplicar.run());
                        t.setRepeats(false);
                        t.start();
                    } else {
                        aplicar.run();
                    }
                },
                ex -> {
                    // não conseguiu nem conectar: tira o ghost e avisa
                    if (recargas && idRecargas == cargaRecargasId) {
                        carregandoRecargas = false;
                        mostrarGhost(cardTabelaRecargas, ghostRecargas, false);
                    }
                    if (formas && idFormas == cargaFormasId) {
                        carregandoFormas = false;
                        mostrarGhost(cardTabelaFormas, ghostFormas, false);
                    }
                    atualizarTotal();
                    erro(this, "Não foi possível conectar ao banco de dados:\n" + ex.getMessage());
                });
    }

    private void aplicarDados(DadosCarga d, boolean aplicarRecargas, boolean aplicarFormas) {
        if (d.formasAtivas != null) {
            formasAtivasCache = d.formasAtivas;
        }
        if (aplicarRecargas) {
            preencher(modeloRecargas, d.recargas);
            carregandoRecargas = false;
            mostrarGhost(cardTabelaRecargas, ghostRecargas, false);
            if (d.erroRecargas != null) {
                erro(this, "Não foi possível carregar as recargas do banco de dados:\n" + d.erroRecargas);
            }
        }
        if (aplicarFormas) {
            preencher(modeloFormas, d.formas);
            carregandoFormas = false;
            mostrarGhost(cardTabelaFormas, ghostFormas, false);
            if (d.erroFormas != null) {
                erro(this, "Não foi possível carregar as formas de pagamento:\n" + d.erroFormas);
            }
        }
        atualizarTotal();
    }

    private void preencher(DefaultTableModel modelo, List<Object[]> linhas) {
        modelo.setRowCount(0);
        if (linhas != null) {
            for (Object[] l : linhas) {
                modelo.addRow(l);
            }
        }
    }

    private void mostrarGhost(JPanel card, GhostPanel ghost, boolean ligar) {
        ((CardLayout) card.getLayout()).show(card, ligar ? "ghost" : "tabela");
        if (ligar) {
            ghost.iniciar();
        } else {
            ghost.parar();
        }
    }

    /** Roda numa thread de fundo, com UMA conexão para tudo (cada parte trata o seu próprio erro). */
    private DadosCarga buscarDados(boolean recargas, boolean formas) throws SQLException {
        DadosCarga d = new DadosCarga();
        try (Connection con = conectar()) {
            try {
                garantirTabelaFormas(con);
            } catch (SQLException ex) {
                d.erroFormas = ex.getMessage();
            }
            if (recargas) {
                try {
                    d.recargas = consultarRecargas(con);
                } catch (SQLException ex) {
                    d.erroRecargas = ex.getMessage();
                }
            }
            if (d.erroFormas == null) {
                try {
                    if (formas) {
                        d.formas = consultarFormas(con);
                    }
                    d.formasAtivas = consultarFormasAtivas(con);
                } catch (SQLException ex) {
                    d.erroFormas = ex.getMessage();
                }
            }
        }
        return d;
    }

    /** SELECT de todas as recargas, já convertido em linhas da tabela. */
    private List<Object[]> consultarRecargas(Connection con) throws SQLException {
        String sql = "SELECT ID_Recarga AS id_recarga, `ID_Cartão` AS id_cartao, "
                + "`ID_Funcionário` AS id_func, Valor AS valor, "
                + "Forma_Pagamento AS forma, Data_Hora AS data_hora "
                + "FROM recargas ORDER BY Data_Hora DESC";

        List<Object[]> linhas = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                BigDecimal valor = rs.getBigDecimal("valor");
                if (valor == null) {
                    valor = BigDecimal.ZERO;
                }
                long creditos = valor.multiply(BigDecimal.valueOf(CREDITOS_POR_REAL))
                        .setScale(0, RoundingMode.HALF_UP).longValue();
                Timestamp ts = rs.getTimestamp("data_hora");

                linhas.add(new Object[] {
                        String.format("%03d", rs.getLong("id_recarga")),
                        String.format("%03d", rs.getLong("id_cartao")),
                        rs.getString("id_func"),
                        MOEDA.format(valor),
                        creditos,
                        rs.getString("forma"),
                        ts != null ? ts.toLocalDateTime().format(DATA_HORA) : ""
                });
            }
        }
        return linhas;
    }

    private void abrirModalNovaRecarga() {
        if (Sessao.getLogado() == null) {
            erro(this, "Nenhum funcionário logado. Faça login novamente.");
            return;
        }

        List<String> formas = obterFormasAtivas();
        if (formas.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Não há nenhuma forma de pagamento ativa.\n"
                            + "Peça ao Manager Master para cadastrar uma na aba \"Formas de Pagamento\".",
                    "Sem formas de pagamento", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog modal = criarModal("Nova Recarga");
        JPanel c = criarPainelModal();

        JTextField txtCartao = criarCampoTexto();
        JTextField txtValor = criarCampoTexto();
        JComboBox<String> cmbForma = new JComboBox<>(formas.toArray(new String[0]));
        cmbForma.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        cmbForma.setPreferredSize(new Dimension(320, 36));

        JLabel lblCreditos = new JLabel("= 0 créditos");
        lblCreditos.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblCreditos.setForeground(new Color(125, 211, 252));

        // Mostra, enquanto digita, quantos créditos o valor vai gerar
        Runnable atualizarPreview = () -> {
            BigDecimal v = lerValor(txtValor.getText());
            BigDecimal cr = v == null ? null : v.multiply(BigDecimal.valueOf(CREDITOS_POR_REAL));
            if (cr == null) {
                lblCreditos.setText("= 0 créditos");
            } else if (cr.stripTrailingZeros().scale() > 0) {
                lblCreditos.setText("Use múltiplos de R$ 0,20 (para fechar créditos inteiros)");
            } else {
                lblCreditos.setText("= " + cr.longValue() + " créditos");
            }
        };
        txtValor.getDocument().addDocumentListener(new AoMudar(atualizarPreview));

        Funcionario logado = Sessao.getLogado();
        JLabel lblFunc = new JLabel("Funcionário: " + logado.getNome() + " (" + logado.getId() + ")");
        lblFunc.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblFunc.setForeground(TEXTO_SUAVE);

        int y = 0;
        linha(c, criarTituloModal("Registrar Nova Recarga"), y++, 0);
        linha(c, criarRotulo("ID do Cartão"), y++, 16);
        linha(c, txtCartao, y++, 4);
        linha(c, criarRotulo("Valor (R$)"), y++, 12);
        linha(c, txtValor, y++, 4);
        linha(c, lblCreditos, y++, 6);
        linha(c, criarRotulo("Forma de Pagamento"), y++, 12);
        linha(c, cmbForma, y++, 4);
        linha(c, lblFunc, y++, 14);

        JButton btnCancelar = criarBotao("Cancelar", false, 110);
        JButton btnSalvar = criarBotao("Salvar Recarga", true, 150);
        btnCancelar.addActionListener(e -> modal.dispose());
        btnSalvar.addActionListener(e -> {
            long idCartao;
            try {
                idCartao = Long.parseLong(txtCartao.getText().trim());
            } catch (NumberFormatException ex) {
                erro(modal, "Informe o ID do cartão (somente números).");
                return;
            }
            BigDecimal valor = lerValor(txtValor.getText());
            if (valor == null) {
                erro(modal, "Informe um valor válido, maior que zero (ex.: 10,00).");
                return;
            }
            String forma = (String) cmbForma.getSelectedItem();
            long idCartaoFinal = idCartao;

            // salva em segundo plano: a janela não congela enquanto o banco responde
            btnSalvar.setEnabled(false);
            btnCancelar.setEnabled(false);
            btnSalvar.setText("Salvando...");

            async(() -> registrarRecarga(idCartaoFinal, valor, forma, logado.getId()),
                    creditos -> {
                        JOptionPane.showMessageDialog(modal,
                                "Recarga registrada!\n" + MOEDA.format(valor) + " = " + creditos
                                        + " créditos adicionados ao cartão " + String.format("%03d", idCartaoFinal) + ".",
                                "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                        modal.dispose();
                        carregar(true, false);
                    },
                    ex -> {
                        btnSalvar.setEnabled(true);
                        btnCancelar.setEnabled(true);
                        btnSalvar.setText("Salvar Recarga");
                        if (ex instanceof RecargaException) {
                            erro(modal, ex.getMessage());
                        } else {
                            erro(modal, "Erro ao salvar no banco de dados:\n" + ex.getMessage());
                        }
                    });
        });

        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rodape.setOpaque(false);
        rodape.add(btnCancelar);
        rodape.add(btnSalvar);
        linha(c, rodape, y++, 20);

        mostrarModal(modal, c);
    }

    /**
     * Grava a recarga e soma os créditos no cartão, na MESMA transação.
     * idFuncionario = null quando a recarga é do autoatendimento (campo fica vazio).
     * Retorna quantos créditos foram adicionados.
     */
    private long registrarRecarga(long idCartao, BigDecimal valor, String forma, String idFuncionario)
            throws SQLException, RecargaException {

        BigDecimal creditosDec = valor.multiply(BigDecimal.valueOf(CREDITOS_POR_REAL));
        if (creditosDec.stripTrailingZeros().scale() > 0) {
            throw new RecargaException("O valor precisa gerar créditos inteiros.\n"
                    + "Use múltiplos de R$ 0,20 (1 real = " + CREDITOS_POR_REAL + " créditos).");
        }
        long creditos = creditosDec.longValue();

        try (Connection con = conectar()) {
            con.setAutoCommit(false);
            try {
                // 1) o cartão existe e está ativo?
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT Ativo FROM `cartões` WHERE `ID_Cartão` = ? FOR UPDATE")) {
                    ps.setLong(1, idCartao);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new RecargaException("Cartão " + String.format("%03d", idCartao) + " não encontrado.");
                        }
                        if (!rs.getBoolean("Ativo")) {
                            throw new RecargaException("Este cartão está inativo e não pode receber recargas.");
                        }
                    }
                }

                // 2) registra a recarga
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO recargas (`ID_Cartão`, `ID_Funcionário`, Valor, Forma_Pagamento) "
                                + "VALUES (?, ?, ?, ?)")) {
                    ps.setLong(1, idCartao);
                    ps.setString(2, idFuncionario);
                    ps.setBigDecimal(3, valor);
                    ps.setString(4, forma);
                    ps.executeUpdate();
                }

                // 3) soma os créditos no cartão
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE `cartões` SET Creditos = Creditos + ? WHERE `ID_Cartão` = ?")) {
                    ps.setLong(1, creditos);
                    ps.setLong(2, idCartao);
                    ps.executeUpdate();
                }

                con.commit();
            } catch (SQLException | RecargaException e) {
                con.rollback();
                throw e;
            }
        }
        return creditos;
    }

    /** Converte "10", "10,50" ou "1.000,50" em BigDecimal (máx. 2 casas). Retorna null se inválido. */
    private static BigDecimal lerValor(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.replace("R$", "").replace(" ", "").trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.contains(",")) {
            t = t.replace(".", "").replace(',', '.');
        }
        try {
            BigDecimal v = new BigDecimal(t);
            if (v.signum() <= 0 || v.scale() > 2) {
                return null;
            }
            return v;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // ---------------------------------------------------------------
    // ABA 2: FORMAS DE PAGAMENTO (Manager Master)
    // ---------------------------------------------------------------
    private JPanel criarPainelFormas() {
        JPanel painel = new JPanel(new BorderLayout(0, 15));
        painel.setOpaque(false);

        JPanel acoes = new JPanel(new BorderLayout(15, 0));
        acoes.setOpaque(false);
        acoes.add(criarBusca("Buscar forma de pagamento...", texto -> filtrar(sorterFormas, texto)),
                BorderLayout.WEST);

        JButton btnEditar = criarBotao("Editar", false, 100);
        btnEditar.addActionListener(e -> editarFormaSelecionada());
        JButton btnNova = criarBotao("+ Nova Forma", true, 150);
        btnNova.addActionListener(e -> abrirModalForma(-1, "", true));

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        botoes.setOpaque(false);
        botoes.add(btnEditar);
        botoes.add(btnNova);
        acoes.add(botoes, BorderLayout.EAST);

        String[] colunas = { "ID", "Forma de Pagamento", "Status" };
        modeloFormas = new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tabelaFormas = criarTabela(modeloFormas);
        sorterFormas = new TableRowSorter<>(modeloFormas);
        tabelaFormas.setRowSorter(sorterFormas);

        painel.add(acoes, BorderLayout.NORTH);
        ghostFormas = new GhostPanel(3);
        cardTabelaFormas = criarCardTabela(tabelaFormas, ghostFormas);
        painel.add(cardTabelaFormas, BorderLayout.CENTER);
        return painel;
    }

    private List<Object[]> consultarFormas(Connection con) throws SQLException {
        List<Object[]> linhas = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT ID_Forma, Nome, Ativo FROM formas_pagamento ORDER BY ID_Forma");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                linhas.add(new Object[] {
                        String.format("%03d", rs.getLong("ID_Forma")),
                        rs.getString("Nome"),
                        rs.getBoolean("Ativo") ? "Ativa" : "Inativa"
                });
            }
        }
        return linhas;
    }

    private List<String> consultarFormasAtivas(Connection con) throws SQLException {
        List<String> nomes = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT Nome FROM formas_pagamento WHERE Ativo = TRUE ORDER BY Nome");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                nomes.add(rs.getString("Nome"));
            }
        }
        return nomes;
    }

    /**
     * Formas ativas para o modal "Nova Recarga". Usa a lista da última carga (abre na hora);
     * só consulta o banco se ainda não houver nenhuma.
     */
    private List<String> obterFormasAtivas() {
        if (formasAtivasCache != null && !formasAtivasCache.isEmpty()) {
            return formasAtivasCache;
        }
        try (Connection con = conectar()) {
            garantirTabelaFormas(con);
            formasAtivasCache = consultarFormasAtivas(con);
        } catch (SQLException ex) {
            erro(this, "Não foi possível carregar as formas de pagamento:\n" + ex.getMessage());
            return new ArrayList<>();
        }
        return formasAtivasCache;
    }

    /** Cria a tabela na primeira vez e já deixa 4 formas básicas cadastradas. */
    private static void garantirTabelaFormas(Connection con) throws SQLException {
        if (tabelaFormasPronta) {
            return;
        }
        try (Statement st = con.createStatement()) {
            st.executeUpdate(SQL_CRIAR_FORMAS);
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM formas_pagamento")) {
                if (rs.next() && rs.getLong(1) == 0) {
                    st.executeUpdate("INSERT INTO formas_pagamento (Nome) VALUES "
                            + "('Dinheiro'), ('Pix'), ('Cartão de Crédito'), ('Cartão de Débito')");
                }
            }
        }
        tabelaFormasPronta = true;
    }

    private void editarFormaSelecionada() {
        int linhaVisual = tabelaFormas.getSelectedRow();
        if (linhaVisual < 0) {
            JOptionPane.showMessageDialog(this, "Selecione uma forma de pagamento na tabela.",
                    "Editar", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int m = tabelaFormas.convertRowIndexToModel(linhaVisual);
        long id = Long.parseLong(String.valueOf(modeloFormas.getValueAt(m, 0)));
        String nome = String.valueOf(modeloFormas.getValueAt(m, 1));
        boolean ativa = "Ativa".equals(modeloFormas.getValueAt(m, 2));
        abrirModalForma(id, nome, ativa);
    }

    /** id = -1 cadastra uma forma nova; senão edita a forma existente. */
    private void abrirModalForma(long id, String nomeAtual, boolean ativaAtual) {
        if (!Sessao.isManagerMaster()) {
            JOptionPane.showMessageDialog(this, "Apenas o Manager Master pode gerenciar formas de pagamento.",
                    "Acesso negado", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean edicao = id >= 0;

        JDialog modal = criarModal(edicao ? "Editar Forma de Pagamento" : "Nova Forma de Pagamento");
        JPanel c = criarPainelModal();

        JTextField txtNome = criarCampoTexto();
        txtNome.setText(nomeAtual);

        JCheckBox chkAtiva = new JCheckBox("Forma ativa (aparece na hora de recarregar)");
        chkAtiva.setOpaque(false);
        chkAtiva.setForeground(Color.WHITE);
        chkAtiva.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        chkAtiva.setSelected(ativaAtual);
        chkAtiva.setFocusPainted(false);

        int y = 0;
        linha(c, criarTituloModal(edicao ? "Editar Forma de Pagamento" : "Cadastrar Forma de Pagamento"), y++, 0);
        linha(c, criarRotulo("Nome"), y++, 16);
        linha(c, txtNome, y++, 4);
        linha(c, chkAtiva, y++, 12);
        if (edicao) {
            JLabel aviso = new JLabel("<html>Renomear não altera recargas antigas: elas mantêm o nome usado na época.</html>");
            aviso.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            aviso.setForeground(TEXTO_SUAVE);
            linha(c, aviso, y++, 10);
        }

        JButton btnCancelar = criarBotao("Cancelar", false, 110);
        JButton btnSalvar = criarBotao("Salvar", true, 110);
        btnCancelar.addActionListener(e -> modal.dispose());
        btnSalvar.addActionListener(e -> {
            String nome = txtNome.getText().trim();
            if (nome.isEmpty()) {
                erro(modal, "Informe o nome da forma de pagamento.");
                return;
            }
            if (nome.length() > 50) {
                erro(modal, "O nome pode ter no máximo 50 caracteres.");
                return;
            }
            boolean ativa = chkAtiva.isSelected();

            btnSalvar.setEnabled(false);
            btnCancelar.setEnabled(false);
            btnSalvar.setText("Salvando...");

            async(() -> {
                        salvarForma(id, nome, ativa);
                        return Boolean.TRUE;
                    },
                    ok -> {
                        modal.dispose();
                        carregar(false, true);
                    },
                    ex -> {
                        btnSalvar.setEnabled(true);
                        btnCancelar.setEnabled(true);
                        btnSalvar.setText("Salvar");
                        if (ex instanceof SQLException && ((SQLException) ex).getErrorCode() == 1062) {
                            // chave duplicada (Nome é UNIQUE)
                            erro(modal, "Já existe uma forma de pagamento com o nome \"" + nome + "\".");
                        } else {
                            erro(modal, "Erro ao salvar no banco de dados:\n" + ex.getMessage());
                        }
                    });
        });

        JPanel rodape = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rodape.setOpaque(false);
        rodape.add(btnCancelar);
        rodape.add(btnSalvar);
        linha(c, rodape, y++, 20);

        mostrarModal(modal, c);
    }

    private void salvarForma(long id, String nome, boolean ativa) throws SQLException {
        try (Connection con = conectar()) {
            garantirTabelaFormas(con);
            if (id < 0) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO formas_pagamento (Nome, Ativo) VALUES (?, ?)")) {
                    ps.setString(1, nome);
                    ps.setBoolean(2, ativa);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE formas_pagamento SET Nome = ?, Ativo = ? WHERE ID_Forma = ?")) {
                    ps.setString(1, nome);
                    ps.setBoolean(2, ativa);
                    ps.setLong(3, id);
                    ps.executeUpdate();
                }
            }
        }
    }

    // ---------------------------------------------------------------
    // BANCO
    // ---------------------------------------------------------------
    /** Conexao.conectar() devolve null quando falha; aqui vira SQLException para a tela mostrar o erro. */
    private Connection conectar() throws SQLException {
        Connection con = Conexao.conectar();
        if (con == null) {
            throw new SQLException("Não foi possível conectar ao banco de dados.");
        }
        return con;
    }

    // ---------------------------------------------------------------
    // COMPONENTES VISUAIS REUTILIZÁVEIS (mesmo visual da TelaMaquinas)
    // ---------------------------------------------------------------
    private void filtrar(TableRowSorter<DefaultTableModel> sorter, String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(texto.trim())));
        }
        atualizarTotal();
    }

    private JPanel criarBusca(String placeholder, Consumer<String> aoDigitar) {
        JPanel painel = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CAMPO);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(30, 48, 80));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
            }
        };
        painel.setOpaque(false);
        painel.setPreferredSize(new Dimension(420, 38));
        painel.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));

        JLabel lupa = new JLabel("🔍");
        lupa.setForeground(TEXTO_SUAVE);

        // Campo com "placeholder" desenhado quando está vazio e sem foco
        JTextField txt = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g2.setColor(new Color(100, 116, 139));
                    g2.setFont(getFont());
                    int y = (getHeight() + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2;
                    g2.drawString(placeholder, getInsets().left, y);
                    g2.dispose();
                }
            }
        };
        txt.setOpaque(false);
        txt.setForeground(Color.WHITE);
        txt.setCaretColor(Color.WHITE);
        txt.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txt.setBorder(null);
        txt.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                txt.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                txt.repaint();
            }
        });
        txt.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                aoDigitar.accept(txt.getText());
            }
        });

        painel.add(lupa, BorderLayout.WEST);
        painel.add(txt, BorderLayout.CENTER);
        return painel;
    }

    private JButton criarBotao(String texto, boolean primario, int largura) {
        JButton b = new JButton(texto) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(primario ? AZUL : new Color(23, 42, 69));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setPreferredSize(new Dimension(largura, 38));
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    private JTable criarTabela(DefaultTableModel modelo) {
        JTable t = new JTable(modelo);
        t.setBackground(CARD);
        t.setForeground(Color.WHITE);
        t.setGridColor(new Color(18, 35, 60));
        t.setRowHeight(42);
        t.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        t.setSelectionBackground(new Color(23, 55, 94));
        t.setSelectionForeground(Color.WHITE);
        t.getTableHeader().setReorderingAllowed(false);
        t.getTableHeader().setBackground(CARD);
        t.getTableHeader().setForeground(TEXTO_SUAVE);
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        t.getTableHeader().setPreferredSize(new Dimension(0, 40));

        DefaultTableCellRenderer centro = new DefaultTableCellRenderer();
        centro.setHorizontalAlignment(SwingConstants.CENTER);
        t.setDefaultRenderer(Object.class, centro);
        t.setDefaultRenderer(Long.class, centro);
        return t;
    }

    /** Card com duas \"páginas\": a tabela de verdade e o ghost loading. */
    private JPanel criarCardTabela(JTable tabela, GhostPanel ghost) {
        JPanel card = new JPanel(new CardLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CARD);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(BORDA);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JScrollPane scroll = new JScrollPane(tabela);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);

        card.add(scroll, "tabela");
        card.add(ghost, "ghost");
        return card;
    }

    // ---------------------------------------------------------------
    // JANELAS MODAIS
    // ---------------------------------------------------------------
    private JDialog criarModal(String titulo) {
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(this), titulo,
                Dialog.ModalityType.APPLICATION_MODAL);
        d.setResizable(false);
        return d;
    }

    private JPanel criarPainelModal() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(new Color(10, 22, 40));
        p.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));
        return p;
    }

    private void mostrarModal(JDialog modal, JPanel conteudo) {
        modal.setContentPane(conteudo);
        modal.pack();
        modal.setLocationRelativeTo(this);
        modal.setVisible(true);
    }

    private void linha(JPanel p, Component c, int y, int topo) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = y;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        g.insets = new Insets(topo, 0, 0, 0);
        p.add(c, g);
    }

    private JLabel criarTituloModal(String texto) {
        JLabel l = new JLabel(texto);
        l.setForeground(Color.WHITE);
        l.setFont(new Font("Segoe UI", Font.BOLD, 17));
        return l;
    }

    private JLabel criarRotulo(String texto) {
        JLabel l = new JLabel(texto);
        l.setForeground(TEXTO_SUAVE);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        return l;
    }

    private JTextField criarCampoTexto() {
        JTextField t = new JTextField();
        t.setPreferredSize(new Dimension(320, 36));
        t.setBackground(CAMPO);
        t.setForeground(Color.WHITE);
        t.setCaretColor(Color.WHITE);
        t.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        t.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 48, 80)),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        return t;
    }

    private void erro(Component pai, String msg) {
        JOptionPane.showMessageDialog(pai, msg, "Atenção", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Roda a tarefa numa thread de fundo e devolve o resultado (ou o erro) na thread da interface.
     * Assim nenhuma consulta ao banco trava a tela.
     */
    private <T> void async(Callable<T> tarefa, Consumer<T> aoConcluir, Consumer<Exception> aoFalhar) {
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

    /**
     * Ghost loading: linhas "fantasma" cinza com um brilho que passa da esquerda para a direita,
     * imitando a tabela enquanto os dados ainda estão chegando.
     */
    private static class GhostPanel extends JPanel {
        private static final Color BASE = new Color(20, 36, 60);
        private static final Color BRILHO = new Color(38, 64, 102);
        private static final int ALTURA_CABECALHO = 40;
        private static final int ALTURA_LINHA = 42;

        private final int colunas;
        private float fase = -0.3f;
        private final Timer timer = new Timer(30, e -> {
            fase += 0.025f;
            if (fase > 1.3f) {
                fase = -0.3f;
            }
            repaint();
        });

        GhostPanel(int colunas) {
            this.colunas = colunas;
            setOpaque(false);
        }

        void iniciar() {
            fase = -0.3f;
            if (!timer.isRunning()) {
                timer.start();
            }
        }

        void parar() {
            timer.stop();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int celula = Math.max(1, w / colunas);
            int linhas = Math.max(1, (h - ALTURA_CABECALHO) / ALTURA_LINHA);

            // linhas divisórias (iguais às da tabela de verdade)
            g2.setColor(new Color(18, 35, 60));
            g2.drawLine(0, ALTURA_CABECALHO, w, ALTURA_CABECALHO);
            for (int r = 0; r < linhas; r++) {
                int y = ALTURA_CABECALHO + (r + 1) * ALTURA_LINHA - 1;
                g2.drawLine(0, y, w, y);
            }

            // brilho que atravessa todas as barras de uma vez
            float cx = fase * w;
            float meia = Math.max(180f, w * 0.22f);
            g2.setPaint(new LinearGradientPaint(
                    new Point2D.Float(cx - meia, 0), new Point2D.Float(cx + meia, 0),
                    new float[] { 0f, 0.5f, 1f }, new Color[] { BASE, BRILHO, BASE }));

            // cabeçalho
            for (int c = 0; c < colunas; c++) {
                int bw = (int) (celula * 0.34);
                g2.fillRoundRect(c * celula + (celula - bw) / 2, (ALTURA_CABECALHO - 10) / 2, bw, 10, 8, 8);
            }

            // linhas (larguras variadas para parecer conteúdo de verdade)
            for (int r = 0; r < linhas; r++) {
                int y = ALTURA_CABECALHO + r * ALTURA_LINHA + (ALTURA_LINHA - 14) / 2;
                for (int c = 0; c < colunas; c++) {
                    int bw = (int) (celula * (0.30 + ((r * 7 + c * 13) % 5) * 0.07));
                    g2.fillRoundRect(c * celula + (celula - bw) / 2, y, bw, 14, 10, 10);
                }
            }
            g2.dispose();
        }
    }

    /** Atalho para rodar um Runnable quando o texto de um campo muda. */
    private static class AoMudar implements DocumentListener {
        private final Runnable acao;

        AoMudar(Runnable acao) {
            this.acao = acao;
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            acao.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            acao.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            acao.run();
        }
    }
}