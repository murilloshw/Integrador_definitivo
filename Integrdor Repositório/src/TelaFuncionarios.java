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
import java.text.ParseException;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
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
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.MaskFormatter;

/**
 * Cadastro de funcionários. Acessível apenas pelo Manager Master.
 * Os dados ficam em RepositorioFuncionarios (e é por ele que o login funciona).
 */
public class TelaFuncionarios extends JPanel {

    private static final String PLACEHOLDER_BUSCA = "Buscar por nome, CPF, cargo ou usuário...";

    private JTable tabela;
    private DefaultTableModel modeloTabela;
    private TableRowSorter<DefaultTableModel> sorter;
    private JTextField txtBuscar;
    private JLabel lblTotalFuncionarios;

    public TelaFuncionarios() {
        setBackground(new Color(2, 12, 27));
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        // -----------------------------------------------------------------
        // 1. TOPO: Título, Busca, Editar e "+ Novo Funcionário"
        // -----------------------------------------------------------------
        JPanel painelTopoGeral = new JPanel(new BorderLayout(0, 15));
        painelTopoGeral.setOpaque(false);

        JLabel lblTitulo = new JLabel("Funcionários");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitulo.setForeground(Color.WHITE);

        JPanel painelAcoes = new JPanel(new BorderLayout(15, 0));
        painelAcoes.setOpaque(false);

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
        painelBusca.setPreferredSize(new Dimension(420, 38));
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
//a
        painelBusca.add(lblLupa, BorderLayout.WEST);
        painelBusca.add(txtBuscar, BorderLayout.CENTER);

        JButton btnNovo = new JButton("+ Novo Funcionário") {
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
        btnNovo.setPreferredSize(new Dimension(170, 38));
        btnNovo.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnNovo.setForeground(Color.WHITE);
        btnNovo.setFocusPainted(false);
        btnNovo.setContentAreaFilled(false);
        btnNovo.setBorderPainted(false);
        btnNovo.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnNovo.addActionListener(e -> abrirModal(null));

        JButton btnEditar = criarBotaoEditar();
        btnEditar.addActionListener(e -> {
            Funcionario f = funcionarioSelecionado();
            if (f != null) {
                abrirModal(f);
            }
        });

        JPanel painelBotoesTopo = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        painelBotoesTopo.setOpaque(false);
        painelBotoesTopo.add(btnEditar);
        painelBotoesTopo.add(btnNovo);

        painelAcoes.add(painelBusca, BorderLayout.WEST);
        painelAcoes.add(painelBotoesTopo, BorderLayout.EAST);

        painelTopoGeral.add(lblTitulo, BorderLayout.NORTH);
        painelTopoGeral.add(painelAcoes, BorderLayout.CENTER);
        add(painelTopoGeral, BorderLayout.NORTH);

        // -----------------------------------------------------------------
        // 2. TABELA (a coluna E-mail fica escondida; guarda dado usado na edição)
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

        String[] colunas = { "ID", "Nome", "CPF", "Telefone", "Cargo", "Usuário", "Admin", "Status", "E-mail" };

        modeloTabela = new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        tabela = new JTable(modeloTabela);
        tabela.removeColumn(tabela.getColumnModel().getColumn(8));
        tabela.setSelectionBackground(new Color(23, 55, 94));
        tabela.setSelectionForeground(Color.WHITE);
        sorter = new TableRowSorter<>(modeloTabela);
        tabela.setRowSorter(sorter);
        tabela.getTableHeader().setReorderingAllowed(false);

        tabela.setBackground(new Color(10, 22, 40));
        tabela.setForeground(new Color(226, 232, 240));
        tabela.setGridColor(new Color(18, 35, 60));
        tabela.setRowHeight(42);
        tabela.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        tabela.getTableHeader().setBackground(new Color(10, 22, 40));
        tabela.getTableHeader().setForeground(new Color(148, 163, 184));
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

        tabela.getColumnModel().getColumn(0).setPreferredWidth(50);
        tabela.getColumnModel().getColumn(1).setPreferredWidth(200);
        tabela.getColumnModel().getColumn(6).setPreferredWidth(60);
        tabela.getColumnModel().getColumn(7).setPreferredWidth(90);

        JScrollPane scrollPane = new JScrollPane(tabela);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(null);

        painelCardTabela.add(scrollPane, BorderLayout.CENTER);
        add(painelCardTabela, BorderLayout.CENTER);

        // -----------------------------------------------------------------
        // 3. RODAPÉ
        // -----------------------------------------------------------------
        JPanel painelRodape = new JPanel(new BorderLayout());
        painelRodape.setOpaque(false);
        painelRodape.setPreferredSize(new Dimension(0, 35));

        lblTotalFuncionarios = new JLabel();
        lblTotalFuncionarios.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTotalFuncionarios.setForeground(new Color(148, 163, 184));
        painelRodape.add(lblTotalFuncionarios, BorderLayout.WEST);
        add(painelRodape, BorderLayout.SOUTH);

        carregarTabela();
    }

    // -----------------------------------------------------------------
    // DADOS
    // -----------------------------------------------------------------

    /** Recarrega a tabela a partir do repositório (fonte única dos dados). */
    private void carregarTabela() {
        modeloTabela.setRowCount(0);
        for (Funcionario f : Repositoriofuncionarios.listar()) {
            modeloTabela.addRow(new Object[] {
                    f.getId(),
                    f.getNome(),
                    f.getCpf(),
                    f.getTelefone(),
                    f.getCargo(),
                    f.getUsuario(),
                    f.isPermissaoAdmin() ? "Sim" : "Não",
                    f.isAtivo() ? "Ativo" : "Bloqueado",
                    f.getEmail()
            });
        }
        atualizarTotal();
    }

    private void atualizarTotal() {
        lblTotalFuncionarios.setText("Total: " + tabela.getRowCount() + " funcionários");
    }

    private Funcionario funcionarioSelecionado() {
        int viewRow = tabela.getSelectedRow();
        if (viewRow < 0) {
            JOptionPane.showMessageDialog(this, "Selecione uma linha da tabela para editar.", "Aviso",
                    JOptionPane.WARNING_MESSAGE);
            return null;
        }
        int modelRow = tabela.convertRowIndexToModel(viewRow);
        String id = String.valueOf(modeloTabela.getValueAt(modelRow, 0));
        return Repositoriofuncionarios.buscarPorId(id);
    }

    // -----------------------------------------------------------------
    // MODAL DE CADASTRO / EDIÇÃO
    // -----------------------------------------------------------------
    // existente == null => novo funcionário; senão edita o informado
    private void abrirModal(Funcionario existente) {
        // Trava de segurança: só o Manager Master cadastra/edita funcionários
        if (!Sessao.isManagerMaster()) {
            JOptionPane.showMessageDialog(this, "Apenas o Manager Master pode cadastrar ou editar funcionários.",
                    "Acesso negado", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (existente != null && existente.isManagerMaster()) {
            JOptionPane.showMessageDialog(this, "O Manager Master é fixo e não pode ser editado.", "Aviso",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean edicao = existente != null;
        java.awt.Window parentWindow = SwingUtilities.getWindowAncestor(this);
        JDialog modal = new JDialog(parentWindow, edicao ? "Editar Funcionário" : "Cadastrar Novo Funcionário",
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        modal.setSize(560, 560);
        modal.setLocationRelativeTo(parentWindow);
        modal.setLayout(new BorderLayout());

        JPanel painelForm = new JPanel(new GridLayout(9, 2, 10, 10));
        painelForm.setBackground(new Color(10, 22, 40));
        painelForm.setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        JTextField txtNome = criarCampoTexto();
        JFormattedTextField txtCPF = criarCampoFormatado("###.###.###-##");
        JFormattedTextField txtTelefone = criarCampoFormatado("(##) #####-####");
        JTextField txtEmail = criarCampoTexto();
        JTextField txtCargo = criarCampoTexto();
        JTextField txtUsuario = criarCampoTexto();
        JPasswordField txtSenha = criarCampoSenha();
        JComboBox<String> cmbAdmin = criarCombo("Não", "Sim");
        JComboBox<String> cmbStatus = criarCombo("Ativo", "Bloqueado");

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

        painelForm.add(criarLabel("Nome Completo:"));
        painelForm.add(txtNome);
        painelForm.add(criarLabel("CPF:"));
        painelForm.add(txtCPF);
        painelForm.add(criarLabel("Telefone:"));
        painelForm.add(txtTelefone);
        painelForm.add(criarLabel("E-mail:"));
        painelForm.add(txtEmail);
        painelForm.add(criarLabel("Cargo:"));
        painelForm.add(txtCargo);
        painelForm.add(criarLabel("Usuário (login):"));
        painelForm.add(txtUsuario);
        painelForm.add(criarLabel(edicao ? "Senha (vazio = manter):" : "Senha:"));
        painelForm.add(txtSenha);
        painelForm.add(criarLabel("Permissão administrativa:"));
        painelForm.add(cmbAdmin);
        painelForm.add(criarLabel("Status:"));
        painelForm.add(cmbStatus);

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        painelBotoes.setBackground(new Color(2, 12, 27));

        JButton btnCancelar = new JButton("Cancelar");
        btnCancelar.setBackground(new Color(71, 85, 105));
        btnCancelar.setForeground(Color.WHITE);
        btnCancelar.setFocusPainted(false);
        btnCancelar.addActionListener(e -> modal.dispose());

        JButton btnSalvar = new JButton(edicao ? "Salvar Alterações" : "Salvar Funcionário");
        btnSalvar.setBackground(new Color(14, 116, 233));
        btnSalvar.setForeground(Color.WHITE);
        btnSalvar.setFocusPainted(false);
        btnSalvar.addActionListener(e -> {
            String nome = txtNome.getText().trim();
            String cpf = txtCPF.getText();
            String telefone = txtTelefone.getText();
            String email = txtEmail.getText().trim();
            String cargo = txtCargo.getText().trim();
            String usuario = txtUsuario.getText().trim();
            String senha = new String(txtSenha.getPassword());
            String idAtual = edicao ? existente.getId() : null;

            // ----- validações -----
            if (nome.isEmpty() || cargo.isEmpty() || usuario.isEmpty()) {
                aviso(modal, "Preencha o Nome, o Cargo e o Usuário.");
                return;
            }
            if (cpf.contains(" ")) {
                aviso(modal, "Preencha o CPF corretamente.");
                return;
            }
            int digitosTelefone = telefone.replaceAll("\\D", "").length();
            if (digitosTelefone == 0) {
                telefone = "";
            } else if (digitosTelefone < 10) {
                aviso(modal, "Preencha o telefone completo ou deixe em branco.");
                return;
            }
            if (usuario.contains(" ")) {
                aviso(modal, "O usuário não pode ter espaços.");
                return;
            }
            if (Repositoriofuncionarios.usuarioEmUso(usuario, idAtual)) {
                aviso(modal, "Já existe um funcionário com esse usuário.");
                return;
            }
            if (Repositoriofuncionarios.cpfEmUso(cpf, idAtual)) {
                aviso(modal, "Já existe um funcionário com esse CPF.");
                return;
            }
            if (!edicao && senha.isEmpty()) {
                aviso(modal, "Defina uma senha para o novo funcionário.");
                return;
            }
            if (!senha.isEmpty() && senha.length() < 4) {
                aviso(modal, "A senha deve ter pelo menos 4 caracteres.");
                return;
            }

            boolean admin = "Sim".equals(cmbAdmin.getSelectedItem());
            boolean ativo = "Ativo".equals(cmbStatus.getSelectedItem());

            // ----- salvar -----
            if (edicao) {
                existente.setNome(nome);
                existente.setCpf(cpf);
                existente.setTelefone(telefone);
                existente.setEmail(email);
                existente.setCargo(cargo);
                existente.setUsuario(usuario);
                existente.setPermissaoAdmin(admin);
                existente.setAtivo(ativo);
                if (!senha.isEmpty()) {
                    existente.definirSenha(senha);
                }
            } else {
                Repositoriofuncionarios.adicionar(new Funcionario(
                        Repositoriofuncionarios.gerarProximoId(),
                        nome, cpf, telefone, email, cargo, usuario, senha,
                        admin, ativo, false));
            }

            carregarTabela();
            JOptionPane.showMessageDialog(modal,
                    edicao ? "Funcionário atualizado com sucesso!" : "Funcionário cadastrado com sucesso!",
                    "Sucesso", JOptionPane.INFORMATION_MESSAGE);
            modal.dispose();
        });

        painelBotoes.add(btnCancelar);
        painelBotoes.add(btnSalvar);

        modal.add(painelForm, BorderLayout.CENTER);
        modal.add(painelBotoes, BorderLayout.SOUTH);
        modal.setVisible(true);
    }

    private void aviso(java.awt.Component pai, String mensagem) {
        JOptionPane.showMessageDialog(pai, mensagem, "Aviso", JOptionPane.WARNING_MESSAGE);
    }

    // -----------------------------------------------------------------
    // COMPONENTES VISUAIS (mesmo padrão da TelaClientes)
    // -----------------------------------------------------------------

    private DefaultTableCellRenderer criarRenderer(int alinhamento, Color cor) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object valor, boolean sel, boolean foco,
                    int linha, int coluna) {
                super.getTableCellRendererComponent(t, valor, sel, false, linha, coluna);
                setHorizontalAlignment(alinhamento);
                setBackground(sel ? t.getSelectionBackground() : new Color(10, 22, 40));
                setForeground(sel ? Color.WHITE : cor);
                return this;
            }
        };
    }

    private DefaultTableCellRenderer criarRendererStatus() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object valor, boolean sel, boolean foco,
                    int linha, int coluna) {
                super.getTableCellRendererComponent(t, valor, sel, false, linha, coluna);
                setHorizontalAlignment(SwingConstants.CENTER);
                setBackground(sel ? t.getSelectionBackground() : new Color(10, 22, 40));
                setForeground("Ativo".equals(valor) ? new Color(74, 222, 128) : new Color(248, 113, 113));
                return this;
            }
        };
    }

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
                g2.setColor(new Color(147, 197, 253));
                g2.fillRoundRect(-3, -11, 6, 3, 2, 2);
                g2.setColor(new Color(14, 116, 233));
                g2.fillRect(-3, -8, 6, 12);
                g2.setColor(new Color(56, 160, 255));
                g2.fillPolygon(new int[] { -3, 3, 0 }, new int[] { 4, 4, 10 }, 3);
                g2.setColor(Color.WHITE);
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

    private JLabel criarLabel(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setForeground(new Color(226, 232, 240));
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return lbl;
    }

    private JTextField criarCampoTexto() {
        JTextField txt = new JTextField();
        estilizarCampo(txt);
        return txt;
    }

    private JPasswordField criarCampoSenha() {
        JPasswordField txt = new JPasswordField();
        estilizarCampo(txt);
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
        estilizarCampo(txt);
        return txt;
    }

    private JComboBox<String> criarCombo(String... itens) {
        JComboBox<String> cmb = new JComboBox<>(itens);
        cmb.setBackground(new Color(2, 12, 27));
        cmb.setForeground(Color.WHITE);
        cmb.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        return cmb;
    }

    private void estilizarCampo(JTextField txt) {
        txt.setBackground(new Color(2, 12, 27));
        txt.setForeground(Color.WHITE);
        txt.setCaretColor(Color.WHITE);
        txt.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txt.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 48, 80), 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
    }
}