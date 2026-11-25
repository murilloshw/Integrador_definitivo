import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

/**
 * Tela de PRÊMIOS, com três abas: Prêmios (cadastro), Lista de Prêmios
 * (cardápio com carrinho)
 * e Registro de Compras (histórico de resgates). O App chama recarregar() ao
 * abrir a tela.
 */
public class TelaPremios extends JPanel {

    private static final Color FUNDO = new Color(2, 12, 27), CARD = new Color(10, 22, 40),
            BORDA = new Color(23, 42, 69), AZUL = new Color(14, 116, 233),
            TEXTO_SUAVE = new Color(148, 163, 184), CAMPO = new Color(13, 27, 46),
            AZUL_CLARO = new Color(186, 230, 253), VERMELHO = new Color(248, 113, 113);

    /**
     * Coluna do cartão que paga os prêmios. Troque para "Creditos" se o desconto
     * for do crédito.
     */
    private static final String COLUNA_SALDO = "Pontos";
    private static final int LARGURA_MIN_CARD = 430;
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static boolean colunaFotoPronta = false;

    // ---- Topo e abas ----
    private final JButton[] abas = new JButton[3];
    private int abaAtual = 0;
    private boolean modoLista = false;
    private JButton btnNovo, btnEditar;
    private IconeCarrinho btnCarrinho;
    private JPanel conteudo;
    private JLabel lblTotal, lblInfoCartao;
    private String filtro = "";

    // ---- Aba Prêmios ----
    private JTable tabela, tabelaRegistro;
    private DefaultTableModel modeloTabela, modeloRegistro;
    private TableRowSorter<DefaultTableModel> sorter, sorterRegistro;
    private JPanel cardTabela, cardRegistro;
    private GhostPanel ghostTabela, ghostRegistro;
    private boolean carregandoTabela, carregandoRegistro;
    private int cargaTabelaId, cargaRegistroId, cargaCatalogoId;

    // ---- Aba Lista de Prêmios ----
    private JPanel gradeCatalogo;
    private JScrollPane scrollCatalogo;
    private JTextField txtCartao;
    private int catalogoVisiveis;
    private final List<PremioItem> premiosCatalogo = new ArrayList<>();

    // ---- Cartão selecionado e carrinho ----
    private long cartaoId = -1, saldoCartao;
    private String nomeCliente;
    private final List<ItemCarrinho> carrinho = new ArrayList<>();

    public TelaPremios() {
        setBackground(FUNDO);
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        lblTotal = rotulo("", "Segoe UI", Font.PLAIN, 13, TEXTO_SUAVE);
        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setOpaque(false);
        rodape.setPreferredSize(new Dimension(0, 35));
        rodape.add(lblTotal, BorderLayout.WEST);

        conteudo = new JPanel(new CardLayout());
        conteudo.setOpaque(false);
        conteudo.add(criarPainelPremios(), "tabela");
        conteudo.add(criarPainelCatalogo(), "catalogo");
        conteudo.add(criarPainelRegistro(), "registro");

        add(criarTopo(), BorderLayout.NORTH);
        add(conteudo, BorderLayout.CENTER);
        add(rodape, BorderLayout.SOUTH);

        mostrarAba(0);
        carregarPremios();
    }

    /** Chamado pelo App ao abrir a tela. */
    public void recarregar() {
        carregarPremios();
        if (modoLista)
            carregarCatalogo();
        if (abaAtual == 2)
            carregarRegistro();
    }

    // ===================== MONTAGEM =====================

    private JPanel criarTopo() {
        JLabel titulo = rotulo("Prêmios", "Segoe UI", Font.BOLD, 22, Color.WHITE);

        btnNovo = botao("+ Novo Prêmio", true, 150);
        btnNovo.addActionListener(e -> abrirModal(-1));

        btnEditar = botao("Editar", false, 100);
        btnEditar.addActionListener(e -> {
            int linha = linhaSelecionada();
            if (linha >= 0)
                abrirModal(linha);
        });

        btnCarrinho = new IconeCarrinho(true);
        btnCarrinho.setPreferredSize(new Dimension(52, 38));
        btnCarrinho.addActionListener(e -> abrirCarrinho());
        btnCarrinho.setVisible(false);

        JPanel acoes = new JPanel(new BorderLayout(15, 0));
        acoes.setOpaque(false);
        acoes.add(busca("Buscar por nome...", this::filtrar), BorderLayout.WEST);
        acoes.add(painelDireita(btnCarrinho, btnEditar, btnNovo), BorderLayout.EAST);

        String[] nomes = { "Prêmios", "Lista de Prêmios", "Registro de Compras" };
        JPanel painelAbas = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 0));
        painelAbas.setOpaque(false);
        for (int k = 0; k < abas.length; k++) {
            abas[k] = aba(nomes[k]);
            final int destino = k;
            abas[k].addActionListener(e -> mostrarAba(destino));
            painelAbas.add(abas[k]);
        }

        JPanel tituloEAbas = new JPanel(new BorderLayout(0, 10));
        tituloEAbas.setOpaque(false);
        tituloEAbas.add(titulo, BorderLayout.NORTH);
        tituloEAbas.add(painelAbas, BorderLayout.CENTER);

        JPanel topo = new JPanel(new BorderLayout(0, 15));
        topo.setOpaque(false);
        topo.add(tituloEAbas, BorderLayout.NORTH);
        topo.add(acoes, BorderLayout.CENTER);
        return topo;
    }

    private JPanel criarPainelPremios() {
        modeloTabela = somenteLeitura("ID", "Nome do Prêmio", "Valor (Pontos)", "Qtd. Disponível", "Status");
        tabela = new JTable(modeloTabela);
        sorter = new TableRowSorter<>(modeloTabela);
        estilizarTabela(tabela, sorter, 1);
        ghostTabela = new GhostPanel(5);
        cardTabela = cardTabela(tabela, ghostTabela);
        return cardTabela;
    }

    private JPanel criarPainelCatalogo() {
        JPanel painel = new JPanel(new BorderLayout(0, 12));
        painel.setOpaque(false);

        JPanel barra = new PainelCard(new FlowLayout(FlowLayout.LEFT, 10, 8), 10);
        txtCartao = campo();
        txtCartao.setPreferredSize(new Dimension(110, 36));
        txtCartao.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER)
                    selecionarCartao();
            }
        });
        JButton btnSelecionar = botao("Selecionar", true, 110);
        btnSelecionar.addActionListener(e -> selecionarCartao());
        lblInfoCartao = rotulo("Nenhum cartão selecionado", "Segoe UI", Font.BOLD, 13, TEXTO_SUAVE);

        barra.add(rotuloForm("ID do Cartão:"));
        barra.add(txtCartao);
        barra.add(btnSelecionar);
        barra.add(lblInfoCartao);
        painel.add(barra, BorderLayout.NORTH);

        gradeCatalogo = new JPanel(new GridLayout(0, 2, 15, 15));
        gradeCatalogo.setOpaque(false);
        PainelRolavel envoltorio = new PainelRolavel(new BorderLayout());
        envoltorio.setOpaque(false);
        envoltorio.add(gradeCatalogo, BorderLayout.NORTH);

        scrollCatalogo = new JScrollPane(envoltorio);
        scrollCatalogo.setOpaque(false);
        scrollCatalogo.getViewport().setOpaque(false);
        scrollCatalogo.setBorder(null);
        scrollCatalogo.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollCatalogo.getVerticalScrollBar().setUnitIncrement(18);
        scrollCatalogo.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                ajustarColunas();
            }
        });
        painel.add(scrollCatalogo, BorderLayout.CENTER);
        return painel;
    }

    private JPanel criarPainelRegistro() {
        modeloRegistro = somenteLeitura("ID Cartão", "Nome do Usuário", "Prêmio", "Preço (Pontos)", "Data/Hora");
        tabelaRegistro = new JTable(modeloRegistro);
        sorterRegistro = new TableRowSorter<>(modeloRegistro);
        sorterRegistro.setSortable(4, false); // texto: mantém a ordem do banco
        estilizarTabela(tabelaRegistro, sorterRegistro, 1, 2);
        ghostRegistro = new GhostPanel(5);
        cardRegistro = cardTabela(tabelaRegistro, ghostRegistro);
        return cardRegistro;
    }

    // ===================== ABAS E FILTRO =====================

    private void mostrarAba(int i) {
        abaAtual = i;
        modoLista = (i == 1);
        ((CardLayout) conteudo.getLayout()).show(conteudo, new String[] { "tabela", "catalogo", "registro" }[i]);
        for (int k = 0; k < abas.length; k++) {
            estilizarAba(abas[k], k == i);
        }
        btnNovo.setVisible(i == 0);
        btnEditar.setVisible(i == 0);
        btnCarrinho.setVisible(i == 1);
        if (i == 1)
            carregarCatalogo();
        if (i == 2)
            carregarRegistro();
        atualizarTotal();
    }

    /** Filtra as três abas pelo mesmo texto. */
    private void filtrar(String texto) {
        filtro = texto == null ? "" : texto.trim();
        if (modoLista)
            renderizarCatalogo();
        aplicarFiltro(sorter, filtro);
        aplicarFiltro(sorterRegistro, filtro);
        atualizarTotal();
    }

    private static void aplicarFiltro(TableRowSorter<DefaultTableModel> s, String texto) {
        if (texto.isEmpty()) {
            s.setRowFilter(null);
        } else {
            s.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(texto)));
        }
    }

    private void atualizarTotal() {
        if (abaAtual == 2) {
            if (carregandoRegistro) {
                lblTotal.setText("Carregando...");
                return;
            }
            int n = tabelaRegistro.getRowCount();
            lblTotal.setText("Total: " + n + (n == 1 ? " compra" : " compras"));
        } else if (modoLista) {
            lblTotal.setText("Total: " + catalogoVisiveis
                    + (catalogoVisiveis == 1 ? " prêmio disponível" : " prêmios disponíveis"));
        } else if (carregandoTabela) {
            lblTotal.setText("Carregando...");
        } else {
            int n = tabela.getRowCount();
            lblTotal.setText("Total: " + n + (n == 1 ? " prêmio" : " prêmios"));
        }
    }

    // ===================== ABA PRÊMIOS (CADASTRO) =====================

    private void carregarPremios() {
        int idCarga = ++cargaTabelaId;
        carregandoTabela = true;
        mostrarCard(cardTabela, "ghost");
        ghostTabela.iniciar();
        atualizarTotal();

        async(() -> {
            List<Object[]> linhas = new ArrayList<>();
            try (Connection con = conectar();
                    PreparedStatement ps = con.prepareStatement(
                            "SELECT ID_Premio, Nome, Valor_pontos, Qtd_Disponivel, Ativo FROM premios ORDER BY ID_Premio");
                    ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    linhas.add(new Object[] {
                            String.format("%03d", rs.getLong("ID_Premio")),
                            rs.getString("Nome"),
                            rs.getLong("Valor_pontos"),
                            rs.getInt("Qtd_Disponivel"),
                            rs.getBoolean("Ativo") ? "Ativo" : "Inativo"
                    });
                }
            }
            return linhas;
        }, linhas -> {
            if (idCarga != cargaTabelaId)
                return;
            preencher(modeloTabela, linhas);
            fimCarga(cardTabela, ghostTabela, false);
        }, ex -> {
            if (idCarga != cargaTabelaId)
                return;
            fimCarga(cardTabela, ghostTabela, false);
            aviso("Erro ao carregar prêmios:\n" + ex.getMessage());
        });
    }

    private int linhaSelecionada() {
        int visual = tabela.getSelectedRow();
        if (visual < 0) {
            aviso("Selecione uma linha da tabela para editar.");
            return -1;
        }
        return tabela.convertRowIndexToModel(visual);
    }

    /** Formulário de prêmio. linhaModelo = -1 cadastra; >= 0 edita aquela linha. */
    private void abrirModal(int linhaModelo) {
        boolean edicao = linhaModelo >= 0;
        JDialog modal = new JDialog(SwingUtilities.getWindowAncestor(this),
                edicao ? "Editar Prêmio" : "Cadastrar Novo Prêmio", Dialog.ModalityType.APPLICATION_MODAL);
        modal.setSize(440, 570);
        modal.setLocationRelativeTo(this);
        modal.setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridLayout(10, 1, 2, 2));
        form.setBackground(CARD);
        form.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));

        JTextField txtNome = campo(), txtValor = campo(), txtQtd = campo();
        JComboBox<String> cmbStatus = new JComboBox<>(new String[] { "Ativo", "Inativo" });
        cmbStatus.setBackground(FUNDO);
        cmbStatus.setForeground(Color.WHITE);
        cmbStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        if (edicao) {
            txtNome.setText(String.valueOf(modeloTabela.getValueAt(linhaModelo, 1)));
            txtValor.setText(String.valueOf(modeloTabela.getValueAt(linhaModelo, 2)));
            txtQtd.setText(String.valueOf(modeloTabela.getValueAt(linhaModelo, 3)));
            cmbStatus.setSelectedItem(modeloTabela.getValueAt(linhaModelo, 4));
        }

        form.add(rotuloForm("Nome do Prêmio (máx. 50):"));
        form.add(txtNome);
        form.add(rotuloForm("Valor em Pontos (maior que 0):"));
        form.add(txtValor);
        form.add(rotuloForm("Quantidade Disponível:"));
        form.add(txtQtd);
        form.add(rotuloForm("Status:"));
        form.add(cmbStatus);

        byte[][] foto = new byte[1][];
        JLabel lblFoto = rotulo(edicao ? "Mantém a foto atual" : "Nenhuma foto escolhida",
                "Segoe UI", Font.PLAIN, 12, TEXTO_SUAVE);
        JButton btnFoto = botao("Escolher foto...", false, 150);
        btnFoto.addActionListener(e -> escolherFoto(modal, foto, lblFoto));
        JPanel linhaFoto = new JPanel(new BorderLayout(10, 0));
        linhaFoto.setOpaque(false);
        linhaFoto.add(btnFoto, BorderLayout.WEST);
        linhaFoto.add(lblFoto, BorderLayout.CENTER);
        form.add(rotuloForm("Foto do Prêmio:"));
        form.add(linhaFoto);

        JButton btnCancelar = botao("Cancelar", false, 110);
        btnCancelar.addActionListener(e -> modal.dispose());
        JButton btnSalvar = botao("Salvar", true, 110);
        btnSalvar.addActionListener(e -> {
            String nome = txtNome.getText().trim();
            if (nome.isEmpty() || nome.length() > 50) {
                aviso(modal, "Informe o Nome do prêmio.");
                return;
            }
            long valor;
            int qtd;
            try {
                valor = Long.parseLong(txtValor.getText().trim());
                qtd = Integer.parseInt(txtQtd.getText().trim());
            } catch (NumberFormatException ex) {
                aviso(modal, "Valor e Quantidade devem ser números inteiros.");
                return;
            }
            if (valor <= 0 || qtd < 0) {
                aviso(modal, "O Valor deve ser > 0 e a Qtd não pode ser negativa.");
                return;
            }
            boolean ativo = "Ativo".equals(cmbStatus.getSelectedItem());
            try {
                long idSalvo;
                if (edicao) {
                    idSalvo = Long.parseLong(String.valueOf(modeloTabela.getValueAt(linhaModelo, 0)));
                    atualizarPremio(idSalvo, nome, valor, qtd, ativo);
                } else {
                    idSalvo = inserirPremio(nome, valor, qtd, ativo);
                }
                if (foto[0] != null)
                    salvarFoto(idSalvo, foto[0]);
            } catch (SQLException ex) {
                aviso(modal, "Erro no banco:\n" + ex.getMessage());
                return;
            }
            modal.dispose();
            carregarPremios();
            if (modoLista)
                carregarCatalogo();
        });

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        botoes.setBackground(FUNDO);
        botoes.add(btnCancelar);
        botoes.add(btnSalvar);

        modal.add(form, BorderLayout.CENTER);
        modal.add(botoes, BorderLayout.SOUTH);
        modal.setVisible(true);
    }

    private void escolherFoto(JDialog modal, byte[][] destino, JLabel lbl) {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Imagens (png, jpg, gif)", "png", "jpg", "jpeg", "gif"));
        if (fc.showOpenDialog(modal) != JFileChooser.APPROVE_OPTION)
            return;
        try {
            destino[0] = prepararFoto(fc.getSelectedFile());
            lbl.setText(fc.getSelectedFile().getName());
        } catch (IOException ex) {
            aviso(modal, "Não foi possível usar esta imagem:\n" + ex.getMessage());
        }
    }

    private long inserirPremio(String nome, long valor, int qtd, boolean ativo) throws SQLException {
        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO premios (Nome, Valor_pontos, Qtd_Disponivel, Ativo) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nome);
            ps.setLong(2, valor);
            ps.setInt(3, qtd);
            ps.setBoolean(4, ativo);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    Auditoria.registrar("CADASTRO_PREMIO", descricaoPremio(id, nome, valor, qtd));
                    return id;
                }
            }
            throw new SQLException("ID não retornado.");
        }
    }

    private void atualizarPremio(long id, String nome, long valor, int qtd, boolean ativo) throws SQLException {
        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(
                        "UPDATE premios SET Nome = ?, Valor_pontos = ?, Qtd_Disponivel = ?, Ativo = ? WHERE ID_Premio = ?")) {
            ps.setString(1, nome);
            ps.setLong(2, valor);
            ps.setInt(3, qtd);
            ps.setBoolean(4, ativo);
            ps.setLong(5, id);
            ps.executeUpdate();
        }
        Auditoria.registrar("EDICAO_PREMIO", descricaoPremio(id, nome, valor, qtd));
    }

    private static String descricaoPremio(long id, String nome, long valor, int qtd) {
        return "ID " + id + " | " + nome + " | " + valor + " pontos | estoque " + qtd;
    }

    // ===================== FOTO =====================

    /** Cria a coluna Foto na tabela premios, se ainda não existir. */
    private static void garantirColunaFoto(Connection con) throws SQLException {
        if (colunaFotoPronta)
            return;
        boolean existe;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        + "AND LOWER(TABLE_NAME) = 'premios' AND COLUMN_NAME = 'Foto'");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            existe = rs.getInt(1) > 0;
        }
        if (!existe) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("ALTER TABLE premios ADD COLUMN Foto MEDIUMBLOB NULL");
            }
        }
        colunaFotoPronta = true;
    }

    private void salvarFoto(long idPremio, byte[] bytes) throws SQLException {
        try (Connection con = conectar()) {
            garantirColunaFoto(con);
            try (PreparedStatement ps = con.prepareStatement("UPDATE premios SET Foto = ? WHERE ID_Premio = ?")) {
                ps.setBytes(1, bytes);
                ps.setLong(2, idPremio);
                ps.executeUpdate();
            }
        }
    }

    /** Reduz a imagem para no máximo 400 px e converte para JPEG. */
    private static byte[] prepararFoto(File arquivo) throws IOException {
        BufferedImage orig = ImageIO.read(arquivo);
        if (orig == null)
            throw new IOException("O arquivo não é uma imagem válida.");
        double esc = Math.min(1.0, 400.0 / Math.max(orig.getWidth(), orig.getHeight()));
        int w = Math.max(1, (int) Math.round(orig.getWidth() * esc));
        int h = Math.max(1, (int) Math.round(orig.getHeight() * esc));
        BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = rgb.createGraphics();
        g2.setColor(CARD);
        g2.fillRect(0, 0, w, h);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.drawImage(orig, 0, 0, w, h, null);
        g2.dispose();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(rgb, "jpg", bos);
        return bos.toByteArray();
    }

    // ===================== ABA LISTA DE PRÊMIOS (CARDÁPIO) =====================

    private void carregarCatalogo() {
        int idCarga = ++cargaCatalogoId;
        gradeCatalogo.removeAll();
        gradeCatalogo.add(labelSuave("Carregando prêmios..."));
        gradeCatalogo.revalidate();
        gradeCatalogo.repaint();

        async(() -> {
            List<PremioItem> lista = new ArrayList<>();
            try (Connection con = conectar()) {
                garantirColunaFoto(con);
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT ID_Premio, Nome, Valor_pontos, Qtd_Disponivel, Foto FROM premios "
                                + "WHERE Ativo = TRUE ORDER BY Nome");
                        ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        PremioItem p = new PremioItem();
                        p.id = rs.getLong("ID_Premio");
                        p.nome = rs.getString("Nome");
                        p.valor = rs.getLong("Valor_pontos");
                        p.estoque = rs.getInt("Qtd_Disponivel");
                        byte[] bytes = rs.getBytes("Foto");
                        if (bytes != null && bytes.length > 0) {
                            try {
                                p.foto = ImageIO.read(new ByteArrayInputStream(bytes));
                            } catch (IOException ignorada) {
                                p.foto = null;
                            }
                        }
                        lista.add(p);
                    }
                }
            }
            return lista;
        }, lista -> {
            if (idCarga != cargaCatalogoId)
                return;
            premiosCatalogo.clear();
            premiosCatalogo.addAll(lista);
            sincronizarCarrinho();
            renderizarCatalogo();
        }, ex -> {
            if (idCarga != cargaCatalogoId)
                return;
            premiosCatalogo.clear();
            renderizarCatalogo();
            aviso("Erro ao carregar a lista de prêmios:\n" + ex.getMessage());
        });
    }

    private void renderizarCatalogo() {
        gradeCatalogo.removeAll();
        String f = filtro.toLowerCase();
        int n = 0;
        for (PremioItem p : premiosCatalogo) {
            if (!f.isEmpty() && (p.nome == null || !p.nome.toLowerCase().contains(f)))
                continue;
            gradeCatalogo.add(new CartaoPremio(p));
            n++;
        }
        catalogoVisiveis = n;
        if (n == 0)
            gradeCatalogo.add(labelSuave("Nenhum prêmio disponível."));
        gradeCatalogo.revalidate();
        gradeCatalogo.repaint();
        atualizarTotal();
    }

    /** Ajusta quantas colunas de cards cabem na largura atual. */
    private void ajustarColunas() {
        int larg = scrollCatalogo.getViewport().getWidth();
        int cols = Math.max(1, (larg + 15) / (LARGURA_MIN_CARD + 15));
        GridLayout gl = (GridLayout) gradeCatalogo.getLayout();
        if (gl.getColumns() != cols) {
            gl.setColumns(cols);
            gradeCatalogo.revalidate();
            gradeCatalogo.repaint();
        }
    }

    // ===================== CARTÃO DO CLIENTE =====================

    private void selecionarCartao() {
        long id;
        try {
            id = Long.parseLong(txtCartao.getText().trim());
        } catch (NumberFormatException ex) {
            aviso("Informe o ID do cartão (somente números).");
            return;
        }
        async(() -> buscarCartao(id), c -> {
            cartaoId = c.id;
            saldoCartao = c.saldo;
            nomeCliente = c.nome;
            atualizarInfoCartao();
            renderizarCatalogo();
        }, ex -> {
            cartaoId = -1;
            saldoCartao = 0;
            lblInfoCartao.setText("Nenhum cartão selecionado");
            lblInfoCartao.setForeground(TEXTO_SUAVE);
            renderizarCatalogo();
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Aviso", JOptionPane.WARNING_MESSAGE);
        });
    }

    /** Relê saldo e nome do cartão já selecionado. */
    private void recarregarCartaoAtual() {
        if (cartaoId < 0)
            return;
        async(() -> buscarCartao(cartaoId), c -> {
            saldoCartao = c.saldo;
            nomeCliente = c.nome;
            atualizarInfoCartao();
        }, ex -> {
        });
    }

    private void atualizarInfoCartao() {
        lblInfoCartao.setText("Cartão " + String.format("%03d", cartaoId)
                + (nomeCliente == null || nomeCliente.isEmpty() ? "" : "  |  " + nomeCliente)
                + "  |  Saldo: " + fmt(saldoCartao) + " pontos");
        lblInfoCartao.setForeground(AZUL_CLARO);
    }

    private DadosCartao buscarCartao(long id) throws SQLException, CompraException {
        String sql = "SELECT c." + COLUNA_SALDO + " AS saldo, c.Ativo, cl.Nome_Completo FROM `cartões` c "
                + "LEFT JOIN clientes cl ON cl.ID_Cliente = c.ID_Cliente WHERE c.`ID_Cartão` = ?";
        try (Connection con = conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    throw new CompraException("Cartão " + String.format("%03d", id) + " não encontrado.");
                if (!rs.getBoolean("Ativo"))
                    throw new CompraException("Este cartão está inativo.");
                DadosCartao d = new DadosCartao();
                d.id = id;
                d.saldo = rs.getLong("saldo");
                d.nome = rs.getString("Nome_Completo");
                return d;
            }
        }
    }

    // ===================== COMPRA =====================

    /** Compra direta de um card: valida, confirma e compra. */
    private void iniciarCompra(PremioItem item, int qtd) {
        if (cartaoId < 0) {
            aviso("Selecione um cartão antes de comprar.");
            return;
        }
        if (qtd <= 0) {
            aviso("Escolha a quantidade com o botão + antes de comprar.");
            return;
        }
        long total = (long) qtd * item.valor;
        if (total > saldoCartao) {
            erroSaldoInsuficiente();
            return;
        }
        if (!confirmar(this, qtd + "x " + item.nome, total))
            return;

        long idCartao = cartaoId;
        async(() -> comprarPremios(idCartao, Collections.singletonList(new long[] { item.id, qtd })),
                this::aoComprar, this::aoFalharCompra);
    }

    private boolean confirmar(Component pai, String detalhe, long total) {
        int r = JOptionPane.showOptionDialog(pai,
                "Tem certeza que quer fazer a compra?\n\n" + detalhe + "\n"
                        + "Total: " + fmt(total) + " pontos\n"
                        + "Saldo após a compra: " + fmt(saldoCartao - total) + " pontos",
                "Confirmar compra", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                new Object[] { "Sim", "Não" }, "Não");
        return r == 0;
    }

    private void aoComprar(long novoSaldo) {
        saldoCartao = novoSaldo;
        atualizarInfoCartao();
        JOptionPane.showMessageDialog(this, "Compra realizada com sucesso!\nSaldo atual: " + fmt(novoSaldo) + " pontos",
                "Compra concluída", JOptionPane.INFORMATION_MESSAGE);
        carregarCatalogo();
        carregarPremios();
        carregarRegistro();
    }

    private void aoFalharCompra(Exception ex) {
        if (ex instanceof CompraException) {
            if ("Saldo insuficiente.".equals(ex.getMessage()))
                erroSaldoInsuficiente();
            else
                aviso(ex.getMessage());
        } else {
            JOptionPane.showMessageDialog(this, "Erro ao registrar a compra:\n" + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
        recarregarCartaoAtual();
        carregarCatalogo();
    }

    /**
     * Debita o saldo, baixa o estoque e registra um resgate por prêmio, tudo numa
     * transação.
     * Cada item é {idPremio, quantidade}. Devolve o novo saldo.
     */
    private long comprarPremios(long idCartao, List<long[]> itens) throws SQLException, CompraException {
        try (Connection con = conectar()) {
            Map<String, String> cols = garantirTabelaResgates(con); // DDL antes da transação
            con.setAutoCommit(false);
            try {
                long saldo = travarCartao(con, idCartao);

                long[] subtotais = new long[itens.size()];
                long total = 0;
                for (int i = 0; i < itens.size(); i++) {
                    subtotais[i] = verificarPremio(con, itens.get(i)[0], (int) itens.get(i)[1]);
                    total += subtotais[i];
                }
                if (saldo < total)
                    throw new CompraException("Saldo insuficiente.");

                try (PreparedStatement ps = con.prepareStatement("UPDATE `cartões` SET " + COLUNA_SALDO + " = "
                        + COLUNA_SALDO + " - ? WHERE `ID_Cartão` = ?")) {
                    ps.setLong(1, total);
                    ps.setLong(2, idCartao);
                    ps.executeUpdate();
                }

                StringBuilder resumo = new StringBuilder(String.format("Cartão %03d | -%d pontos |", idCartao, total));
                for (int i = 0; i < itens.size(); i++) {
                    long idPremio = itens.get(i)[0];
                    int qtd = (int) itens.get(i)[1];
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE premios SET Qtd_Disponivel = Qtd_Disponivel - ? WHERE ID_Premio = ?")) {
                        ps.setInt(1, qtd);
                        ps.setLong(2, idPremio);
                        ps.executeUpdate();
                    }
                    registrarResgate(con, cols, idCartao, idPremio, qtd, subtotais[i]);
                    resumo.append(" prêmio ").append(idPremio).append(" x").append(qtd).append(';');
                }

                con.commit();
                Auditoria.registrar("RESGATE", resumo.toString());
                return saldo - total;
            } catch (SQLException | CompraException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Trava o cartão (FOR UPDATE) e devolve o saldo atual. */
    private static long travarCartao(Connection con, long idCartao) throws SQLException, CompraException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT " + COLUNA_SALDO + " AS saldo, Ativo FROM `cartões` WHERE `ID_Cartão` = ? FOR UPDATE")) {
            ps.setLong(1, idCartao);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    throw new CompraException("Cartão não encontrado.");
                if (!rs.getBoolean("Ativo"))
                    throw new CompraException("Este cartão está inativo.");
                return rs.getLong("saldo");
            }
        }
    }

    /**
     * Trava o prêmio, confere se está ativo e com estoque, e devolve o subtotal.
     */
    private static long verificarPremio(Connection con, long idPremio, int qtd) throws SQLException, CompraException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT Nome, Valor_pontos, Qtd_Disponivel, Ativo FROM premios WHERE ID_Premio = ? FOR UPDATE")) {
            ps.setLong(1, idPremio);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next())
                    throw new CompraException("Prêmio não encontrado.");
                String nome = rs.getString("Nome");
                if (!rs.getBoolean("Ativo"))
                    throw new CompraException("O prêmio \"" + nome + "\" não está mais disponível.");
                int estoque = rs.getInt("Qtd_Disponivel");
                if (estoque < qtd) {
                    throw new CompraException("Estoque insuficiente de \"" + nome + "\". Disponível: " + estoque + ".");
                }
                return rs.getLong("Valor_pontos") * qtd;
            }
        }
    }

    /** Insere o resgate usando só as colunas que existem na tabela. */
    private static void registrarResgate(Connection con, Map<String, String> cols, long idCartao, long idPremio,
            int qtd, long total) throws SQLException {
        List<String> nomes = new ArrayList<>();
        List<Object> valores = new ArrayList<>();
        adicionarColuna(cols, nomes, valores, "ID_Cartão", idCartao);
        adicionarColuna(cols, nomes, valores, "ID_Premio", idPremio);
        adicionarColuna(cols, nomes, valores, "Quantidade", qtd);
        adicionarColuna(cols, nomes, valores, "Pontos_Gastos", total);
        if (Sessao.getLogado() != null) {
            try {
                adicionarColuna(cols, nomes, valores, "ID_Funcionário", Long.parseLong(Sessao.getLogado().getId()));
            } catch (NumberFormatException ignorada) {
                // sem responsável registrado
            }
        }
        String colunas = String.join(", ", nomes);
        String marcas = String.join(", ", Collections.nCopies(nomes.size(), "?"));
        try (PreparedStatement ps = con
                .prepareStatement("INSERT INTO resgates (" + colunas + ") VALUES (" + marcas + ")")) {
            for (int i = 0; i < valores.size(); i++)
                ps.setObject(i + 1, valores.get(i));
            ps.executeUpdate();
        }
    }

    private static void adicionarColuna(Map<String, String> cols, List<String> nomes, List<Object> valores,
            String candidata, Object valor) {
        String real = cols.get(candidata.toLowerCase());
        if (real != null) {
            nomes.add("`" + real + "`");
            valores.add(valor);
        }
    }

    /**
     * Cria a tabela resgates se faltar, ou adiciona a coluna Quantidade. Devolve as
     * colunas (minúsculas -> real).
     */
    private static Map<String, String> garantirTabelaResgates(Connection con) throws SQLException {
        Map<String, String> cols = lerColunasResgates(con);
        if (cols.isEmpty()) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("CREATE TABLE IF NOT EXISTS resgates ("
                        + "ID_Resgate BIGINT AUTO_INCREMENT PRIMARY KEY, "
                        + "`ID_Cartão` BIGINT NOT NULL, "
                        + "ID_Premio BIGINT NOT NULL, "
                        + "Quantidade INT NOT NULL DEFAULT 1, "
                        + "Pontos_Gastos BIGINT NOT NULL, "
                        + "`ID_Funcionário` BIGINT NULL, "
                        + "Data_Hora DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            }
            cols = lerColunasResgates(con);
        } else if (!cols.containsKey("quantidade")) {
            try (Statement st = con.createStatement()) {
                st.executeUpdate("ALTER TABLE resgates ADD COLUMN Quantidade INT NOT NULL DEFAULT 1");
            }
            cols = lerColunasResgates(con);
        }
        return cols;
    }

    private static Map<String, String> lerColunasResgates(Connection con) throws SQLException {
        Map<String, String> cols = new HashMap<>();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND LOWER(TABLE_NAME) = 'resgates'");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String nome = rs.getString(1);
                cols.put(nome.toLowerCase(), nome);
            }
        }
        return cols;
    }

    // ===================== CARRINHO =====================

    private void atualizarBadgeCarrinho() {
        int n = 0;
        for (ItemCarrinho it : carrinho)
            n += it.qtd;
        btnCarrinho.setBadge(n);
    }

    /**
     * Após recarregar o catálogo: atualiza preço e estoque dos itens e tira os que
     * acabaram.
     */
    private void sincronizarCarrinho() {
        carrinho.removeIf(it -> {
            for (PremioItem p : premiosCatalogo) {
                if (p.id == it.premio.id) {
                    it.premio = p;
                    it.qtd = Math.min(it.qtd, p.estoque);
                    return it.qtd <= 0;
                }
            }
            return true;
        });
        atualizarBadgeCarrinho();
    }

    /** Salva a quantidade escolhida no carrinho. Devolve true se adicionou. */
    private boolean adicionarAoCarrinho(PremioItem item, int qtd) {
        if (qtd <= 0) {
            aviso("Escolha a quantidade com o botão + antes de salvar no carrinho.");
            return false;
        }
        ItemCarrinho existente = null;
        for (ItemCarrinho it : carrinho) {
            if (it.premio.id == item.id)
                existente = it;
        }
        int jaNoCarrinho = existente == null ? 0 : existente.qtd;
        if (jaNoCarrinho + qtd > item.estoque) {
            aviso("Estoque insuficiente. Disponível: " + item.estoque + " (já no carrinho: " + jaNoCarrinho + ").");
            return false;
        }
        if (existente == null) {
            carrinho.add(new ItemCarrinho(item, qtd));
        } else {
            existente.qtd += qtd;
            existente.premio = item;
        }
        atualizarBadgeCarrinho();
        return true;
    }

    private void abrirCarrinho() {
        JDialog dlg = new JDialog(SwingUtilities.getWindowAncestor(this), "Carrinho de Prêmios",
                Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setSize(580, 560);
        dlg.setLocationRelativeTo(this);
        dlg.setLayout(new BorderLayout());
        dlg.getContentPane().setBackground(FUNDO);

        JPanel lista = new JPanel();
        lista.setLayout(new BoxLayout(lista, BoxLayout.Y_AXIS));
        lista.setBackground(FUNDO);
        lista.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JScrollPane scroll = new JScrollPane(lista);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(FUNDO);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JLabel lblTotalCar = rotulo("", "Segoe UI", Font.BOLD, 15, Color.WHITE);
        JLabel lblSaldoCar = rotulo("", "Segoe UI", Font.PLAIN, 12, TEXTO_SUAVE);
        preencherCarrinho(lista, lblTotalCar, lblSaldoCar);

        JPanel resumo = new JPanel(new GridLayout(2, 1, 0, 2));
        resumo.setOpaque(false);
        resumo.add(lblTotalCar);
        resumo.add(lblSaldoCar);

        JButton esvaziar = botao("Esvaziar", false, 100);
        esvaziar.addActionListener(e -> {
            carrinho.clear();
            atualizarBadgeCarrinho();
            preencherCarrinho(lista, lblTotalCar, lblSaldoCar);
        });
        JButton fechar = botao("Fechar", false, 100);
        fechar.addActionListener(e -> dlg.dispose());
        JButton finalizar = botao("Finalizar compra", true, 160);
        finalizar.addActionListener(e -> finalizarCarrinho(dlg));

        JPanel rodape = new JPanel(new BorderLayout(10, 0));
        rodape.setBackground(CARD);
        rodape.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDA),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        rodape.add(resumo, BorderLayout.WEST);
        rodape.add(painelDireita(esvaziar, fechar, finalizar), BorderLayout.EAST);

        dlg.add(scroll, BorderLayout.CENTER);
        dlg.add(rodape, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    /** Redesenha a lista de itens do carrinho e os totais. */
    private void preencherCarrinho(JPanel lista, JLabel lblTotalCar, JLabel lblSaldoCar) {
        lista.removeAll();
        long total = 0;
        if (carrinho.isEmpty())
            lista.add(labelSuave("O carrinho está vazio."));

        for (ItemCarrinho it : new ArrayList<>(carrinho)) {
            long sub = (long) it.qtd * it.premio.valor;
            total += sub;

            JPanel linha = new PainelCard(new BorderLayout(10, 0), 10);
            linha.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
            linha.setPreferredSize(new Dimension(0, 62));
            linha.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
            linha.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel textos = new JPanel(new GridLayout(2, 1));
            textos.setOpaque(false);
            textos.add(rotulo(it.premio.nome, "Segoe UI", Font.BOLD, 14, Color.WHITE));
            textos.add(
                    rotulo(it.qtd + "x  " + fmt(it.premio.valor) + " pontos", "Segoe UI", Font.PLAIN, 12, TEXTO_SUAVE));

            JButton remover = botao("Remover", false, 90);
            remover.setPreferredSize(new Dimension(90, 32));
            remover.addActionListener(e -> {
                carrinho.remove(it);
                atualizarBadgeCarrinho();
                preencherCarrinho(lista, lblTotalCar, lblSaldoCar);
            });

            JPanel direita = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
            direita.setOpaque(false);
            direita.add(rotulo(fmt(sub) + " pts", "Segoe UI", Font.BOLD, 14, AZUL_CLARO));
            direita.add(remover);

            linha.add(textos, BorderLayout.CENTER);
            linha.add(direita, BorderLayout.EAST);
            lista.add(linha);
            lista.add(Box.createVerticalStrut(8));
        }

        lblTotalCar.setText("Total: " + fmt(total) + " pontos");
        lblSaldoCar.setText(cartaoId < 0 ? "Nenhum cartão selecionado"
                : "Saldo do cartão " + String.format("%03d", cartaoId) + ": " + fmt(saldoCartao) + " pontos");
        lista.revalidate();
        lista.repaint();
    }

    private void finalizarCarrinho(JDialog dlg) {
        if (carrinho.isEmpty()) {
            aviso(dlg, "O carrinho está vazio.");
            return;
        }
        if (cartaoId < 0) {
            aviso(dlg, "Selecione um cartão antes de finalizar a compra.");
            return;
        }
        long total = 0;
        List<long[]> itens = new ArrayList<>();
        for (ItemCarrinho it : carrinho) {
            total += (long) it.qtd * it.premio.valor;
            itens.add(new long[] { it.premio.id, it.qtd });
        }
        if (total > saldoCartao) {
            JOptionPane.showMessageDialog(dlg, "Saldo insuficiente.", "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String detalhe = carrinho.size() + (carrinho.size() == 1 ? " prêmio" : " prêmios") + " no carrinho";
        if (!confirmar(dlg, detalhe, total))
            return;

        long idCartao = cartaoId;
        async(() -> comprarPremios(idCartao, itens), novoSaldo -> {
            carrinho.clear();
            atualizarBadgeCarrinho();
            dlg.dispose();
            aoComprar(novoSaldo);
        }, ex -> {
            dlg.dispose();
            aoFalharCompra(ex);
        });
    }

    // ===================== ABA REGISTRO DE COMPRAS =====================

    private void carregarRegistro() {
        int idCarga = ++cargaRegistroId;
        carregandoRegistro = true;
        mostrarCard(cardRegistro, "ghost");
        ghostRegistro.iniciar();
        atualizarTotal();

        async(() -> {
            List<Object[]> linhas = new ArrayList<>();
            try (Connection con = conectar()) {
                garantirTabelaResgates(con);
                String sql = "SELECT r.`ID_Cartão` AS id_cartao, cl.Nome_Completo AS nome, p.Nome AS premio, "
                        + "r.Quantidade AS qtd, r.Pontos_Gastos AS preco, r.Data_Hora AS data_hora FROM resgates r "
                        + "LEFT JOIN `cartões` c ON c.`ID_Cartão` = r.`ID_Cartão` "
                        + "LEFT JOIN clientes cl ON cl.ID_Cliente = c.ID_Cliente "
                        + "LEFT JOIN premios p ON p.ID_Premio = r.ID_Premio "
                        + "ORDER BY r.Data_Hora DESC";
                try (PreparedStatement ps = con.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        int qtd = rs.getInt("qtd");
                        String premio = rs.getString("premio");
                        if (premio == null)
                            premio = "(prêmio removido)";
                        String nome = rs.getString("nome");
                        Timestamp ts = rs.getTimestamp("data_hora");
                        linhas.add(new Object[] {
                                String.format("%03d", rs.getLong("id_cartao")),
                                nome == null ? "—" : nome,
                                qtd > 1 ? qtd + "x " + premio : premio,
                                rs.getLong("preco"),
                                ts != null ? ts.toLocalDateTime().format(DATA_HORA) : ""
                        });
                    }
                }
            }
            return linhas;
        }, linhas -> {
            if (idCarga != cargaRegistroId)
                return;
            preencher(modeloRegistro, linhas);
            fimCarga(cardRegistro, ghostRegistro, true);
        }, ex -> {
            if (idCarga != cargaRegistroId)
                return;
            fimCarga(cardRegistro, ghostRegistro, true);
            aviso("Erro ao carregar o registro de compras:\n" + ex.getMessage());
        });
    }

    // ===================== UTILITÁRIOS =====================

    /**
     * Fim de carga: tira o ghost e mostra a tabela. registro = true para a aba de
     * compras.
     */
    private void fimCarga(JPanel card, GhostPanel ghost, boolean registro) {
        if (registro)
            carregandoRegistro = false;
        else
            carregandoTabela = false;
        mostrarCard(card, "tabela");
        ghost.parar();
        atualizarTotal();
    }

    private static void preencher(DefaultTableModel m, List<Object[]> linhas) {
        m.setRowCount(0);
        for (Object[] l : linhas)
            m.addRow(l);
    }

    private Connection conectar() throws SQLException {
        Connection con = Conexao.conectar();
        if (con == null)
            throw new SQLException("Não foi possível conectar ao banco de dados.");
        return con;
    }

    /**
     * Roda a tarefa fora da tela e chama aoConcluir ou aoFalhar de volta na tela.
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
                    Throwable c = ee.getCause();
                    aoFalhar.accept(c instanceof Exception ? (Exception) c : new Exception(c));
                }
            }
        }.execute();
    }

    private static String fmt(long n) {
        return String.format(new Locale("pt", "BR"), "%,d", n);
    }

    private void aviso(String msg) {
        aviso(this, msg);
    }

    private static void aviso(Component pai, String msg) {
        JOptionPane.showMessageDialog(pai, msg, "Aviso", JOptionPane.WARNING_MESSAGE);
    }

    private void erroSaldoInsuficiente() {
        JOptionPane.showMessageDialog(this, "Saldo insuficiente.", "Erro", JOptionPane.ERROR_MESSAGE);
    }

    private static void mostrarCard(JPanel card, String nome) {
        ((CardLayout) card.getLayout()).show(card, nome);
    }

    private static DefaultTableModel somenteLeitura(String... colunas) {
        return new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
    }

    /**
     * Visual padrão da tabela: centralizada, exceto as colunas indicadas em
     * colunasEsquerda.
     */
    private static void estilizarTabela(JTable t, TableRowSorter<DefaultTableModel> s, int... colunasEsquerda) {
        t.setRowSorter(s);
        t.setSelectionBackground(new Color(23, 55, 94));
        t.setSelectionForeground(Color.WHITE);
        t.getTableHeader().setReorderingAllowed(false);
        t.setBackground(CARD);
        t.setForeground(Color.WHITE);
        t.setGridColor(new Color(18, 35, 60));
        t.setRowHeight(42);
        t.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        t.getTableHeader().setBackground(CARD);
        t.getTableHeader().setForeground(TEXTO_SUAVE);
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        t.getTableHeader().setPreferredSize(new Dimension(0, 40));

        DefaultTableCellRenderer centro = new DefaultTableCellRenderer();
        centro.setHorizontalAlignment(SwingConstants.CENTER);
        centro.setBackground(CARD);
        centro.setForeground(AZUL_CLARO);

        DefaultTableCellRenderer esquerda = new DefaultTableCellRenderer();
        esquerda.setHorizontalAlignment(SwingConstants.LEFT);
        esquerda.setBackground(CARD);
        esquerda.setForeground(Color.WHITE);

        for (int c = 0; c < t.getColumnCount(); c++)
            t.getColumnModel().getColumn(c).setCellRenderer(centro);
        for (int c : colunasEsquerda)
            t.getColumnModel().getColumn(c).setCellRenderer(esquerda);
    }

    /** Card arredondado com duas "páginas": a tabela e o ghost de carregamento. */
    private static JPanel cardTabela(JTable t, GhostPanel ghost) {
        JPanel card = new PainelCard(new CardLayout(), 10);
        card.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        JScrollPane scroll = new JScrollPane(t);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        card.add(scroll, "tabela");
        card.add(ghost, "ghost");
        return card;
    }

    private static JButton aba(String texto) {
        JButton b = new JButton(texto);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static void estilizarAba(JButton b, boolean ativa) {
        b.setForeground(ativa ? Color.WHITE : TEXTO_SUAVE);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, ativa ? AZUL : new Color(0, 0, 0, 0)),
                BorderFactory.createEmptyBorder(6, 4, 6, 4)));
    }

    /** Campo de busca com lupa e placeholder. */
    private static JPanel busca(String placeholder, Consumer<String> aoDigitar) {
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
        painel.setPreferredSize(new Dimension(360, 38));
        painel.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));

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

        painel.add(rotulo("🔍", "Segoe UI", Font.PLAIN, 13, TEXTO_SUAVE), BorderLayout.WEST);
        painel.add(txt, BorderLayout.CENTER);
        return painel;
    }

    /** Botão arredondado: primário = azul; senão, azul escuro. */
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
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setBorderPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    private static JTextField campo() {
        JTextField t = new JTextField();
        t.setBackground(FUNDO);
        t.setForeground(Color.WHITE);
        t.setCaretColor(Color.WHITE);
        t.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        t.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 48, 80), 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return t;
    }

    private static JPanel painelDireita(Component... itens) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        p.setOpaque(false);
        for (Component c : itens)
            p.add(c);
        return p;
    }

    private static JLabel rotulo(String texto, String fonte, int estilo, int tamanho, Color cor) {
        JLabel l = new JLabel(texto);
        l.setFont(new Font(fonte, estilo, tamanho));
        l.setForeground(cor);
        return l;
    }

    private static JLabel rotuloForm(String texto) {
        return rotulo(texto, "Segoe UI", Font.BOLD, 12, new Color(226, 232, 240));
    }

    private static JLabel labelSuave(String texto) {
        return rotulo(texto, "Segoe UI", Font.PLAIN, 13, TEXTO_SUAVE);
    }

    // ===================== CLASSES INTERNAS =====================

    private static class PremioItem {
        long id;
        String nome;
        long valor;
        int estoque;
        BufferedImage foto;
    }

    private static class DadosCartao {
        long id;
        String nome;
        long saldo;
    }

    private static class ItemCarrinho {
        PremioItem premio;
        int qtd;

        ItemCarrinho(PremioItem premio, int qtd) {
            this.premio = premio;
            this.qtd = qtd;
        }
    }

    private static class CompraException extends Exception {
        CompraException(String msg) {
            super(msg);
        }
    }

    /** Painel com fundo arredondado e borda, no estilo dos cards do sistema. */
    private static class PainelCard extends JPanel {
        private final int raio;

        PainelCard(LayoutManager lm, int raio) {
            super(lm);
            this.raio = raio;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(CARD);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), raio, raio);
            g2.setColor(BORDA);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, raio, raio);
            g2.dispose();
        }
    }

    /** Painel que acompanha a largura da área visível do scroll. */
    private static class PainelRolavel extends JPanel implements Scrollable {
        PainelRolavel(LayoutManager lm) {
            super(lm);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return 120;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /**
     * Foto do prêmio preenchendo o quadro, ou a inicial do nome quando não há foto.
     */
    private static class FotoPremio extends JPanel {
        private final PremioItem item;

        FotoPremio(PremioItem item) {
            this.item = item;
            setOpaque(false);
            setPreferredSize(new Dimension(130, 100));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int w = getWidth(), h = getHeight();
            Shape clip = g2.getClip();
            g2.clip(new RoundRectangle2D.Float(0, 0, w, h, 14, 14));
            if (item.foto != null) {
                double s = Math.max((double) w / item.foto.getWidth(), (double) h / item.foto.getHeight());
                int dw = (int) Math.round(item.foto.getWidth() * s);
                int dh = (int) Math.round(item.foto.getHeight() * s);
                g2.drawImage(item.foto, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
            } else {
                g2.setColor(new Color(20, 36, 60));
                g2.fillRect(0, 0, w, h);
                String letra = (item.nome == null || item.nome.isEmpty()) ? "?"
                        : item.nome.substring(0, 1).toUpperCase();
                g2.setColor(new Color(38, 64, 102));
                g2.setFont(new Font("Segoe UI", Font.BOLD, 44));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(letra, (w - fm.stringWidth(letra)) / 2,
                        (h + fm.getAscent() - fm.getDescent()) / 2);
            }
            g2.setClip(clip);
            g2.setColor(BORDA);
            g2.draw(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 14, 14));
            g2.dispose();
        }
    }

    /**
     * Card do cardápio: foto, nome, preço, quantidade (+/-), Comprar e carrinho.
     */
    private class CartaoPremio extends PainelCard {
        private final PremioItem item;
        private int qtd = 0;
        private final JLabel lblQtd = new JLabel("0", SwingConstants.CENTER);
        private final JLabel lblTotalCard = new JLabel();

        CartaoPremio(PremioItem item) {
            super(new BorderLayout(14, 0), 12);
            this.item = item;
            setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 14));
            setPreferredSize(new Dimension(LARGURA_MIN_CARD, 200));
            add(new FotoPremio(item), BorderLayout.WEST);

            boolean esgotado = item.estoque <= 0;
            JLabel lblNome = rotulo(item.nome, "Segoe UI", Font.BOLD, 15, Color.WHITE);
            lblNome.setToolTipText(item.nome);
            JLabel lblPreco = rotulo(fmt(item.valor) + " pontos", "Segoe UI", Font.BOLD, 13, AZUL_CLARO);
            JLabel lblEstoque = rotulo(esgotado ? "Esgotado" : "Estoque: " + item.estoque, "Segoe UI", Font.PLAIN, 12,
                    esgotado ? VERMELHO : TEXTO_SUAVE);

            lblQtd.setFont(new Font("Segoe UI", Font.BOLD, 15));
            lblQtd.setForeground(Color.WHITE);
            lblQtd.setPreferredSize(new Dimension(36, 34));
            lblTotalCard.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            lblTotalCard.setForeground(TEXTO_SUAVE);

            JButton btnMenos = botao("-", false, 40);
            JButton btnMais = botao("+", true, 40);
            JButton btnComprar = botao("Comprar", true, 110);
            IconeCarrinho btnSalvar = new IconeCarrinho(false);
            for (JButton b : new JButton[] { btnMenos, btnMais }) {
                b.setPreferredSize(new Dimension(40, 34));
                b.setMargin(new Insets(0, 0, 0, 0));
            }
            btnComprar.setPreferredSize(new Dimension(110, 34));
            btnSalvar.setPreferredSize(new Dimension(44, 34));

            btnMenos.addActionListener(e -> {
                if (qtd > 0) {
                    qtd--;
                    atualizarQuantidade();
                }
            });
            btnMais.addActionListener(e -> {
                if (cartaoId < 0) {
                    aviso("Selecione um cartão antes de escolher a quantidade.");
                    return;
                }
                if (qtd + 1 > item.estoque) {
                    aviso("Estoque insuficiente. Disponível: " + item.estoque + ".");
                    return;
                }
                if ((long) (qtd + 1) * item.valor > saldoCartao) {
                    erroSaldoInsuficiente();
                    return;
                }
                qtd++;
                atualizarQuantidade();
            });
            btnComprar.addActionListener(e -> iniciarCompra(item, qtd));
            btnSalvar.addActionListener(e -> {
                if (adicionarAoCarrinho(item, qtd)) {
                    qtd = 0;
                    atualizarQuantidade();
                }
            });
            if (esgotado) {
                for (JButton b : new JButton[] { btnMenos, btnMais, btnComprar, btnSalvar })
                    b.setEnabled(false);
            }

            JPanel linhaQtd = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            linhaQtd.setOpaque(false);
            linhaQtd.add(btnMenos);
            linhaQtd.add(lblQtd);
            linhaQtd.add(btnMais);

            JPanel linhaComprar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            linhaComprar.setOpaque(false);
            linhaComprar.add(btnComprar);
            linhaComprar.add(Box.createHorizontalStrut(8));
            linhaComprar.add(btnSalvar);

            JPanel detalhes = new JPanel();
            detalhes.setOpaque(false);
            detalhes.setLayout(new BoxLayout(detalhes, BoxLayout.Y_AXIS));
            for (JComponent c : new JComponent[] { lblNome, lblPreco, lblEstoque, lblTotalCard, linhaQtd,
                    linhaComprar }) {
                c.setAlignmentX(Component.LEFT_ALIGNMENT);
            }
            detalhes.add(lblNome);
            detalhes.add(Box.createVerticalStrut(3));
            detalhes.add(lblPreco);
            detalhes.add(Box.createVerticalStrut(2));
            detalhes.add(lblEstoque);
            detalhes.add(Box.createVerticalGlue());
            detalhes.add(linhaQtd);
            detalhes.add(Box.createVerticalStrut(4));
            detalhes.add(lblTotalCard);
            detalhes.add(Box.createVerticalStrut(8));
            detalhes.add(linhaComprar);
            add(detalhes, BorderLayout.CENTER);

            atualizarQuantidade();
        }

        private void atualizarQuantidade() {
            lblQtd.setText(String.valueOf(qtd));
            lblTotalCard.setText("Total: " + fmt((long) qtd * item.valor) + " pontos");
        }
    }

    /** Botão de carrinho desenhado, com contador de itens. */
    private static class IconeCarrinho extends JButton {
        private final boolean grande;
        private int badge = 0;

        IconeCarrinho(boolean grande) {
            this.grande = grande;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setToolTipText(grande ? "Carrinho" : "Salvar no carrinho");
        }

        void setBadge(int n) {
            badge = n;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth(), h = getHeight();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(grande ? AZUL : new Color(23, 42, 69));
            g2.fillRoundRect(0, 0, w, h, 8, 8);

            double esc = Math.min(w, h) * 0.6 / 24.0;
            g2.translate((w - 24 * esc) / 2.0, (h - 24 * esc) / 2.0);
            g2.scale(esc, esc);
            g2.setColor(isEnabled() ? Color.WHITE : new Color(160, 170, 185));
            g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D.Float p = new Path2D.Float();
            p.moveTo(1, 3);
            p.lineTo(4, 3);
            p.lineTo(6.5f, 15);
            p.lineTo(19, 15);
            p.lineTo(21.5f, 6);
            p.lineTo(5, 6);
            g2.draw(p);
            g2.fill(new Ellipse2D.Float(7.2f, 18.2f, 3.6f, 3.6f));
            g2.fill(new Ellipse2D.Float(15.2f, 18.2f, 3.6f, 3.6f));
            g2.dispose();

            if (badge > 0) {
                Graphics2D g3 = (Graphics2D) g.create();
                g3.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int d = 18;
                g3.setColor(new Color(239, 68, 68));
                g3.fillOval(w - d - 1, 1, d, d);
                g3.setColor(Color.WHITE);
                g3.setFont(new Font("Segoe UI", Font.BOLD, 10));
                String t = badge > 9 ? "9+" : String.valueOf(badge);
                FontMetrics fm = g3.getFontMetrics();
                g3.drawString(t, w - d - 1 + (d - fm.stringWidth(t)) / 2,
                        1 + (d + fm.getAscent() - fm.getDescent()) / 2);
                g3.dispose();
            }
        }
    }

    /** Esqueleto animado mostrado no lugar da tabela enquanto os dados carregam. */
    private static class GhostPanel extends JPanel {
        private static final Color BASE = new Color(20, 36, 60), BRILHO = new Color(38, 64, 102);
        private static final int CAB = 40, LINHA = 42;

        private final int colunas;
        private float fase = -0.3f;
        private final Timer timer = new Timer(30, e -> {
            fase += 0.025f;
            if (fase > 1.3f)
                fase = -0.3f;
            repaint();
        });

        GhostPanel(int colunas) {
            this.colunas = colunas;
            setOpaque(false);
        }

        void iniciar() {
            fase = -0.3f;
            if (!timer.isRunning())
                timer.start();
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
            g2.setPaint(new LinearGradientPaint(new Point2D.Float(cx - meia, 0), new Point2D.Float(cx + meia, 0),
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