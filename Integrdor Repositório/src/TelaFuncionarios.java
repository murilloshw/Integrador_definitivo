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

public class TelaFuncionarios extends JPanel {

    private static final String PLACEHOLDER_BUSCA = "Buscar por nome, CPF, cargo ou usuário...";
    private static final Color FUNDO = new Color(2, 12, 27);
    private static final Color CARD = new Color(10, 22, 40);
    private static final Color BORDA = new Color(23, 42, 69);
    private static final Color AZUL = new Color(14, 116, 233);
    private static final Color TEXTO_SUAVE = new Color(148, 163, 184);
    private static final Color CAMPO = new Color(13, 27, 46);

    private JTable tabela;
    private DefaultTableModel modeloTabela;
    private TableRowSorter<DefaultTableModel> sorter;
    private JLabel lblTotal;

    private JPanel cardTabela;
    private GhostPanel ghostPanel;
    private boolean carregando = false;
    private int cargaId = 0;

    private Connection conectar() throws SQLException {
        Connection con = Conexao.conectar();
        if (con == null) throw new SQLException("Sem conexão com o banco.");
        return con;
    }

    public TelaFuncionarios() {
        setBackground(FUNDO);
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        JPanel topo = new JPanel(new BorderLayout(0, 15));
        topo.setOpaque(false);

        JLabel lblTitulo = new JLabel("Funcionários");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitulo.setForeground(Color.WHITE);

        JPanel painelAcoes = new JPanel(new BorderLayout(15, 0));
        painelAcoes.setOpaque(false);

        JPanel painelBusca = criarBusca(PLACEHOLDER_BUSCA, this::filtrar);

        JButton btnNovo = criarBotao("+ Novo Funcionário", true, 170);
        btnNovo.addActionListener(e -> abrirModal(null));
        
        JButton btnEditar = criarBotao("Editar", false, 100);
        btnEditar.addActionListener(e -> {
            Funcionario f = funcionarioSelecionado();
            if (f != null) abrirModal(f);
        });

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        painelBotoes.setOpaque(false);
        painelBotoes.add(btnEditar);
        painelBotoes.add(btnNovo);

        painelAcoes.add(painelBusca, BorderLayout.WEST);
        painelAcoes.add(painelBotoes, BorderLayout.EAST);

        topo.add(lblTitulo, BorderLayout.NORTH);
        topo.add(painelAcoes, BorderLayout.CENTER);
        add(topo, BorderLayout.NORTH);

        // Tabela e Colunas
        String[] colunas = { "ID", "Nome", "CPF", "Telefone", "Cargo", "Usuário", "Admin", "Status", "E-mail", "Salt", "Hash" };
        modeloTabela = new DefaultTableModel(colunas, 0) {
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

        Color azulClaro = new Color(186, 230, 253);
        tabela.getColumnModel().getColumn(0).setCellRenderer(criarRenderer(SwingConstants.CENTER, azulClaro));
        tabela.getColumnModel().getColumn(1).setCellRenderer(criarRenderer(SwingConstants.LEFT, Color.WHITE));
        tabela.getColumnModel().getColumn(2).setCellRenderer(criarRenderer(SwingConstants.CENTER, azulClaro));
        tabela.getColumnModel().getColumn(3).setCellRenderer(criarRenderer(SwingConstants.CENTER, azulClaro));
        tabela.getColumnModel().getColumn(4).setCellRenderer(criarRenderer(SwingConstants.LEFT, Color.WHITE));
        tabela.getColumnModel().getColumn(5).setCellRenderer(criarRenderer(SwingConstants.CENTER, azulClaro));
        tabela.getColumnModel().getColumn(6).setCellRenderer(criarRenderer(SwingConstants.CENTER, azulClaro));
        tabela.getColumnModel().getColumn(7).setCellRenderer(criarRendererStatus());

        // Ghost Loading
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
        add(cardTabela, BorderLayout.CENTER);

        // Rodapé
        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setOpaque(false);
        rodape.setPreferredSize(new Dimension(0, 35));
        lblTotal = new JLabel();
        lblTotal.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTotal.setForeground(TEXTO_SUAVE);
        rodape.add(lblTotal, BorderLayout.WEST);
        add(rodape, BorderLayout.SOUTH);

        carregarTabela();
    }

    public void recarregar() {
        carregarTabela();
    }

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
        int n = tabela.getRowCount();
        lblTotal.setText("Total: " + n + " funcionários");
    }

    private void carregarTabela() {
        final int idCarga = ++cargaId;
        carregando = true;
        ((CardLayout) cardTabela.getLayout()).show(cardTabela, "ghost");
        ghostPanel.iniciar();
        atualizarTotal();

        async(() -> {
            List<Object[]> linhas = new ArrayList<>();
            
            // SELECT faz LEFT JOIN com a Seguranca_Senhas para pegar o Salt
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
            carregando = false;
            ((CardLayout) cardTabela.getLayout()).show(cardTabela, "tabela");
            ghostPanel.parar();
            atualizarTotal();
        }, ex -> {
            if (idCarga != cargaId) return;
            carregando = false;
            ((CardLayout) cardTabela.getLayout()).show(cardTabela, "tabela");
            ghostPanel.parar();
            atualizarTotal();
            JOptionPane.showMessageDialog(this, "Erro ao carregar funcionários:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        });
    }

    private Funcionario funcionarioSelecionado() {
        int viewRow = tabela.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Selecione uma linha.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        int m = tabela.convertRowIndexToModel(viewRow);
        
        // Como todos agora vêm do banco, montamos o objeto diretamente
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
            String idAtual = edicao ? existente.getId() : null;

            if (nome.isEmpty() || cargo.isEmpty() || usuario.isEmpty() || cpf.isEmpty()) {
                JOptionPane.showMessageDialog(modal, "Preencha Nome, CPF, Cargo e Usuário.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (!edicao && senha.isEmpty()) {
                JOptionPane.showMessageDialog(modal, "Defina uma senha.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }

            try {
                if (edicao) {
                    atualizarFuncionario(idAtual, nome, cpf, telefone, email, cargo, usuario, senha, admin, ativo);
                } else {
                    inserirFuncionario(nome, cpf, telefone, email, cargo, usuario, senha, admin, ativo);
                }
                modal.dispose();
                carregarTabela();
            } catch (SQLException ex) {
                if (ex.getErrorCode() == 1062) {
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

    private void inserirFuncionario(String nome, String cpf, String tel, String email, String cargo, String user, String senha, boolean admin, boolean ativo) throws SQLException {
        byte[] saltBytes = new byte[16];
        new SecureRandom().nextBytes(saltBytes);
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
                
                // Insere a senha na tabela de segurança
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO Seguranca_Senhas (Usuario, Salt) VALUES (?, ?)")) {
                    ps.setString(1, user);
                    ps.setString(2, salt);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        }
    }

    private void atualizarFuncionario(String id, String nome, String cpf, String tel, String email, String cargo, String user, String senhaNova, boolean admin, boolean ativo) throws SQLException {
        boolean mudarSenha = !senhaNova.isEmpty();
        
        try (Connection con = conectar()) {
            con.setAutoCommit(false);
            try {
                String sql = "UPDATE funcionários SET Nome_Completo=?, CPF=?, Telefone=?, Email=?, Cargo=?, Usuário=?, Permissão_ADM=?, Ativo=? " +
                             (mudarSenha ? ", Senha=? " : "") + "WHERE ID_Funcionário=?";
                
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    int i = 1;
                    ps.setString(i++, nome); ps.setString(i++, cpf); ps.setString(i++, tel); ps.setString(i++, email);
                    ps.setString(i++, cargo); ps.setString(i++, user); ps.setBoolean(i++, admin); ps.setBoolean(i++, ativo);
                    
                    if (mudarSenha) {
                        byte[] saltBytes = new byte[16];
                        new SecureRandom().nextBytes(saltBytes);
                        ps.setString(i++, Funcionario.gerarHash(senhaNova, saltBytes));
                        ps.setLong(i++, Long.parseLong(id));
                        ps.executeUpdate();

                        // Salva o Salt na tabela de segurança
                        String saltStr = Base64.getEncoder().encodeToString(saltBytes);
                        try (PreparedStatement ps2 = con.prepareStatement(
                                "INSERT INTO Seguranca_Senhas (Usuario, Salt) VALUES (?, ?) ON DUPLICATE KEY UPDATE Salt = ?")) {
                            ps2.setString(1, user);
                            ps2.setString(2, saltStr);
                            ps2.setString(3, saltStr);
                            ps2.executeUpdate();
                        }
                    } else {
                        ps.setLong(i++, Long.parseLong(id));
                        ps.executeUpdate();
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
    // MÉTODOS VISUAIS E HELPERS
    // =========================================================================
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

            g2.setColor(new Color(18, 35, 60));
            g2.drawLine(0, ALTURA_CABECALHO, w, ALTURA_CABECALHO);
            for (int r = 0; r < linhas; r++) {
                int y = ALTURA_CABECALHO + (r + 1) * ALTURA_LINHA - 1;
                g2.drawLine(0, y, w, y);
            }

            float cx = fase * w;
            float meia = Math.max(180f, w * 0.22f);
            g2.setPaint(new LinearGradientPaint(
                    new Point2D.Float(cx - meia, 0), new Point2D.Float(cx + meia, 0),
                    new float[] { 0f, 0.5f, 1f }, new Color[] { BASE, BRILHO, BASE }));

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