import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;

public class TelaRecargas extends JPanel {

    private static final String PLACEHOLDER_BUSCA = "Buscar por ID, cartão, funcionário ou forma de pagamento...";

    private JTable tabelaRecargas;
    private DefaultTableModel modeloTabela;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField txtBuscar;
    private JLabel lblTotal;

    private Connection conectar() throws SQLException {
        Connection con = Conexao.conectar();
        if (con == null) {
            throw new SQLException("Não foi possível conectar ao banco de dados.");
        }
        return con;
    }

    public TelaRecargas() {
        setBackground(new Color(2, 12, 27));
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        // --- TOPO: Título, Busca e Botão ---
        JPanel painelTopoGeral = new JPanel(new BorderLayout(0, 15));
        painelTopoGeral.setOpaque(false);

        JLabel lblTitulo = new JLabel("Recargas");
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
        painelBusca.setPreferredSize(new Dimension(400, 38));
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

        txtBuscar.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String texto = txtBuscar.getText();
                if (texto.equals(PLACEHOLDER_BUSCA) || texto.trim().isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(texto.trim())));
                }
                atualizarTotal();
            }
        });

        painelBusca.add(lblLupa, BorderLayout.WEST);
        painelBusca.add(txtBuscar, BorderLayout.CENTER);

        // Botão Nova Recarga
        JButton btnNovaRecarga = new JButton("+ Nova Recarga") {
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
        btnNovaRecarga.setPreferredSize(new Dimension(150, 38));
        btnNovaRecarga.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnNovaRecarga.setForeground(Color.WHITE);
        btnNovaRecarga.setFocusPainted(false);
        btnNovaRecarga.setContentAreaFilled(false);
        btnNovaRecarga.setBorderPainted(false);
        btnNovaRecarga.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnNovaRecarga.addActionListener(e -> abrirModalNovaRecarga());

        painelAcoes.add(painelBusca, BorderLayout.WEST);
        painelAcoes.add(btnNovaRecarga, BorderLayout.EAST);

        painelTopoGeral.add(lblTitulo, BorderLayout.NORTH);
        painelTopoGeral.add(painelAcoes, BorderLayout.CENTER);
        add(painelTopoGeral, BorderLayout.NORTH);

        // --- TABELA ---
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

        String[] colunas = {"ID Recarga", "ID Cartão", "ID Funcionário", "Valor", "Forma de Pagamento", "Data e Hora"};
        modeloTabela = new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tabelaRecargas = new JTable(modeloTabela);
        tabelaRecargas.setBackground(new Color(10, 22, 40));
        tabelaRecargas.setForeground(new Color(226, 232, 240));
        tabelaRecargas.setGridColor(new Color(18, 35, 60));
        tabelaRecargas.setRowHeight(42);
        tabelaRecargas.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        tabelaRecargas.getTableHeader().setBackground(new Color(10, 22, 40));
        tabelaRecargas.getTableHeader().setForeground(new Color(148, 163, 184));
        tabelaRecargas.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabelaRecargas.getTableHeader().setPreferredSize(new Dimension(0, 40));
        tabelaRecargas.getTableHeader().setReorderingAllowed(false);

        sorter = new TableRowSorter<>(modeloTabela);
        tabelaRecargas.setRowSorter(sorter);

        DefaultTableCellRenderer centralizado = new DefaultTableCellRenderer();
        centralizado.setHorizontalAlignment(SwingConstants.CENTER);
        centralizado.setBackground(new Color(10, 22, 40));
        centralizado.setForeground(new Color(186, 230, 253));

        for (int i = 0; i < colunas.length; i++) {
            tabelaRecargas.getColumnModel().getColumn(i).setCellRenderer(centralizado);
        }

        JScrollPane scrollPane = new JScrollPane(tabelaRecargas);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);

        painelCardTabela.add(scrollPane, BorderLayout.CENTER);
        add(painelCardTabela, BorderLayout.CENTER);

        // --- RODAPÉ ---
        JPanel painelRodape = new JPanel(new BorderLayout());
        painelRodape.setOpaque(false);
        painelRodape.setPreferredSize(new Dimension(0, 35));

        lblTotal = new JLabel();
        lblTotal.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTotal.setForeground(new Color(148, 163, 184));
        painelRodape.add(lblTotal, BorderLayout.WEST);

        add(painelRodape, BorderLayout.SOUTH);

        carregarDadosRecargas();
    }

    public void carregarDadosRecargas() {
        modeloTabela.setRowCount(0);
        String sql = "SELECT ID_Recarga, ID_Cartão, ID_Funcionário, Valor, Forma_Pagamento, Data_Hora FROM recargas ORDER BY Data_Hora DESC";

        try (Connection conn = conectar();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                long idRecarga = rs.getLong("ID_Recarga");
                long idCartao = rs.getLong("ID_Cartão");
                long idFuncionario = rs.getLong("ID_Funcionário");
                double valor = rs.getDouble("Valor");
                String formaPgto = rs.getString("Forma_Pagamento");
                Timestamp dataHora = rs.getTimestamp("Data_Hora");

                String valorFormatado = String.format(Locale.US, "R$ ", valor);
                String dataFormatada = dataHora != null ? dataHora.toLocalDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) : "";

                modeloTabela.addRow(new Object[]{
                    String.format("%03d", idRecarga),
                    String.format("%03d", idCartao),
                    String.format("%03d", idFuncionario),
                    valorFormatado,
                    formaPgto,
                    dataFormatada
                });
            }

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this,
                    "Não foi possível carregar as recargas do banco de dados:\n" + e.getMessage(),
                    "Erro de banco de dados", JOptionPane.ERROR_MESSAGE);
        }

        atualizarTotal();
    }

    private void atualizarTotal() {
        int count = tabelaRecargas.getRowCount();
        lblTotal.setText("Total: " + count + (count == 1 ? " recarga" : " recargas"));
    }

    private void abrirModalNovaRecarga() {
        java.awt.Window parentWindow = SwingUtilities.getWindowAncestor(this);
        JDialog modal = new JDialog(parentWindow, "Nova Recarga", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        modal.setSize(420, 380);
        modal.setLocationRelativeTo(parentWindow);
        modal.setLayout(null);
        modal.getContentPane().setBackground(new Color(10, 22, 40));

        JLabel lblTitle = new JLabel("Registrar Nova Recarga");
        lblTitle.setForeground(Color.WHITE);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTitle.setBounds(30, 20, 300, 25);
        modal.add(lblTitle);

        JLabel lblCartao = new JLabel("ID do Cartão:");
        lblCartao.setForeground(new Color(226, 232, 240));
        lblCartao.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblCartao.setBounds(30, 65, 150, 20);
        modal.add(lblCartao);

        JTextField txtCartao = criarCampoTexto();
        txtCartao.setBounds(30, 88, 340, 30);
        modal.add(txtCartao);

        JLabel lblFunc = new JLabel("ID do Funcionário:");
        lblFunc.setForeground(new Color(226, 232, 240));
        lblFunc.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblFunc.setBounds(30, 125, 150, 20);
        modal.add(lblFunc);

        JTextField txtFunc = criarCampoTexto();
        txtFunc.setBounds(30, 148, 340, 30);
        modal.add(txtFunc);

        JLabel lblValor = new JLabel("Valor (R$):");
        lblValor.setForeground(new Color(226, 232, 240));
        lblValor.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblValor.setBounds(30, 185, 150, 20);
        modal.add(lblValor);

        JTextField txtValor = criarCampoTexto();
        txtValor.setBounds(30, 208, 160, 30);
        modal.add(txtValor);

        JLabel lblPgto = new JLabel("Forma de Pagamento:");
        lblPgto.setForeground(new Color(226, 232, 240));
        lblPgto.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblPgto.setBounds(205, 185, 165, 20);
        modal.add(lblPgto);

        String[] formas = {"Dinheiro", "Pix", "Cartão de Crédito", "Cartão de Débito"};
        JComboBox<String> cmbPgto = new JComboBox<>(formas);
        cmbPgto.setBounds(205, 208, 165, 30);
        cmbPgto.setBackground(new Color(2, 12, 27));
        cmbPgto.setForeground(Color.WHITE);
        modal.add(cmbPgto);

        JButton btnSalvar = new JButton("Salvar Recarga");
        btnSalvar.setBounds(230, 275, 140, 35);
        btnSalvar.setBackground(new Color(14, 116, 233));
        btnSalvar.setForeground(Color.WHITE);
        btnSalvar.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSalvar.setFocusPainted(false);
        btnSalvar.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btnSalvar.addActionListener(e -> {
            try {
                long idCartao = Long.parseLong(txtCartao.getText().trim());
                long idFunc = Long.parseLong(txtFunc.getText().trim());
                double valor = Double.parseDouble(txtValor.getText().trim().replace(",", "."));
                String formaPgto = (String) cmbPgto.getSelectedItem();

                if (valor <= 0) {
                    JOptionPane.showMessageDialog(modal, "O valor da recarga deve ser maior que zero!", "Aviso", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                salvarRecargaNoBanco(idCartao, idFunc, valor, formaPgto);
                JOptionPane.showMessageDialog(modal, "Recarga cadastrada com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                modal.dispose();
                carregarDadosRecargas();

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(modal, "Por favor, insira valores numéricos válidos para IDs e Valor.", "Erro de Validação", JOptionPane.ERROR_MESSAGE);
            }
        });
        modal.add(btnSalvar);

        modal.setVisible(true);
    }

    private void salvarRecargaNoBanco(long idCartao, long idFuncionario, double valor, String formaPagamento) {
        String sql = "INSERT INTO recargas (ID_Cartão, ID_Funcionário, Valor, Forma_Pagamento) VALUES (?, ?, ?, ?)";

        try (Connection conn = conectar();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, idCartao);
            stmt.setLong(2, idFuncionario);
            stmt.setBigDecimal(3, BigDecimal.valueOf(valor));
            stmt.setString(4, formaPagamento);
            stmt.executeUpdate();

        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Erro ao salvar no banco de dados: " + e.getMessage(), "Erro SQL", JOptionPane.ERROR_MESSAGE);
        }
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
}