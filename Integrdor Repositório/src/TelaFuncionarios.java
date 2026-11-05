import java.awt.BorderLayout;
import java.awt.CardLayout;
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
import java.awt.geom.Point2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
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
import javax.swing.JPasswordField;
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

/**
 * Tela de FUNCIONÁRIOS (só aparece para quem tem permissão de administrador).
 *
 * Mostra a lista de funcionários do banco numa tabela com busca, e permite
 * cadastrar, editar e excluir. As senhas nunca ficam em texto puro: são
 * guardadas como hash (PBKDF2) na tabela funcionários, e o "salt" de cada
 * usuário fica na tabela Seguranca_Senhas.
 */
public class TelaFuncionarios extends JPanel {

    // =========================================================================
    // CONSTANTES VISUAIS
    // Cores e textos fixos usados em toda a tela.
    // =========================================================================
    private static final String PLACEHOLDER_BUSCA = "Buscar por nome, CPF, cargo ou usuário...";
    private static final Color FUNDO = new Color(2, 12, 27);
    private static final Color CARD = new Color(10, 22, 40);
    private static final Color BORDA = new Color(23, 42, 69);
    private static final Color AZUL = new Color(14, 116, 233);
    private static final Color TEXTO_SUAVE = new Color(148, 163, 184);
    private static final Color CAMPO = new Color(13, 27, 46);

    // =========================================================================
    // CAMPOS
    // Componentes da tela e controle do carregamento.
    // =========================================================================

    // Colunas do modelo. As 3 últimas (E-mail, Salt, Hash) ficam escondidas na tabela,
    // mas são guardadas para montar o objeto Funcionario na edição.
    private static final String[] COLUNAS = { "ID", "Nome", "CPF", "Telefone", "Cargo", "Usuário", "Admin", "Status", "E-mail", "Salt", "Hash" };

    private JTable tabela;
    private DefaultTableModel modeloTabela;
    private TableRowSorter<DefaultTableModel> sorter;
    private JLabel lblTotal;

    private JPanel cardTabela;      // alterna entre a tabela e o "ghost" (animação de carregando)
    private GhostPanel ghostPanel;
    private boolean carregando = false;
    private int cargaId = 0;        // descarta respostas de cargas antigas

    // =========================================================================
    // CONSTRUTOR
    // Monta o topo (título, busca, botões), a tabela e o rodapé, e carrega os dados.
    // =========================================================================
    public TelaFuncionarios() {
        setBackground(FUNDO);
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        add(criarTopo(), BorderLayout.NORTH);
        add(criarAreaTabela(), BorderLayout.CENTER);
        add(criarRodape(), BorderLayout.SOUTH);

        carregarTabela();
    }

    /** Chamado pelo App sempre que a tela é aberta. */
    public void recarregar() {
        carregarTabela();
    }

    // =========================================================================
    // MONTAGEM DA INTERFACE
    // Cria cada pedaço visual da tela.
    // =========================================================================

    /** Título + campo de busca + botões Editar / Excluir / Novo. */
    private JPanel criarTopo() {
        JPanel topo = new JPanel(new BorderLayout(0, 15));
        topo.setOpaque(false);

        JLabel lblTitulo = new JLabel("Funcionários");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitulo.setForeground(Color.WHITE);

        JButton btnNovo = criarBotao("+ Novo Funcionário", true, 170);
        btnNovo.addActionListener(e -> abrirModal(null));

        JButton btnEditar = criarBotao("Editar", false, 100);
        btnEditar.addActionListener(e -> {
            Funcionario f = funcionarioSelecionado();
            if (f != null) abrirModal(f);
        });

        JButton btnExcluir = Exclusao.criarBotao();
        btnExcluir.addActionListener(e -> excluirSelecionado());

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        painelBotoes.setOpaque(false);
        painelBotoes.add(btnEditar);
        painelBotoes.add(btnExcluir);
        painelBotoes.add(btnNovo);

        JPanel painelAcoes = new JPanel(new BorderLayout(15, 0));
        painelAcoes.setOpaque(false);
        painelAcoes.add(criarBusca(PLACEHOLDER_BUSCA, this::filtrar), BorderLayout.WEST);
        painelAcoes.add(painelBotoes, BorderLayout.EAST);

        topo.add(lblTitulo, BorderLayout.NORTH);
        topo.add(painelAcoes, BorderLayout.CENTER);
        return topo;
    }

    /** Tabela de funcionários dentro de um card arredondado (com o ghost de carregamento). */
    private JPanel criarAreaTabela() {
        modeloTabela = new DefaultTableModel(COLUNAS, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        tabela = new JTable(modeloTabela);
        tabela.removeColumn(tabela.getColumnModel().getColumn(10)); // Hash
        tabela.removeColumn(tabela.getColumnModel().getColumn(9));  // Salt
        tabela.removeColumn(tabela.getColumnModel().getColumn(8));  // Email

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

        // Alinhamento e cor de cada coluna visível (a 7, Status, tem cor verde/vermelha)
        Color azulClaro = new Color(186, 230, 253);
        int[] alinhamentos = { SwingConstants.CENTER, SwingConstants.LEFT, SwingConstants.CENTER, SwingConstants.CENTER,
                SwingConstants.LEFT, SwingConstants.CENTER, SwingConstants.CENTER };
        Color[] cores = { azulClaro, Color.WHITE, azulClaro, azulClaro, Color.WHITE, azulClaro, azulClaro };
        for (int c = 0; c < alinhamentos.length; c++) {
            tabela.getColumnModel().getColumn(c).setCellRenderer(criarRenderer(alinhamentos[c], cores[c]));
        }
        tabela.getColumnModel().getColumn(7).setCellRenderer(criarRendererStatus());

        ghostPanel = new GhostPanel(7);
        cardTabela = new JPanel(new CardLayout()) {
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
        return cardTabela;
    }

    /** Rodapé com o total de funcionários. */
    private JPanel criarRodape() {
        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setOpaque(false);
        rodape.setPreferredSize(new Dimension(0, 35));
        lblTotal = new JLabel();
        lblTotal.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTotal.setForeground(TEXTO_SUAVE);
        rodape.add(lblTotal, BorderLayout.WEST);
        return rodape;
    }

    // =========================================================================
    // CARREGAMENTO DE DADOS (BANCO)
    // Busca os funcionários em segundo plano e preenche a tabela.
    // =========================================================================

    private Connection conectar() throws SQLException {
        Connection con = Conexao.conectar();
        if (con == null) throw new SQLException("Sem conexão com o banco.");
        return con;
    }

    /** Mostra o ghost, busca os funcionários (com o Salt) e depois mostra a tabela. */
    private void carregarTabela() {
        final int idCarga = ++cargaId;
        carregando = true;
        ((CardLayout) cardTabela.getLayout()).show(cardTabela, "ghost");
        ghostPanel.iniciar();
        atualizarTotal();

        async(() -> {
            List<Object[]> linhas = new ArrayList<>();

            // LEFT JOIN com a Seguranca_Senhas para pegar o Salt
            String sql = "SELECT f.*, s.Salt FROM funcionários f " +
                         "LEFT JOIN Seguranca_Senhas s ON f.Usuário = s.Usuario " +
                         "ORDER BY f.ID_Funcionário";

            try (Connection con = conectar();
                 PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String salt = rs.getString("Salt");
                    linhas.add(new Object[] {
                        String.format("%03d", rs.getLong("ID_Funcionário")),
                        rs.getString("Nome_Completo"),
                        rs.getString("CPF"),
                        rs.getString("Telefone"),
                        rs.getString("Cargo"),
                        rs.getString("Usuário"),
                        rs.getBoolean("Permissão_ADM") ? "Sim" : "Não",
                        rs.getBoolean("Ativo") ? "Ativo" : "Bloqueado",
                        rs.getString("Email"),
                        salt == null ? "" : salt,
                        rs.getString("Senha") // Hash
                    });
                }
            }
            return linhas;
        }, linhas -> {
            if (idCarga != cargaId) return;
            modeloTabela.setRowCount(0);
            for (Object[] l : linhas) modeloTabela.addRow(l);
            finalizarCarga();
        }, ex -> {
            if (idCarga != cargaId) return;
            finalizarCarga();
            JOptionPane.showMessageDialog(this, "Erro ao carregar funcionários:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        });
    }

    /** Sai do modo "carregando": esconde o ghost e volta a mostrar a tabela. */
    private void finalizarCarga() {
        carregando = false;
        ((CardLayout) cardTabela.getLayout()).show(cardTabela, "tabela");
        ghostPanel.parar();
        atualizarTotal();
    }

    // =========================================================================
    // BUSCA E TOTAL
    // Filtra as linhas da tabela conforme o texto digitado.
    // =========================================================================

    private void filtrar(String texto) {
        if (texto == null || texto.trim().isEmpty()) sorter.setRowFilter(null);
        else sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(texto.trim())));
        atualizarTotal();
    }

    private void atualizarTotal() {
        if (carregando) {
            lblTotal.setText("Carregando...");
            return;
        }
        lblTotal.setText("Total: " + tabela.getRowCount() + " funcionários");
    }

    // =========================================================================
    // AÇÕES: SELECIONAR, EXCLUIR
    // =========================================================================

    /** Monta um Funcionario a partir da linha selecionada (ou avisa se nada foi selecionado). */
    private Funcionario funcionarioSelecionado() {
        int viewRow = tabela.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Selecione uma linha.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        int m = tabela.convertRowIndexToModel(viewRow);

        return new Funcionario(
            String.valueOf(modeloTabela.getValueAt(m, 0)),
            String.valueOf(modeloTabela.getValueAt(m, 1)),
            String.valueOf(modeloTabela.getValueAt(m, 2)),
            String.valueOf(modeloTabela.getValueAt(m, 3)),
            String.valueOf(modeloTabela.getValueAt(m, 8)), // Email
            String.valueOf(modeloTabela.getValueAt(m, 4)), // Cargo
            String.valueOf(modeloTabela.getValueAt(m, 5)), // Usuario
            String.valueOf(modeloTabela.getValueAt(m, 9)), // Salt
            String.valueOf(modeloTabela.getValueAt(m, 10)),// Hash
            "Sim".equals(modeloTabela.getValueAt(m, 6)),
            "Ativo".equals(modeloTabela.getValueAt(m, 7)),
            false
        );
    }

    /**
     * Exclui o funcionário selecionado. Não deixa apagar o próprio usuário logado,
     * e falha se ele já tiver recargas, resgates ou outros registros feitos por ele.
     */
    private void excluirSelecionado() {
        Funcionario f = funcionarioSelecionado();
        if (f == null) {
            return;
        }
        if (Sessao.getLogado() != null
                && Long.parseLong(f.getId().trim()) == Long.parseLong(Sessao.getLogado().getId().trim())) {
            JOptionPane.showMessageDialog(this, "Você não pode excluir o funcionário que está logado.",
                    "Exclusão não permitida", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!Exclusao.confirmar(this, "o funcionário " + f.getNome())) {
            return;
        }
        long id = Long.parseLong(f.getId().trim());
        try (Connection con = conectar()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM funcionários WHERE ID_Funcionário = ?")) {
                    ps.setLong(1, id);
                    ps.executeUpdate();
                }
                // Apaga também o salt desse login (só depois de o funcionário sair com sucesso)
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM Seguranca_Senhas WHERE Usuario = ?")) {
                    ps.setString(1, f.getUsuario());
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException ex) {
                con.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            Exclusao.erro(this, ex);
            return;
        }
        recarregar();
    }

    // =========================================================================
    // AÇÕES: CADASTRAR / EDITAR (JANELA MODAL)
    // Formulário usado tanto para "Novo" (existente == null) quanto para "Editar".
    // =========================================================================

    private void abrirModal(Funcionario existente) {
        if (!Sessao.isManagerMaster()) {
            JOptionPane.showMessageDialog(this, "Apenas administradores podem cadastrar/editar.", "Acesso negado", JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean edicao = existente != null;
        JDialog modal = new JDialog(SwingUtilities.getWindowAncestor(this),
                edicao ? "Editar Funcionário" : "Novo Funcionário", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        modal.setSize(560, 560);
        modal.setLocationRelativeTo(this);
        modal.setLayout(new BorderLayout());

        JPanel painelForm = new JPanel(new GridLayout(9, 2, 10, 10));
        painelForm.setBackground(CARD);
        painelForm.setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        // --- Campos do formulário ---
        JTextField txtNome = criarCampoTexto();
        JTextField txtCPF = criarCampoTexto();
        JTextField txtTelefone = criarCampoTexto();
        JTextField txtEmail = criarCampoTexto();
        JTextField txtCargo = criarCampoTexto();
        JTextField txtUsuario = criarCampoTexto();
        JPasswordField txtSenha = new JPasswordField();
        estilizarCampo(txtSenha);

        JComboBox<String> cmbAdmin = new JComboBox<>(new String[] { "Não", "Sim" });
        cmbAdmin.setBackground(FUNDO); cmbAdmin.setForeground(Color.WHITE);
        JComboBox<String> cmbStatus = new JComboBox<>(new String[] { "Ativo", "Bloqueado" });
        cmbStatus.setBackground(FUNDO); cmbStatus.setForeground(Color.WHITE);

        // Na edição, preenche com os dados atuais (a senha fica vazia = manter)
        if (edicao) {
            txtNome.setText(existente.getNome());
            txtCPF.setText(existente.getCpf());
            txtTelefone.setText(existente.getTelefone());
            txtEmail.setText(existente.getEmail());
            txtCargo.setText(existente.getCargo());
            txtUsuario.setText(existente.getUsuario());
            cmbAdmin.setSelectedItem(existente.isPermissaoAdmin() ? "Sim" : "Não");
            cmbStatus.setSelectedItem(existente.isAtivo() ? "Ativo" : "Bloqueado");
        }

        painelForm.add(criarLabel("Nome Completo:")); painelForm.add(txtNome);
        painelForm.add(criarLabel("CPF (só números):")); painelForm.add(txtCPF);
        painelForm.add(criarLabel("Telefone:")); painelForm.add(txtTelefone);
        painelForm.add(criarLabel("E-mail:")); painelForm.add(txtEmail);
        painelForm.add(criarLabel("Cargo:")); painelForm.add(txtCargo);
        painelForm.add(criarLabel("Usuário (login):")); painelForm.add(txtUsuario);
        painelForm.add(criarLabel(edicao ? "Senha (vazio = manter):" : "Senha:")); painelForm.add(txtSenha);
        painelForm.add(criarLabel("Permissão administrativa:")); painelForm.add(cmbAdmin);
        painelForm.add(criarLabel("Status:")); painelForm.add(cmbStatus);

        // --- Botões Cancelar / Salvar ---
        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        painelBotoes.setBackground(FUNDO);

        JButton btnCancelar = criarBotao("Cancelar", false, 110);
        btnCancelar.addActionListener(e -> modal.dispose());

        JButton btnSalvar = criarBotao("Salvar", true, 110);
        btnSalvar.addActionListener(e -> {
            String nome = txtNome.getText().trim();
            String cpf = txtCPF.getText().replaceAll("\\D", "");
            String telefone = txtTelefone.getText().replaceAll("\\D", "");
            String email = txtEmail.getText().trim();
            String cargo = txtCargo.getText().trim();
            String usuario = txtUsuario.getText().trim();
            String senha = new String(txtSenha.getPassword());
            boolean admin = "Sim".equals(cmbAdmin.getSelectedItem());
            boolean ativo = "Ativo".equals(cmbStatus.getSelectedItem());

            // Validações
            if (nome.isEmpty() || cargo.isEmpty() || usuario.isEmpty() || cpf.isEmpty()) {
                JOptionPane.showMessageDialog(modal, "Preencha Nome, CPF, Cargo e Usuário.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!edicao && senha.isEmpty()) {
                JOptionPane.showMessageDialog(modal, "Defina uma senha.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }

            // Grava no banco
            try {
                if (edicao) {
                    atualizarFuncionario(existente.getId(), existente.getUsuario(), nome, cpf, telefone, email, cargo, usuario, senha, admin, ativo);
                } else {
                    inserirFuncionario(nome, cpf, telefone, email, cargo, usuario, senha, admin, ativo);
                }
                modal.dispose();
                carregarTabela();
            } catch (SQLException ex) {
                if (ex.getErrorCode() == 1062) { // chave duplicada
                    JOptionPane.showMessageDialog(modal, "CPF, E-mail ou Usuário já cadastrado.", "Erro", JOptionPane.ERROR_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(modal, "Erro no banco:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        painelBotoes.add(btnCancelar);
        painelBotoes.add(btnSalvar);

        modal.add(painelForm, BorderLayout.CENTER);
        modal.add(painelBotoes, BorderLayout.SOUTH);
        modal.setVisible(true);
    }

    /** INSERT do novo funcionário (com hash da senha) + salt na Seguranca_Senhas, numa transação. */
    private void inserirFuncionario(String nome, String cpf, String tel, String email, String cargo, String user, String senha, boolean admin, boolean ativo) throws SQLException {
        byte[] saltBytes = novoSalt();
        String salt = Base64.getEncoder().encodeToString(saltBytes);
        String hash = Funcionario.gerarHash(senha, saltBytes);

        try (Connection con = conectar()) {
            con.setAutoCommit(false);
            try {
                String sql = "INSERT INTO funcionários (Nome_Completo, CPF, Telefone, Email, Cargo, Usuário, Senha, Permissão_ADM, Ativo) VALUES (?,?,?,?,?,?,?,?,?)";
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    ps.setString(1, nome); ps.setString(2, cpf); ps.setString(3, tel); ps.setString(4, email);
                    ps.setString(5, cargo); ps.setString(6, user); ps.setString(7, hash);
                    ps.setBoolean(8, admin); ps.setBoolean(9, ativo);
                    ps.executeUpdate();
                }
                salvarSalt(con, user, salt);
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * UPDATE do funcionário. Só troca a senha se uma nova foi digitada, e se o
     * nome de usuário mudou, leva o salt junto para o novo nome.
     */
    private void atualizarFuncionario(String id, String usuarioAnterior, String nome, String cpf, String tel, String email, String cargo, String user, String senhaNova, boolean admin, boolean ativo) throws SQLException {
        boolean mudarSenha = !senhaNova.isEmpty();
        boolean renomeado = usuarioAnterior != null && !usuarioAnterior.equals(user);

        try (Connection con = conectar()) {
            con.setAutoCommit(false);
            try {
                String sql = "UPDATE funcionários SET Nome_Completo=?, CPF=?, Telefone=?, Email=?, Cargo=?, Usuário=?, Permissão_ADM=?, Ativo=? " +
                             (mudarSenha ? ", Senha=? " : "") + "WHERE ID_Funcionário=?";

                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    int i = 1;
                    ps.setString(i++, nome); ps.setString(i++, cpf); ps.setString(i++, tel); ps.setString(i++, email);
                    ps.setString(i++, cargo); ps.setString(i++, user); ps.setBoolean(i++, admin); ps.setBoolean(i++, ativo);

                    String saltStr = null;
                    if (mudarSenha) {
                        byte[] saltBytes = novoSalt();
                        ps.setString(i++, Funcionario.gerarHash(senhaNova, saltBytes));
                        saltStr = Base64.getEncoder().encodeToString(saltBytes);
                    }
                    ps.setLong(i, Long.parseLong(id));
                    ps.executeUpdate();

                    // O login confere a senha com o Salt guardado em Seguranca_Senhas, usando o nome de usuário.
                    // Se o usuário mudou de nome, o Salt precisa ir junto; senão o login fica "sem senha configurada".
                    if (renomeado) {
                        try (PreparedStatement mover = con.prepareStatement(
                                "INSERT INTO Seguranca_Senhas (Usuario, Salt) SELECT ?, Salt FROM Seguranca_Senhas "
                                + "WHERE Usuario = ? ON DUPLICATE KEY UPDATE Salt = VALUES(Salt)")) {
                            mover.setString(1, user);
                            mover.setString(2, usuarioAnterior);
                            mover.executeUpdate();
                        }
                        try (PreparedStatement apagar = con.prepareStatement(
                                "DELETE FROM Seguranca_Senhas WHERE Usuario = ?")) {
                            apagar.setString(1, usuarioAnterior);
                            apagar.executeUpdate();
                        }
                    }

                    if (saltStr != null) {
                        salvarSalt(con, user, saltStr);
                    }
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    // =========================================================================
    // UTILITÁRIOS DE SENHA E EXECUÇÃO
    // =========================================================================

    /** 16 bytes aleatórios usados como salt da senha. */
    private static byte[] novoSalt() {
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
        return saltBytes;
    }

    /** Grava (ou substitui) o salt do usuário na tabela Seguranca_Senhas. */
    private static void salvarSalt(Connection con, String usuario, String salt) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO Seguranca_Senhas (Usuario, Salt) VALUES (?, ?) ON DUPLICATE KEY UPDATE Salt = VALUES(Salt)")) {
            ps.setString(1, usuario);
            ps.setString(2, salt);
            ps.executeUpdate();
        }
    }

    /** Roda a tarefa fora da tela (sem travar) e volta para a tela com o resultado ou o erro. */
    private <T> void async(Callable<T> tarefa, Consumer<T> aoConcluir, Consumer<Exception> aoFalhar) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return tarefa.call(); }
            @Override protected void done() {
                try { aoConcluir.accept(get()); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                catch (ExecutionException ee) {
                    Throwable c = ee.getCause();
                    aoFalhar.accept(c instanceof Exception ? (Exception) c : new Exception(c));
                }
            }
        }.execute();
    }

    // =========================================================================
    // FÁBRICA DE COMPONENTES VISUAIS
    // Botões, campos, rótulos, busca e renderizadores com o estilo da tela.
    // =========================================================================

    /** Célula comum da tabela: alinhamento e cor de texto definidos por coluna. */
    private DefaultTableCellRenderer criarRenderer(int alinhamento, Color cor) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object valor, boolean sel, boolean foco, int linha, int coluna) {
                super.getTableCellRendererComponent(t, valor, sel, false, linha, coluna);
                setHorizontalAlignment(alinhamento);
                setBackground(sel ? t.getSelectionBackground() : CARD);
                setForeground(sel ? Color.WHITE : cor);
                return this;
            }
        };
    }

    /** Célula de Status: verde para "Ativo", vermelho para "Bloqueado". */
    private DefaultTableCellRenderer criarRendererStatus() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object valor, boolean sel, boolean foco, int linha, int coluna) {
                super.getTableCellRendererComponent(t, valor, sel, false, linha, coluna);
                setHorizontalAlignment(SwingConstants.CENTER);
                setBackground(sel ? t.getSelectionBackground() : CARD);
                setForeground("Ativo".equals(valor) ? new Color(74, 222, 128) : new Color(248, 113, 113));
                return this;
            }
        };
    }

    /** Botão arredondado: azul (primário) ou escuro (secundário). */
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
        estilizarCampo(txt);
        return txt;
    }

    private void estilizarCampo(JTextField txt) {
        txt.setBackground(FUNDO);
        txt.setForeground(Color.WHITE);
        txt.setCaretColor(Color.WHITE);
        txt.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txt.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 48, 80), 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
    }

    private JLabel criarLabel(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setForeground(new Color(226, 232, 240));
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return lbl;
    }

    /** Campo de busca com lupa e texto de dica (placeholder); chama aoDigitar a cada tecla. */
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
            @Override public void focusGained(java.awt.event.FocusEvent e) { txt.repaint(); }
            @Override public void focusLost(java.awt.event.FocusEvent e) { txt.repaint(); }
        });
        txt.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) { aoDigitar.accept(txt.getText()); }
        });

        painel.add(lupa, BorderLayout.WEST);
        painel.add(txt, BorderLayout.CENTER);
        return painel;
    }

    // =========================================================================
    // CLASSE INTERNA: GHOST PANEL
    // "Esqueleto" animado de tabela mostrado enquanto os dados carregam.
    // =========================================================================
    private static class GhostPanel extends JPanel {
        private static final Color BASE = new Color(20, 36, 60);
        private static final Color BRILHO = new Color(38, 64, 102);
        private static final int ALTURA_CABECALHO = 40;
        private static final int ALTURA_LINHA = 42;
        private final int colunas;
        private float fase = -0.3f;
        private final Timer timer = new Timer(30, e -> {
            fase += 0.025f;
            if (fase > 1.3f) fase = -0.3f;
            repaint();
        });

        GhostPanel(int colunas) { this.colunas = colunas; setOpaque(false); }
        void iniciar() { fase = -0.3f; if (!timer.isRunning()) timer.start(); }
        void parar() { timer.stop(); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth(); int h = getHeight();
            int celula = Math.max(1, w / colunas);
            int linhas = Math.max(1, (h - ALTURA_CABECALHO) / ALTURA_LINHA);

            // Linhas divisórias
            g2.setColor(new Color(18, 35, 60));
            g2.drawLine(0, ALTURA_CABECALHO, w, ALTURA_CABECALHO);
            for (int r = 0; r < linhas; r++) {
                int y = ALTURA_CABECALHO + (r + 1) * ALTURA_LINHA - 1;
                g2.drawLine(0, y, w, y);
            }

            // Brilho que passa da esquerda para a direita
            float cx = fase * w;
            float meia = Math.max(180f, w * 0.22f);
            g2.setPaint(new LinearGradientPaint(
                    new Point2D.Float(cx - meia, 0), new Point2D.Float(cx + meia, 0),
                    new float[] { 0f, 0.5f, 1f }, new Color[] { BASE, BRILHO, BASE }));

            // Barrinhas do cabeçalho e das linhas
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
