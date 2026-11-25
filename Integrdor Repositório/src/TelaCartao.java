import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.MaskFormatter;

/**
 * Tela de Cartões: lista todos os cartões do arcade (cliente dono, usuário,
 * créditos, pontos e status) e permite cadastrar, editar, excluir e buscar.
 *
 * Como funciona: os dados ficam na tabela `cartões` do MySQL. A lista é lida em
 * segundo plano (a tela não trava) e o App chama atualizarSilenciosamente() a
 * cada 3s para refletir mudanças feitas em outros lugares (ex.: recargas).
 * Recebe a TelaClientes para conferir se o cliente informado existe e mostrar o nome dele.
 */
public class TelaCartao extends JPanel {

    // =========================================================================
    // CONSTANTES
    // Textos fixos e o SELECT que traz os cartões (com o nome do cliente via JOIN).
    // Nomes com acento no banco ficam entre crases: `cartões`, `ID_Cartão`, `Usuário`.
    // =========================================================================

    private static final String PLACEHOLDER_BUSCA = "Buscar por ID, cliente ou usuário...";

    private static final String SQL_CARTOES = "SELECT c.`ID_Cartão` AS id_cartao, c.ID_Cliente AS id_cliente, "
            + "cl.Nome_Completo AS nome_cliente, c.`Usuário` AS usuario, "
            + "c.Creditos AS creditos, c.Pontos AS pontos, c.Ativo AS ativo "
            + "FROM `cartões` c "
            + "JOIN clientes cl ON cl.ID_Cliente = c.ID_Cliente "
            + "ORDER BY c.`ID_Cartão`";

    // =========================================================================
    // CAMPOS
    // Componentes da tela e o controle da busca em segundo plano.
    // =========================================================================

    private JTable tabela;
    private DefaultTableModel modeloTabela;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField txtBuscar;
    private JLabel lblTotalCartoes;

    private final TelaClientes telaClientes;

    /** true enquanto uma busca no banco está em andamento (evita buscas sobrepostas). */
    private boolean carregando = false;
    /** true se pediram uma nova busca enquanto outra ainda corria: ela é refeita ao terminar. */
    private boolean recarregarDepois = false;

    // =========================================================================
    // CONSTRUTOR
    // Monta a tela em três partes (topo, tabela, rodapé) e carrega os cartões.
    // =========================================================================

    public TelaCartao(TelaClientes telaClientes) {
        this.telaClientes = telaClientes;
        setBackground(new Color(2, 12, 27)); // Fundo azul marinho escuro
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25)); // Margens ajustadas para não cortar o rodapé

        add(criarTopo(), BorderLayout.NORTH);
        add(criarTabela(), BorderLayout.CENTER);
        add(criarRodape(), BorderLayout.SOUTH);

        // Carrega os cartões do banco (também atualiza o "Total: X cartões")
        carregarCartoes(true);
    }

    // =========================================================================
    // MONTAGEM DA INTERFACE
    // Topo (título, busca e botões), tabela de cartões e rodapé com o total.
    // =========================================================================

    /** Título "Cartões", campo de busca e os botões Editar / Excluir / + Novo Cartão. */
    private JPanel criarTopo() {
        JPanel painelTopoGeral = new JPanel(new BorderLayout(0, 15));
        painelTopoGeral.setOpaque(false);

        JLabel lblTitulo = new JLabel("Cartões");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitulo.setForeground(Color.WHITE);

        JPanel painelAcoes = new JPanel(new BorderLayout(15, 0));
        painelAcoes.setOpaque(false);
        painelAcoes.add(criarBusca(), BorderLayout.WEST);

        JButton btnEditar = criarBotaoEditar();
        btnEditar.addActionListener(e -> {
            int linha = linhaSelecionadaNoModelo();
            if (linha >= 0) {
                abrirModal(linha);
            }
        });

        JButton btnExcluir = Exclusao.criarBotao();
        btnExcluir.addActionListener(e -> excluirSelecionado());

        JPanel painelBotoesTopo = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        painelBotoesTopo.setOpaque(false);
        painelBotoesTopo.add(btnEditar);
        painelBotoesTopo.add(btnExcluir);
        painelBotoesTopo.add(criarBotaoNovo());
        painelAcoes.add(painelBotoesTopo, BorderLayout.EAST);

        painelTopoGeral.add(lblTitulo, BorderLayout.NORTH);
        painelTopoGeral.add(painelAcoes, BorderLayout.CENTER);
        return painelTopoGeral;
    }

    /** Caixa de busca arredondada: filtra a tabela a cada tecla (ID, cliente ou usuário). */
    private JPanel criarBusca() {
        JPanel painelBusca = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(13, 27, 46));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(30, 48, 80));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
            }
        };
        painelBusca.setOpaque(false);
        painelBusca.setPreferredSize(new Dimension(360, 38));
        painelBusca.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));

        JLabel lblLupa = new JLabel("🔍");
        lblLupa.setForeground(new Color(148, 163, 184));

        txtBuscar = new JTextField(PLACEHOLDER_BUSCA);
        txtBuscar.setOpaque(false);
        txtBuscar.setForeground(new Color(100, 116, 139));
        txtBuscar.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtBuscar.setBorder(null);

        // Texto de exemplo ("placeholder"): some ao clicar e volta se o campo ficar vazio
        txtBuscar.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent evt) {
                if (txtBuscar.getText().equals(PLACEHOLDER_BUSCA)) {
                    txtBuscar.setText("");
                    txtBuscar.setForeground(Color.WHITE);
                }
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                if (txtBuscar.getText().isEmpty()) {
                    txtBuscar.setForeground(new Color(100, 116, 139));
                    txtBuscar.setText(PLACEHOLDER_BUSCA);
                }
            }
        });

        txtBuscar.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String texto = txtBuscar.getText();
                if (texto.equals(PLACEHOLDER_BUSCA) || texto.trim().isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(texto.trim())));
                }
                atualizarTotal();
            }
        });

        painelBusca.add(lblLupa, BorderLayout.WEST);
        painelBusca.add(txtBuscar, BorderLayout.CENTER);
        return painelBusca;
    }

    /** Botão azul "+ Novo Cartão": abre o formulário vazio. */
    private JButton criarBotaoNovo() {
        JButton btnNovoCartao = new JButton("+ Novo Cartão") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(14, 116, 233));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btnNovoCartao.setPreferredSize(new Dimension(140, 38));
        btnNovoCartao.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnNovoCartao.setForeground(Color.WHITE);
        btnNovoCartao.setFocusPainted(false);
        btnNovoCartao.setContentAreaFilled(false);
        btnNovoCartao.setBorderPainted(false);
        btnNovoCartao.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnNovoCartao.addActionListener(e -> abrirModal(-1));
        return btnNovoCartao;
    }

    /** Botão de lápis azul (ícone desenhado em código, sem depender de imagem/fonte). */
    private JButton criarBotaoEditar() {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? new Color(20, 40, 68) : new Color(13, 27, 46));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(30, 48, 80));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);

                g2.translate(getWidth() / 2.0, getHeight() / 2.0);
                g2.rotate(Math.toRadians(45));
                g2.setColor(new Color(147, 197, 253)); // borracha
                g2.fillRoundRect(-3, -11, 6, 3, 2, 2);
                g2.setColor(new Color(14, 116, 233)); // corpo
                g2.fillRect(-3, -8, 6, 12);
                g2.setColor(new Color(56, 160, 255)); // ponta
                g2.fillPolygon(new int[] { -3, 3, 0 }, new int[] { 4, 4, 10 }, 3);
                g2.setColor(Color.WHITE); // grafite
                g2.fillPolygon(new int[] { -1, 1, 0 }, new int[] { 8, 8, 10 }, 3);
                g2.dispose();
            }
        };
        btn.setPreferredSize(new Dimension(38, 38));
        btn.setToolTipText("Editar selecionado");
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setRolloverEnabled(true);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** Card arredondado com a tabela de cartões (não editável, colunas fixas, ordenável). */
    private JPanel criarTabela() {
        JPanel painelCardTabela = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(10, 22, 40));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(new Color(23, 42, 69));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
            }
        };
        painelCardTabela.setOpaque(false);
        painelCardTabela.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        String[] colunas = { "ID Cartão ", "ID Cliente ", "Cliente ", "Usuário ", "Crédito ", "Pontos ", "Status " };

        modeloTabela = new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tabela = new JTable(modeloTabela);
        tabela.setSelectionBackground(new Color(23, 55, 94));
        tabela.setSelectionForeground(Color.WHITE);
        sorter = new TableRowSorter<>(modeloTabela);
        tabela.setRowSorter(sorter);

        // Impede que o usuário arraste as colunas
        tabela.getTableHeader().setReorderingAllowed(false);

        // Cores e fontes da tabela e do cabeçalho
        tabela.setBackground(new Color(10, 22, 40));
        tabela.setForeground(new Color(226, 232, 240));
        tabela.setGridColor(new Color(18, 35, 60));
        tabela.setRowHeight(42);
        tabela.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        tabela.getTableHeader().setBackground(new Color(10, 22, 40));
        tabela.getTableHeader().setForeground(new Color(148, 163, 184));
        tabela.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabela.getTableHeader().setPreferredSize(new Dimension(0, 40));

        // Números centralizados (azul claro); nome do cliente e usuário à esquerda (branco)
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        centerRenderer.setBackground(new Color(10, 22, 40));
        centerRenderer.setForeground(new Color(186, 230, 253));

        DefaultTableCellRenderer leftRenderer = new DefaultTableCellRenderer();
        leftRenderer.setHorizontalAlignment(SwingConstants.LEFT);
        leftRenderer.setBackground(new Color(10, 22, 40));
        leftRenderer.setForeground(Color.WHITE);

        for (int c = 0; c < colunas.length; c++) {
            boolean texto = c == 2 || c == 3;
            tabela.getColumnModel().getColumn(c).setCellRenderer(texto ? leftRenderer : centerRenderer);
        }

        JScrollPane scrollPane = new JScrollPane(tabela);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);

        painelCardTabela.add(scrollPane, BorderLayout.CENTER);
        return painelCardTabela;
    }

    /** Rodapé com o "Total: X cartões". */
    private JPanel criarRodape() {
        JPanel painelRodape = new JPanel(new BorderLayout());
        painelRodape.setOpaque(false);
        painelRodape.setPreferredSize(new Dimension(0, 35));

        lblTotalCartoes = new JLabel();
        lblTotalCartoes.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTotalCartoes.setForeground(new Color(148, 163, 184));

        JPanel painelPaginacao = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        painelPaginacao.setOpaque(false);

        painelRodape.add(lblTotalCartoes, BorderLayout.WEST);
        painelRodape.add(painelPaginacao, BorderLayout.EAST);
        return painelRodape;
    }

    // =========================================================================
    // MÉTODOS CHAMADOS PELO APP
    // Recarregar ao abrir a tela, atualização automática e nomes de clientes.
    // =========================================================================

    /** Atualiza o nome do cliente em cada cartão (caso o cliente tenha sido editado). */
    public void atualizarNomesClientes() {
        for (int i = 0; i < modeloTabela.getRowCount(); i++) {
            String nome = telaClientes.buscarNomePorId(String.valueOf(modeloTabela.getValueAt(i, 1)));
            if (nome != null) {
                modeloTabela.setValueAt(nome, i, 2);
            }
        }
    }

    /** Recarrega a tabela a partir do banco (em segundo plano). Chamado ao abrir a tela. */
    public void recarregar() {
        carregarCartoes(true);
    }

    /** Atualização em tempo real: busca em segundo plano, sem mensagem de erro e sem piscar se nada mudou. */
    public void atualizarSilenciosamente() {
        carregarCartoes(false);
    }

    // =========================================================================
    // CARREGAMENTO DOS DADOS (BANCO -> TABELA)
    // SELECT em segundo plano; a tabela só é trocada se algo mudou.
    // =========================================================================

    /** Conexao.conectar() devolve null quando falha; aqui vira exceção com mensagem clara. */
    private Connection conectar() throws SQLException {
        Connection con = Conexao.conectar();
        if (con == null) {
            throw new SQLException(
                    "Não foi possível conectar ao MySQL. Verifique usuário, senha e se o servidor está ligado.");
        }
        return con;
    }

    /**
     * SELECT de todos os cartões (com o nome do cliente via JOIN), feito em segundo plano.
     * A interface não trava esperando o banco.
     */
    private void carregarCartoes(boolean mostrarErro) {
        if (carregando) {
            recarregarDepois = true; // um pedido (ex.: clique em Cartões) não pode se perder
            return;
        }
        carregando = true;

        new SwingWorker<List<Object[]>, Void>() {
            @Override
            protected List<Object[]> doInBackground() throws Exception {
                List<Object[]> linhas = new ArrayList<>();
                try (Connection con = conectar();
                        PreparedStatement ps = con.prepareStatement(SQL_CARTOES);
                        ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        linhas.add(new Object[] {
                                String.format("%03d", rs.getLong("id_cartao")),
                                String.format("%03d", rs.getLong("id_cliente")),
                                rs.getString("nome_cliente"),
                                rs.getString("usuario"),
                                rs.getLong("creditos"),
                                rs.getLong("pontos"),
                                rs.getBoolean("ativo") ? "Ativo" : "Inativo"
                        });
                    }
                }
                return linhas;
            }

            @Override
            protected void done() {
                carregando = false;
                try {
                    aplicarLinhas(get());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ee) {
                    String msg = ee.getCause() != null ? ee.getCause().getMessage() : ee.getMessage();
                    if (mostrarErro) {
                        JOptionPane.showMessageDialog(TelaCartao.this,
                                "Não foi possível carregar os cartões do banco de dados:\n" + msg,
                                "Erro de banco de dados", JOptionPane.ERROR_MESSAGE);
                    } else {
                        System.err.println("Atualização dos cartões falhou: " + msg);
                    }
                }
                if (recarregarDepois) {
                    recarregarDepois = false;
                    carregarCartoes(true);
                }
            }
        }.execute();
    }

    /** Troca as linhas da tabela só se algo mudou, mantendo a linha selecionada. */
    private void aplicarLinhas(List<Object[]> novas) {
        if (mesmasLinhas(novas)) {
            return;
        }

        String idSelecionado = null;
        int visual = tabela.getSelectedRow();
        if (visual >= 0) {
            idSelecionado = String.valueOf(modeloTabela.getValueAt(tabela.convertRowIndexToModel(visual), 0));
        }

        modeloTabela.setRowCount(0);
        for (Object[] linha : novas) {
            modeloTabela.addRow(linha);
        }

        if (idSelecionado != null) {
            for (int m = 0; m < modeloTabela.getRowCount(); m++) {
                if (idSelecionado.equals(modeloTabela.getValueAt(m, 0))) {
                    int v = tabela.convertRowIndexToView(m);
                    tabela.setRowSelectionInterval(v, v);
                    break;
                }
            }
        }
        atualizarTotal();
    }

    /** Compara as linhas novas com as que já estão na tabela, célula por célula. */
    private boolean mesmasLinhas(List<Object[]> novas) {
        if (novas.size() != modeloTabela.getRowCount()) {
            return false;
        }
        int colunas = modeloTabela.getColumnCount();
        for (int r = 0; r < novas.size(); r++) {
            Object[] atual = new Object[colunas];
            for (int c = 0; c < colunas; c++) {
                atual[c] = modeloTabela.getValueAt(r, c);
            }
            if (!Arrays.equals(atual, novas.get(r))) {
                return false;
            }
        }
        return true;
    }

    // =========================================================================
    // GRAVAÇÃO NO BANCO (INSERT / UPDATE / DELETE)
    // =========================================================================

    /** INSERT de um novo cartão. Retorna o ID gerado pelo banco (AUTO_INCREMENT). */
    private long inserirCartao(long idCliente, String usuario, long credito, long pontos, boolean ativo)
            throws SQLException {
        String sql = "INSERT INTO `cartões` (ID_Cliente, `Usuário`, Creditos, Pontos, Ativo) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, idCliente);
            ps.setString(2, usuario);
            ps.setLong(3, credito);
            ps.setLong(4, pontos);
            ps.setBoolean(5, ativo);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    Auditoria.registrar("CADASTRO_CARTAO", String.format("Cartão %03d | cliente %03d | usuário %s", id, idCliente, usuario));
                    return id;
                }
            }
            throw new SQLException("O banco não retornou o ID do cartão cadastrado.");
        }
    }

    /** UPDATE de um cartão existente. */
    private void atualizarCartao(long idCartao, long idCliente, String usuario, long credito, long pontos,
            boolean ativo) throws SQLException {
        String sql = "UPDATE `cartões` SET ID_Cliente = ?, `Usuário` = ?, Creditos = ?, Pontos = ?, Ativo = ? "
                + "WHERE `ID_Cartão` = ?";

        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setLong(1, idCliente);
            ps.setString(2, usuario);
            ps.setLong(3, credito);
            ps.setLong(4, pontos);
            ps.setBoolean(5, ativo);
            ps.setLong(6, idCartao);
            ps.executeUpdate();
        }
        Auditoria.registrar("EDICAO_CARTAO", String.format("Cartão %03d | créditos %d | pontos %d | %s",
                idCartao, credito, pontos, ativo ? "ativo" : "inativo"));
    }

    /** Exclui o cartão selecionado (só se não tiver recargas, partidas ou resgates ligados a ele). */
    private void excluirSelecionado() {
        int linha = linhaSelecionadaNoModelo();
        if (linha < 0) {
            return;
        }
        if (!Exclusao.confirmar(this, "o cartão " + String.valueOf(modeloTabela.getValueAt(linha, 0)))) {
            return;
        }
        long id = idDaLinha(linha);
        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement("DELETE FROM `cartões` WHERE `ID_Cartão` = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            Exclusao.erro(this, ex);
            return;
        }
        Auditoria.registrar("EXCLUSAO_CARTAO", String.format("Cartão %03d", id));
        carregarCartoes(true);
    }

    /** Traduz erros comuns do MySQL para mensagens amigáveis. */
    private String mensagemDeErro(SQLException ex) {
        if (ex.getErrorCode() == 1062) { // valor duplicado (UNIQUE)
            return "Este usuário já está em uso por outro cartão. Escolha outro nome de usuário.";
        }
        if (ex.getErrorCode() == 1452) { // chave estrangeira (cliente inexistente)
            return "O cliente informado não existe no banco de dados.";
        }
        return "Erro ao salvar no banco de dados:\n" + ex.getMessage();
    }

    // =========================================================================
    // FORMULÁRIO DE CADASTRO / EDIÇÃO (JANELA MODAL)
    // Valida os campos, grava no banco e só então atualiza a tabela.
    // =========================================================================

    /** linhaModelo < 0 => novo cadastro; senão edita a linha indicada. */
    private void abrirModal(int linhaModelo) {
        boolean edicao = linhaModelo >= 0;
        java.awt.Window parentWindow = SwingUtilities.getWindowAncestor(this);
        JDialog modal = new JDialog(parentWindow, edicao ? "Editar Cartão" : "Cadastrar Novo Cartão",
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        modal.setSize(440, 520);
        modal.setLocationRelativeTo(parentWindow);
        modal.setLayout(new BorderLayout());

        JPanel painelForm = new JPanel(new GridLayout(10, 1, 2, 2));
        painelForm.setBackground(new Color(10, 22, 40));
        painelForm.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));

        // ---------- Campos ----------
        JFormattedTextField txtIdCliente = criarCampoFormatado("###");
        JTextField txtUsuario = criarCampoTexto();
        JTextField txtCredito = criarCampoTexto();
        txtCredito.setText("0");
        JTextField txtPontos = criarCampoTexto();
        txtPontos.setText("0");
        JComboBox<String> cmbStatus = new JComboBox<>(new String[] { "Ativo", "Inativo" });
        cmbStatus.setBackground(new Color(2, 12, 27));
        cmbStatus.setForeground(Color.WHITE);
        cmbStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        // Na edição, os campos já vêm preenchidos com os dados da linha
        if (edicao) {
            txtIdCliente.setText(String.valueOf(modeloTabela.getValueAt(linhaModelo, 1)));
            txtUsuario.setText(String.valueOf(modeloTabela.getValueAt(linhaModelo, 3)));
            txtCredito.setText(String.valueOf(modeloTabela.getValueAt(linhaModelo, 4)));
            txtPontos.setText(String.valueOf(modeloTabela.getValueAt(linhaModelo, 5)));
            cmbStatus.setSelectedItem(modeloTabela.getValueAt(linhaModelo, 6));
        }

        painelForm.add(criarLabel("ID do Cliente (ex.: 001):"));
        painelForm.add(txtIdCliente);
        painelForm.add(criarLabel("Usuário (máx. 50 caracteres):"));
        painelForm.add(txtUsuario);
        painelForm.add(criarLabel("Crédito:"));
        painelForm.add(txtCredito);
        painelForm.add(criarLabel("Pontos:"));
        painelForm.add(txtPontos);
        painelForm.add(criarLabel("Status:"));
        painelForm.add(cmbStatus);

        // ---------- Botões ----------
        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        painelBotoes.setBackground(new Color(2, 12, 27));

        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.setBackground(new Color(71, 85, 105));
        btnCancelar.setForeground(Color.WHITE);
        btnCancelar.setFocusPainted(false);
        btnCancelar.addActionListener(e -> modal.dispose());

        JButton btnSalvar = new JButton(edicao ? "Salvar Alterações" : "Salvar Cartão");
        btnSalvar.setBackground(new Color(14, 116, 233));
        btnSalvar.setForeground(Color.WHITE);
        btnSalvar.setFocusPainted(false);
        btnSalvar.addActionListener(e -> {
            String usuario = txtUsuario.getText().trim();
            long credito, pontos;
            String idCliente = txtIdCliente.getText();

            // ---------- Validações ----------
            if (idCliente.contains(" ")) {
                aviso(modal, "Digite o ID do Cliente com 3 dígitos (ex.: 001, 002).");
                return;
            }

            String nomeCliente = telaClientes.buscarNomePorId(idCliente);
            if (nomeCliente == null) {
                JOptionPane.showMessageDialog(modal,
                        "Não existe cliente cadastrado com o ID " + idCliente + ".", "Cliente não encontrado",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                credito = Long.parseLong(txtCredito.getText().trim());
                pontos = Long.parseLong(txtPontos.getText().trim());
            } catch (NumberFormatException ex) {
                aviso(modal, "Crédito e Pontos devem ser números inteiros.");
                return;
            }

            if (usuario.isEmpty() || usuario.length() > 50) {
                aviso(modal, "Informe o Usuário (até 50 caracteres).");
                return;
            }
            if (credito < 0 || pontos < 0) {
                aviso(modal, "Crédito e Pontos não podem ser negativos.");
                return;
            }

            long idClienteNum = Long.parseLong(idCliente.trim());
            String status = (String) cmbStatus.getSelectedItem();
            boolean ativo = "Ativo".equals(status);

            // ---------- Grava no banco; só mexe na tabela se o banco aceitou ----------
            try {
                if (edicao) {
                    atualizarCartao(idDaLinha(linhaModelo), idClienteNum, usuario, credito, pontos, ativo);

                    modeloTabela.setValueAt(String.format("%03d", idClienteNum), linhaModelo, 1);
                    modeloTabela.setValueAt(nomeCliente, linhaModelo, 2);
                    modeloTabela.setValueAt(usuario, linhaModelo, 3);
                    modeloTabela.setValueAt(credito, linhaModelo, 4);
                    modeloTabela.setValueAt(pontos, linhaModelo, 5);
                    modeloTabela.setValueAt(status, linhaModelo, 6);
                } else {
                    long novoId = inserirCartao(idClienteNum, usuario, credito, pontos, ativo);

                    modeloTabela.addRow(new Object[] {
                            String.format("%03d", novoId),
                            String.format("%03d", idClienteNum),
                            nomeCliente,
                            usuario,
                            credito,
                            pontos,
                            status
                    });
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(modal, mensagemDeErro(ex), "Erro", JOptionPane.ERROR_MESSAGE);
                return; // mantém o modal aberto para o usuário corrigir
            }

            atualizarTotal();
            JOptionPane.showMessageDialog(modal,
                    edicao ? "Cartão atualizado com sucesso!" : "Cartão cadastrado com sucesso!", "Sucesso",
                    JOptionPane.INFORMATION_MESSAGE);
            modal.dispose();
        });

        painelBotoes.add(btnCancelar);
        painelBotoes.add(btnSalvar);

        modal.add(painelForm, BorderLayout.CENTER);
        modal.add(painelBotoes, BorderLayout.SOUTH);
        modal.setVisible(true);
    }

    // =========================================================================
    // UTILITÁRIOS
    // Pequenas ajudas: total no rodapé, linha selecionada, ID da linha,
    // avisos e criação de labels/campos no estilo da tela.
    // =========================================================================

    /** Mostra no rodapé quantos cartões estão visíveis (respeita o filtro da busca). */
    private void atualizarTotal() {
        int count = tabela.getRowCount();
        lblTotalCartoes.setText("Total: " + count + " cartões");
    }

    /** Linha selecionada convertida para o índice do modelo (a tabela pode estar filtrada/ordenada). */
    private int linhaSelecionadaNoModelo() {
        int viewRow = tabela.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Selecione uma linha da tabela para editar.", "Aviso",
                    JOptionPane.WARNING_MESSAGE);
            return -1;
        }
        return tabela.convertRowIndexToModel(viewRow);
    }

    /** Lê o ID real (numérico) da linha do modelo: "003" -> 3. */
    private long idDaLinha(int linhaModelo) {
        return Long.parseLong(String.valueOf(modeloTabela.getValueAt(linhaModelo, 0)).trim());
    }

    /** Caixa de aviso amarela do formulário. */
    private void aviso(JDialog modal, String mensagem) {
        JOptionPane.showMessageDialog(modal, mensagem, "Aviso", JOptionPane.WARNING_MESSAGE);
    }

    private JLabel criarLabel(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setForeground(new Color(226, 232, 240));
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return lbl;
    }

    private JTextField criarCampoTexto() {
        return estilizarCampo(new JTextField());
    }

    /** Campo com máscara (ex.: "###" aceita só 3 dígitos). */
    private JFormattedTextField criarCampoFormatado(String mascara) {
        JFormattedTextField txt;
        try {
            MaskFormatter mf = new MaskFormatter(mascara);
            mf.setPlaceholderCharacter(' ');
            txt = new JFormattedTextField(mf);
        } catch (ParseException e) {
            txt = new JFormattedTextField();
        }
        return estilizarCampo(txt);
    }

    /** Fundo escuro, texto branco e borda azul usados em todos os campos do formulário. */
    private <T extends JTextField> T estilizarCampo(T txt) {
        txt.setBackground(new Color(2, 12, 27));
        txt.setForeground(Color.WHITE);
        txt.setCaretColor(Color.WHITE);
        txt.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txt.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 48, 80), 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return txt;
    }
}
