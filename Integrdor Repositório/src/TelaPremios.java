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
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
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

public class TelaPremios extends JPanel {

    private static final String PLACEHOLDER_BUSCA = "Buscar por nome...";
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
        if (con == null) throw new SQLException("Não foi possível conectar ao banco de dados.");
        return con;
    }

    public TelaPremios() {
        setBackground(FUNDO);
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        // TOPO
        JPanel topo = new JPanel(new BorderLayout(0, 15));
        topo.setOpaque(false);

        JLabel lblTitulo = new JLabel("Prêmios");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitulo.setForeground(Color.WHITE);

        JPanel painelAcoes = new JPanel(new BorderLayout(15, 0));
        painelAcoes.setOpaque(false);

        JPanel painelBusca = criarBusca(PLACEHOLDER_BUSCA, texto -> filtrar(texto));
        
        JButton btnNovo = criarBotao("+ Novo Prêmio", true, 150);
        btnNovo.addActionListener(e -> abrirModal(-1));
        
        JButton btnEditar = criarBotao("Editar", false, 100);
        btnEditar.addActionListener(e -> {
            int linha = linhaSelecionada();
            if (linha >= 0) abrirModal(linha);
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

        // TABELA
        String[] colunas = { "ID", "Nome do Prêmio", "Valor (Pontos)", "Qtd. Disponível", "Status" };
        modeloTabela = new DefaultTableModel(colunas, 0) {
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        tabela = new JTable(modeloTabela);
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

        DefaultTableCellRenderer centro = new DefaultTableCellRenderer();
        centro.setHorizontalAlignment(SwingConstants.CENTER);
        centro.setBackground(CARD);
        centro.setForeground(new Color(186, 230, 253));

        DefaultTableCellRenderer esquerda = new DefaultTableCellRenderer();
        esquerda.setHorizontalAlignment(SwingConstants.LEFT);
        esquerda.setBackground(CARD);
        esquerda.setForeground(Color.WHITE);

        tabela.getColumnModel().getColumn(0).setCellRenderer(centro);
        tabela.getColumnModel().getColumn(1).setCellRenderer(esquerda);
        tabela.getColumnModel().getColumn(2).setCellRenderer(centro);
        tabela.getColumnModel().getColumn(3).setCellRenderer(centro);
        tabela.getColumnModel().getColumn(4).setCellRenderer(centro);

        // Ghost Loading
        ghostPanel = new GhostPanel(5);
        cardTabela = new JPanel(new java.awt.CardLayout()) {
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

        // RODAPÉ
        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setOpaque(false);
        rodape.setPreferredSize(new Dimension(0, 35));

        lblTotal = new JLabel();
        lblTotal.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTotal.setForeground(TEXTO_SUAVE);
        rodape.add(lblTotal, BorderLayout.WEST);
        add(rodape, BorderLayout.SOUTH);

        carregarPremios();
    }

    public void recarregar() {
        carregarPremios();
    }

    private void filtrar(String texto) {
        if (texto == null || texto.trim().isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(texto.trim())));
        }
        atualizarTotal();
    }

    private void atualizarTotal() {
        if (carregando) {
            lblTotal.setText("Carregando...");
            return;
        }
        int n = tabela.getRowCount();
        lblTotal.setText("Total: " + n + (n == 1 ? " prêmio" : " prêmios"));
    }

    private int linhaSelecionada() {
        int linhaVisual = tabela.getSelectedRow();
        if (linhaVisual < 0) {
            JOptionPane.showMessageDialog(this, "Selecione uma linha da tabela para editar.", "Aviso", JOptionPane.WARNING_MESSAGE);
            return -1;
        }
        return tabela.convertRowIndexToModel(linhaVisual);
    }

    // ---------------------------------------------------------
    // BANCO DE DADOS
    // ---------------------------------------------------------
    private void carregarPremios() {
        final int idCarga = ++cargaId;
        carregando = true;
        ((java.awt.CardLayout) cardTabela.getLayout()).show(cardTabela, "ghost");
        ghostPanel.iniciar();
        atualizarTotal();

        async(() -> {
            List<Object[]> linhas = new ArrayList<>();
            try (Connection con = conectar();
                 PreparedStatement ps = con.prepareStatement("SELECT * FROM premios ORDER BY ID_Premio");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    linhas.add(new Object[] {
                            String.format("%03d", rs.getLong("ID_Premio")),
                            rs.getString("Nome"),
                            rs.getLong("Valor_pontos"),
                            rs.getInt("Qtd_Disponivel"),
                            rs.getBoolean("Ativo") ? "Ativo" : "Inativo"
                    });
                }
            }
            return linhas;
        }, linhas -> {
            if (idCarga != cargaId) return;
            modeloTabela.setRowCount(0);
            for (Object[] l : linhas) modeloTabela.addRow(l);
            carregando = false;
            ((java.awt.CardLayout) cardTabela.getLayout()).show(cardTabela, "tabela");
            ghostPanel.parar();
            atualizarTotal();
        }, ex -> {
            if (idCarga != cargaId) return;
            carregando = false;
            ((java.awt.CardLayout) cardTabela.getLayout()).show(cardTabela, "tabela");
            ghostPanel.parar();
            atualizarTotal();
            JOptionPane.showMessageDialog(this, "Erro ao carregar prêmios:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
        });
    }

    private long inserirPremio(String nome, long valor, int qtd, boolean ativo) throws SQLException {
        String sql = "INSERT INTO premios (Nome, Valor_pontos, Qtd_Disponivel, Ativo) VALUES (?, ?, ?, ?)";
        try (Connection con = conectar();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nome);
            ps.setLong(2, valor);
            ps.setInt(3, qtd);
            ps.setBoolean(4, ativo);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getLong(1);
            }
            throw new SQLException("ID não retornado.");
        }
    }

    private void atualizarPremio(long id, String nome, long valor, int qtd, boolean ativo) throws SQLException {
        String sql = "UPDATE premios SET Nome = ?, Valor_pontos = ?, Qtd_Disponivel = ?, Ativo = ? WHERE ID_Premio = ?";
        try (Connection con = conectar();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, nome);
            ps.setLong(2, valor);
            ps.setInt(3, qtd);
            ps.setBoolean(4, ativo);
            ps.setLong(5, id);
            ps.executeUpdate();
        }
    }

    private void abrirModal(int linhaModelo) {
        boolean edicao = linhaModelo >= 0;
        JDialog modal = new JDialog(SwingUtilities.getWindowAncestor(this),
                edicao ? "Editar Prêmio" : "Cadastrar Novo Prêmio", java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        modal.setSize(440, 480);
        modal.setLocationRelativeTo(this);
        modal.setLayout(new BorderLayout());

        JPanel painelForm = new JPanel(new GridLayout(8, 1, 2, 2));
        painelForm.setBackground(CARD);
        painelForm.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));

        JTextField txtNome = criarCampoTexto();
        JTextField txtValor = criarCampoTexto();
        JTextField txtQtd = criarCampoTexto();
        JComboBox<String> cmbStatus = new JComboBox<>(new String[] { "Ativo", "Inativo" });
        cmbStatus.setBackground(FUNDO);
        cmbStatus.setForeground(Color.WHITE);
        cmbStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        if (edicao) {
            txtNome.setText(String.valueOf(modeloTabela.getValueAt(linhaModelo, 1)));
            txtValor.setText(String.valueOf(modeloTabela.getValueAt(linhaModelo, 2)));
            txtQtd.setText(String.valueOf(modeloTabela.getValueAt(linhaModelo, 3)));
            cmbStatus.setSelectedItem(modeloTabela.getValueAt(linhaModelo, 4));
        }

        painelForm.add(criarLabel("Nome do Prêmio (máx. 50):"));
        painelForm.add(txtNome);
        painelForm.add(criarLabel("Valor em Pontos (maior que 0):"));
        painelForm.add(txtValor);
        painelForm.add(criarLabel("Quantidade Disponível:"));
        painelForm.add(txtQtd);
        painelForm.add(criarLabel("Status:"));
        painelForm.add(cmbStatus);

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        painelBotoes.setBackground(FUNDO);

        JButton btnCancelar = criarBotao("Cancelar", false, 110);
        btnCancelar.addActionListener(e -> modal.dispose());

        JButton btnSalvar = criarBotao("Salvar", true, 110);
        btnSalvar.addActionListener(e -> {
            String nome = txtNome.getText().trim();
            long valor;
            int qtd;

            if (nome.isEmpty() || nome.length() > 50) {
                JOptionPane.showMessageDialog(modal, "Informe o Nome do prêmio.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            try {
                valor = Long.parseLong(txtValor.getText().trim());
                qtd = Integer.parseInt(txtQtd.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(modal, "Valor e Quantidade devem ser números inteiros.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (valor <= 0 || qtd < 0) {
                JOptionPane.showMessageDialog(modal, "O Valor deve ser > 0 e a Qtd não pode ser negativa.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }

            boolean ativo = "Ativo".equals(cmbStatus.getSelectedItem());

            try {
                if (edicao) {
                    atualizarPremio(Long.parseLong(String.valueOf(modeloTabela.getValueAt(linhaModelo, 0))), nome, valor, qtd, ativo);
                } else {
                    inserirPremio(nome, valor, qtd, ativo);
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(modal, "Erro no banco:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }

            modal.dispose();
            carregarPremios();
        });

        painelBotoes.add(btnCancelar);
        painelBotoes.add(btnSalvar);

        modal.add(painelForm, BorderLayout.CENTER);
        modal.add(painelBotoes, BorderLayout.SOUTH);
        modal.setVisible(true);
    }

    // ---------------------------------------------------------
    // HELPERS VISUAIS (Idênticos às outras telas)
    // ---------------------------------------------------------
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
        txt.setBackground(FUNDO);
        txt.setForeground(Color.WHITE);
        txt.setCaretColor(Color.WHITE);
        txt.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txt.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(30, 48, 80), 1),
                BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        return txt;
    }

    private JLabel criarLabel(String texto) {
        JLabel lbl = new JLabel(texto);
        lbl.setForeground(new Color(226, 232, 240));
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        return lbl;
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

    // Ghost Loading visual
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

        GhostPanel(int colunas) {
            this.colunas = colunas;
            setOpaque(false);
        }

        void iniciar() {
            fase = -0.3f;
            if (!timer.isRunning()) timer.start();
        }

        void parar() { timer.stop(); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
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