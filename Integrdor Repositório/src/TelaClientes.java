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
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JButton;
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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.MaskFormatter;

/**
 * Tela de CLIENTES: lista todos os clientes do banco numa tabela com busca,
 * e permite cadastrar, editar e excluir clientes.
 *
 * Também é usada pela TelaCartao, que pergunta o nome de um cliente pelo ID
 * (buscarNomePorId).
 */
public class TelaClientes extends JPanel {

    // =========================================================================
    // CONSTANTES
    // Formato de data e texto de dica do campo de busca.
    // =========================================================================

    /** Data na tela: dd/MM/aaaa. STRICT recusa datas inexistentes (ex.: 31/02/2000). */
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter
            .ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    private static final String DICA_BUSCA = "Buscar por nome, CPF ou telefone...";

    // =========================================================================
    // CAMPOS
    // Componentes da tela que são usados em mais de um método.
    // =========================================================================
    private JTable tabela;
    private DefaultTableModel modeloTabela;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField txtBuscar;
    private JLabel lblTotalClientes;

    // =========================================================================
    // CONSTRUTOR
    // Monta as três partes da tela (topo, tabela, rodapé) e carrega os dados.
    // =========================================================================
    public TelaClientes() {
        setBackground(new Color(2, 12, 27)); // fundo azul marinho escuro
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        add(criarTopo(), BorderLayout.NORTH);
        add(criarTabela(), BorderLayout.CENTER);
        add(criarRodape(), BorderLayout.SOUTH);

        carregarClientes(); // também atualiza o "Total: X clientes"
    }

    // =========================================================================
    // MÉTODOS PÚBLICOS
    // Usados por outras telas (App e TelaCartao).
    // =========================================================================

    /** Retorna o nome do cliente com o ID informado (ex.: "001" ou "1") ou null se não existir. */
    public String buscarNomePorId(String id) {
        long idProcurado;
        try {
            idProcurado = Long.parseLong(id.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        for (int i = 0; i < modeloTabela.getRowCount(); i++) {
            if (idDaLinha(i) == idProcurado) {
                return String.valueOf(modeloTabela.getValueAt(i, 1));
            }
        }
        return null;
    }

    /** Recarrega a tabela a partir do banco. */
    public void recarregar() {
        carregarClientes();
    }

    // =========================================================================
    // MONTAGEM DA INTERFACE
    // Topo (título, busca e botões), tabela de clientes e rodapé com o total.
    // =========================================================================

    /** Topo: título "Clientes", campo de busca e botões Editar / Excluir / + Novo Cliente. */
    private JPanel criarTopo() {
        JLabel lblTitulo = new JLabel("Clientes");
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

        JButton btnNovoCliente = criarBotaoNovo();
        btnNovoCliente.addActionListener(e -> abrirModal(-1));

        JPanel painelBotoesTopo = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        painelBotoesTopo.setOpaque(false);
        painelBotoesTopo.add(btnEditar);
        painelBotoesTopo.add(btnExcluir);
        painelBotoesTopo.add(btnNovoCliente);

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

        txtBuscar = new JTextField(DICA_BUSCA);
        txtBuscar.setOpaque(false);
        txtBuscar.setForeground(new Color(100, 116, 139));
        txtBuscar.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtBuscar.setBorder(null);

        // Texto de dica (placeholder): some ao clicar, volta se o campo ficar vazio
        txtBuscar.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent evt) {
                if (txtBuscar.getText().equals(DICA_BUSCA)) {
                    txtBuscar.setText("");
                    txtBuscar.setForeground(Color.WHITE);
                }
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent evt) {
                if (txtBuscar.getText().isEmpty()) {
                    txtBuscar.setForeground(new Color(100, 116, 139));
                    txtBuscar.setText(DICA_BUSCA);
                }
            }
        });

        // Filtro da tabela (ignora maiúsculas/minúsculas)
        txtBuscar.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String texto = txtBuscar.getText();
                if (texto.equals(DICA_BUSCA) || texto.trim().isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    // Pattern.quote evita erro se o usuário digitar caracteres como ( ) + . *
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(texto)));
                }
                atualizarTotal();
            }
        });

        painelBusca.add(lblLupa, BorderLayout.WEST);
        painelBusca.add(txtBuscar, BorderLayout.CENTER);
        return painelBusca;
    }

    /**
     * Tabela de clientes dentro de um card arredondado.
     * O modelo tem 6 colunas; Nascimento e E-mail ficam escondidas (só usadas na edição).
     */
    private JPanel criarTabela() {
        String[] colunas = { "ID ", "Nome ", "CPF ", "Telefone ", "Nascimento", "E-mail" };
        modeloTabela = new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tabela = new JTable(modeloTabela);
        tabela.removeColumn(tabela.getColumnModel().getColumn(5));
        tabela.removeColumn(tabela.getColumnModel().getColumn(4));
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

        // Alinhamento: ID, CPF e Telefone centralizados; Nome à esquerda
        DefaultTableCellRenderer centro = criarRenderer(SwingConstants.CENTER, new Color(186, 230, 253));
        DefaultTableCellRenderer esquerda = criarRenderer(SwingConstants.LEFT, Color.WHITE);
        tabela.getColumnModel().getColumn(0).setCellRenderer(centro);
        tabela.getColumnModel().getColumn(1).setCellRenderer(esquerda);
        tabela.getColumnModel().getColumn(2).setCellRenderer(centro);
        tabela.getColumnModel().getColumn(3).setCellRenderer(centro);

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

    /** Rodapé: mostra "Total: X clientes". */
    private JPanel criarRodape() {
        lblTotalClientes = new JLabel();
        lblTotalClientes.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTotalClientes.setForeground(new Color(148, 163, 184));

        JPanel painelRodape = new JPanel(new BorderLayout());
        painelRodape.setOpaque(false);
        painelRodape.setPreferredSize(new Dimension(0, 35));
        painelRodape.add(lblTotalClientes, BorderLayout.WEST);
        return painelRodape;
    }

    // =========================================================================
    // BANCO DE DADOS
    // Consultas SQL da tabela "clientes": listar, inserir e atualizar.
    // =========================================================================

    private Connection conectar() throws SQLException {
        return Conexao.conectar();
    }

    /** SELECT de todos os clientes e preenchimento da tabela. */
    private void carregarClientes() {
        String sql = "SELECT ID_Cliente, Nome_Completo, CPF, Telefone, Data_Nascimento, Email "
                + "FROM clientes ORDER BY ID_Cliente";

        modeloTabela.setRowCount(0);

        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Date data = rs.getDate("Data_Nascimento");
                modeloTabela.addRow(new Object[] {
                        String.format("%03d", rs.getLong("ID_Cliente")),
                        rs.getString("Nome_Completo"),
                        formatarCpf(rs.getLong("CPF")),
                        formatarTelefone(rs.getLong("Telefone")),
                        data != null ? data.toLocalDate().format(FORMATO_DATA) : "",
                        rs.getString("Email")
                });
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this,
                    "Não foi possível carregar os clientes do banco de dados:\n" + ex.getMessage(),
                    "Erro de banco de dados", JOptionPane.ERROR_MESSAGE);
        }

        atualizarTotal();
    }

    /** INSERT de um novo cliente. Retorna o ID gerado pelo banco (AUTO_INCREMENT). */
    private long inserirCliente(String nome, long cpf, LocalDate nascimento, long telefone, String email)
            throws SQLException {
        String sql = "INSERT INTO clientes (Nome_Completo, CPF, Data_Nascimento, Telefone, Email) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, nome);
            ps.setLong(2, cpf);
            ps.setDate(3, Date.valueOf(nascimento));
            ps.setLong(4, telefone);
            ps.setString(5, email);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long id = keys.getLong(1);
                    Auditoria.registrar("CADASTRO_CLIENTE", "ID " + id + " | " + nome);
                    return id;
                }
            }
            throw new SQLException("O banco não retornou o ID do cliente cadastrado.");
        }
    }

    /** UPDATE de um cliente existente. */
    private void atualizarCliente(long id, String nome, long cpf, LocalDate nascimento, long telefone, String email)
            throws SQLException {
        String sql = "UPDATE clientes SET Nome_Completo = ?, CPF = ?, Data_Nascimento = ?, "
                + "Telefone = ?, Email = ? WHERE ID_Cliente = ?";

        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, nome);
            ps.setLong(2, cpf);
            ps.setDate(3, Date.valueOf(nascimento));
            ps.setLong(4, telefone);
            ps.setString(5, email);
            ps.setLong(6, id);
            ps.executeUpdate();
        }
        Auditoria.registrar("EDICAO_CLIENTE", "ID " + id + " | " + nome);
    }

    /** Traduz o erro 1062 (valor duplicado) do MySQL para uma mensagem amigável. */
    private String mensagemDeErro(SQLException ex) {
        if (ex.getErrorCode() == 1062) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            int pos = msg.indexOf("for key");
            String chave = pos >= 0 ? msg.substring(pos) : msg;
            if (chave.contains("cpf")) {
                return "Já existe um cliente cadastrado com este CPF.";
            }
            if (chave.contains("telefone")) {
                return "Já existe um cliente cadastrado com este telefone.";
            }
            if (chave.contains("email")) {
                return "Já existe um cliente cadastrado com este e-mail.";
            }
            return "Já existe um cliente com algum desses dados (CPF, telefone ou e-mail).";
        }
        return "Erro ao salvar no banco de dados:\n" + ex.getMessage();
    }

    // =========================================================================
    // AÇÕES: CADASTRAR / EDITAR / EXCLUIR
    // Janela (modal) de cadastro e edição, com validações, e exclusão.
    // =========================================================================

    /** Abre o formulário. linhaModelo < 0 => novo cadastro; senão edita a linha indicada. */
    private void abrirModal(int linhaModelo) {
        boolean edicao = linhaModelo >= 0;
        java.awt.Window parentWindow = SwingUtilities.getWindowAncestor(this);
        JDialog modal = new JDialog(parentWindow, edicao ? "Editar Cliente" : "Cadastrar Novo Cliente",
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        modal.setSize(440, 520);
        modal.setLocationRelativeTo(parentWindow);
        modal.setLayout(new BorderLayout());

        // ---------- Campos do formulário ----------
        JTextField txtNome = criarCampoTexto();
        JFormattedTextField txtCPF = criarCampoFormatado("###.###.###-##");
        JFormattedTextField txtDataNasc = criarCampoFormatado("##/##/####");
        JFormattedTextField txtTelefone = criarCampoFormatado("+## ## #####-####");
        JTextField txtEmail = criarCampoTexto();

        if (edicao) {
            txtNome.setText(String.valueOf(modeloTabela.getValueAt(linhaModelo, 1)));
            txtCPF.setText(String.valueOf(modeloTabela.getValueAt(linhaModelo, 2)));
            txtTelefone.setText(String.valueOf(modeloTabela.getValueAt(linhaModelo, 3)));
            txtDataNasc.setText(String.valueOf(modeloTabela.getValueAt(linhaModelo, 4)));
            txtEmail.setText(String.valueOf(modeloTabela.getValueAt(linhaModelo, 5)));
        }

        JPanel painelForm = new JPanel(new GridLayout(10, 1, 2, 2));
        painelForm.setBackground(new Color(10, 22, 40));
        painelForm.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));
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

        // ---------- Botões ----------
        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.setBackground(new Color(71, 85, 105));
        btnCancelar.setForeground(Color.WHITE);
        btnCancelar.setFocusPainted(false);
        btnCancelar.addActionListener(e -> modal.dispose());

        JButton btnSalvar = new JButton(edicao ? "Salvar Alterações" : "Salvar Cliente");
        btnSalvar.setBackground(new Color(14, 116, 233));
        btnSalvar.setForeground(Color.WHITE);
        btnSalvar.setFocusPainted(false);
        btnSalvar.addActionListener(e -> {
            // ---------- Validações (espelham as regras NOT NULL / tamanhos do banco) ----------
            String nome = txtNome.getText().trim();
            String email = txtEmail.getText().trim();
            String cpfDigitos = apenasDigitos(txtCPF.getText());
            String telDigitos = apenasDigitos(txtTelefone.getText());

            if (nome.isEmpty()) {
                aviso(modal, "Por favor, preencha o Nome.");
                return;
            }
            if (nome.length() > 50) {
                aviso(modal, "O nome pode ter no máximo 50 caracteres.");
                return;
            }
            if (cpfDigitos.length() != 11) {
                aviso(modal, "Por favor, preencha o CPF corretamente (11 dígitos).");
                return;
            }

            LocalDate nascimento;
            try {
                nascimento = LocalDate.parse(txtDataNasc.getText(), FORMATO_DATA);
            } catch (DateTimeParseException ex) {
                aviso(modal, "Data de nascimento inválida. Use o formato dd/mm/aaaa.");
                return;
            }
            if (nascimento.isAfter(LocalDate.now()) || nascimento.getYear() < 1900) {
                aviso(modal, "Data de nascimento fora do intervalo permitido.");
                return;
            }

            if (telDigitos.length() != 13) {
                aviso(modal, "Por favor, preencha o telefone completo (+DD DDD XXXXX-XXXX).");
                return;
            }
            if (email.isEmpty() || !email.contains("@") || email.length() > 50) {
                aviso(modal, "Informe um e-mail válido (máximo de 50 caracteres).");
                return;
            }

            long cpf = Long.parseLong(cpfDigitos);
            long telefone = Long.parseLong(telDigitos);

            // ---------- Grava no banco; só mexe na tabela se o banco aceitou ----------
            try {
                if (edicao) {
                    atualizarCliente(idDaLinha(linhaModelo), nome, cpf, nascimento, telefone, email);

                    modeloTabela.setValueAt(nome, linhaModelo, 1);
                    modeloTabela.setValueAt(formatarCpf(cpf), linhaModelo, 2);
                    modeloTabela.setValueAt(formatarTelefone(telefone), linhaModelo, 3);
                    modeloTabela.setValueAt(nascimento.format(FORMATO_DATA), linhaModelo, 4);
                    modeloTabela.setValueAt(email, linhaModelo, 5);
                } else {
                    long novoId = inserirCliente(nome, cpf, nascimento, telefone, email);

                    modeloTabela.addRow(new Object[] {
                            String.format("%03d", novoId),
                            nome,
                            formatarCpf(cpf),
                            formatarTelefone(telefone),
                            nascimento.format(FORMATO_DATA),
                            email
                    });
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(modal, mensagemDeErro(ex), "Erro", JOptionPane.ERROR_MESSAGE);
                return; // mantém o modal aberto para o usuário corrigir
            }

            atualizarTotal();
            JOptionPane.showMessageDialog(modal,
                    edicao ? "Cliente atualizado com sucesso!" : "Cliente cadastrado com sucesso!", "Sucesso",
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

    /** Exclui o cliente selecionado (só se não tiver cartões ou outros registros ligados a ele). */
    private void excluirSelecionado() {
        int linha = linhaSelecionadaNoModelo();
        if (linha < 0) {
            return;
        }
        String nome = String.valueOf(modeloTabela.getValueAt(linha, 1)).trim();
        if (!Exclusao.confirmar(this, "o cliente " + nome)) {
            return;
        }
        long id = idDaLinha(linha);
        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement("DELETE FROM clientes WHERE ID_Cliente = ?")) {
            ps.setLong(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            Exclusao.erro(this, ex);
            return;
        }
        Auditoria.registrar("EXCLUSAO_CLIENTE", "ID " + id + " | " + nome);
        carregarClientes();
    }

    // =========================================================================
    // UTILITÁRIOS
    // Conversões tela <-> banco, total, linha selecionada e avisos.
    // =========================================================================

    private void atualizarTotal() {
        lblTotalClientes.setText("Total: " + tabela.getRowCount() + " clientes");
    }

    /** Remove tudo que não for dígito: "123.456.789-09" -> "12345678909". */
    private String apenasDigitos(String texto) {
        return texto == null ? "" : texto.replaceAll("\\D", "");
    }

    /** 12345678909 -> "123.456.789-09" (completa com zeros à esquerda até 11 dígitos). */
    private String formatarCpf(long cpf) {
        String d = String.format("%011d", cpf);
        return d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }

    /** 5548999998888 -> "+55 48 99999-8888". Se não tiver 13 dígitos, devolve o número puro. */
    private String formatarTelefone(long telefone) {
        String d = String.valueOf(telefone);
        if (d.length() != 13) {
            return d;
        }
        return "+" + d.substring(0, 2) + " " + d.substring(2, 4) + " " + d.substring(4, 9) + "-" + d.substring(9);
    }

    /** Lê o ID real (numérico) da linha do modelo: "003" -> 3. */
    private long idDaLinha(int linhaModelo) {
        return Long.parseLong(String.valueOf(modeloTabela.getValueAt(linhaModelo, 0)).trim());
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

    /** Botão azul arredondado "+ Novo Cliente". */
    private JButton criarBotaoNovo() {
        JButton btn = new JButton("+ Novo Cliente") {
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
        btn.setPreferredSize(new Dimension(140, 38));
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

    private JTextField criarCampoTexto() {
        return estilizarCampo(new JTextField());
    }

    /** Campo com máscara (ex.: "###.###.###-##"), que só aceita o formato indicado. */
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

    /** Visual padrão dos campos do formulário (fundo escuro, borda azul). */
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
