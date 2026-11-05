import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.Rectangle;
import java.util.Collections;
import java.awt.Shape;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFileChooser;
import javax.swing.Scrollable;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JButton;
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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

public class TelaPremios extends JPanel {

    private static final String PLACEHOLDER_BUSCA = "Buscar por nome...";
    private static final Color FUNDO = new Color(2, 12, 27);
    private static final Color CARD = new Color(10, 22, 40);
    private static final Color BORDA = new Color(23, 42, 69);
    private static final Color AZUL = new Color(14, 116, 233);
    private static final Color TEXTO_SUAVE = new Color(148, 163, 184);
    private static final Color CAMPO = new Color(13, 27, 46);

    private JTable tabela;
    private DefaultTableModel modeloTabela;
    private TableRowSorter<DefaultTableModel> sorter;
    private JLabel lblTotal;

    private JPanel cardTabela;
    private GhostPanel ghostPanel;
    private boolean carregando = false;
    private int cargaId = 0;

    // ---- Lista de Prêmios (cardápio) ----
    /**
     * Coluna do cartão usada para pagar os prêmios. Para descontar do crédito,
     * troque para "Creditos".
     */
    private static final String COLUNA_SALDO = "Pontos";
    private static final int LARGURA_MIN_CARD = 430;

    private JButton btnNovo;
    private JButton btnEditar;
    private JPanel conteudo;
    private JPanel gradeCatalogo;
    private JScrollPane scrollCatalogo;
    private JTextField txtCartao;
    private JLabel lblInfoCartao;
    private boolean modoLista = false;
    private int cargaCatalogoId = 0;
    private String filtroCatalogo = "";
    private int catalogoVisiveis = 0;
    private final List<PremioItem> premiosCatalogo = new ArrayList<>();
    private long cartaoId = -1;
    private long saldoCartao = 0;
    private static boolean colunaFotoPronta = false;

    private Connection conectar() throws SQLException {
        Connection con = Conexao.conectar();
        if (con == null)
            throw new SQLException("Não foi possível conectar ao banco de dados.");
        return con;
    }

    public TelaPremios() {
        setBackground(FUNDO);
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        // TOPO
        JPanel topo = new JPanel(new BorderLayout(0, 15));
        topo.setOpaque(false);

        JLabel lblTitulo = new JLabel("Prêmios");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitulo.setForeground(Color.WHITE);

        JPanel painelAcoes = new JPanel(new BorderLayout(15, 0));
        painelAcoes.setOpaque(false);

        JPanel painelBusca = criarBusca(PLACEHOLDER_BUSCA, texto -> filtrar(texto));

        btnNovo = criarBotao("+ Novo Prêmio", true, 150);
        btnNovo.addActionListener(e -> abrirModal(-1));

        btnEditar = criarBotao("Editar", false, 100);
        btnEditar.addActionListener(e -> {
            int linha = linhaSelecionada();
            if (linha >= 0)
                abrirModal(linha);
        });

        btnCarrinho = new IconeCarrinho(true);
        btnCarrinho.setPreferredSize(new Dimension(52, 38));
        btnCarrinho.addActionListener(e -> abrirCarrinho());
        btnCarrinho.setVisible(false); // só aparece na aba Lista de Prêmios

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        painelBotoes.setOpaque(false);
        painelBotoes.add(btnCarrinho);
        painelBotoes.add(btnEditar);
        painelBotoes.add(btnNovo);

        painelAcoes.add(painelBusca, BorderLayout.WEST);
        painelAcoes.add(painelBotoes, BorderLayout.EAST);

        abas[0] = criarAba("Prêmios");
        abas[1] = criarAba("Lista de Prêmios");
        abas[2] = criarAba("Registro de Compras");
        JPanel painelAbas = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 0));
        painelAbas.setOpaque(false);
        for (int k = 0; k < abas.length; k++) {
            final int destino = k;
            abas[k].addActionListener(e -> mostrarAba(destino));
            painelAbas.add(abas[k]);
        }
        JPanel tituloEAbas = new JPanel(new BorderLayout(0, 10));
        tituloEAbas.setOpaque(false);
        tituloEAbas.add(lblTitulo, BorderLayout.NORTH);
        tituloEAbas.add(painelAbas, BorderLayout.CENTER);

        topo.add(tituloEAbas, BorderLayout.NORTH);
        topo.add(painelAcoes, BorderLayout.CENTER);
        add(topo, BorderLayout.NORTH);

        // TABELA
        String[] colunas = { "ID", "Nome do Prêmio", "Valor (Pontos)", "Qtd. Disponível", "Status" };
        modeloTabela = new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        tabela = new JTable(modeloTabela);
        tabela.setSelectionBackground(new Color(23, 55, 94));
        tabela.setSelectionForeground(Color.WHITE);
        sorter = new TableRowSorter<>(modeloTabela);
        tabela.setRowSorter(sorter);
        tabela.getTableHeader().setReorderingAllowed(false);

        tabela.setBackground(CARD);
        tabela.setForeground(Color.WHITE);
        tabela.setGridColor(new Color(18, 35, 60));
        tabela.setRowHeight(42);
        tabela.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabela.getTableHeader().setBackground(CARD);
        tabela.getTableHeader().setForeground(TEXTO_SUAVE);
        tabela.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabela.getTableHeader().setPreferredSize(new Dimension(0, 40));

        DefaultTableCellRenderer centro = new DefaultTableCellRenderer();
        centro.setHorizontalAlignment(SwingConstants.CENTER);
        centro.setBackground(CARD);
        centro.setForeground(new Color(186, 230, 253));

        DefaultTableCellRenderer esquerda = new DefaultTableCellRenderer();
        esquerda.setHorizontalAlignment(SwingConstants.LEFT);
        esquerda.setBackground(CARD);
        esquerda.setForeground(Color.WHITE);

        tabela.getColumnModel().getColumn(0).setCellRenderer(centro);
        tabela.getColumnModel().getColumn(1).setCellRenderer(esquerda);
        tabela.getColumnModel().getColumn(2).setCellRenderer(centro);
        tabela.getColumnModel().getColumn(3).setCellRenderer(centro);
        tabela.getColumnModel().getColumn(4).setCellRenderer(centro);

        // Ghost Loading
        ghostPanel = new GhostPanel(5);
        cardTabela = new JPanel(new java.awt.CardLayout()) {
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
        cardTabela.setOpaque(false);
        cardTabela.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        JScrollPane scroll = new JScrollPane(tabela);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);

        cardTabela.add(scroll, "tabela");
        cardTabela.add(ghostPanel, "ghost");
        conteudo = new JPanel(new java.awt.CardLayout());
        conteudo.setOpaque(false);
        conteudo.add(cardTabela, "tabela");
        conteudo.add(criarPainelCatalogo(), "catalogo");
        conteudo.add(criarPainelRegistro(), "registro");
        add(conteudo, BorderLayout.CENTER);

        // RODAPÉ
        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setOpaque(false);
        rodape.setPreferredSize(new Dimension(0, 35));

        lblTotal = new JLabel();
        lblTotal.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTotal.setForeground(TEXTO_SUAVE);
        rodape.add(lblTotal, BorderLayout.WEST);
        add(rodape, BorderLayout.SOUTH);

        mostrarAba(0);
        carregarPremios();
    }

    public void recarregar() {
        carregarPremios();
        if (modoLista)
            carregarCatalogo();
        if (abaAtual == 2)
            carregarRegistro();
    }

    private void filtrar(String texto) {
        filtroCatalogo = texto == null ? "" : texto.trim();
        if (modoLista)
            renderizarCatalogo();
        if (texto == null || texto.trim().isEmpty()) {
            sorterRegistro.setRowFilter(null);
        } else {
            sorterRegistro.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(texto.trim())));
        }
        if (texto == null || texto.trim().isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(texto.trim())));
        }
        atualizarTotal();
    }

    private void atualizarTotal() {
        if (abaAtual == 2) {
            if (carregandoRegistro) {
                lblTotal.setText("Carregando...");
                return;
            }
            int n = tabelaRegistro.getRowCount();
            lblTotal.setText("Total: " + n + (n == 1 ? " compra" : " compras"));
            return;
        }
        if (modoLista) {
            lblTotal.setText("Total: " + catalogoVisiveis
                    + (catalogoVisiveis == 1 ? " prêmio disponível" : " prêmios disponíveis"));
            return;
        }
        if (carregando) {
            lblTotal.setText("Carregando...");
            return;
        }
        int n = tabela.getRowCount();
        lblTotal.setText("Total: " + n + (n == 1 ? " prêmio" : " prêmios"));
    }

    private int linhaSelecionada() {
        int linhaVisual = tabela.getSelectedRow();
        if (linhaVisual < 0) {
            JOptionPane.showMessageDialog(this, "Selecione uma linha da tabela para editar.", "Aviso",
                    JOptionPane.WARNING_MESSAGE);
            return -1;
        }
        return tabela.convertRowIndexToModel(linhaVisual);
    }

    // ---------------------------------------------------------
    // BANCO DE DADOS
    // ---------------------------------------------------------
    private void carregarPremios() {
        final int idCarga = ++cargaId;
        carregando = true;
        ((java.awt.CardLayout) cardTabela.getLayout()).show(cardTabela, "ghost");
        ghostPanel.iniciar();
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
            if (idCarga != cargaId)
                return;
            modeloTabela.setRowCount(0);
            for (Object[] l : linhas)
                modeloTabela.addRow(l);
            carregando = false;
            ((java.awt.CardLayout) cardTabela.getLayout()).show(cardTabela, "tabela");
            ghostPanel.parar();
            atualizarTotal();
        }, ex -> {
            if (idCarga != cargaId)
                return;
            carregando = false;
            ((java.awt.CardLayout) cardTabela.getLayout()).show(cardTabela, "tabela");
            ghostPanel.parar();
            atualizarTotal();
            JOptionPane.showMessageDialog(this, "Erro ao carregar prêmios:\n" + ex.getMessage(), "Erro",
                    JOptionPane.ERROR_MESSAGE);
        });
    }

    private long inserirPremio(String nome, long valor, int qtd, boolean ativo) throws SQLException {
        String sql = "INSERT INTO premios (Nome, Valor_pontos, Qtd_Disponivel, Ativo) VALUES (?, ?, ?, ?)";
        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nome);
            ps.setLong(2, valor);
            ps.setInt(3, qtd);
            ps.setBoolean(4, ativo);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next())
                    return keys.getLong(1);
            }
            throw new SQLException("ID não retornado.");
        }
    }

    private void atualizarPremio(long id, String nome, long valor, int qtd, boolean ativo) throws SQLException {
        String sql = "UPDATE premios SET Nome = ?, Valor_pontos = ?, Qtd_Disponivel = ?, Ativo = ? WHERE ID_Premio = ?";
        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nome);
            ps.setLong(2, valor);
            ps.setInt(3, qtd);
            ps.setBoolean(4, ativo);
            ps.setLong(5, id);
            ps.executeUpdate();
        }
    }

    private void abrirModal(int linhaModelo) {
        boolean edicao = linhaModelo >= 0;
        JDialog modal = new JDialog(SwingUtilities.getWindowAncestor(this),
                edicao ? "Editar Prêmio" : "Cadastrar Novo Prêmio", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        modal.setSize(440, 570);
        modal.setLocationRelativeTo(this);
        modal.setLayout(new BorderLayout());

        JPanel painelForm = new JPanel(new GridLayout(10, 1, 2, 2));
        painelForm.setBackground(CARD);
        painelForm.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));

        JTextField txtNome = criarCampoTexto();
        JTextField txtValor = criarCampoTexto();
        JTextField txtQtd = criarCampoTexto();
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

        painelForm.add(criarLabel("Nome do Prêmio (máx. 50):"));
        painelForm.add(txtNome);
        painelForm.add(criarLabel("Valor em Pontos (maior que 0):"));
        painelForm.add(txtValor);
        painelForm.add(criarLabel("Quantidade Disponível:"));
        painelForm.add(txtQtd);
        painelForm.add(criarLabel("Status:"));
        painelForm.add(cmbStatus);

        final byte[][] fotoEscolhida = new byte[1][];
        JButton btnFoto = criarBotao("Escolher foto...", false, 150);
        JLabel lblFoto = new JLabel(edicao ? "Mantém a foto atual" : "Nenhuma foto escolhida");
        lblFoto.setForeground(TEXTO_SUAVE);
        lblFoto.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btnFoto.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("Imagens (png, jpg, gif)", "png", "jpg", "jpeg", "gif"));
            if (fc.showOpenDialog(modal) != JFileChooser.APPROVE_OPTION)
                return;
            try {
                fotoEscolhida[0] = prepararFoto(fc.getSelectedFile());
                lblFoto.setText(fc.getSelectedFile().getName());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(modal, "Não foi possível usar esta imagem:\n" + ex.getMessage(), "Aviso",
                        JOptionPane.WARNING_MESSAGE);
            }
        });
        JPanel linhaFoto = new JPanel(new BorderLayout(10, 0));
        linhaFoto.setOpaque(false);
        linhaFoto.add(btnFoto, BorderLayout.WEST);
        linhaFoto.add(lblFoto, BorderLayout.CENTER);
        painelForm.add(criarLabel("Foto do Prêmio:"));
        painelForm.add(linhaFoto);

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        painelBotoes.setBackground(FUNDO);

        JButton btnCancelar = criarBotao("Cancelar", false, 110);
        btnCancelar.addActionListener(e -> modal.dispose());

        JButton btnSalvar = criarBotao("Salvar", true, 110);
        btnSalvar.addActionListener(e -> {
            String nome = txtNome.getText().trim();
            long valor;
            int qtd;

            if (nome.isEmpty() || nome.length() > 50) {
                JOptionPane.showMessageDialog(modal, "Informe o Nome do prêmio.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                valor = Long.parseLong(txtValor.getText().trim());
                qtd = Integer.parseInt(txtQtd.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(modal, "Valor e Quantidade devem ser números inteiros.", "Aviso",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (valor <= 0 || qtd < 0) {
                JOptionPane.showMessageDialog(modal, "O Valor deve ser > 0 e a Qtd não pode ser negativa.", "Aviso",
                        JOptionPane.WARNING_MESSAGE);
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
                if (fotoEscolhida[0] != null)
                    salvarFoto(idSalvo, fotoEscolhida[0]);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(modal, "Erro no banco:\n" + ex.getMessage(), "Erro",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            modal.dispose();
            carregarPremios();
            if (modoLista)
                carregarCatalogo();
        });

        painelBotoes.add(btnCancelar);
        painelBotoes.add(btnSalvar);

        modal.add(painelForm, BorderLayout.CENTER);
        modal.add(painelBotoes, BorderLayout.SOUTH);
        modal.setVisible(true);
    }

    // ---------------------------------------------------------
    // LISTA DE PRÊMIOS (CARDÁPIO)
    // ---------------------------------------------------------
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

    private static class CompraException extends Exception {
        CompraException(String msg) {
            super(msg);
        }
    }

    private String nomeCliente;

    private static String fmt(long n) {
        return String.format(new Locale("pt", "BR"), "%,d", n);
    }

    private JPanel criarPainelCatalogo() {
        JPanel painel = new JPanel(new BorderLayout(0, 12));
        painel.setOpaque(false);

        // Barra do cartão
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8)) {
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
        barra.setOpaque(false);

        JLabel lblCartao = criarLabel("ID do Cartão:");
        txtCartao = criarCampoTexto();
        txtCartao.setPreferredSize(new Dimension(110, 36));
        txtCartao.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER)
                    selecionarCartao();
            }
        });
        JButton btnSelecionar = criarBotao("Selecionar", true, 110);
        btnSelecionar.addActionListener(e -> selecionarCartao());

        lblInfoCartao = new JLabel("Nenhum cartão selecionado");
        lblInfoCartao.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblInfoCartao.setForeground(TEXTO_SUAVE);

        barra.add(lblCartao);
        barra.add(txtCartao);
        barra.add(btnSelecionar);
        barra.add(lblInfoCartao);
        painel.add(barra, BorderLayout.NORTH);

        // Grade de cards
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

    private void aviso(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Aviso", JOptionPane.WARNING_MESSAGE);
    }

    private void erroSaldoInsuficiente() {
        JOptionPane.showMessageDialog(this, "Saldo insuficiente.", "Erro", JOptionPane.ERROR_MESSAGE);
    }

    // ---- carregar e desenhar os cards ----
    private void carregarCatalogo() {
        final int idCarga = ++cargaCatalogoId;
        gradeCatalogo.removeAll();
        JLabel l = new JLabel("Carregando prêmios...");
        l.setForeground(TEXTO_SUAVE);
        l.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        gradeCatalogo.add(l);
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
            JOptionPane.showMessageDialog(this, "Erro ao carregar a lista de prêmios:\n" + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        });
    }

    private void renderizarCatalogo() {
        gradeCatalogo.removeAll();
        String f = filtroCatalogo.toLowerCase();
        int n = 0;
        for (PremioItem p : premiosCatalogo) {
            if (!f.isEmpty() && (p.nome == null || !p.nome.toLowerCase().contains(f)))
                continue;
            gradeCatalogo.add(new CartaoPremio(p));
            n++;
        }
        catalogoVisiveis = n;
        if (n == 0) {
            JLabel l = new JLabel("Nenhum prêmio disponível.");
            l.setForeground(TEXTO_SUAVE);
            l.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            gradeCatalogo.add(l);
        }
        gradeCatalogo.revalidate();
        gradeCatalogo.repaint();
        atualizarTotal();
    }

    // ---- cartão ----
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
            renderizarCatalogo(); // zera as quantidades escolhidas
        }, ex -> {
            cartaoId = -1;
            saldoCartao = 0;
            lblInfoCartao.setText("Nenhum cartão selecionado");
            lblInfoCartao.setForeground(TEXTO_SUAVE);
            renderizarCatalogo();
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Aviso", JOptionPane.WARNING_MESSAGE);
        });
    }

    private void atualizarInfoCartao() {
        lblInfoCartao.setText("Cartão " + String.format("%03d", cartaoId)
                + (nomeCliente == null || nomeCliente.isEmpty() ? "" : "  |  " + nomeCliente)
                + "  |  Saldo: " + fmt(saldoCartao) + " pontos");
        lblInfoCartao.setForeground(new Color(186, 230, 253));
    }

    private DadosCartao buscarCartao(long id) throws SQLException, CompraException {
        String sql = "SELECT c." + COLUNA_SALDO + " AS saldo, c.Ativo, cl.Nome_Completo "
                + "FROM `cartões` c LEFT JOIN clientes cl ON cl.ID_Cliente = c.ID_Cliente "
                + "WHERE c.`ID_Cartão` = ?";
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

    // ---- compra ----
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
        int r = JOptionPane.showOptionDialog(this,
                "Tem certeza que quer fazer a compra?\n\n"
                        + qtd + "x " + item.nome + "\n"
                        + "Total: " + fmt(total) + " pontos\n"
                        + "Saldo após a compra: " + fmt(saldoCartao - total) + " pontos",
                "Confirmar compra", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                new Object[] { "Sim", "Não" }, "Não");
        if (r != 0)
            return;

        final long idCartao = cartaoId;
        async(() -> comprarPremios(idCartao, Collections.singletonList(new long[] { item.id, qtd })),
                this::aoComprar, this::aoFalharCompra);
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
                JOptionPane.showMessageDialog(this, ex.getMessage(), "Aviso", JOptionPane.WARNING_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Erro ao registrar a compra:\n" + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        }
        // saldo e estoque podem ter mudado: recarrega
        selecionarCartaoAtual();
        carregarCatalogo();
    }

    private void selecionarCartaoAtual() {
        if (cartaoId < 0)
            return;
        final long id = cartaoId;
        async(() -> buscarCartao(id), c -> {
            saldoCartao = c.saldo;
            nomeCliente = c.nome;
            atualizarInfoCartao();
        }, ex -> {
        });
    }

    /**
     * Debita o saldo do cartão, baixa o estoque e registra os resgates (um por
     * prêmio), tudo na mesma transação.
     * Cada item é {idPremio, quantidade}. Devolve o novo saldo do cartão.
     */
    private long comprarPremios(long idCartao, List<long[]> itens) throws SQLException, CompraException {
        try (Connection con = conectar()) {
            Map<String, String> colsResgate = garantirTabelaResgates(con); // DDL fora da transação

            con.setAutoCommit(false);
            try {
                long saldo;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT " + COLUNA_SALDO
                                + " AS saldo, Ativo FROM `cartões` WHERE `ID_Cartão` = ? FOR UPDATE")) {
                    ps.setLong(1, idCartao);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next())
                            throw new CompraException("Cartão não encontrado.");
                        if (!rs.getBoolean("Ativo"))
                            throw new CompraException("Este cartão está inativo.");
                        saldo = rs.getLong("saldo");
                    }
                }

                long totalGeral = 0;
                long[] subtotais = new long[itens.size()];
                for (int i = 0; i < itens.size(); i++) {
                    long idPremio = itens.get(i)[0];
                    int qtd = (int) itens.get(i)[1];
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
                                throw new CompraException(
                                        "Estoque insuficiente de \"" + nome + "\". Disponível: " + estoque + ".");
                            }
                            subtotais[i] = rs.getLong("Valor_pontos") * qtd;
                            totalGeral += subtotais[i];
                        }
                    }
                }
                if (saldo < totalGeral)
                    throw new CompraException("Saldo insuficiente.");

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE `cartões` SET " + COLUNA_SALDO + " = " + COLUNA_SALDO + " - ? WHERE `ID_Cartão` = ?")) {
                    ps.setLong(1, totalGeral);
                    ps.setLong(2, idCartao);
                    ps.executeUpdate();
                }
                for (int i = 0; i < itens.size(); i++) {
                    long idPremio = itens.get(i)[0];
                    int qtd = (int) itens.get(i)[1];
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE premios SET Qtd_Disponivel = Qtd_Disponivel - ? WHERE ID_Premio = ?")) {
                        ps.setInt(1, qtd);
                        ps.setLong(2, idPremio);
                        ps.executeUpdate();
                    }
                    registrarResgate(con, colsResgate, idCartao, idPremio, qtd, subtotais[i]);
                }

                con.commit();
                return saldo - totalGeral;
            } catch (SQLException | CompraException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Registra o resgate usando só as colunas que existem na tabela. */
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
        StringBuilder sql = new StringBuilder("INSERT INTO resgates (");
        StringBuilder interrogacoes = new StringBuilder();
        for (int i = 0; i < nomes.size(); i++) {
            if (i > 0) {
                sql.append(", ");
                interrogacoes.append(", ");
            }
            sql.append(nomes.get(i));
            interrogacoes.append("?");
        }
        sql.append(") VALUES (").append(interrogacoes).append(")");
        try (PreparedStatement ps = con.prepareStatement(sql.toString())) {
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
     * Garante a tabela resgates e a coluna Quantidade. Devolve as colunas
     * existentes (minúsculas -> nome real).
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

    // ---- foto do prêmio ----
    private static void garantirColunaFoto(Connection con) throws SQLException {
        if (colunaFotoPronta)
            return;
        boolean existe;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND LOWER(TABLE_NAME) = 'premios' "
                        + "AND COLUMN_NAME = 'Foto'");
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

    /**
     * Reduz a imagem (máx. 400 px) e converte para JPEG, para não pesar no banco.
     */
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

    // ---- componentes visuais do cardápio ----
    /** Painel que acompanha a largura da área visível do scroll. */
    private static class PainelRolavel extends JPanel implements Scrollable {
        PainelRolavel(java.awt.LayoutManager lm) {
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

    /** Foto do prêmio (ou uma inicial, quando não tem foto). */
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
            int w = getWidth();
            int h = getHeight();
            Shape forma = new RoundRectangle2D.Float(0, 0, w, h, 14, 14);
            Shape clipOriginal = g2.getClip();
            g2.clip(forma);
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
                int tw = g2.getFontMetrics().stringWidth(letra);
                int ty = (h + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2;
                g2.drawString(letra, (w - tw) / 2, ty);
            }
            g2.setClip(clipOriginal);
            g2.setColor(BORDA);
            g2.draw(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, 14, 14));
            g2.dispose();
        }
    }

    /**
     * Um quadrado do cardápio: foto, nome, preço e seletor de quantidade (+ / -).
     */
    private class CartaoPremio extends JPanel {
        private final PremioItem item;
        private int qtd = 0;
        private final JLabel lblQtd = new JLabel("0", SwingConstants.CENTER);
        private final JLabel lblTotalCard = new JLabel();

        CartaoPremio(PremioItem item) {
            this.item = item;
            setOpaque(false);
            setLayout(new BorderLayout(14, 0));
            setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 14));
            setPreferredSize(new Dimension(LARGURA_MIN_CARD, 200));

            add(new FotoPremio(item), BorderLayout.WEST);

            JLabel lblNome = new JLabel(item.nome);
            lblNome.setFont(new Font("Segoe UI", Font.BOLD, 15));
            lblNome.setForeground(Color.WHITE);
            lblNome.setToolTipText(item.nome);

            JLabel lblPreco = new JLabel(fmt(item.valor) + " pontos");
            lblPreco.setFont(new Font("Segoe UI", Font.BOLD, 13));
            lblPreco.setForeground(new Color(186, 230, 253));

            boolean esgotado = item.estoque <= 0;
            JLabel lblEstoque = new JLabel(esgotado ? "Esgotado" : "Estoque: " + item.estoque);
            lblEstoque.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            lblEstoque.setForeground(esgotado ? new Color(248, 113, 113) : TEXTO_SUAVE);

            JButton btnMenos = criarBotao("-", false, 40);
            JButton btnMais = criarBotao("+", true, 40);
            btnMenos.setPreferredSize(new Dimension(40, 34));
            btnMais.setPreferredSize(new Dimension(40, 34));
            btnMenos.setMargin(new java.awt.Insets(0, 0, 0, 0));
            btnMais.setMargin(new java.awt.Insets(0, 0, 0, 0));

            lblQtd.setFont(new Font("Segoe UI", Font.BOLD, 15));
            lblQtd.setForeground(Color.WHITE);
            lblQtd.setPreferredSize(new Dimension(36, 34));

            lblTotalCard.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            lblTotalCard.setForeground(TEXTO_SUAVE);

            JButton btnComprar = criarBotao("Comprar", true, 110);
            btnComprar.setPreferredSize(new Dimension(110, 34));
            IconeCarrinho btnSalvar = new IconeCarrinho(false);
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
                btnMais.setEnabled(false);
                btnMenos.setEnabled(false);
                btnComprar.setEnabled(false);
                btnSalvar.setEnabled(false);
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
            for (JPanel p : new JPanel[] { linhaQtd, linhaComprar })
                p.setAlignmentX(LEFT_ALIGNMENT);
            for (JLabel l : new JLabel[] { lblNome, lblPreco, lblEstoque, lblTotalCard })
                l.setAlignmentX(LEFT_ALIGNMENT);

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

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(CARD);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            g2.setColor(BORDA);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
            g2.dispose();
        }
    }

    // ---------------------------------------------------------
    // ABAS (mesmo design da tela de Recargas)
    // ---------------------------------------------------------
    private static final String[] NOMES_CARDS = { "tabela", "catalogo", "registro" };
    private JButton[] abas = new JButton[3];
    private int abaAtual = 0;
    private IconeCarrinho btnCarrinho;

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

    private void mostrarAba(int i) {
        abaAtual = i;
        modoLista = (i == 1);
        ((java.awt.CardLayout) conteudo.getLayout()).show(conteudo, NOMES_CARDS[i]);
        for (int k = 0; k < abas.length; k++)
            estilizarAba(abas[k], k == i);
        btnNovo.setVisible(i == 0);
        btnEditar.setVisible(i == 0);
        btnCarrinho.setVisible(i == 1);
        if (i == 1)
            carregarCatalogo();
        if (i == 2)
            carregarRegistro();
        atualizarTotal();
    }

    // ---------------------------------------------------------
    // REGISTRO DE COMPRAS
    // ---------------------------------------------------------
    private JTable tabelaRegistro;
    private DefaultTableModel modeloRegistro;
    private TableRowSorter<DefaultTableModel> sorterRegistro;
    private JPanel cardRegistro;
    private GhostPanel ghostRegistro;
    private boolean carregandoRegistro = false;
    private int cargaRegistroId = 0;
    private static final java.time.format.DateTimeFormatter DATA_HORA = java.time.format.DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm");

    private JPanel criarPainelRegistro() {
        String[] colunas = { "ID Cartão", "Nome do Usuário", "Prêmio", "Preço (Pontos)", "Data/Hora" };
        modeloRegistro = new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        tabelaRegistro = new JTable(modeloRegistro);
        tabelaRegistro.setSelectionBackground(new Color(23, 55, 94));
        tabelaRegistro.setSelectionForeground(Color.WHITE);
        sorterRegistro = new TableRowSorter<>(modeloRegistro);
        sorterRegistro.setSortable(4, false); // data é texto: mantém a ordem do banco (mais recente primeiro)
        tabelaRegistro.setRowSorter(sorterRegistro);
        tabelaRegistro.getTableHeader().setReorderingAllowed(false);
        tabelaRegistro.setBackground(CARD);
        tabelaRegistro.setForeground(Color.WHITE);
        tabelaRegistro.setGridColor(new Color(18, 35, 60));
        tabelaRegistro.setRowHeight(42);
        tabelaRegistro.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        tabelaRegistro.getTableHeader().setBackground(CARD);
        tabelaRegistro.getTableHeader().setForeground(TEXTO_SUAVE);
        tabelaRegistro.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabelaRegistro.getTableHeader().setPreferredSize(new Dimension(0, 40));

        DefaultTableCellRenderer centro = new DefaultTableCellRenderer();
        centro.setHorizontalAlignment(SwingConstants.CENTER);
        centro.setBackground(CARD);
        centro.setForeground(new Color(186, 230, 253));
        DefaultTableCellRenderer esquerda = new DefaultTableCellRenderer();
        esquerda.setHorizontalAlignment(SwingConstants.LEFT);
        esquerda.setBackground(CARD);
        esquerda.setForeground(Color.WHITE);
        tabelaRegistro.getColumnModel().getColumn(0).setCellRenderer(centro);
        tabelaRegistro.getColumnModel().getColumn(1).setCellRenderer(esquerda);
        tabelaRegistro.getColumnModel().getColumn(2).setCellRenderer(esquerda);
        tabelaRegistro.getColumnModel().getColumn(3).setCellRenderer(centro);
        tabelaRegistro.getColumnModel().getColumn(4).setCellRenderer(centro);

        ghostRegistro = new GhostPanel(5);
        cardRegistro = new JPanel(new java.awt.CardLayout()) {
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
        cardRegistro.setOpaque(false);
        cardRegistro.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        JScrollPane scroll = new JScrollPane(tabelaRegistro);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        cardRegistro.add(scroll, "tabela");
        cardRegistro.add(ghostRegistro, "ghost");
        return cardRegistro;
    }

    private void carregarRegistro() {
        final int idCarga = ++cargaRegistroId;
        carregandoRegistro = true;
        ((java.awt.CardLayout) cardRegistro.getLayout()).show(cardRegistro, "ghost");
        ghostRegistro.iniciar();
        atualizarTotal();

        async(() -> {
            List<Object[]> linhas = new ArrayList<>();
            try (Connection con = conectar()) {
                garantirTabelaResgates(con);
                String sql = "SELECT r.`ID_Cartão` AS id_cartao, cl.Nome_Completo AS nome, p.Nome AS premio, "
                        + "r.Quantidade AS qtd, r.Pontos_Gastos AS preco, r.Data_Hora AS data_hora "
                        + "FROM resgates r "
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
                        java.sql.Timestamp ts = rs.getTimestamp("data_hora");
                        linhas.add(new Object[] {
                                String.format("%03d", rs.getLong("id_cartao")),
                                rs.getString("nome") == null ? "—" : rs.getString("nome"),
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
            modeloRegistro.setRowCount(0);
            for (Object[] l : linhas)
                modeloRegistro.addRow(l);
            carregandoRegistro = false;
            ((java.awt.CardLayout) cardRegistro.getLayout()).show(cardRegistro, "tabela");
            ghostRegistro.parar();
            atualizarTotal();
        }, ex -> {
            if (idCarga != cargaRegistroId)
                return;
            carregandoRegistro = false;
            ((java.awt.CardLayout) cardRegistro.getLayout()).show(cardRegistro, "tabela");
            ghostRegistro.parar();
            atualizarTotal();
            JOptionPane.showMessageDialog(this, "Erro ao carregar o registro de compras:\n" + ex.getMessage(),
                    "Erro", JOptionPane.ERROR_MESSAGE);
        });
    }

    // ---------------------------------------------------------
    // CARRINHO
    // ---------------------------------------------------------
    private static class ItemCarrinho {
        PremioItem premio;
        int qtd;

        ItemCarrinho(PremioItem premio, int qtd) {
            this.premio = premio;
            this.qtd = qtd;
        }
    }

    private final List<ItemCarrinho> carrinho = new ArrayList<>();

    private void atualizarBadgeCarrinho() {
        int n = 0;
        for (ItemCarrinho it : carrinho)
            n += it.qtd;
        btnCarrinho.setBadge(n);
    }

    /**
     * Depois de recarregar o catálogo: atualiza preços/estoque dos itens salvos e
     * tira os que acabaram.
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

    private boolean adicionarAoCarrinho(PremioItem item, int qtd) {
        if (qtd <= 0) {
            aviso("Escolha a quantidade com o botão + antes de salvar no carrinho.");
            return false;
        }
        ItemCarrinho existente = null;
        for (ItemCarrinho it : carrinho)
            if (it.premio.id == item.id)
                existente = it;
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
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
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

        JLabel lblTotalCar = new JLabel();
        lblTotalCar.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTotalCar.setForeground(Color.WHITE);
        JLabel lblSaldoCar = new JLabel();
        lblSaldoCar.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblSaldoCar.setForeground(TEXTO_SUAVE);

        Runnable[] redesenhar = new Runnable[1];
        redesenhar[0] = () -> {
            lista.removeAll();
            long total = 0;
            if (carrinho.isEmpty()) {
                JLabel vazio = new JLabel("O carrinho está vazio.");
                vazio.setForeground(TEXTO_SUAVE);
                vazio.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                lista.add(vazio);
            }
            for (ItemCarrinho it : new ArrayList<>(carrinho)) {
                long sub = (long) it.qtd * it.premio.valor;
                total += sub;
                JPanel linha = new JPanel(new BorderLayout(10, 0)) {
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
                linha.setOpaque(false);
                linha.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
                linha.setPreferredSize(new Dimension(0, 62));
                linha.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
                linha.setAlignmentX(Component.LEFT_ALIGNMENT);

                JLabel nome = new JLabel(it.premio.nome);
                nome.setFont(new Font("Segoe UI", Font.BOLD, 14));
                nome.setForeground(Color.WHITE);
                JLabel detalhe = new JLabel(it.qtd + "x  " + fmt(it.premio.valor) + " pontos");
                detalhe.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                detalhe.setForeground(TEXTO_SUAVE);
                JPanel textos = new JPanel(new GridLayout(2, 1));
                textos.setOpaque(false);
                textos.add(nome);
                textos.add(detalhe);

                JLabel lblSub = new JLabel(fmt(sub) + " pts");
                lblSub.setFont(new Font("Segoe UI", Font.BOLD, 14));
                lblSub.setForeground(new Color(186, 230, 253));
                JButton remover = criarBotao("Remover", false, 90);
                remover.setPreferredSize(new Dimension(90, 32));
                remover.addActionListener(e -> {
                    carrinho.remove(it);
                    atualizarBadgeCarrinho();
                    redesenhar[0].run();
                });
                JPanel direita = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 8));
                direita.setOpaque(false);
                direita.add(lblSub);
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
        };
        redesenhar[0].run();

        JPanel resumo = new JPanel(new GridLayout(2, 1, 0, 2));
        resumo.setOpaque(false);
        resumo.add(lblTotalCar);
        resumo.add(lblSaldoCar);

        JButton btnEsvaziar = criarBotao("Esvaziar", false, 100);
        btnEsvaziar.addActionListener(e -> {
            carrinho.clear();
            atualizarBadgeCarrinho();
            redesenhar[0].run();
        });
        JButton btnFechar = criarBotao("Fechar", false, 100);
        btnFechar.addActionListener(e -> dlg.dispose());
        JButton btnFinalizar = criarBotao("Finalizar compra", true, 160);
        btnFinalizar.addActionListener(e -> finalizarCarrinho(dlg));

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botoes.setOpaque(false);
        botoes.add(btnEsvaziar);
        botoes.add(btnFechar);
        botoes.add(btnFinalizar);

        JPanel rodape = new JPanel(new BorderLayout(10, 0));
        rodape.setBackground(CARD);
        rodape.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDA),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        rodape.add(resumo, BorderLayout.WEST);
        rodape.add(botoes, BorderLayout.EAST);

        dlg.add(scroll, BorderLayout.CENTER);
        dlg.add(rodape, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    private void finalizarCarrinho(JDialog dlg) {
        if (carrinho.isEmpty()) {
            JOptionPane.showMessageDialog(dlg, "O carrinho está vazio.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (cartaoId < 0) {
            JOptionPane.showMessageDialog(dlg, "Selecione um cartão antes de finalizar a compra.", "Aviso",
                    JOptionPane.WARNING_MESSAGE);
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
        int r = JOptionPane.showOptionDialog(dlg,
                "Tem certeza que quer fazer a compra?\n\n"
                        + carrinho.size() + (carrinho.size() == 1 ? " prêmio" : " prêmios") + " no carrinho\n"
                        + "Total: " + fmt(total) + " pontos\n"
                        + "Saldo após a compra: " + fmt(saldoCartao - total) + " pontos",
                "Confirmar compra", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                new Object[] { "Sim", "Não" }, "Não");
        if (r != 0)
            return;

        final long idCartao = cartaoId;
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

    /** Botão de carrinho desenhado (sem imagem), com contador de itens. */
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
            int w = getWidth();
            int h = getHeight();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g2.setColor(grande ? AZUL : new Color(23, 42, 69));
            g2.fillRoundRect(0, 0, w, h, 8, 8);

            double esc = Math.min(w, h) * 0.6 / 24.0;
            g2.translate((w - 24 * esc) / 2.0, (h - 24 * esc) / 2.0);
            g2.scale(esc, esc);
            g2.setColor(isEnabled() ? Color.WHITE : new Color(160, 170, 185));
            g2.setStroke(
                    new java.awt.BasicStroke(2.2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
            p.moveTo(1, 3);
            p.lineTo(4, 3);
            p.lineTo(6.5f, 15);
            p.lineTo(19, 15);
            p.lineTo(21.5f, 6);
            p.lineTo(5, 6);
            g2.draw(p);
            g2.fill(new java.awt.geom.Ellipse2D.Float(7.2f, 18.2f, 3.6f, 3.6f));
            g2.fill(new java.awt.geom.Ellipse2D.Float(15.2f, 18.2f, 3.6f, 3.6f));
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
                int tw = g3.getFontMetrics().stringWidth(t);
                g3.drawString(t, w - d - 1 + (d - tw) / 2,
                        1 + (d + g3.getFontMetrics().getAscent() - g3.getFontMetrics().getDescent()) / 2);
                g3.dispose();
            }
        }
    }

    // ---------------------------------------------------------
    // HELPERS VISUAIS (Idênticos às outras telas)
    // ---------------------------------------------------------
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
        painel.setPreferredSize(new Dimension(360, 38));
        painel.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));

        JLabel lupa = new JLabel("🔍");
        lupa.setForeground(TEXTO_SUAVE);

        JTextField txt = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g.create();
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
        txt.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                txt.repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
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

    private JTextField criarCampoTexto() {
        JTextField txt = new JTextField();
        txt.setBackground(FUNDO);
        txt.setForeground(Color.WHITE);
        txt.setCaretColor(Color.WHITE);
        txt.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txt.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 48, 80), 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return txt;
    }

    private JLabel criarLabel(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setForeground(new Color(226, 232, 240));
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return lbl;
    }

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

    // Ghost Loading visual
    private static class GhostPanel extends JPanel {
        private static final Color BASE = new Color(20, 36, 60);
        private static final Color BRILHO = new Color(38, 64, 102);
        private static final int ALTURA_CABECALHO = 40;
        private static final int ALTURA_LINHA = 42;

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

            int w = getWidth();
            int h = getHeight();
            int celula = Math.max(1, w / colunas);
            int linhas = Math.max(1, (h - ALTURA_CABECALHO) / ALTURA_LINHA);

            g2.setColor(new Color(18, 35, 60));
            g2.drawLine(0, ALTURA_CABECALHO, w, ALTURA_CABECALHO);
            for (int r = 0; r < linhas; r++) {
                int y = ALTURA_CABECALHO + (r + 1) * ALTURA_LINHA - 1;
                g2.drawLine(0, y, w, y);
            }

            float cx = fase * w;
            float meia = Math.max(180f, w * 0.22f);
            g2.setPaint(new LinearGradientPaint(
                    new Point2D.Float(cx - meia, 0), new Point2D.Float(cx + meia, 0),
                    new float[] { 0f, 0.5f, 1f }, new Color[] { BASE, BRILHO, BASE }));

            for (int c = 0; c < colunas; c++) {
                int bw = (int) (celula * 0.34);
                g2.fillRoundRect(c * celula + (celula - bw) / 2, (ALTURA_CABECALHO - 10) / 2, bw, 10, 8, 8);
            }

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
}