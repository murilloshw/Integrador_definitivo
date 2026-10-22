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
import javax.swing.JComboBox;
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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.MaskFormatter;

public class TelaCartao extends JPanel {

    private JTable tabela;
    private DefaultTableModel modeloTabela;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField txtBuscar;
    private JLabel lblTotalCartoes;
    private int proximoId = 1;

    private final TelaClientes telaClientes;

    public TelaCartao(TelaClientes telaClientes) {
        this.telaClientes = telaClientes;
        setBackground(new Color(2, 12, 27)); // Fundo azul marinho escuro
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25)); // Margens ajustadas para não cortar o rodapé

        // -----------------------------------------------------------------
        // 1. TOPO: Título, Busca e Botão "+ Novo Cartão"
        // -----------------------------------------------------------------
        JPanel painelTopoGeral = new JPanel(new BorderLayout(0, 15));
        painelTopoGeral.setOpaque(false);

        JLabel lblTitulo = new JLabel("Cartões");
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

        txtBuscar = new JTextField("Buscar por ID, cliente ou usuário...");
        txtBuscar.setOpaque(false);
        txtBuscar.setForeground(new Color(100, 116, 139));
        txtBuscar.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtBuscar.setBorder(null);

        txtBuscar.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent evt) {
                if (txtBuscar.getText().equals("Buscar por ID, cliente ou usuário...")) {
                    txtBuscar.setText("");
                    txtBuscar.setForeground(Color.WHITE);
                }
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                if (txtBuscar.getText().isEmpty()) {
                    txtBuscar.setForeground(new Color(100, 116, 139));
                    txtBuscar.setText("Buscar por ID, cliente ou usuário...");
                }
            }
        });

        txtBuscar.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String texto = txtBuscar.getText();
                if (texto.equals("Buscar por ID, cliente ou usuário...") || texto.trim().isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(texto.trim())));
                }
                atualizarTotal();
            }
        });

        painelBusca.add(lblLupa, BorderLayout.WEST);
        painelBusca.add(txtBuscar, BorderLayout.CENTER);

        // Botão Novo Cliente
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

        painelAcoes.add(painelBusca, BorderLayout.WEST);
        JButton btnEditar = criarBotaoEditar();
        btnEditar.addActionListener(e -> {
            int linha = linhaSelecionadaNoModelo();
            if (linha >= 0) {
                abrirModal(linha);
            }
        });

        JPanel painelBotoesTopo = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        painelBotoesTopo.setOpaque(false);
        painelBotoesTopo.add(btnEditar);
        painelBotoesTopo.add(btnNovoCartao);
        painelAcoes.add(painelBotoesTopo, BorderLayout.EAST);

        painelTopoGeral.add(lblTitulo, BorderLayout.NORTH);
        painelTopoGeral.add(painelAcoes, BorderLayout.CENTER);

        add(painelTopoGeral, BorderLayout.NORTH);

        // -----------------------------------------------------------------
        // 2. TABELA DE CARTÕES (Design Suave)
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
        tabela.getColumnModel().getColumn(1).setCellRenderer(centerRenderer);
        tabela.getColumnModel().getColumn(2).setCellRenderer(leftRenderer);
        tabela.getColumnModel().getColumn(3).setCellRenderer(leftRenderer);
        tabela.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);
        tabela.getColumnModel().getColumn(5).setCellRenderer(centerRenderer);
        tabela.getColumnModel().getColumn(6).setCellRenderer(centerRenderer);

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

        lblTotalCartoes = new JLabel();
        lblTotalCartoes.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTotalCartoes.setForeground(new Color(148, 163, 184));
        atualizarTotal();

        JPanel painelPaginacao = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        painelPaginacao.setOpaque(false);

        painelRodape.add(lblTotalCartoes, BorderLayout.WEST);
        painelRodape.add(painelPaginacao, BorderLayout.EAST);

        add(painelRodape, BorderLayout.SOUTH);
    }

    /** Atualiza o nome do cliente em cada cartão (caso o cliente tenha sido editado). */
    public void atualizarNomesClientes() {
        for (int i = 0; i < modeloTabela.getRowCount(); i++) {
            String nome = telaClientes.buscarNomePorId(String.valueOf(modeloTabela.getValueAt(i, 1)));
            if (nome != null) {
                modeloTabela.setValueAt(nome, i, 2);
            }
        }
    }

    private void atualizarTotal() {
        int count = tabela.getRowCount();
        lblTotalCartoes.setText("Total: " + count + " cartões");
    }


    // -----------------------------------------------------------------
    // 4. MODAL DE CADASTRO COM JFORMATTEDTEXTFIELD
    // -----------------------------------------------------------------
    // linhaModelo < 0 => novo cadastro; senão edita a linha indicada
    private void abrirModal(int linhaModelo) {
        boolean edicao = linhaModelo >= 0;
        java.awt.Window parentWindow = SwingUtilities.getWindowAncestor(this);
        JDialog modal = new JDialog(parentWindow, edicao ? "Editar Cartão" : "Cadastrar Novo Cartão", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        modal.setSize(440, 520);
        modal.setLocationRelativeTo(parentWindow);
        modal.setLayout(new BorderLayout());

        JPanel painelForm = new JPanel(new GridLayout(10, 1, 2, 2));
        painelForm.setBackground(new Color(10, 22, 40));
        painelForm.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));

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

            if (idCliente.contains(" ")) {
                JOptionPane.showMessageDialog(modal,
                        "Digite o ID do Cliente com 3 dígitos (ex.: 001, 002).", "Aviso",
                        JOptionPane.WARNING_MESSAGE);
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
                JOptionPane.showMessageDialog(modal,
                        "Crédito e Pontos devem ser números inteiros.", "Aviso",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (usuario.isEmpty() || usuario.length() > 50) {
                JOptionPane.showMessageDialog(modal, "Informe o Usuário (até 50 caracteres).", "Aviso",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (credito < 0 || pontos < 0) {
                JOptionPane.showMessageDialog(modal,
                        "Crédito e Pontos não podem ser negativos.", "Aviso",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (edicao) {
                modeloTabela.setValueAt(idCliente, linhaModelo, 1);
                modeloTabela.setValueAt(nomeCliente, linhaModelo, 2);
                modeloTabela.setValueAt(usuario, linhaModelo, 3);
                modeloTabela.setValueAt(credito, linhaModelo, 4);
                modeloTabela.setValueAt(pontos, linhaModelo, 5);
                modeloTabela.setValueAt((String) cmbStatus.getSelectedItem(), linhaModelo, 6);
            } else {
                String idFormatado = String.format("%03d", proximoId++);
                modeloTabela.addRow(new Object[] {
                        idFormatado,
                        idCliente,
                        nomeCliente,
                        usuario,
                        credito,
                        pontos,
                        (String) cmbStatus.getSelectedItem()
                });
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


    // Botão de lápis azul (ícone desenhado em código, sem depender de imagem/fonte)
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

    private int linhaSelecionadaNoModelo() {
        int viewRow = tabela.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Selecione uma linha da tabela para editar.", "Aviso",
                    JOptionPane.WARNING_MESSAGE);
            return -1;
        }
        return tabela.convertRowIndexToModel(viewRow);
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
