import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.*;
import java.text.Normalizer;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;

/**
 * Tela de Recargas: aba "Recargas" (todos) e "Formas de Pagamento" (só Manager
 * Master).
 * 1 real = 5 créditos. Recarga e soma dos créditos são gravadas na mesma
 * transação.
 */
public class TelaRecargas extends JPanel {

    static final int CREDITOS_POR_REAL = 5;

    private static final Color FUNDO = new Color(2, 12, 27), CARD = new Color(10, 22, 40),
            BORDA = new Color(23, 42, 69), AZUL = new Color(14, 116, 233),
            TEXTO_SUAVE = new Color(148, 163, 184), CAMPO = new Color(13, 27, 46),
            AZUL_CLARO = new Color(125, 211, 252);
    private static final NumberFormat MOEDA = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String SQL_CRIAR_FORMAS = "CREATE TABLE IF NOT EXISTS formas_pagamento ("
            + "ID_Forma INT AUTO_INCREMENT PRIMARY KEY, Nome VARCHAR(50) NOT NULL UNIQUE, "
            + "Ativo BOOLEAN NOT NULL DEFAULT TRUE)";
    private static final int GHOST_MIN_MS = 350;
    private static boolean tabelaFormasPronta = false;

    private final CardLayout cards = new CardLayout();
    private JPanel painelCards, cardRecargas, cardFormas;
    private JButton abaRecargas, abaFormas;
    private boolean abaFormasAtiva;
    private Runnable acaoAutoatendimento;
    private JTable tabRecargas, tabFormas;
    private DefaultTableModel modeloRecargas, modeloFormas;
    private TableRowSorter<DefaultTableModel> sorterRecargas, sorterFormas;
    private JLabel lblTotal;
    private GhostPanel ghostRecargas, ghostFormas;
    private boolean carregandoRecargas, carregandoFormas, atualizandoSilencio;
    private int cargaRecargasId, cargaFormasId;
    private List<String> formasAtivasCache;

    public TelaRecargas() {
        setBackground(FUNDO);
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        JPanel topo = new JPanel(new BorderLayout(0, 10));
        topo.setOpaque(false);
        topo.add(rotulo("Recargas", Font.BOLD, 22, Color.WHITE), BorderLayout.NORTH);

        abaRecargas = criarAba("Recargas", () -> mostrarAba(false));
        abaFormas = criarAba("Formas de Pagamento", () -> mostrarAba(true));
        abaFormas.setVisible(false);
        JPanel abas = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 0));
        abas.setOpaque(false);
        abas.add(abaRecargas);
        abas.add(abaFormas);
        topo.add(abas, BorderLayout.CENTER);
        add(topo, BorderLayout.NORTH);

        painelCards = new JPanel(cards);
        painelCards.setOpaque(false);
        painelCards.add(montarRecargas(), "recargas");
        painelCards.add(montarFormas(), "formas");
        add(painelCards, BorderLayout.CENTER);

        lblTotal = rotulo("");
        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setOpaque(false);
        rodape.setPreferredSize(new Dimension(0, 35));
        rodape.add(lblTotal, BorderLayout.WEST);
        rodape.add(rotulo("1 real = " + CREDITOS_POR_REAL + " créditos"), BorderLayout.EAST);
        add(rodape, BorderLayout.SOUTH);

        mostrarAba(false);
    }

    // ===================== API PÚBLICA (App) =====================

    public void carregarDadosRecargas() {
        atualizarPermissoes();
        carregar(true, Sessao.isManagerMaster());
    }

    public void setAcaoAutoatendimento(Runnable acao) {
        this.acaoAutoatendimento = acao;
    }

    /**
     * Atualiza a aba Recargas em segundo plano, sem ghost e sem erro. Só troca se
     * algo mudou.
     */
    public void atualizarSilenciosamente() {
        if (carregandoRecargas || atualizandoSilencio) {
            return;
        }
        atualizandoSilencio = true;
        int cargaAtual = cargaRecargasId;
        async(() -> {
            try (Connection con = conectar()) {
                return consultarRecargas(con);
            }
        }, linhas -> {
            atualizandoSilencio = false;
            if (cargaAtual == cargaRecargasId && !carregandoRecargas && !mesmasLinhas(linhas)) {
                aplicarRecargasPreservandoSelecao(linhas);
            }
        }, ex -> {
            atualizandoSilencio = false;
            System.err.println("Atualização das recargas falhou: " + ex.getMessage());
        });
    }

    // ===================== API DO AUTOATENDIMENTO =====================

    /** Devolve null se o cartão não existir. */
    public CartaoInfo buscarCartao(long idCartao) throws SQLException {
        String sql = "SELECT `Usuário` AS usuario, Creditos AS creditos, Ativo AS ativo "
                + "FROM `cartões` WHERE `ID_Cartão` = ?";
        try (Connection con = conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, idCartao);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String nome = rs.getString("usuario");
                nome = (nome == null || nome.trim().isEmpty()) ? "cliente" : nome.trim();
                return new CartaoInfo(idCartao, nome, rs.getLong("creditos"), rs.getBoolean("ativo"));
            }
        }
    }

    /**
     * Só Pix, crédito e débito: no autoatendimento não há como receber dinheiro.
     */
    public List<String> formasAtivasAutoatendimento() throws SQLException {
        try (Connection con = conectar()) {
            garantirTabelaFormas(con);
            List<String> aceitas = new ArrayList<>();
            for (String nome : consultarFormasAtivas(con)) {
                if (tipoFormaAutoatendimento(nome) != null) {
                    aceitas.add(nome);
                }
            }
            return aceitas;
        }
    }

    /** "pix", "credito", "debito" ou null. Ignora acentos e maiúsculas. */
    static String tipoFormaAutoatendimento(String nome) {
        if (nome == null) {
            return null;
        }
        String n = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
        for (String tipo : new String[] { "pix", "credito", "debito" }) {
            if (n.contains(tipo)) {
                return tipo;
            }
        }
        return null;
    }

    public long registrarRecargaAutoatendimento(long idCartao, BigDecimal valor, String forma)
            throws SQLException, RecargaException {
        return registrarRecarga(idCartao, valor, forma, null);
    }

    // ===================== MONTAGEM =====================

    private JButton criarAba(String texto, Runnable acao) {
        JButton b = new JButton(texto);
        b.setFont(fonte(Font.BOLD, 14));
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        b.addActionListener(e -> acao.run());
        return b;
    }

    private static void estilizarAba(JButton b, boolean ativa) {
        b.setForeground(ativa ? Color.WHITE : TEXTO_SUAVE);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, ativa ? AZUL : new Color(0, 0, 0, 0)),
                BorderFactory.createEmptyBorder(6, 4, 6, 4)));
    }

    private void mostrarAba(boolean formas) {
        abaFormasAtiva = formas;
        cards.show(painelCards, formas ? "formas" : "recargas");
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
        boolean f = abaFormasAtiva;
        if (f ? carregandoFormas : carregandoRecargas) {
            lblTotal.setText("Carregando...");
            return;
        }
        int n = (f ? tabFormas : tabRecargas).getRowCount();
        String unidade = f
                ? (n == 1 ? " forma de pagamento" : " formas de pagamento")
                : (n == 1 ? " recarga" : " recargas");
        lblTotal.setText("Total: " + n + unidade);
    }

    private JPanel montarRecargas() {
        JButton btnAuto = botao("Autoatendimento", false, 190);
        btnAuto.addActionListener(e -> {
            if (acaoAutoatendimento != null) {
                acaoAutoatendimento.run();
            }
        });
        JButton btnNova = botao("+ Nova Recarga", true, 175);
        btnNova.addActionListener(e -> abrirModalNovaRecarga());

        modeloRecargas = somenteLeitura("ID", "Cartão", "Funcionário", "Valor", "Créditos",
                "Forma de Pagamento", "Data e Hora");
        tabRecargas = tabela(modeloRecargas);
        sorterRecargas = new TableRowSorter<>(modeloRecargas);
        tabRecargas.setRowSorter(sorterRecargas);

        ghostRecargas = new GhostPanel(7);
        cardRecargas = cardTabela(tabRecargas, ghostRecargas);
        return painelAba(busca("Buscar por cartão, funcionário ou forma de pagamento...",
                t -> filtrar(sorterRecargas, t)), cardRecargas, btnAuto, btnNova);
    }

    private JPanel montarFormas() {
        JButton btnEditar = botao("Editar", false, 100);
        btnEditar.addActionListener(e -> editarFormaSelecionada());
        JButton btnExcluir = Exclusao.criarBotao();
        btnExcluir.addActionListener(e -> excluirFormaSelecionada());
        JButton btnNova = botao("+ Nova Forma", true, 150);
        btnNova.addActionListener(e -> abrirModalForma(-1, "", true));

        modeloFormas = somenteLeitura("ID", "Forma de Pagamento", "Status");
        tabFormas = tabela(modeloFormas);
        sorterFormas = new TableRowSorter<>(modeloFormas);
        tabFormas.setRowSorter(sorterFormas);

        ghostFormas = new GhostPanel(3);
        cardFormas = cardTabela(tabFormas, ghostFormas);
        return painelAba(busca("Buscar forma de pagamento...", t -> filtrar(sorterFormas, t)),
                cardFormas, btnEditar, btnExcluir, btnNova);
    }

    /** Topo com busca e botões à direita, e o conteúdo embaixo. */
    private static JPanel painelAba(JPanel busca, JComponent conteudo, JButton... botoes) {
        JPanel direita = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        direita.setOpaque(false);
        for (JButton b : botoes) {
            direita.add(b);
        }
        JPanel acoes = new JPanel(new BorderLayout(15, 0));
        acoes.setOpaque(false);
        acoes.add(busca, BorderLayout.WEST);
        acoes.add(direita, BorderLayout.EAST);

        JPanel p = new JPanel(new BorderLayout(0, 15));
        p.setOpaque(false);
        p.add(acoes, BorderLayout.NORTH);
        p.add(conteudo, BorderLayout.CENTER);
        return p;
    }

    // ===================== CARREGAMENTO =====================

    /** Mostra o ghost, busca em segundo plano e troca o ghost pela tabela. */
    private void carregar(boolean recargas, boolean formas) {
        long inicio = System.currentTimeMillis();
        int idR = recargas ? ++cargaRecargasId : cargaRecargasId;
        int idF = formas ? ++cargaFormasId : cargaFormasId;

        if (recargas) {
            carregandoRecargas = true;
            mostrarGhost(cardRecargas, ghostRecargas, true);
        }
        if (formas) {
            carregandoFormas = true;
            mostrarGhost(cardFormas, ghostFormas, true);
        }
        atualizarTotal();

        async(() -> buscarDados(recargas, formas), dados -> {
            Runnable aplicar = () -> aplicarDados(dados,
                    recargas && idR == cargaRecargasId, formas && idF == cargaFormasId);
            long resto = GHOST_MIN_MS - (System.currentTimeMillis() - inicio);
            if (resto > 0) {
                Timer t = new Timer((int) resto, ev -> aplicar.run());
                t.setRepeats(false);
                t.start();
            } else {
                aplicar.run();
            }
        }, ex -> {
            if (recargas && idR == cargaRecargasId) {
                carregandoRecargas = false;
                mostrarGhost(cardRecargas, ghostRecargas, false);
            }
            if (formas && idF == cargaFormasId) {
                carregandoFormas = false;
                mostrarGhost(cardFormas, ghostFormas, false);
            }
            atualizarTotal();
            erro(this, "Não foi possível conectar ao banco de dados:\n" + ex.getMessage());
        });
    }

    /** Uma conexão para tudo; cada parte guarda o próprio erro. */
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

    private void aplicarDados(DadosCarga d, boolean aplicarRecargas, boolean aplicarFormas) {
        if (d.formasAtivas != null) {
            formasAtivasCache = d.formasAtivas;
        }
        if (aplicarRecargas) {
            preencher(modeloRecargas, d.recargas);
            carregandoRecargas = false;
            mostrarGhost(cardRecargas, ghostRecargas, false);
            if (d.erroRecargas != null) {
                erro(this, "Não foi possível carregar as recargas do banco de dados:\n" + d.erroRecargas);
            }
        }
        if (aplicarFormas) {
            preencher(modeloFormas, d.formas);
            carregandoFormas = false;
            mostrarGhost(cardFormas, ghostFormas, false);
            if (d.erroFormas != null) {
                erro(this, "Não foi possível carregar as formas de pagamento:\n" + d.erroFormas);
            }
        }
        atualizarTotal();
    }

    private void aplicarRecargasPreservandoSelecao(List<Object[]> linhas) {
        String idSelecionado = null;
        int v = tabRecargas.getSelectedRow();
        if (v >= 0) {
            idSelecionado = String.valueOf(modeloRecargas.getValueAt(tabRecargas.convertRowIndexToModel(v), 0));
        }
        preencher(modeloRecargas, linhas);
        if (idSelecionado != null) {
            for (int m = 0; m < modeloRecargas.getRowCount(); m++) {
                if (idSelecionado.equals(modeloRecargas.getValueAt(m, 0))) {
                    int vis = tabRecargas.convertRowIndexToView(m);
                    tabRecargas.setRowSelectionInterval(vis, vis);
                    break;
                }
            }
        }
        atualizarTotal();
    }

    private boolean mesmasLinhas(List<Object[]> novas) {
        if (novas.size() != modeloRecargas.getRowCount()) {
            return false;
        }
        int cols = modeloRecargas.getColumnCount();
        for (int r = 0; r < novas.size(); r++) {
            Object[] atual = new Object[cols];
            for (int c = 0; c < cols; c++) {
                atual[c] = modeloRecargas.getValueAt(r, c);
            }
            if (!Arrays.equals(atual, novas.get(r))) {
                return false;
            }
        }
        return true;
    }

    private static void preencher(DefaultTableModel m, List<Object[]> linhas) {
        m.setRowCount(0);
        if (linhas != null) {
            for (Object[] l : linhas) {
                m.addRow(l);
            }
        }
    }

    private static void mostrarGhost(JPanel card, GhostPanel ghost, boolean ligar) {
        ((CardLayout) card.getLayout()).show(card, ligar ? "ghost" : "tabela");
        if (ligar) {
            ghost.iniciar();
        } else {
            ghost.parar();
        }
    }

    private List<Object[]> consultarRecargas(Connection con) throws SQLException {
        String sql = "SELECT ID_Recarga AS id_recarga, `ID_Cartão` AS id_cartao, `ID_Funcionário` AS id_func, "
                + "Valor AS valor, Forma_Pagamento AS forma, Data_Hora AS data_hora "
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
                LocalDateTime dataHora = rs.getObject("data_hora", LocalDateTime.class);
                linhas.add(new Object[] {
                        String.format("%03d", rs.getLong("id_recarga")),
                        String.format("%03d", rs.getLong("id_cartao")),
                        rs.getString("id_func"),
                        MOEDA.format(valor),
                        creditos,
                        rs.getString("forma"),
                        dataHora != null ? dataHora.format(DATA_HORA) : ""
                });
            }
        }
        return linhas;
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
     * Usa a lista da última carga; só consulta o banco se ainda não houver nenhuma.
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

    /** Cria a tabela na primeira vez e insere 4 formas básicas. */
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

    // ===================== NOVA RECARGA =====================

    private void abrirModalNovaRecarga() {
        Funcionario logado = Sessao.getLogado();
        if (logado == null) {
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

        JDialog modal = novoModal("Nova Recarga");
        JTextField txtCartao = campo();
        JTextField txtValor = campo();
        JComboBox<String> cmbForma = new JComboBox<>(formas.toArray(new String[0]));
        cmbForma.setFont(fonte(Font.PLAIN, 14));
        cmbForma.setPreferredSize(new Dimension(320, 36));

        JLabel lblCreditos = rotulo("= 0 créditos", Font.BOLD, 13, AZUL_CLARO);
        txtValor.getDocument().addDocumentListener(aoMudar(() -> {
            BigDecimal v = lerValor(txtValor.getText());
            if (v == null) {
                lblCreditos.setText("= 0 créditos");
                return;
            }
            BigDecimal cr = v.multiply(BigDecimal.valueOf(CREDITOS_POR_REAL));
            lblCreditos.setText(cr.stripTrailingZeros().scale() > 0
                    ? "Use múltiplos de R$ 0,20 (para fechar créditos inteiros)"
                    : "= " + cr.longValue() + " créditos");
        }));

        JLabel lblFunc = rotulo("Funcionário: " + logado.getNome() + " (" + logado.getId() + ")", Font.PLAIN, 12,
                TEXTO_SUAVE);

        JButton btnCancelar = botao("Cancelar", false, 110);
        JButton btnSalvar = botao("Salvar Recarga", true, 150);
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

            bloquear(btnSalvar, btnCancelar, true, "Salvando...");
            async(() -> registrarRecarga(idCartao, valor, forma, logado.getId()), creditos -> {
                JOptionPane.showMessageDialog(modal,
                        "Recarga registrada!\n" + MOEDA.format(valor) + " = " + creditos
                                + " créditos adicionados ao cartão " + String.format("%03d", idCartao) + ".",
                        "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                modal.dispose();
                carregar(true, false);
            }, ex -> {
                bloquear(btnSalvar, btnCancelar, false, "Salvar Recarga");
                erro(modal, ex instanceof RecargaException
                        ? ex.getMessage()
                        : "Erro ao salvar no banco de dados:\n" + ex.getMessage());
            });
        });

        mostrarModal(modal, painelModal(
                rotulo("Registrar Nova Recarga", Font.BOLD, 17, Color.WHITE),
                rotulo("ID do Cartão"), txtCartao,
                rotulo("Valor (R$)"), txtValor,
                lblCreditos,
                rotulo("Forma de Pagamento"), cmbForma,
                lblFunc,
                rodape(btnCancelar, btnSalvar)));
    }

    /**
     * Grava a recarga e soma os créditos no cartão, na MESMA transação.
     * idFuncionario = null no autoatendimento.
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
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT Ativo FROM `cartões` WHERE `ID_Cartão` = ? FOR UPDATE")) {
                    ps.setLong(1, idCartao);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new RecargaException(
                                    "Cartão " + String.format("%03d", idCartao) + " não encontrado.");
                        }
                        if (!rs.getBoolean("Ativo")) {
                            throw new RecargaException("Este cartão está inativo e não pode receber recargas.");
                        }
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO recargas (`ID_Cartão`, `ID_Funcionário`, Valor, Forma_Pagamento, Data_Hora) "
                                + "VALUES (?, ?, ?, ?, NOW())")) {
                    ps.setLong(1, idCartao);
                    if (idFuncionario == null) {
                        ps.setNull(2, Types.VARCHAR);
                    } else {
                        ps.setString(2, idFuncionario);
                    }
                    ps.setBigDecimal(3, valor);
                    ps.setString(4, forma);
                    ps.executeUpdate();
                }

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
        Auditoria.registrar(idFuncionario == null ? "RECARGA_AUTOATENDIMENTO" : "RECARGA",
                String.format("Cartão %03d | R$ %s | %s | +%d créditos", idCartao, valor.toPlainString(), forma,
                        creditos));
        return creditos;
    }

    /**
     * "10", "10,50" ou "1.000,50" → BigDecimal (máx. 2 casas). Null se inválido.
     */
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
            return (v.signum() <= 0 || v.scale() > 2) ? null : v;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    // ===================== FORMAS DE PAGAMENTO =====================

    private int formaSelecionada(String tituloAviso) {
        int visual = tabFormas.getSelectedRow();
        if (visual < 0) {
            JOptionPane.showMessageDialog(this, "Selecione uma forma de pagamento na tabela.",
                    tituloAviso, JOptionPane.INFORMATION_MESSAGE);
            return -1;
        }
        return tabFormas.convertRowIndexToModel(visual);
    }

    private void editarFormaSelecionada() {
        int m = formaSelecionada("Editar");
        if (m < 0) {
            return;
        }
        long id = Long.parseLong(String.valueOf(modeloFormas.getValueAt(m, 0)));
        String nome = String.valueOf(modeloFormas.getValueAt(m, 1));
        abrirModalForma(id, nome, "Ativa".equals(modeloFormas.getValueAt(m, 2)));
    }

    private void excluirFormaSelecionada() {
        int m = formaSelecionada("Excluir");
        if (m < 0) {
            return;
        }
        String nome = String.valueOf(modeloFormas.getValueAt(m, 1));
        if (!Exclusao.confirmar(this, "a forma de pagamento " + nome)) {
            return;
        }
        String idForma = String.valueOf(modeloFormas.getValueAt(m, 0)).trim();
        try (Connection con = Conexao.conectar();
                PreparedStatement ps = con.prepareStatement("DELETE FROM formas_pagamento WHERE ID_Forma = ?")) {
            ps.setLong(1, Long.parseLong(idForma));
            ps.executeUpdate();
        } catch (SQLException ex) {
            Exclusao.erro(this, ex);
            return;
        }
        Auditoria.registrar("EXCLUSAO_FORMA_PAGAMENTO", "ID " + idForma + " | " + nome);
        carregar(false, true);
    }

    /** id = -1 cadastra; senão edita. */
    private void abrirModalForma(long id, String nomeAtual, boolean ativaAtual) {
        if (!Sessao.isManagerMaster()) {
            JOptionPane.showMessageDialog(this, "Apenas o Manager Master pode gerenciar formas de pagamento.",
                    "Acesso negado", JOptionPane.WARNING_MESSAGE);
            return;
        }
        boolean edicao = id >= 0;
        JDialog modal = novoModal(edicao ? "Editar Forma de Pagamento" : "Nova Forma de Pagamento");

        JTextField txtNome = campo();
        txtNome.setText(nomeAtual);
        JCheckBox chkAtiva = new JCheckBox("Forma ativa (aparece na hora de recarregar)", ativaAtual);
        chkAtiva.setOpaque(false);
        chkAtiva.setForeground(Color.WHITE);
        chkAtiva.setFont(fonte(Font.PLAIN, 13));
        chkAtiva.setFocusPainted(false);

        JLabel aviso = rotulo("<html>Renomear não altera recargas antigas: elas mantêm o nome usado na época.</html>",
                Font.PLAIN, 12, TEXTO_SUAVE);
        aviso.setVisible(edicao);

        JButton btnCancelar = botao("Cancelar", false, 110);
        JButton btnSalvar = botao("Salvar", true, 110);
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

            bloquear(btnSalvar, btnCancelar, true, "Salvando...");
            async(() -> {
                salvarForma(id, nome, ativa);
                return Boolean.TRUE;
            }, ok -> {
                modal.dispose();
                carregar(false, true);
            }, ex -> {
                bloquear(btnSalvar, btnCancelar, false, "Salvar");
                if (ex instanceof SQLException && ((SQLException) ex).getErrorCode() == 1062) {
                    erro(modal, "Já existe uma forma de pagamento com o nome \"" + nome + "\".");
                } else {
                    erro(modal, "Erro ao salvar no banco de dados:\n" + ex.getMessage());
                }
            });
        });

        mostrarModal(modal, painelModal(
                rotulo(edicao ? "Editar Forma de Pagamento" : "Cadastrar Forma de Pagamento", Font.BOLD, 17,
                        Color.WHITE),
                rotulo("Nome"), txtNome,
                chkAtiva,
                aviso,
                rodape(btnCancelar, btnSalvar)));
    }

    private void salvarForma(long id, String nome, boolean ativa) throws SQLException {
        try (Connection con = conectar()) {
            garantirTabelaFormas(con);
            String sql = id < 0
                    ? "INSERT INTO formas_pagamento (Nome, Ativo) VALUES (?, ?)"
                    : "UPDATE formas_pagamento SET Nome = ?, Ativo = ? WHERE ID_Forma = ?";
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, nome);
                ps.setBoolean(2, ativa);
                if (id >= 0) {
                    ps.setLong(3, id);
                }
                ps.executeUpdate();
            }
        }
        Auditoria.registrar(id < 0 ? "CADASTRO_FORMA_PAGAMENTO" : "EDICAO_FORMA_PAGAMENTO",
                nome + (ativa ? "" : " (inativa)"));
    }

    // ===================== UTILITÁRIOS =====================

    private Connection conectar() throws SQLException {
        Connection con = Conexao.conectar();
        if (con == null) {
            throw new SQLException("Não foi possível conectar ao banco de dados.");
        }
        return con;
    }

    private void filtrar(TableRowSorter<DefaultTableModel> sorter, String texto) {
        sorter.setRowFilter(texto == null || texto.trim().isEmpty()
                ? null
                : RowFilter.regexFilter("(?i)" + Pattern.quote(texto.trim())));
        atualizarTotal();
    }

    private static void erro(Component pai, String msg) {
        JOptionPane.showMessageDialog(pai, msg, "Atenção", JOptionPane.ERROR_MESSAGE);
    }

    /**
     * Desabilita (ou reabilita) os dois botões do modal e troca o texto do Salvar.
     */
    private static void bloquear(JButton salvar, JButton cancelar, boolean travar, String textoSalvar) {
        salvar.setEnabled(!travar);
        cancelar.setEnabled(!travar);
        salvar.setText(textoSalvar);
    }

    /**
     * Roda a tarefa em segundo plano e devolve o resultado (ou erro) na thread da
     * interface.
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

    // ===================== COMPONENTES VISUAIS =====================

    private static Font fonte(int estilo, int tamanho) {
        return new Font("Segoe UI", estilo, tamanho);
    }

    private static JLabel rotulo(String texto) {
        return rotulo(texto, Font.PLAIN, 13, TEXTO_SUAVE);
    }

    private static JLabel rotulo(String texto, int estilo, int tamanho, Color cor) {
        JLabel l = new JLabel(texto);
        l.setFont(fonte(estilo, tamanho));
        l.setForeground(cor);
        return l;
    }

    private static JPanel busca(String placeholder, Consumer<String> aoDigitar) {
        JPanel p = new JPanel(new BorderLayout(8, 0)) {
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
        p.setOpaque(false);
        p.setPreferredSize(new Dimension(420, 38));
        p.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));

        JTextField txt = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(new Color(100, 116, 139));
                    g2.setFont(getFont());
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(placeholder, getInsets().left, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                    g2.dispose();
                }
            }
        };
        txt.setOpaque(false);
        txt.setForeground(Color.WHITE);
        txt.setCaretColor(Color.WHITE);
        txt.setFont(fonte(Font.PLAIN, 13));
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

        p.add(rotulo("🔍"), BorderLayout.WEST);
        p.add(txt, BorderLayout.CENTER);
        return p;
    }

    private static JButton botao(String texto, boolean primario, int largura) {
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
        b.setFont(fonte(Font.BOLD, 13));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static DefaultTableModel somenteLeitura(String... colunas) {
        return new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JTable tabela(DefaultTableModel modelo) {
        JTable t = new JTable(modelo);
        t.setBackground(CARD);
        t.setForeground(Color.WHITE);
        t.setGridColor(new Color(18, 35, 60));
        t.setRowHeight(42);
        t.setFont(fonte(Font.PLAIN, 13));
        t.setSelectionBackground(new Color(23, 55, 94));
        t.setSelectionForeground(Color.WHITE);

        JTableHeader h = t.getTableHeader();
        h.setReorderingAllowed(false);
        h.setBackground(CARD);
        h.setForeground(TEXTO_SUAVE);
        h.setFont(fonte(Font.BOLD, 12));
        h.setPreferredSize(new Dimension(0, 40));

        DefaultTableCellRenderer centro = new DefaultTableCellRenderer();
        centro.setHorizontalAlignment(SwingConstants.CENTER);
        t.setDefaultRenderer(Object.class, centro);
        t.setDefaultRenderer(Long.class, centro);
        return t;
    }

    /** Card com duas "páginas": a tabela e o ghost loading. */
    private static JPanel cardTabela(JTable t, GhostPanel ghost) {
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

        JScrollPane scroll = new JScrollPane(t);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);

        card.add(scroll, "tabela");
        card.add(ghost, "ghost");
        return card;
    }

    private static JTextField campo() {
        JTextField t = new JTextField();
        t.setPreferredSize(new Dimension(320, 36));
        t.setBackground(CAMPO);
        t.setForeground(Color.WHITE);
        t.setCaretColor(Color.WHITE);
        t.setFont(fonte(Font.PLAIN, 14));
        t.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 48, 80)),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        return t;
    }

    private JDialog novoModal(String titulo) {
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(this), titulo,
                Dialog.ModalityType.APPLICATION_MODAL);
        d.setResizable(false);
        return d;
    }

    /** Empilha os componentes do formulário, um embaixo do outro. */
    private static JPanel painelModal(Component... itens) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(CARD);
        p.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.weightx = 1;
        g.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < itens.length; i++) {
            g.gridy = i;
            g.insets = new Insets(i == 0 ? 0 : 10, 0, 0, 0);
            p.add(itens[i], g);
        }
        return p;
    }

    private static JPanel rodape(JButton... botoes) {
        JPanel r = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        r.setOpaque(false);
        for (JButton b : botoes) {
            r.add(b);
        }
        return r;
    }

    private void mostrarModal(JDialog modal, JPanel conteudo) {
        modal.setContentPane(conteudo);
        modal.pack();
        modal.setLocationRelativeTo(this);
        modal.setVisible(true);
    }

    /** Listener que chama o Runnable em qualquer mudança no texto. */
    private static DocumentListener aoMudar(Runnable r) {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                r.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                r.run();
            }
        };
    }

    // ===================== CLASSES INTERNAS =====================

    public static class RecargaException extends Exception {
        RecargaException(String msg) {
            super(msg);
        }
    }

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

    private static class DadosCarga {
        List<Object[]> recargas;
        List<Object[]> formas;
        List<String> formasAtivas;
        String erroRecargas;
        String erroFormas;
    }

    /**
     * Ghost loading: linhas cinza com um brilho que passa da esquerda para a
     * direita.
     */
    private static class GhostPanel extends JPanel {
        private static final Color BASE = new Color(20, 36, 60), BRILHO = new Color(38, 64, 102);
        private static final int CAB = 40, LINHA = 42;

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

            int w = getWidth(), h = getHeight();
            int celula = Math.max(1, w / colunas);
            int linhas = Math.max(1, (h - CAB) / LINHA);

            g2.setColor(new Color(18, 35, 60));
            g2.drawLine(0, CAB, w, CAB);
            for (int r = 0; r < linhas; r++) {
                int y = CAB + (r + 1) * LINHA - 1;
                g2.drawLine(0, y, w, y);
            }

            float cx = fase * w, meia = Math.max(180f, w * 0.22f);
            g2.setPaint(new LinearGradientPaint(
                    new Point2D.Float(cx - meia, 0), new Point2D.Float(cx + meia, 0),
                    new float[] { 0f, 0.5f, 1f }, new Color[] { BASE, BRILHO, BASE }));

            for (int c = 0; c < colunas; c++) {
                int bw = (int) (celula * 0.34);
                g2.fillRoundRect(c * celula + (celula - bw) / 2, (CAB - 10) / 2, bw, 10, 8, 8);
            }
            for (int r = 0; r < linhas; r++) {
                int y = CAB + r * LINHA + (LINHA - 14) / 2;
                for (int c = 0; c < colunas; c++) {
                    int bw = (int) (celula * (0.30 + ((r * 7 + c * 13) % 5) * 0.07));
                    g2.fillRoundRect(c * celula + (celula - bw) / 2, y, bw, 14, 10, 10);
                }
            }
            g2.dispose();
        }
    }
}