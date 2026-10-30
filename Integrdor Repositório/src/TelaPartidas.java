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
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
 
/**
 * Tela de Partidas (mesma estrutura de TelaMaquinas / TelaCartao).
 *
 * Tabela: ID da partida, Data/Hora, Nome, ID do cartão, Novo saldo (ex.: 1000
 * (-15)),
 * Máquina utilizada e Pontos ganhos.
 * Botão "Simular Partida": abre um modal (mesmo design dos outros) onde se
 * informa
 * o cartão e a máquina. Se o cartão tiver saldo, o valor da partida é debitado;
 * se não tiver, a tentativa aparece na tabela como "Saldo insuficiente".
 *
 * Os dados ficam na tabela `Partidas` do banco (ID_Partida, ID_Cartão,
 * ID_Máquina,
 * Data_Hora, Créditos_Gastos, Pontos_Obtidos).
 */
public class TelaPartidas extends JPanel {
 
    private static final String PLACEHOLDER_BUSCA = "Buscar por nome, cartão ou máquina...";
    private static final String SALDO_INSUFICIENTE = "Saldo insuficiente";
 
    private JTable tabela;
    private DefaultTableModel modeloTabela;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField txtBuscar;
    private JLabel lblTotalPartidas;
 
    // -----------------------------------------------------------------
    // CONEXÃO COM O BANCO
    // -----------------------------------------------------------------
    private Connection conectar() throws SQLException {
        Connection con = Conexao.conectar();
        if (con == null) {
            throw new SQLException("Não foi possível conectar ao banco de dados.");
        }
        return con;
    }
 
    public TelaPartidas() {
        setBackground(new Color(2, 12, 27)); // Fundo azul marinho escuro
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));
 
        // -----------------------------------------------------------------
        // 1. TOPO: Título, Busca e Botão "Simular Partida"
        // -----------------------------------------------------------------
        JPanel painelTopoGeral = new JPanel(new BorderLayout(0, 15));
        painelTopoGeral.setOpaque(false);
 
        JLabel lblTitulo = new JLabel("Partidas");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitulo.setForeground(Color.WHITE);
 
        JPanel painelAcoes = new JPanel(new BorderLayout(15, 0));
        painelAcoes.setOpaque(false);
 
        // Campo de Busca
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
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    System.exit(0);
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
                    // Pattern.quote evita erro se o usuário digitar caracteres como ( ) + . *
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(texto.trim())));
                }
                atualizarTotal();
            }
        });
 
        painelBusca.add(lblLupa, BorderLayout.WEST);
        painelBusca.add(txtBuscar, BorderLayout.CENTER);
 
        // Botão Simular Partida
        JButton btnSimular = new JButton("▶ Simular Partida") {
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
        btnSimular.setPreferredSize(new Dimension(170, 38));
        btnSimular.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSimular.setForeground(Color.WHITE);
        btnSimular.setFocusPainted(false);
        btnSimular.setContentAreaFilled(false);
        btnSimular.setBorderPainted(false);
        btnSimular.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnSimular.addActionListener(e -> abrirModal());
 
        painelAcoes.add(painelBusca, BorderLayout.WEST);
 
        JPanel painelBotoesTopo = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        painelBotoesTopo.setOpaque(false);
        painelBotoesTopo.add(btnSimular);
        painelAcoes.add(painelBotoesTopo, BorderLayout.EAST);
 
        painelTopoGeral.add(lblTitulo, BorderLayout.NORTH);
        painelTopoGeral.add(painelAcoes, BorderLayout.CENTER);
 
        add(painelTopoGeral, BorderLayout.NORTH);
 
        // -----------------------------------------------------------------
        // 2. TABELA DE PARTIDAS (Design Suave)
        // -----------------------------------------------------------------
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
 
        String[] colunas = { "ID Partida ", "Data/Hora ", "Nome ", "ID Cartão ", "Novo Saldo ", "Máquina Utilizada ",
                "Pontos Ganhos " };
 
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
 
        // IMPEDE QUE O USUÁRIO ARRASTE AS COLUNAS
        tabela.getTableHeader().setReorderingAllowed(false);
 
        // Estilização da tabela
        tabela.setBackground(new Color(10, 22, 40));
        tabela.setForeground(new Color(226, 232, 240));
        tabela.setGridColor(new Color(18, 35, 60));
        tabela.setRowHeight(42);
        tabela.setFont(new Font("Segoe UI", Font.PLAIN, 13));
 
        tabela.getTableHeader().setBackground(new Color(10, 22, 40));
        tabela.getTableHeader().setForeground(new Color(148, 163, 184));
        tabela.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabela.getTableHeader().setPreferredSize(new Dimension(0, 40));
 
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        centerRenderer.setBackground(new Color(10, 22, 40));
        centerRenderer.setForeground(new Color(186, 230, 253));
 
        DefaultTableCellRenderer leftRenderer = new DefaultTableCellRenderer();
        leftRenderer.setHorizontalAlignment(SwingConstants.LEFT);
        leftRenderer.setBackground(new Color(10, 22, 40));
        leftRenderer.setForeground(Color.WHITE);
 
        // Saldo: igual ao centralizado, mas "Saldo insuficiente" aparece em vermelho
        DefaultTableCellRenderer saldoRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                    boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                if (isSelected) {
                    c.setForeground(Color.WHITE);
                } else if (SALDO_INSUFICIENTE.equals(String.valueOf(value))) {
                    c.setForeground(new Color(248, 113, 113));
                } else {
                    c.setForeground(new Color(186, 230, 253));
                }
                return c;
            }
        };
        saldoRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        saldoRenderer.setBackground(new Color(10, 22, 40));
 
        tabela.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        tabela.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        tabela.getColumnModel().getColumn(2).setCellRenderer(leftRenderer);
        tabela.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        tabela.getColumnModel().getColumn(4).setCellRenderer(saldoRenderer);
        tabela.getColumnModel().getColumn(5).setCellRenderer(leftRenderer);
        tabela.getColumnModel().getColumn(6).setCellRenderer(centerRenderer);
 
        JScrollPane scrollPane = new JScrollPane(tabela);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);
 
        painelCardTabela.add(scrollPane, BorderLayout.CENTER);
        add(painelCardTabela, BorderLayout.CENTER);
 
        // -----------------------------------------------------------------
        // 3. RODAPÉ (TOTAL)
        // -----------------------------------------------------------------
        JPanel painelRodape = new JPanel(new BorderLayout());
        painelRodape.setOpaque(false);
        painelRodape.setPreferredSize(new Dimension(0, 35));
 
        lblTotalPartidas = new JLabel();
        lblTotalPartidas.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTotalPartidas.setForeground(new Color(148, 163, 184));
 
        JPanel painelPaginacao = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        painelPaginacao.setOpaque(false);
 
        painelRodape.add(lblTotalPartidas, BorderLayout.WEST);
        painelRodape.add(painelPaginacao, BorderLayout.EAST);
 
        add(painelRodape, BorderLayout.SOUTH);
 
        atualizarTotal();
    }
 
    /** Recarrega a tabela a partir do banco. Chamado pelo App ao abrir a tela. */
    public void recarregar() {
        carregarPartidas();
    }
 
    private void atualizarTotal() {
        int count = tabela.getRowCount();
        lblTotalPartidas.setText("Total: " + count + (count == 1 ? " partida" : " partidas"));
    }
 
    // -----------------------------------------------------------------
    // BANCO DE DADOS - tabela `partidas`
    // (ID_Partida, ID_Cartão, ID_Máquina, Data_Hora, Créditos_Gastos,
    // Pontos_Obtidos)
    // -----------------------------------------------------------------
 
    /**
     * Garante a coluna Saldo_Apos (saldo do cartão logo depois da partida).
     * Se ainda não existir na tabela, cria uma única vez.
     */
    private static boolean colunaPronta = false;
 
    private static void garantirColunaSaldo(Connection con) throws SQLException {
        if (colunaPronta) {
            return;
        }
        boolean existe;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.COLUMNS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND LOWER(TABLE_NAME) = 'partidas' "
                        + "AND COLUMN_NAME = 'Saldo_Apos'");
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            existe = rs.getInt(1) > 0;
        }
        if (!existe) {
            try (java.sql.Statement st = con.createStatement()) {
                st.executeUpdate("ALTER TABLE partidas ADD COLUMN Saldo_Apos BIGINT NULL");
            }
        }
        colunaPronta = true;
    }
 
    /**
     * SELECT de todas as partidas (com nome do cliente e da máquina via JOIN).
     * Novo Saldo = saldo do cartão logo após a partida + (créditos gastos).
     * Partida com 0 créditos gastos em máquina que cobra créditos = tentativa sem
     * saldo.
     */
    private void carregarPartidas() {
        String sql = "SELECT p.ID_Partida AS id_partida, p.Data_Hora AS data_hora, "
                + "cl.Nome_Completo AS nome_cliente, p.`ID_Cartão` AS id_cartao, "
                + "p.Saldo_Apos AS saldo_apos, p.`Créditos_Gastos` AS gastos, "
                + "p.Pontos_Obtidos AS pontos, m.Valor_Partida AS valor, m.Nome AS maquina "
                + "FROM partidas p "
                + "LEFT JOIN `cartões` c ON c.`ID_Cartão` = p.`ID_Cartão` "
                + "LEFT JOIN clientes cl ON cl.ID_Cliente = c.ID_Cliente "
                + "LEFT JOIN `máquinas` m ON m.`ID_Máquina` = p.`ID_Máquina` "
                + "ORDER BY p.ID_Partida DESC";
 
        modeloTabela.setRowCount(0);
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
 
        try (Connection con = conectar()) {
            garantirColunaSaldo(con);
            try (PreparedStatement ps = con.prepareStatement(sql);
                    ResultSet rs = ps.executeQuery()) {
 
                while (rs.next()) {
                    String nome = rs.getString("nome_cliente");
                    String maquina = rs.getString("maquina");
                    Timestamp data = rs.getTimestamp("data_hora");
                    long gastos = rs.getLong("gastos");
                    long saldoApos = rs.getLong("saldo_apos");
                    boolean semSaldoGravado = rs.wasNull(); // partidas antigas, de antes da coluna existir
                    boolean insuficiente = gastos == 0 && rs.getLong("valor") > 0;
                    long pontos = rs.getLong("pontos");
 
                    String textoSaldo;
                    if (insuficiente) {
                        textoSaldo = SALDO_INSUFICIENTE;
                    } else {
                        textoSaldo = (semSaldoGravado ? "—" : String.valueOf(saldoApos))
                                + " (" + (gastos > 0 ? "-" : "") + gastos + ")";
                    }
 
                    modeloTabela.addRow(new Object[] {
                            String.format("%03d", rs.getLong("id_partida")),
                            data != null ? fmt.format(data) : "—",
                            nome != null ? nome : "—",
                            String.format("%03d", rs.getLong("id_cartao")),
                            textoSaldo,
                            maquina != null ? maquina : "—",
                            pontos > 0 ? "+" + pontos : "0"
                    });
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Não foi possível carregar as partidas do banco de dados:\n" + ex.getMessage(),
                    "Erro de banco de dados", JOptionPane.ERROR_MESSAGE);
        }
 
        atualizarTotal();
    }
 
    /** Máquinas disponíveis para o combo do modal. */
    private List<MaquinaItem> carregarMaquinasDisponiveis() throws SQLException {
        String sql = "SELECT `ID_Máquina` AS id_maquina, Nome, Valor_Partida "
                + "FROM `máquinas` WHERE Ativo = TRUE ORDER BY Nome";
        List<MaquinaItem> lista = new ArrayList<>();
 
        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new MaquinaItem(rs.getLong("id_maquina"), rs.getString("Nome"),
                        rs.getLong("Valor_Partida")));
            }
        }
        return lista;
    }
 
    /**
     * Simula uma partida em uma transação:
     * - cartão precisa existir e estar ativo;
     * - se o saldo cobre o valor da partida, debita e registra;
     * - se não cobre, registra a tentativa como "Saldo insuficiente" (sem debitar).
     */
    private Resultado simularPartida(long idCartao, MaquinaItem maquina, long pontosGanhos)
            throws SQLException, PartidaException {
        try (Connection con = conectar()) {
            garantirColunaSaldo(con); // DDL fora da transação
 
            con.setAutoCommit(false);
            try {
                long saldo;
                String nomeCliente;
 
                // 1) cartão existe e está ativo? (trava a linha durante a transação)
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT c.Creditos, c.Ativo, cl.Nome_Completo "
                                + "FROM `cartões` c LEFT JOIN clientes cl ON cl.ID_Cliente = c.ID_Cliente "
                                + "WHERE c.`ID_Cartão` = ? FOR UPDATE")) {
                    ps.setLong(1, idCartao);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new PartidaException(
                                    "Cartão " + String.format("%03d", idCartao) + " não encontrado.");
                        }
                        if (!rs.getBoolean("Ativo")) {
                            throw new PartidaException("Este cartão está inativo e não pode jogar.");
                        }
                        saldo = rs.getLong("Creditos");
                        nomeCliente = rs.getString("Nome_Completo");
                    }
                }
 
                // 2) máquina continua disponível? valor atualizado
                long valor;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT Valor_Partida, Ativo FROM `máquinas` WHERE `ID_Máquina` = ?")) {
                    ps.setLong(1, maquina.id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            throw new PartidaException("Máquina não encontrada.");
                        }
                        if (!rs.getBoolean("Ativo")) {
                            throw new PartidaException("Esta máquina está indisponível.");
                        }
                        valor = rs.getLong("Valor_Partida");
                    }
                }
 
                boolean insuficiente = saldo < valor;
                long saldoApos = insuficiente ? saldo : saldo - valor;
 
                long pontos = insuficiente ? 0 : pontosGanhos;
 
                // 3) debita os créditos e soma os pontos (somente se houver saldo)
                if (!insuficiente) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE `cartões` SET Creditos = Creditos - ?, Pontos = Pontos + ? "
                                    + "WHERE `ID_Cartão` = ?")) {
                        ps.setLong(1, valor);
                        ps.setLong(2, pontos);
                        ps.setLong(3, idCartao);
                        ps.executeUpdate();
                    }
                }
 
                // 4) registra a partida (0 créditos gastos = tentativa sem saldo)
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO partidas (`ID_Cartão`, `ID_Máquina`, `Créditos_Gastos`, `Pontos_Obtidos`, Saldo_Apos) "
                                + "VALUES (?, ?, ?, ?, ?)")) {
                    ps.setLong(1, idCartao);
                    ps.setLong(2, maquina.id);
                    ps.setLong(3, insuficiente ? 0 : valor);
                    ps.setLong(4, pontos);
                    ps.setLong(5, saldoApos);
                    ps.executeUpdate();
                }
 
                con.commit();
                return new Resultado(nomeCliente, insuficiente, valor, saldoApos, pontos);
            } catch (SQLException | PartidaException e) {
                con.rollback();
                throw e;
            }
        }
    }
 
    /** 5 -> "5 créditos" (singular quando for 1). */
    private String formatarCreditos(long valor) {
        return valor + (valor == 1 ? " crédito" : " créditos");
    }
 
    // -----------------------------------------------------------------
    // 4. MODAL DE SIMULAÇÃO DE PARTIDA
    // -----------------------------------------------------------------
    private void abrirModal() {
        List<MaquinaItem> maquinas;
        try {
            maquinas = carregarMaquinasDisponiveis();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Não foi possível carregar as máquinas:\n" + ex.getMessage(),
                    "Erro de banco de dados", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (maquinas.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Não há máquinas disponíveis. Cadastre ou ative uma máquina primeiro.", "Aviso",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
 
        java.awt.Window parentWindow = SwingUtilities.getWindowAncestor(this);
        JDialog modal = new JDialog(parentWindow, "Simular Partida",
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        modal.setSize(440, 400);
        modal.setLocationRelativeTo(parentWindow);
        modal.setLayout(new BorderLayout());
 
        JPanel painelForm = new JPanel(new GridLayout(6, 1, 2, 2));
        painelForm.setBackground(new Color(10, 22, 40));
        painelForm.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));
 
        JTextField txtCartao = criarCampoTexto();
        JComboBox<MaquinaItem> cmbMaquina = new JComboBox<>(maquinas.toArray(new MaquinaItem[0]));
        cmbMaquina.setBackground(new Color(2, 12, 27));
        cmbMaquina.setForeground(Color.WHITE);
        cmbMaquina.setFont(new Font("Segoe UI", Font.PLAIN, 13));
 
        painelForm.add(criarLabel("Número do cartão (ex.: 001):"));
        painelForm.add(txtCartao);
        painelForm.add(criarLabel("Máquina utilizada:"));
        painelForm.add(cmbMaquina);
        JTextField txtPontos = criarCampoTexto();
        txtPontos.setText("0");
        painelForm.add(criarLabel("Pontos ganhos na partida:"));
        painelForm.add(txtPontos);
 
        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        painelBotoes.setBackground(new Color(2, 12, 27));
 
        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.setBackground(new Color(71, 85, 105));
        btnCancelar.setForeground(Color.WHITE);
        btnCancelar.setFocusPainted(false);
        btnCancelar.addActionListener(e -> modal.dispose());
 
        JButton btnSalvar = new JButton("Simular Partida");
        btnSalvar.setBackground(new Color(14, 116, 233));
        btnSalvar.setForeground(Color.WHITE);
        btnSalvar.setFocusPainted(false);
        btnSalvar.addActionListener(e -> {
            long idCartao;
            try {
                idCartao = Long.parseLong(txtCartao.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(modal, "Informe o número do cartão (somente números).", "Aviso",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (idCartao <= 0) {
                JOptionPane.showMessageDialog(modal, "Informe um número de cartão válido.", "Aviso",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
 
            MaquinaItem maquina = (MaquinaItem) cmbMaquina.getSelectedItem();
            if (maquina == null) {
                JOptionPane.showMessageDialog(modal, "Selecione uma máquina.", "Aviso",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
 
            long pontosGanhos;
            try {
                pontosGanhos = Long.parseLong(txtPontos.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(modal, "Pontos ganhos deve ser um número inteiro.", "Aviso",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (pontosGanhos < 0) {
                JOptionPane.showMessageDialog(modal, "Pontos ganhos não pode ser negativo.", "Aviso",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
 
            Resultado r;
            try {
                r = simularPartida(idCartao, maquina, pontosGanhos);
            } catch (PartidaException ex) {
                JOptionPane.showMessageDialog(modal, ex.getMessage(), "Aviso", JOptionPane.WARNING_MESSAGE);
                return; // mantém o modal aberto para o usuário corrigir
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(modal, "Erro ao salvar no banco de dados:\n" + ex.getMessage(),
                        "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }
 
            carregarPartidas();
 
            if (r.insuficiente) {
                JOptionPane.showMessageDialog(modal,
                        "Saldo insuficiente! A partida custa " + formatarCreditos(r.valor)
                                + " e o cartão tem " + formatarCreditos(r.saldoApos) + ".",
                        "Saldo insuficiente", JOptionPane.WARNING_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(modal,
                        "Partida simulada com sucesso!\nSaldo restante: " + formatarCreditos(r.saldoApos)
                                + "\nPontos ganhos: " + r.pontos,
                        "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            }
            modal.dispose();
        });
 
        painelBotoes.add(btnCancelar);
        painelBotoes.add(btnSalvar);
 
        modal.add(painelForm, BorderLayout.CENTER);
        modal.add(painelBotoes, BorderLayout.SOUTH);
        modal.setVisible(true);
    }
 
    private JLabel criarLabel(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setForeground(new Color(226, 232, 240));
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return lbl;
    }
 
    private JTextField criarCampoTexto() {
        JTextField txt = new JTextField();
        txt.setBackground(new Color(2, 12, 27));
        txt.setForeground(Color.WHITE);
        txt.setCaretColor(Color.WHITE);
        txt.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txt.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 48, 80), 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return txt;
    }
 
    // -----------------------------------------------------------------
    // CLASSES AUXILIARES
    // -----------------------------------------------------------------
 
    /** Item do combo de máquinas. */
    private static class MaquinaItem {
        final long id;
        final String nome;
        final long valor;
 
        MaquinaItem(long id, String nome, long valor) {
            this.id = id;
            this.nome = nome;
            this.valor = valor;
        }
 
        @Override
        public String toString() {
            return String.format("%03d", id) + " - " + nome + " (" + valor + (valor == 1 ? " crédito)" : " créditos)");
        }
    }
 
    /** Resultado de uma simulação. */
    private static class Resultado {
        final String nomeCliente;
        final boolean insuficiente;
        final long valor;
        final long saldoApos;
        final long pontos;
 
        Resultado(String nomeCliente, boolean insuficiente, long valor, long saldoApos, long pontos) {
            this.pontos = pontos;
            this.nomeCliente = nomeCliente;
            this.insuficiente = insuficiente;
            this.valor = valor;
            this.saldoApos = saldoApos;
        }
    }
 
    /**
     * Erro de regra de negócio (cartão inexistente/inativo, máquina indisponível).
     */
    private static class PartidaException extends Exception {
        PartidaException(String mensagem) {
            super(mensagem);
        }
    }
}