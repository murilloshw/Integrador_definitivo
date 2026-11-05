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

/**
 * Tela de MÁQUINAS: lista as máquinas de jogo do banco numa tabela com busca,
 * e permite cadastrar, editar e excluir máquinas.
 *
 * Campos: ID, Nome, Tipo de jogo, Valor da partida (em créditos) e Status
 * (Disponível / Indisponível). Os dados vêm da tabela `máquinas` do MySQL.
 */
public class TelaMaquinas extends JPanel {

    // =========================================================================
    // CONSTANTES
    // Texto de dica da busca e os dois valores possíveis de Status.
    // =========================================================================
    private static final String PLACEHOLDER_BUSCA = "Buscar por ID, nome ou tipo de jogo...";
    private static final String DISPONIVEL = "Disponível";
    private static final String INDISPONIVEL = "Indisponível";

    // =========================================================================
    // CAMPOS
    // Componentes da tela que são usados em mais de um método.
    // =========================================================================
    private JTable tabela;
    private DefaultTableModel modeloTabela;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField txtBuscar;
    private JLabel lblTotalMaquinas;

    // =========================================================================
    // CONSTRUTOR
    // Monta as três partes da tela (topo, tabela, rodapé) e carrega os dados.
    // =========================================================================
    public TelaMaquinas() {
        setBackground(new Color(2, 12, 27)); // fundo azul marinho escuro
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        add(criarTopo(), BorderLayout.NORTH);
        add(criarTabela(), BorderLayout.CENTER);
        add(criarRodape(), BorderLayout.SOUTH);

        carregarMaquinas(); // também atualiza o "Total: X máquinas"
    }

    // =========================================================================
    // MÉTODOS PÚBLICOS
    // Podem ser chamados por outras telas.
    // =========================================================================

    /** Recarrega a tabela a partir do banco. */
    public void recarregar() {
        carregarMaquinas();
    }

    /** Devolve o nome da máquina com o ID informado (ex.: "007") ou null se não existir. */
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

    // =========================================================================
    // MONTAGEM DA INTERFACE
    // Topo (título, busca e botões), tabela de máquinas e rodapé com o total.
    // =========================================================================

    /** Topo: título "Máquinas", campo de busca e botões Editar / Excluir / + Nova Máquina. */
    private JPanel criarTopo() {
        JLabel lblTitulo = new JLabel("Máquinas");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitulo.setForeground(Color.WHITE);

        JButton btnEditar = criarBotaoEditar();
        btnEditar.addActionListener(e -> {
            int linha = linhaSelecionadaNoModelo();
            if (linha >= 0) {
                abrirModal(linha);
            }
        });

        JButton btnExcluir = Exclusao.criarBotao();
        btnExcluir.addActionListener(e -> excluirSelecionado());

        JButton btnNovaMaquina = criarBotaoNovo();
        btnNovaMaquina.addActionListener(e -> abrirModal(-1));

        JPanel painelBotoesTopo = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        painelBotoesTopo.setOpaque(false);
        painelBotoesTopo.add(btnEditar);
        painelBotoesTopo.add(btnExcluir);
        painelBotoesTopo.add(btnNovaMaquina);

        JPanel painelAcoes = new JPanel(new BorderLayout(15, 0));
        painelAcoes.setOpaque(false);
        painelAcoes.add(criarBusca(), BorderLayout.WEST);
        painelAcoes.add(painelBotoesTopo, BorderLayout.EAST);

        JPanel painelTopo = new JPanel(new BorderLayout(0, 15));
        painelTopo.setOpaque(false);
        painelTopo.add(lblTitulo, BorderLayout.NORTH);
        painelTopo.add(painelAcoes, BorderLayout.CENTER);
        return painelTopo;
    }

    /** Campo de busca arredondado: filtra a tabela a cada tecla digitada. */
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

        // Texto de dica (placeholder): some ao clicar, volta se o campo ficar vazio
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

        // Filtro da tabela (ignora maiúsculas/minúsculas)
        txtBuscar.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String texto = txtBuscar.getText();
                if (texto.equals(PLACEHOLDER_BUSCA) || texto.trim().isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    // Pattern.quote evita erro se o usuário digitar caracteres como ( ) + . *
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(texto.trim())));
                }
                atualizarTotal();
            }
        });

        painelBusca.add(lblLupa, BorderLayout.WEST);
        painelBusca.add(txtBuscar, BorderLayout.CENTER);
        return painelBusca;
    }

    /** Tabela de máquinas dentro de um card arredondado. */
    private JPanel criarTabela() {
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
        tabela.getTableHeader().setReorderingAllowed(false); // impede arrastar colunas

        // Cores e fontes
        tabela.setBackground(new Color(10, 22, 40));
        tabela.setForeground(new Color(226, 232, 240));
        tabela.setGridColor(new Color(18, 35, 60));
        tabela.setRowHeight(42);
        tabela.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        tabela.getTableHeader().setBackground(new Color(10, 22, 40));
        tabela.getTableHeader().setForeground(new Color(148, 163, 184));
        tabela.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        tabela.getTableHeader().setPreferredSize(new Dimension(0, 40));

        // Alinhamento: Nome e Tipo à esquerda; o resto centralizado
        DefaultTableCellRenderer centro = criarRenderer(SwingConstants.CENTER, new Color(186, 230, 253));
        DefaultTableCellRenderer esquerda = criarRenderer(SwingConstants.LEFT, Color.WHITE);
        tabela.getColumnModel().getColumn(0).setCellRenderer(centro);
        tabela.getColumnModel().getColumn(1).setCellRenderer(esquerda);
        tabela.getColumnModel().getColumn(2).setCellRenderer(esquerda);
        tabela.getColumnModel().getColumn(3).setCellRenderer(centro);
        tabela.getColumnModel().getColumn(4).setCellRenderer(centro);

        JScrollPane scrollPane = new JScrollPane(tabela);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);

        JPanel card = new JPanel(new BorderLayout()) {
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
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        card.add(scrollPane, BorderLayout.CENTER);
        return card;
    }

    /** Rodapé: mostra "Total: X máquinas". */
    private JPanel criarRodape() {
        lblTotalMaquinas = new JLabel();
        lblTotalMaquinas.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTotalMaquinas.setForeground(new Color(148, 163, 184));

        JPanel painelRodape = new JPanel(new BorderLayout());
        painelRodape.setOpaque(false);
        painelRodape.setPreferredSize(new Dimension(0, 35));
        painelRodape.add(lblTotalMaquinas, BorderLayout.WEST);
        return painelRodape;
    }

    // =========================================================================
    // BANCO DE DADOS
    // Tabela `máquinas` (ID_Máquina, Nome, Tipo_Jogo, Valor_Partida, Ativo).
    // Nomes com acento ficam entre crases.
    // =========================================================================

    /** Conexao.conectar() devolve null quando falha; aqui vira SQLException para mostrar a mensagem de erro. */
    private Connection conectar() throws SQLException {
        Connection con = Conexao.conectar();
        if (con == null) {
            throw new SQLException("Não foi possível conectar ao banco de dados.");
        }
        return con;
    }

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

    /** Traduz erros do banco para mensagens amigáveis. */
    private String mensagemDeErro(SQLException ex) {
        if (ex.getErrorCode() == 3819) { // CHECK (Valor_Partida >= 0)
            return "O Valor da partida não pode ser negativo.";
        }
        return "Erro ao salvar no banco de dados:\n" + ex.getMessage();
    }

    // =========================================================================
    // AÇÕES: CADASTRAR / EDITAR / EXCLUIR
    // Janela (modal) de cadastro e edição, com validações, e exclusão.
    // =========================================================================

    /** Abre o formulário. linhaModelo < 0 => nova máquina; senão edita a linha indicada. */
    private void abrirModal(int linhaModelo) {
        boolean edicao = linhaModelo >= 0;
        java.awt.Window parentWindow = SwingUtilities.getWindowAncestor(this);
        JDialog modal = new JDialog(parentWindow, edicao ? "Editar Máquina" : "Cadastrar Nova Máquina",
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        modal.setSize(440, 470);
        modal.setLocationRelativeTo(parentWindow);
        modal.setLayout(new BorderLayout());

        // ---------- Campos do formulário ----------
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

        JPanel painelForm = new JPanel(new GridLayout(8, 1, 2, 2));
        painelForm.setBackground(new Color(10, 22, 40));
        painelForm.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));
        painelForm.add(criarLabel("Nome da máquina (máx. 50 caracteres):"));
        painelForm.add(txtNome);
        painelForm.add(criarLabel("Tipo de jogo (ex.: Boliche, Corrida):"));
        painelForm.add(txtTipo);
        painelForm.add(criarLabel("Valor da partida (em créditos):"));
        painelForm.add(txtValor);
        painelForm.add(criarLabel("Status:"));
        painelForm.add(cmbStatus);

        // ---------- Botões ----------
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
            // ---------- Validações ----------
            String nome = txtNome.getText().trim();
            String tipo = txtTipo.getText().trim();
            long valor;

            if (nome.isEmpty() || nome.length() > 50) {
                aviso(modal, "Informe o Nome da máquina (até 50 caracteres).");
                return;
            }
            if (tipo.isEmpty() || tipo.length() > 50) {
                aviso(modal, "Informe o Tipo de jogo (até 50 caracteres).");
                return;
            }
            try {
                valor = Long.parseLong(txtValor.getText().trim());
            } catch (NumberFormatException ex) {
                aviso(modal, "O Valor da partida deve ser um número inteiro de créditos.");
                return;
            }
            if (valor < 0) {
                aviso(modal, "O Valor da partida não pode ser negativo.");
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

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        painelBotoes.setBackground(new Color(2, 12, 27));
        painelBotoes.add(btnCancelar);
        painelBotoes.add(btnSalvar);

        modal.add(painelForm, BorderLayout.CENTER);
        modal.add(painelBotoes, BorderLayout.SOUTH);
        modal.setVisible(true);
    }

    /** Exclui a máquina selecionada (só se não tiver partidas ligadas a ela). */
    private void excluirSelecionado() {
        int linha = linhaSelecionadaNoModelo();
        if (linha < 0) {
            return;
        }
        if (!Exclusao.confirmar(this, "a máquina " + String.valueOf(modeloTabela.getValueAt(linha, 1)).trim())) {
            return;
        }
        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement("DELETE FROM `máquinas` WHERE `ID_Máquina` = ?")) {
            ps.setLong(1, idDaLinha(linha));
            ps.executeUpdate();
        } catch (SQLException ex) {
            Exclusao.erro(this, ex);
            return;
        }
        carregarMaquinas();
    }

    // =========================================================================
    // UTILITÁRIOS
    // Conversões tela <-> banco, total, linha selecionada e avisos.
    // =========================================================================

    private void atualizarTotal() {
        int count = tabela.getRowCount();
        lblTotalMaquinas.setText("Total: " + count + (count == 1 ? " máquina" : " máquinas"));
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

    /** Linha selecionada convertida para o índice do modelo (considera busca/ordenação); -1 se nenhuma. */
    private int linhaSelecionadaNoModelo() {
        int viewRow = tabela.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Selecione uma linha da tabela para editar.", "Aviso",
                    JOptionPane.WARNING_MESSAGE);
            return -1;
        }
        return tabela.convertRowIndexToModel(viewRow);
    }

    private void aviso(JDialog modal, String mensagem) {
        JOptionPane.showMessageDialog(modal, mensagem, "Aviso", JOptionPane.WARNING_MESSAGE);
    }

    // =========================================================================
    // COMPONENTES VISUAIS
    // Fábricas de botões, rótulos, campos e renderizadores no estilo da tela.
    // =========================================================================

    /** Botão azul arredondado "+ Nova Máquina". */
    private JButton criarBotaoNovo() {
        JButton btn = new JButton("+ Nova Máquina") {
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
        btn.setPreferredSize(new Dimension(150, 38));
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    /** Botão quadrado com um lápis azul (ícone desenhado em código, sem imagem). */
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

    private DefaultTableCellRenderer criarRenderer(int alinhamento, Color corTexto) {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer();
        r.setHorizontalAlignment(alinhamento);
        r.setBackground(new Color(10, 22, 40));
        r.setForeground(corTexto);
        return r;
    }

    private JLabel criarLabel(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setForeground(new Color(226, 232, 240));
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return lbl;
    }

    /** Campo de texto padrão do formulário (fundo escuro, borda azul). */
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
