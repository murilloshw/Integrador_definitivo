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
 * Tela de Máquinas (mesma estrutura de TelaClientes / TelaCartao).
 *
 * Campos: ID da máquina, Nome, Tipo de jogo, Valor da partida (em créditos) e Status.
 * A localização física da máquina NÃO é armazenada.
 *
 * Os dados vêm da tabela `Máquinas` do MySQL (SELECT / INSERT / UPDATE via Conexao).
 */
public class TelaMaquinas extends JPanel {

    private static final String PLACEHOLDER_BUSCA = "Buscar por ID, nome ou tipo de jogo...";
    private static final String DISPONIVEL = "Disponível";
    private static final String INDISPONIVEL = "Indisponível";

    private JTable tabela;
    private DefaultTableModel modeloTabela;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField txtBuscar;
    private JLabel lblTotalMaquinas;

    // -----------------------------------------------------------------
    // CONEXÃO COM O BANCO
    // Conexao.conectar() devolve null quando falha; aqui viramos SQLException
    // para a tela mostrar a mensagem de erro em vez de dar NullPointerException.
    // -----------------------------------------------------------------
    private Connection conectar() throws SQLException {
        Connection con = Conexao.conectar();
        if (con == null) {
            throw new SQLException("Não foi possível conectar ao banco de dados.");
        }
        return con;
    }

    public TelaMaquinas() {
        setBackground(new Color(2, 12, 27)); // Fundo azul marinho escuro
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        // -----------------------------------------------------------------
        // 1. TOPO: Título, Busca e Botão "+ Nova Máquina"
        // -----------------------------------------------------------------
        JPanel painelTopoGeral = new JPanel(new BorderLayout(0, 15));
        painelTopoGeral.setOpaque(false);

        JLabel lblTitulo = new JLabel("Máquinas");
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

        // Botão Nova Máquina
        JButton btnNovaMaquina = new JButton("+ Nova Máquina") {
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
        btnNovaMaquina.setPreferredSize(new Dimension(150, 38));
        btnNovaMaquina.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnNovaMaquina.setForeground(Color.WHITE);
        btnNovaMaquina.setFocusPainted(false);
        btnNovaMaquina.setContentAreaFilled(false);
        btnNovaMaquina.setBorderPainted(false);
        btnNovaMaquina.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnNovaMaquina.addActionListener(e -> abrirModal(-1));

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
        painelBotoesTopo.add(btnNovaMaquina);
        painelAcoes.add(painelBotoesTopo, BorderLayout.EAST);

        painelTopoGeral.add(lblTitulo, BorderLayout.NORTH);
        painelTopoGeral.add(painelAcoes, BorderLayout.CENTER);

        add(painelTopoGeral, BorderLayout.NORTH);

        // -----------------------------------------------------------------
        // 2. TABELA DE MÁQUINAS (Design Suave)
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

        String[] colunas = { "ID ", "Nome ", "Tipo de Jogo ", "Valor da Partida ", "Status " };

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
        tabela.getColumnModel().getColumn(1).setCellRenderer(leftRenderer);
        tabela.getColumnModel().getColumn(2).setCellRenderer(leftRenderer);
        tabela.getColumnModel().getColumn(3).setCellRenderer(centerRenderer);
        tabela.getColumnModel().getColumn(4).setCellRenderer(centerRenderer);

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

        lblTotalMaquinas = new JLabel();
        lblTotalMaquinas.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTotalMaquinas.setForeground(new Color(148, 163, 184));

        JPanel painelPaginacao = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        painelPaginacao.setOpaque(false);

        painelRodape.add(lblTotalMaquinas, BorderLayout.WEST);
        painelRodape.add(painelPaginacao, BorderLayout.EAST);

        add(painelRodape, BorderLayout.SOUTH);

        // Carrega as máquinas do banco (também atualiza o "Total: X máquinas")
        carregarMaquinas();
    }

    /** Recarrega a tabela a partir do banco. Pode ser chamado por outras telas. */
    public void recarregar() {
        carregarMaquinas();
    }

    /** Usado por outras telas (ex.: Partidas): devolve o nome da máquina ou null se não existir. */
    public String buscarNomePorId(String idComoTexto) {
        if (idComoTexto == null) {
            return null;
        }
        String id = idComoTexto.trim();
        for (int i = 0; i < modeloTabela.getRowCount(); i++) {
            if (String.valueOf(modeloTabela.getValueAt(i, 0)).trim().equals(id)) {
                return String.valueOf(modeloTabela.getValueAt(i, 1));
            }
        }
        return null;
    }

    private void atualizarTotal() {
        int count = tabela.getRowCount();
        lblTotalMaquinas.setText("Total: " + count + (count == 1 ? " máquina" : " máquinas"));
    }

    // -----------------------------------------------------------------
    // BANCO DE DADOS - tabela `Máquinas`
    // (ID_Máquina, Nome, Tipo_Jogo, Valor_Partida, Ativo)
    // Nomes com acento ficam entre crases.
    // -----------------------------------------------------------------

    /** SELECT de todas as máquinas e preenchimento da tabela. */
    private void carregarMaquinas() {
        String sql = "SELECT `ID_Máquina` AS id_maquina, Nome, Tipo_Jogo, Valor_Partida, Ativo "
                + "FROM `máquinas` ORDER BY `ID_Máquina`";

        modeloTabela.setRowCount(0);

        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                modeloTabela.addRow(new Object[] {
                        String.format("%03d", rs.getLong("id_maquina")),
                        rs.getString("Nome"),
                        rs.getString("Tipo_Jogo"),
                        formatarValor(rs.getLong("Valor_Partida")),
                        rs.getBoolean("Ativo") ? DISPONIVEL : INDISPONIVEL
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Não foi possível carregar as máquinas do banco de dados:\n" + ex.getMessage(),
                    "Erro de banco de dados", JOptionPane.ERROR_MESSAGE);
        }

        atualizarTotal();
    }

    /** INSERT de uma nova máquina. Retorna o ID gerado pelo banco (AUTO_INCREMENT). */
    private long inserirMaquina(String nome, String tipo, long valor, boolean disponivel) throws SQLException {
        String sql = "INSERT INTO `máquinas` (Nome, Tipo_Jogo, Valor_Partida, Ativo) VALUES (?, ?, ?, ?)";

        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, nome);
            ps.setString(2, tipo);
            ps.setLong(3, valor);
            ps.setBoolean(4, disponivel);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            throw new SQLException("O banco não retornou o ID da máquina cadastrada.");
        }
    }

    /** UPDATE de uma máquina existente. */
    private void atualizarMaquina(long idMaquina, String nome, String tipo, long valor, boolean disponivel)
            throws SQLException {
        String sql = "UPDATE `máquinas` SET Nome = ?, Tipo_Jogo = ?, Valor_Partida = ?, Ativo = ? "
                + "WHERE `ID_Máquina` = ?";

        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, nome);
            ps.setString(2, tipo);
            ps.setLong(3, valor);
            ps.setBoolean(4, disponivel);
            ps.setLong(5, idMaquina);
            ps.executeUpdate();
        }
    }

    /** Traduz erros para mensagens amigáveis. */
    private String mensagemDeErro(SQLException ex) {
        if (ex.getErrorCode() == 3819) { // CHECK (Valor_Partida >= 0)
            return "O Valor da partida não pode ser negativo.";
        }
        return "Erro ao salvar no banco de dados:\n" + ex.getMessage();
    }

    /** Lê o ID real (numérico) da linha do modelo: "007" -> 7. */
    private long idDaLinha(int linhaModelo) {
        return Long.parseLong(String.valueOf(modeloTabela.getValueAt(linhaModelo, 0)).trim());
    }

    /** 5 -> "5 créditos" (singular quando for 1). */
    private String formatarValor(long valor) {
        return valor + (valor == 1 ? " crédito" : " créditos");
    }

    /** "5 créditos" -> 5 */
    private long valorDaLinha(int linhaModelo) {
        String texto = String.valueOf(modeloTabela.getValueAt(linhaModelo, 3)).trim();
        return Long.parseLong(texto.split(" ")[0]);
    }

    // -----------------------------------------------------------------
    // 4. MODAL DE CADASTRO / EDIÇÃO
    // -----------------------------------------------------------------
    // linhaModelo < 0 => nova máquina; senão edita a linha indicada
    private void abrirModal(int linhaModelo) {
        boolean edicao = linhaModelo >= 0;
        java.awt.Window parentWindow = SwingUtilities.getWindowAncestor(this);
        JDialog modal = new JDialog(parentWindow, edicao ? "Editar Máquina" : "Cadastrar Nova Máquina",
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        modal.setSize(440, 470);
        modal.setLocationRelativeTo(parentWindow);
        modal.setLayout(new BorderLayout());

        JPanel painelForm = new JPanel(new GridLayout(8, 1, 2, 2));
        painelForm.setBackground(new Color(10, 22, 40));
        painelForm.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));

        JTextField txtNome = criarCampoTexto();
        JTextField txtTipo = criarCampoTexto();
        JTextField txtValor = criarCampoTexto();
        JComboBox<String> cmbStatus = new JComboBox<>(new String[] { DISPONIVEL, INDISPONIVEL });
        cmbStatus.setBackground(new Color(2, 12, 27));
        cmbStatus.setForeground(Color.WHITE);
        cmbStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        if (edicao) {
            txtNome.setText(String.valueOf(modeloTabela.getValueAt(linhaModelo, 1)));
            txtTipo.setText(String.valueOf(modeloTabela.getValueAt(linhaModelo, 2)));
            txtValor.setText(String.valueOf(valorDaLinha(linhaModelo)));
            cmbStatus.setSelectedItem(modeloTabela.getValueAt(linhaModelo, 4));
        }

        painelForm.add(criarLabel("Nome da máquina (máx. 50 caracteres):"));
        painelForm.add(txtNome);
        painelForm.add(criarLabel("Tipo de jogo (ex.: Boliche, Corrida):"));
        painelForm.add(txtTipo);
        painelForm.add(criarLabel("Valor da partida (em créditos):"));
        painelForm.add(txtValor);
        painelForm.add(criarLabel("Status:"));
        painelForm.add(cmbStatus);

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        painelBotoes.setBackground(new Color(2, 12, 27));

        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.setBackground(new Color(71, 85, 105));
        btnCancelar.setForeground(Color.WHITE);
        btnCancelar.setFocusPainted(false);
        btnCancelar.addActionListener(e -> modal.dispose());

        JButton btnSalvar = new JButton(edicao ? "Salvar Alterações" : "Salvar Máquina");
        btnSalvar.setBackground(new Color(14, 116, 233));
        btnSalvar.setForeground(Color.WHITE);
        btnSalvar.setFocusPainted(false);
        btnSalvar.addActionListener(e -> {
            String nome = txtNome.getText().trim();
            String tipo = txtTipo.getText().trim();
            long valor;

            if (nome.isEmpty() || nome.length() > 50) {
                JOptionPane.showMessageDialog(modal, "Informe o Nome da máquina (até 50 caracteres).", "Aviso",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (tipo.isEmpty() || tipo.length() > 50) {
                JOptionPane.showMessageDialog(modal, "Informe o Tipo de jogo (até 50 caracteres).", "Aviso",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                valor = Long.parseLong(txtValor.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(modal,
                        "O Valor da partida deve ser um número inteiro de créditos.", "Aviso",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (valor < 0) {
                JOptionPane.showMessageDialog(modal,
                        "O Valor da partida não pode ser negativo.", "Aviso",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            String status = (String) cmbStatus.getSelectedItem();
            boolean disponivel = DISPONIVEL.equals(status);

            // ---------- Grava no banco; só mexe na tabela se o banco aceitou ----------
            try {
                if (edicao) {
                    atualizarMaquina(idDaLinha(linhaModelo), nome, tipo, valor, disponivel);

                    modeloTabela.setValueAt(nome, linhaModelo, 1);
                    modeloTabela.setValueAt(tipo, linhaModelo, 2);
                    modeloTabela.setValueAt(formatarValor(valor), linhaModelo, 3);
                    modeloTabela.setValueAt(status, linhaModelo, 4);
                } else {
                    long novoId = inserirMaquina(nome, tipo, valor, disponivel);

                    modeloTabela.addRow(new Object[] {
                            String.format("%03d", novoId),
                            nome,
                            tipo,
                            formatarValor(valor),
                            status
                    });
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(modal, mensagemDeErro(ex), "Erro", JOptionPane.ERROR_MESSAGE);
                return; // mantém o modal aberto para o usuário corrigir
            }

            atualizarTotal();
            JOptionPane.showMessageDialog(modal,
                    edicao ? "Máquina atualizada com sucesso!" : "Máquina cadastrada com sucesso!", "Sucesso",
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
}