import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.sql.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class TelaRecargas extends JPanel {

    private JTable tabelaRecargas;
    private DefaultTableModel modeloTabela;
    private JTextField txtBuscar;
    private JLabel lblTotal;

    public TelaRecargas() {
        setLayout(null);
        setBackground(new Color(11, 19, 41)); // Fundo padrão escuro do sistema
        setBounds(70, 0, 1210, 720); // Ajuste conforme o painel principal do seu layout

        // --- TÍTULO DA PÁGINA ---
        JLabel lblTitulo = new JLabel("Recargas");
        lblTitulo.setForeground(Color.WHITE);
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 24));
        lblTitulo.setBounds(30, 25, 200, 30);
        add(lblTitulo);

        // --- CAMPO DE BUSCA ---
        txtBuscar = new JTextField();
        txtBuscar.setBounds(30, 75, 280, 35);
        txtBuscar.setBackground(new Color(18, 30, 59));
        txtBuscar.setForeground(Color.WHITE);
        txtBuscar.setCaretColor(Color.WHITE);
        txtBuscar.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        txtBuscar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(40, 60, 100)),
            BorderFactory.createEmptyBorder(5, 10, 5, 10)
        ));
        txtBuscar.setToolTipText("Buscar por ID, Cartão, Funcionário ou Forma de Pagamento...");
        add(txtBuscar);

        // --- BOTÃO NOVA RECARGA ---
        JButton btnNovaRecarga = new JButton("+ Nova Recarga");
        btnNovaRecarga.setBounds(1050, 75, 130, 35);
        btnNovaRecarga.setBackground(new Color(14, 116, 244));
        btnNovaRecarga.setForeground(Color.WHITE);
        btnNovaRecarga.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnNovaRecarga.setFocusPainted(false);
        btnNovaRecarga.setBorder(null);
        btnNovaRecarga.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnNovaRecarga.addActionListener(e -> abrirModalNovaRecarga());
        add(btnNovaRecarga);

        // --- TABELA DE RECARGAS ---
        String[] colunas = {"ID Recarga", "ID Cartão", "ID Funcionário", "Valor", "Forma de Pagamento", "Data e Hora"};
        modeloTabela = new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Tabela somente leitura
            }
        };

        tabelaRecargas = new JTable(modeloTabela);
        tabelaRecargas.setBackground(new Color(14, 24, 48));
        tabelaRecargas.setForeground(Color.WHITE);
        tabelaRecargas.setGridColor(new Color(30, 50, 85));
        tabelaRecargas.setRowHeight(35);
        tabelaRecargas.getTableHeader().setBackground(new Color(18, 30, 59));
        tabelaRecargas.getTableHeader().setForeground(Color.WHITE);
        tabelaRecargas.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabelaRecargas.setSelectionBackground(new Color(24, 75, 150));
        tabelaRecargas.setSelectionForeground(Color.WHITE);

        // Centralizar dados da tabela
        DefaultTableCellRenderer centralizado = new DefaultTableCellRenderer();
        centralizado.setHorizontalAlignment(SwingConstants.CENTER);
        for (int i = 0; i < colunas.length; i++) {
            tabelaRecargas.getColumnModel().getColumn(i).setCellRenderer(centralizado);
        }

        JScrollPane scrollPane = new JScrollPane(tabelaRecargas);
        scrollPane.setBounds(30, 130, 1150, 520);
        scrollPane.getViewport().setBackground(new Color(11, 19, 41));
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(40, 60, 100)));
        add(scrollPane);

        // --- RODAPÉ COM TOTAL ---
        lblTotal = new JLabel("Total: 0 recargas");
        lblTotal.setForeground(new Color(180, 190, 210));
        lblTotal.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTotal.setBounds(30, 665, 200, 25);
        add(lblTotal);

        // Carregar dados iniciais
        carregarDadosRecargas();
    }

    /**
     * Carrega os dados do banco para preencher a tabela.
     * Insira aqui a chamada para a sua classe de conexão (ex: ConexaoBD.getConexao()).
     */
    public void carregarDadosRecargas() {
        modeloTabela.setRowCount(0);
        
        String sql = "SELECT ID_Recarga, ID_Cartão, ID_Funcionário, Valor, Forma_Pagamento, Data_Hora FROM recargas ORDER BY Data_Hora DESC";
        
        // TODO: Substitua a linha abaixo pelo método de conexão do seu projeto
        // Exemplo: try (Connection conn = ConexaoBD.getConexao();
        try (Connection conn = /* INSIRA SUA CONEXAO AQUI */ null;
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null;
             ResultSet rs = stmt != null ? stmt.executeQuery() : null) {

            if (conn == null) {
                // Modo de exemplo visual caso a conexão venha nula nos seus testes iniciais
                return;
            }

            int total = 0;
            while (rs.next()) {
                long idRecarga = rs.getLong("ID_Recarga");
                long idCartao = rs.getLong("ID_Cartão");
                long idFuncionario = rs.getLong("ID_Funcionário");
                double valor = rs.getDouble("Valor");
                String formaPgto = rs.getString("Forma_Pagamento");
                Timestamp dataHora = rs.getTimestamp("Data_Hora");

                String valorFormatado = String.format(Locale.US, "R$ %.2f", valor);
                String dataFormatada = dataHora != null ? dataHora.toLocalDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")) : "";

                modeloTabela.addRow(new Object[]{
                    idRecarga, idCartao, idFuncionario, valorFormatado, formaPgto, dataFormatada
                });
                total++;
            }
            lblTotal.setText("Total: " + total + " recargas");

        } catch (Exception e) {
            System.err.println("Erro ao carregar recargas: " + e.getMessage());
        }
    }

    /**
     * Abre a janela modal (JDialog) estilizada para cadastrar uma nova recarga.
     */
    private void abrirModalNovaRecarga() {
        JDialog modal = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Nova Recarga", true);
        modal.setSize(420, 380);
        modal.setLocationRelativeTo(this);
        modal.setLayout(null);
        modal.getContentPane().setBackground(new Color(18, 30, 59));

        JLabel lblTitle = new JLabel("Registrar Nova Recarga");
        lblTitle.setForeground(Color.WHITE);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTitle.setBounds(30, 20, 300, 25);
        modal.add(lblTitle);

        // ID Cartão
        JLabel lblCartao = new JLabel("ID do Cartão:");
        lblCartao.setForeground(new Color(180, 190, 210));
        lblCartao.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblCartao.setBounds(30, 65, 150, 20);
        modal.add(lblCartao);

        JTextField txtCartao = new JTextField();
        txtCartao.setBounds(30, 88, 340, 30);
        txtCartao.setBackground(new Color(11, 19, 41));
        txtCartao.setForeground(Color.WHITE);
        txtCartao.setCaretColor(Color.WHITE);
        txtCartao.setBorder(BorderFactory.createLineBorder(new Color(40, 60, 100)));
        modal.add(txtCartao);

        // ID Funcionário
        JLabel lblFunc = new JLabel("ID do Funcionário:");
        lblFunc.setForeground(new Color(180, 190, 210));
        lblFunc.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblFunc.setBounds(30, 125, 150, 20);
        modal.add(lblFunc);

        JTextField txtFunc = new JTextField();
        txtFunc.setBounds(30, 148, 340, 30);
        txtFunc.setBackground(new Color(11, 19, 41));
        txtFunc.setForeground(Color.WHITE);
        txtFunc.setCaretColor(Color.WHITE);
        txtFunc.setBorder(BorderFactory.createLineBorder(new Color(40, 60, 100)));
        modal.add(txtFunc);

        // Valor
        JLabel lblValor = new JLabel("Valor (R$):");
        lblValor.setForeground(new Color(180, 190, 210));
        lblValor.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblValor.setBounds(30, 185, 150, 20);
        modal.add(lblValor);

        JTextField txtValor = new JTextField();
        txtValor.setBounds(30, 208, 160, 30);
        txtValor.setBackground(new Color(11, 19, 41));
        txtValor.setForeground(Color.WHITE);
        txtValor.setCaretColor(Color.WHITE);
        txtValor.setBorder(BorderFactory.createLineBorder(new Color(40, 60, 100)));
        modal.add(txtValor);

        // Forma de Pagamento
        JLabel lblPgto = new JLabel("Forma de Pagamento:");
        lblPgto.setForeground(new Color(180, 190, 210));
        lblPgto.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblPgto.setBounds(205, 185, 165, 20);
        modal.add(lblPgto);

        String[] formas = {"Dinheiro", "Pix", "Cartão de Crédito", "Cartão de Débito"};
        JComboBox<String> cmbPgto = new JComboBox<>(formas);
        cmbPgto.setBounds(205, 208, 165, 30);
        cmbPgto.setBackground(new Color(11, 19, 41));
        cmbPgto.setForeground(Color.WHITE);
        modal.add(cmbPgto);

        // Botão Salvar
        JButton btnSalvar = new JButton("Salvar Recarga");
        btnSalvar.setBounds(230, 275, 140, 35);
        btnSalvar.setBackground(new Color(14, 116, 244));
        btnSalvar.setForeground(Color.WHITE);
        btnSalvar.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnSalvar.setFocusPainted(false);
        btnSalvar.setBorder(null);
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

                // Chamada para salvar utilizando o seu método de conexão
                salvarRecargaNoBanco(idCartao, idFunc, valor, formaPgto);
                
                JOptionPane.showMessageDialog(modal, "Recarga cadastrada com sucesso!", "Sucesso", JOptionPane.INFORMATION_MESSAGE);
                modal.dispose();
                carregarDadosRecargas(); // Atualiza a tabela principal

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(modal, "Por favor, insira valores numéricos válidos para IDs e Valor.", "Erro de Validação", JOptionPane.ERROR_MESSAGE);
            }
        });
        modal.add(btnSalvar);

        modal.setVisible(true);
    }

    /**
     * Insere a recarga utilizando a sua classe de conexão.
     */
    private void salvarRecargaNoBanco(long idCartao, long idFuncionario, double valor, String formaPagamento) {
        String sql = "INSERT INTO recargas (ID_Cartão, ID_Funcionário, Valor, Forma_Pagamento) VALUES (?, ?, ?, ?)";
        
        // TODO: Substitua a linha abaixo pelo método de conexão do seu projeto
        // Exemplo: try (Connection conn = ConexaoBD.getConexao();
        try (Connection conn = Conexao.conectar();
             PreparedStatement stmt = conn != null ? conn.prepareStatement(sql) : null) {
            
            if (conn == null) {
                JOptionPane.showMessageDialog(this, "Erro: Conexão com o banco não configurada no método salvarRecargaNoBanco.", "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }
            
            stmt.setLong(1, idCartao);
            stmt.setLong(2, idFuncionario);
            stmt.setBigDecimal(3, BigDecimal.valueOf(valor));
            stmt.setString(4, formaPagamento);
            
            stmt.executeUpdate();
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Erro ao salvar no banco de dados: " + e.getMessage(), "Erro SQL", JOptionPane.ERROR_MESSAGE);
        }
    }
}