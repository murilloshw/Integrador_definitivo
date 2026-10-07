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
import java.text.ParseException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JFrame;
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
import javax.swing.text.MaskFormatter;

public class TelaCartao {


public class TelaClientes extends JPanel {

    private JTable tabela;
    private DefaultTableModel modeloTabela;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField txtBuscar;
    private JLabel lblTotalClientes;
    private int proximoId = 1;

    public TelaClientes() {
        setBackground(new Color(2, 12, 27)); // Fundo azul marinho escuro
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25)); // Margens ajustadas para não cortar o rodapé

        // -----------------------------------------------------------------
        // 1. TOPO: Título, Busca e Botão "+ Novo Cliente"
        // -----------------------------------------------------------------
        JPanel painelTopoGeral = new JPanel(new BorderLayout(0, 15));
        painelTopoGeral.setOpaque(false);

        JLabel lblTitulo = new JLabel("Clientes");
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

        txtBuscar = new JTextField("Buscar por nome, CPF ou telefone...");
        txtBuscar.setOpaque(false);
        txtBuscar.setForeground(new Color(100, 116, 139));
        txtBuscar.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtBuscar.setBorder(null);

        txtBuscar.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent evt) {
                if (txtBuscar.getText().equals("Buscar por nome, CPF ou telefone...")) {
                    txtBuscar.setText("");
                    txtBuscar.setForeground(Color.WHITE);
                }
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                if (txtBuscar.getText().isEmpty()) {
                    txtBuscar.setForeground(new Color(100, 116, 139));
                    txtBuscar.setText("Buscar por nome, CPF ou telefone...");
                }
            }
        });

        txtBuscar.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String texto = txtBuscar.getText();
                if (texto.equals("Buscar por nome, CPF ou telefone...") || texto.trim().isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + texto));
                }
                atualizarTotal();
            }
        });

        painelBusca.add(lblLupa, BorderLayout.WEST);
        painelBusca.add(txtBuscar, BorderLayout.CENTER);

        // Botão Novo Cliente
        JButton btnNovoCliente = new JButton("+ Novo Cliente") {
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
        btnNovoCliente.setPreferredSize(new Dimension(140, 38));
        btnNovoCliente.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnNovoCliente.setForeground(Color.WHITE);
        btnNovoCliente.setFocusPainted(false);
        btnNovoCliente.setContentAreaFilled(false);
        btnNovoCliente.setBorderPainted(false);
        btnNovoCliente.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnNovoCliente.addActionListener(e -> abrirModalCadastro());

        painelAcoes.add(painelBusca, BorderLayout.WEST);
        painelAcoes.add(btnNovoCliente, BorderLayout.EAST);

        painelTopoGeral.add(lblTitulo, BorderLayout.NORTH);
        painelTopoGeral.add(painelAcoes, BorderLayout.CENTER);

        add(painelTopoGeral, BorderLayout.NORTH);

        // -----------------------------------------------------------------
        // 2. TABELA DE CLIENTES (Design Suave)
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

        String[] colunas = { "ID ▾", "Nome ▾", "CPF ▾", "Telefone ▾" };

        modeloTabela = new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tabela = new JTable(modeloTabela);
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

        tabela.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        tabela.getColumnModel().getColumn(1).setCellRenderer(leftRenderer);
        tabela.getColumnModel().getColumn(2).setCellRenderer(centerRenderer);
        tabela.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);

        JScrollPane scrollPane = new JScrollPane(tabela);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);

        painelCardTabela.add(scrollPane, BorderLayout.CENTER);
        add(painelCardTabela, BorderLayout.CENTER);
        //oi
        // -----------------------------------------------------------------
        // 3. RODAPÉ (PAGINAÇÃO E TOTAL CORRIGIDOS)
        // -----------------------------------------------------------------
        JPanel painelRodape = new JPanel(new BorderLayout());
        painelRodape.setOpaque(false);
        painelRodape.setPreferredSize(new Dimension(0, 35));

        lblTotalClientes = new JLabel();
        lblTotalClientes.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTotalClientes.setForeground(new Color(148, 163, 184));
        atualizarTotal();

        JPanel painelPaginacao = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        painelPaginacao.setOpaque(false);

        painelRodape.add(lblTotalClientes, BorderLayout.WEST);
        painelRodape.add(painelPaginacao, BorderLayout.EAST);

        add(painelRodape, BorderLayout.SOUTH);
    }

    private void atualizarTotal() {
        int count = tabela.getRowCount();
        lblTotalClientes.setText("Total: " + count + " clientes");
    }

    private JButton criarBotaoPaginacao(String texto, boolean ativo) {
        JButton btn = new JButton(texto) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (ativo) {
                    g2.setColor(new Color(14, 116, 233));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                } else {// de vez em quando o bolder mija no meu pé e eu tenho que virar o tom pearl
                        // para limpar meu pé de maneira correta
                    g2.setColor(new Color(10, 22, 40));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                    g2.setColor(new Color(30, 48, 80));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setPreferredSize(new Dimension(30, 30));
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setForeground(ativo ? Color.WHITE : new Color(148, 163, 184));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // -----------------------------------------------------------------
    // 4. MODAL DE CADASTRO COM JFORMATTEDTEXTFIELD
    // -----------------------------------------------------------------
    private void abrirModalCadastro() {
        JFrame parentFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
        JDialog modal = new JDialog(parentFrame, "Cadastrar Novo Cliente", true);
        modal.setSize(440, 520);
        modal.setLocationRelativeTo(parentFrame);
        modal.setLayout(new BorderLayout());

        JPanel painelForm = new JPanel(new GridLayout(10, 1, 2, 2));
        painelForm.setBackground(new Color(10, 22, 40));
        painelForm.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));

        JTextField txtNome = criarCampoTexto();
        JFormattedTextField txtCPF = criarCampoFormatado("###.###.###-##");
        JFormattedTextField txtDataNasc = criarCampoFormatado("##/##/####");
        JFormattedTextField txtTelefone = criarCampoFormatado("+## ## #####-####");
        JTextField txtEmail = criarCampoTexto();

        painelForm.add(criarLabel("Nome Completo:"));
        painelForm.add(txtNome);
        painelForm.add(criarLabel("CPF:"));
        painelForm.add(txtCPF);
        painelForm.add(criarLabel("Data de Nascimento:"));
        painelForm.add(txtDataNasc);
        painelForm.add(criarLabel("Telefone (+DD DDD XXXXX-XXXX):"));
        painelForm.add(txtTelefone);
        painelForm.add(criarLabel("E-mail:"));
        painelForm.add(txtEmail);

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        painelBotoes.setBackground(new Color(2, 12, 27));

        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.setBackground(new Color(71, 85, 105));
        btnCancelar.setForeground(Color.WHITE);
        btnCancelar.setFocusPainted(false);
        btnCancelar.addActionListener(e -> modal.dispose());

        JButton btnSalvar = new JButton("Salvar Cliente");
        btnSalvar.setBackground(new Color(14, 116, 233));
        btnSalvar.setForeground(Color.WHITE);
        btnSalvar.setFocusPainted(false);
        btnSalvar.addActionListener(e -> {
            if (txtNome.getText().trim().isEmpty() || txtCPF.getText().contains(" ")) {
                JOptionPane.showMessageDialog(modal, "Por favor, preencha o Nome e o CPF corretamente.", "Aviso",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            String idFormatado = String.format("%03d", proximoId++);

            modeloTabela.addRow(new Object[] {
                    idFormatado,
                    txtNome.getText().trim(),
                    txtCPF.getText().trim(),
                    txtTelefone.getText().trim()
            });

            atualizarTotal();
            JOptionPane.showMessageDialog(modal, "Cliente cadastrado com sucesso!", "Sucesso",
                    JOptionPane.INFORMATION_MESSAGE);
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

    private JFormattedTextField criarCampoFormatado(String mascara) {
        JFormattedTextField txt;
        try {
            MaskFormatter mf = new MaskFormatter(mascara);
            mf.setPlaceholderCharacter(' ');
            txt = new JFormattedTextField(mf);
        } catch (ParseException e) {
            txt = new JFormattedTextField();
        }

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

public void setBounds(int i, int j, int k, int alturaTela) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'setBounds'");
}

public void setVisible(boolean b) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'setVisible'");
}}

