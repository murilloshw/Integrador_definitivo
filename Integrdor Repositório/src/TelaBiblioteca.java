import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dialog;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
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

/**
 * Tela da Biblioteca (só para funcionários com cargo "bibliotecário"), com quatro abas:
 *   1. Livros            - cadastro e edição dos livros (título, autor, exemplares disponíveis).
 *   2. Fichas de leitura - a leitura do cliente. Só quando a ficha está "Aprovada" os créditos
 *                          entram no cartão do cliente.
 *   3. Doação de livros  - o cliente doa um livro: ganha créditos e o exemplar entra no estoque.
 *   4. Doação de sangue  - comprovante de doação de sangue: créditos para o cartão do cliente.
 *
 * Toda gravação que mexe em créditos roda numa única transação (registro + saldo do cartão),
 * e a edição ajusta apenas a diferença, para o saldo nunca ficar errado.
 */
public class TelaBiblioteca extends JPanel {

    private static final Color FUNDO = new Color(2, 12, 27);
    private static final Color CARD = new Color(10, 22, 40);
    private static final Color BORDA = new Color(23, 42, 69);
    private static final Color AZUL = new Color(14, 116, 233);
    private static final Color TEXTO_SUAVE = new Color(148, 163, 184);
    private static final Color CAMPO = new Color(13, 27, 46);
    private static final DateTimeFormatter DATA_BR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private static final String STATUS_APROVADA = "Aprovada";
    private static final String STATUS_PENDENTE = "Pendente";

    private static final String SQL_FICHA_TRAVADA = "SELECT ID_Cliente, ID_Livro, Créditos_Recebidos, ID_Funcionário, Ativo "
            + "FROM ficha_leitura WHERE ID_Ficha = ? FOR UPDATE";

    private final CardLayout cards = new CardLayout();
    private final JPanel painelCards = new JPanel(cards);
    private final JLabel lblTotal = new JLabel();

    private final JButton btnLivros = criarAba("Livros");
    private final JButton btnFichas = criarAba("Fichas de Leitura");
    private final JButton btnDoacaoLivros = criarAba("Doação de Livros");
    private final JButton btnDoacaoSangue = criarAba("Doação de Sangue");

    private final Aba abaLivros;
    private final Aba abaFichas;
    private final Aba abaDoacaoLivros;
    private final Aba abaDoacaoSangue;
    private Aba abaAtiva;

    // ---------------------------------------------------------------
    // CONSTRUÇÃO DA TELA
    // ---------------------------------------------------------------
    public TelaBiblioteca() {
        setBackground(FUNDO);
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        // Colunas depois de "colunasVisiveis" são auxiliares (IDs usados na edição) e ficam ocultas.
        abaLivros = new Aba("livros", "livro", "livros",
                new String[] { "ID", "Título", "Autor", "Qtd. Disponível" }, 4,
                this::carregarLivros, 1, 2);
        abaFichas = new Aba("fichas", "ficha", "fichas",
                new String[] { "ID", "Cliente", "Livro", "Data", "Créditos", "Bibliotecário", "Status",
                        "ID_CLI", "ID_LIV", "APROVADA" }, 7,
                this::carregarFichas, 1, 2, 5);
        abaDoacaoLivros = new Aba("doacao_livros", "doação de livro", "doações de livros",
                new String[] { "ID", "Cliente", "Livro", "Data", "Créditos", "Bibliotecário", "ID_CLI", "ID_LIV" }, 6,
                this::carregarDoacoesLivros, 1, 2, 5);
        abaDoacaoSangue = new Aba("doacao_sangue", "doação de sangue", "doações de sangue",
                new String[] { "ID", "Cliente", "Data", "Créditos", "Bibliotecário", "ID_CLI" }, 5,
                this::carregarDoacoesSangue, 1, 4);

        // Cabeçalho: título + abas (mesmo estilo da tela de Recargas)
        JLabel lblTitulo = new JLabel("Biblioteca");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitulo.setForeground(Color.WHITE);

        JPanel abas = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 0));
        abas.setOpaque(false);
        btnLivros.addActionListener(e -> mostrarAba(abaLivros));
        btnFichas.addActionListener(e -> mostrarAba(abaFichas));
        btnDoacaoLivros.addActionListener(e -> mostrarAba(abaDoacaoLivros));
        btnDoacaoSangue.addActionListener(e -> mostrarAba(abaDoacaoSangue));
        abas.add(btnLivros);
        abas.add(btnFichas);
        abas.add(btnDoacaoLivros);
        abas.add(btnDoacaoSangue);

        JPanel topo = new JPanel(new BorderLayout(0, 10));
        topo.setOpaque(false);
        topo.add(lblTitulo, BorderLayout.NORTH);
        topo.add(abas, BorderLayout.CENTER);
        add(topo, BorderLayout.NORTH);

        // Conteúdo de cada aba
        painelCards.setOpaque(false);
        painelCards.add(montarAbaLivros(), abaLivros.chave);
        painelCards.add(montarAbaFichas(), abaFichas.chave);
        painelCards.add(montarAbaDoacaoLivros(), abaDoacaoLivros.chave);
        painelCards.add(montarAbaDoacaoSangue(), abaDoacaoSangue.chave);
        add(painelCards, BorderLayout.CENTER);

        // Rodapé: total da aba + regra
        JPanel rodape = new JPanel(new BorderLayout());
        rodape.setOpaque(false);
        rodape.setPreferredSize(new Dimension(0, 35));
        lblTotal.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblTotal.setForeground(TEXTO_SUAVE);
        JLabel lblRegra = new JLabel("Créditos de leitura e doações são somados ao cartão do cliente");
        lblRegra.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblRegra.setForeground(TEXTO_SUAVE);
        rodape.add(lblTotal, BorderLayout.WEST);
        rodape.add(lblRegra, BorderLayout.EAST);
        add(rodape, BorderLayout.SOUTH);

        // Abre na primeira aba sem carregar nada: os dados vêm quando a tela é aberta (recarregar)
        abaAtiva = abaLivros;
        cards.show(painelCards, abaLivros.chave);
        estilizarAbas();
    }

    /** Chamado pelo App ao abrir a Biblioteca: recarrega a aba que estiver selecionada. */
    public void recarregar() {
        mostrarAba(abaAtiva);
    }

    private JPanel montarAbaLivros() {
        JButton btnNovo = criarBotao("+ Novo Livro", true, 150);
        btnNovo.addActionListener(e -> abrirFormLivro(null));

        JButton btnEditar = criarBotao("Editar", false, 100);
        btnEditar.addActionListener(e -> {
            Object[] linha = abaLivros.linhaSelecionada();
            if (linha != null) abrirFormLivro(linha);
        });

        JButton btnExcluir = Exclusao.criarBotao();
        btnExcluir.addActionListener(e -> {
            Object[] linha = abaLivros.linhaSelecionada();
            if (linha != null) excluirLivro(linha);
        });

        return abaLivros.montar(montarBarra(abaLivros, btnExcluir, btnEditar, btnNovo));
    }

    /** Exclui um livro (só se não houver fichas ou doações ligadas a ele). */
    private void excluirLivro(Object[] linha) {
        if (!Exclusao.confirmar(this, "o livro " + linha[1])) {
            return;
        }
        try (Connection con = Conexao.conectar();
                PreparedStatement ps = con.prepareStatement("DELETE FROM livros WHERE ID_Livro = ?")) {
            ps.setLong(1, idDe(linha[0]));
            ps.executeUpdate();
        } catch (SQLException ex) {
            Exclusao.erro(this, ex);
            return;
        }
        abaLivros.carregar();
    }

    private JPanel montarAbaFichas() {
        JButton btnNovo = criarBotao("+ Nova Ficha", true, 150);
        btnNovo.addActionListener(e -> abrirFormFicha(null));

        JButton btnEditar = criarBotao("Editar", false, 100);
        btnEditar.addActionListener(e -> {
            Object[] linha = abaFichas.linhaSelecionada();
            if (linha != null) abrirFormFicha(linha);
        });

        JButton btnAprovar = criarBotao("Aprovar", false, 120);
        btnAprovar.addActionListener(e -> {
            Object[] linha = abaFichas.linhaSelecionada();
            if (linha == null) return;
            if (Boolean.TRUE.equals(linha[9])) {
                JOptionPane.showMessageDialog(this, "Esta ficha já está aprovada.", "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            }
            long id = idDe(linha[0]);
            executarAcao(() -> aprovarFicha(id), () -> abaFichas.carregar());
        });

        return abaFichas.montar(montarBarra(abaFichas, btnAprovar, btnEditar, btnNovo));
    }

    private JPanel montarAbaDoacaoLivros() {
        JButton btnNovo = criarBotao("+ Nova Doação", true, 150);
        btnNovo.addActionListener(e -> abrirFormDoacaoLivro(null));

        JButton btnEditar = criarBotao("Editar", false, 100);
        btnEditar.addActionListener(e -> {
            Object[] linha = abaDoacaoLivros.linhaSelecionada();
            if (linha != null) abrirFormDoacaoLivro(linha);
        });

        return abaDoacaoLivros.montar(montarBarra(abaDoacaoLivros, btnEditar, btnNovo));
    }

    private JPanel montarAbaDoacaoSangue() {
        JButton btnNovo = criarBotao("+ Nova Doação", true, 150);
        btnNovo.addActionListener(e -> abrirFormDoacaoSangue(null));

        JButton btnEditar = criarBotao("Editar", false, 100);
        btnEditar.addActionListener(e -> {
            Object[] linha = abaDoacaoSangue.linhaSelecionada();
            if (linha != null) abrirFormDoacaoSangue(linha);
        });

        return abaDoacaoSangue.montar(montarBarra(abaDoacaoSangue, btnEditar, btnNovo));
    }

    /** Barra de cada aba: busca à esquerda, botões à direita. */
    private JPanel montarBarra(Aba aba, JButton... botoes) {
        JPanel barra = new JPanel(new BorderLayout(15, 0));
        barra.setOpaque(false);
        barra.add(criarBusca("Buscar na tabela...", texto -> {
            aba.filtrar(texto);
            atualizarTotal();
        }), BorderLayout.WEST);

        JPanel direita = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        direita.setOpaque(false);
        for (JButton b : botoes) {
            direita.add(b);
        }
        barra.add(direita, BorderLayout.EAST);
        return barra;
    }

    // ---------------------------------------------------------------
    // ABAS
    // ---------------------------------------------------------------
    private void mostrarAba(Aba aba) {
        abaAtiva = aba;
        cards.show(painelCards, aba.chave);
        estilizarAbas();
        aba.carregar();
    }

    private void estilizarAbas() {
        estilizarAba(btnLivros, abaAtiva == abaLivros);
        estilizarAba(btnFichas, abaAtiva == abaFichas);
        estilizarAba(btnDoacaoLivros, abaAtiva == abaDoacaoLivros);
        estilizarAba(btnDoacaoSangue, abaAtiva == abaDoacaoSangue);
    }

    private void atualizarTotal() {
        Aba a = abaAtiva;
        if (a == null) return;
        if (a.carregando) {
            lblTotal.setText("Carregando...");
            return;
        }
        int n = a.tabela.getRowCount();
        lblTotal.setText("Total: " + n + (n == 1 ? " " + a.singular : " " + a.plural));
    }

    // ---------------------------------------------------------------
    // CARGA DOS DADOS (rodam em segundo plano)
    // ---------------------------------------------------------------
    private List<Object[]> carregarLivros() throws SQLException {
        String sql = "SELECT ID_Livro, `Título`, Autor, Qtd_Disponivel FROM livros ORDER BY ID_Livro";
        List<Object[]> linhas = new ArrayList<>();
        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                linhas.add(new Object[] {
                        formatarId(rs.getLong(1)),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getLong(4)
                });
            }
        }
        return linhas;
    }

    private List<Object[]> carregarFichas() throws SQLException {
        String sql = "SELECT fl.ID_Ficha, c.Nome_Completo, l.`Título`, fl.Data_, fl.Créditos_Recebidos, "
                + "f.Nome_Completo, fl.Ativo, fl.ID_Cliente, fl.ID_Livro "
                + "FROM ficha_leitura fl "
                + "JOIN clientes c ON c.ID_Cliente = fl.ID_Cliente "
                + "JOIN livros l ON l.ID_Livro = fl.ID_Livro "
                + "LEFT JOIN `funcionários` f ON f.ID_Funcionário = fl.ID_Funcionário "
                + "ORDER BY fl.ID_Ficha";
        List<Object[]> linhas = new ArrayList<>();
        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                boolean aprovada = rs.getBoolean(7);
                linhas.add(new Object[] {
                        formatarId(rs.getLong(1)),
                        rs.getString(2),
                        rs.getString(3),
                        formatarData(rs.getDate(4)),
                        rs.getLong(5),
                        nvl(rs.getString(6)),
                        aprovada ? STATUS_APROVADA : STATUS_PENDENTE,
                        rs.getLong(8),
                        rs.getLong(9),
                        aprovada
                });
            }
        }
        return linhas;
    }

    private List<Object[]> carregarDoacoesLivros() throws SQLException {
        String sql = "SELECT d.ID_Doação, c.Nome_Completo, l.`Título`, d.Data_, d.Créditos_Recebidos, "
                + "f.Nome_Completo, d.ID_Cliente, d.ID_Livro "
                + "FROM `doação_livros` d "
                + "JOIN clientes c ON c.ID_Cliente = d.ID_Cliente "
                + "JOIN livros l ON l.ID_Livro = d.ID_Livro "
                + "LEFT JOIN `funcionários` f ON f.ID_Funcionário = d.ID_Funcionário "
                + "ORDER BY d.ID_Doação";
        List<Object[]> linhas = new ArrayList<>();
        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                linhas.add(new Object[] {
                        formatarId(rs.getLong(1)),
                        rs.getString(2),
                        rs.getString(3),
                        formatarData(rs.getDate(4)),
                        rs.getLong(5),
                        nvl(rs.getString(6)),
                        rs.getLong(7),
                        rs.getLong(8)
                });
            }
        }
        return linhas;
    }

    private List<Object[]> carregarDoacoesSangue() throws SQLException {
        String sql = "SELECT d.ID_Doação, c.Nome_Completo, d.Data_, d.Créditos_Recebidos, f.Nome_Completo, d.ID_Cliente "
                + "FROM `doação_sangue` d "
                + "JOIN clientes c ON c.ID_Cliente = d.ID_Cliente "
                + "LEFT JOIN `funcionários` f ON f.ID_Funcionário = d.ID_Funcionário "
                + "ORDER BY d.ID_Doação";
        List<Object[]> linhas = new ArrayList<>();
        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                linhas.add(new Object[] {
                        formatarId(rs.getLong(1)),
                        rs.getString(2),
                        formatarData(rs.getDate(3)),
                        rs.getLong(4),
                        nvl(rs.getString(5)),
                        rs.getLong(6)
                });
            }
        }
        return linhas;
    }

    private static List<Opcao> listarClientes() throws SQLException {
        List<Opcao> lista = new ArrayList<>();
        String sql = "SELECT ID_Cliente, Nome_Completo FROM clientes ORDER BY Nome_Completo";
        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong(1);
                lista.add(new Opcao(id, formatarId(id) + " - " + rs.getString(2)));
            }
        }
        return lista;
    }

    private static List<Opcao> listarLivros() throws SQLException {
        List<Opcao> lista = new ArrayList<>();
        String sql = "SELECT ID_Livro, `Título`, Autor FROM livros ORDER BY `Título`";
        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong(1);
                lista.add(new Opcao(id, formatarId(id) + " - " + rs.getString(2) + " (" + rs.getString(3) + ")"));
            }
        }
        return lista;
    }

    // ---------------------------------------------------------------
    // FORMULÁRIOS (cadastro e edição)
    // ---------------------------------------------------------------
    private void abrirFormLivro(Object[] linha) {
        boolean edicao = linha != null;
        JTextField txtTitulo = criarCampoTexto();
        JTextField txtAutor = criarCampoTexto();
        JTextField txtQtd = criarCampoTexto();
        if (edicao) {
            txtTitulo.setText(String.valueOf(linha[1]));
            txtAutor.setText(String.valueOf(linha[2]));
            txtQtd.setText(String.valueOf(linha[3]));
        }
        Long id = edicao ? idDe(linha[0]) : null;

        abrirDialogo(edicao ? "Editar Livro" : "Cadastrar Novo Livro",
                new String[] { "Título (máx. 50):", "Autor (máx. 50):", "Quantidade disponível:" },
                new JComponent[] { txtTitulo, txtAutor, txtQtd },
                () -> {
                    String titulo = txtTitulo.getText().trim();
                    String autor = txtAutor.getText().trim();
                    if (titulo.isEmpty() || titulo.length() > 50) {
                        throw new ErroValidacao("Informe o título do livro (até 50 caracteres).");
                    }
                    if (autor.isEmpty() || autor.length() > 50) {
                        throw new ErroValidacao("Informe o autor do livro (até 50 caracteres).");
                    }
                    long qtd = lerInteiro(txtQtd.getText(), "A quantidade disponível deve ser um número inteiro maior ou igual a zero.");
                    gravarLivro(id, titulo, autor, qtd);
                },
                () -> abaLivros.carregar());
    }

    private void abrirFormFicha(Object[] linha) {
        boolean edicao = linha != null;
        List<Opcao> clientes;
        List<Opcao> livros;
        try {
            clientes = listarClientes();
            livros = listarLivros();
        } catch (SQLException ex) {
            erroCarga(ex);
            return;
        }

        JComboBox<Opcao> cmbCliente = criarCombo(clientes);
        JComboBox<Opcao> cmbLivro = criarCombo(livros);
        JTextField txtCred = criarCampoTexto();
        JComboBox<String> cmbStatus = new JComboBox<>(new String[] { STATUS_PENDENTE, STATUS_APROVADA });
        estilizarCombo(cmbStatus);
        cmbStatus.setSelectedItem(STATUS_PENDENTE);

        if (edicao) {
            selecionar(cmbCliente, idDe(linha[7]));
            selecionar(cmbLivro, idDe(linha[8]));
            txtCred.setText(String.valueOf(linha[4]));
            cmbStatus.setSelectedItem(String.valueOf(linha[6]));
        }
        Long id = edicao ? idDe(linha[0]) : null;

        abrirDialogo(edicao ? "Editar Ficha de Leitura" : "Nova Ficha de Leitura",
                new String[] { "Cliente:", "Livro:", "Créditos recebidos:", "Status:" },
                new JComponent[] { cmbCliente, cmbLivro, txtCred, cmbStatus },
                () -> {
                    Opcao cli = escolhido(cmbCliente, "Selecione o cliente.");
                    Opcao liv = escolhido(cmbLivro, "Selecione o livro.");
                    long cred = lerInteiro(txtCred.getText(), "Os créditos devem ser um número inteiro maior ou igual a zero.");
                    boolean aprovada = STATUS_APROVADA.equals(cmbStatus.getSelectedItem());
                    gravarFicha(id, cli.id, liv.id, cred, aprovada);
                },
                () -> abaFichas.carregar());
    }

    private void abrirFormDoacaoLivro(Object[] linha) {
        boolean edicao = linha != null;
        List<Opcao> clientes;
        List<Opcao> livros;
        try {
            clientes = listarClientes();
            livros = listarLivros();
        } catch (SQLException ex) {
            erroCarga(ex);
            return;
        }

        JComboBox<Opcao> cmbCliente = criarCombo(clientes);
        JComboBox<Opcao> cmbLivro = criarCombo(livros);
        JTextField txtCred = criarCampoTexto();
        if (edicao) {
            selecionar(cmbCliente, idDe(linha[6]));
            selecionar(cmbLivro, idDe(linha[7]));
            txtCred.setText(String.valueOf(linha[4]));
        }
        Long id = edicao ? idDe(linha[0]) : null;

        abrirDialogo(edicao ? "Editar Doação de Livro" : "Registrar Doação de Livro",
                new String[] { "Cliente que doou:", "Livro doado:", "Créditos recebidos:" },
                new JComponent[] { cmbCliente, cmbLivro, txtCred },
                () -> {
                    Opcao cli = escolhido(cmbCliente, "Selecione o cliente.");
                    Opcao liv = escolhido(cmbLivro, "Selecione o livro.");
                    long cred = lerInteiro(txtCred.getText(), "Os créditos devem ser um número inteiro maior ou igual a zero.");
                    gravarDoacaoLivro(id, cli.id, liv.id, cred);
                },
                () -> abaDoacaoLivros.carregar());
    }

    private void abrirFormDoacaoSangue(Object[] linha) {
        boolean edicao = linha != null;
        List<Opcao> clientes;
        try {
            clientes = listarClientes();
        } catch (SQLException ex) {
            erroCarga(ex);
            return;
        }

        JComboBox<Opcao> cmbCliente = criarCombo(clientes);
        JTextField txtCred = criarCampoTexto();
        if (edicao) {
            selecionar(cmbCliente, idDe(linha[5]));
            txtCred.setText(String.valueOf(linha[3]));
        }
        Long id = edicao ? idDe(linha[0]) : null;

        abrirDialogo(edicao ? "Editar Doação de Sangue" : "Registrar Doação de Sangue",
                new String[] { "Cliente (comprovante apresentado):", "Créditos recebidos:" },
                new JComponent[] { cmbCliente, txtCred },
                () -> {
                    Opcao cli = escolhido(cmbCliente, "Selecione o cliente.");
                    long cred = lerInteiro(txtCred.getText(), "Os créditos devem ser um número inteiro maior ou igual a zero.");
                    gravarDoacaoSangue(id, cli.id, cred);
                },
                () -> abaDoacaoSangue.carregar());
    }

    /** Janela modal com os campos; a ação roda ao clicar em Salvar e, se der certo, fecha e chama aoSalvar. */
    private void abrirDialogo(String titulo, String[] rotulos, JComponent[] campos, Acao acao, Runnable aoSalvar) {
        JDialog modal = new JDialog(SwingUtilities.getWindowAncestor(this), titulo,
                Dialog.ModalityType.APPLICATION_MODAL);
        modal.setLayout(new BorderLayout());

        JPanel form = new JPanel(new GridLayout(rotulos.length * 2, 1, 2, 4));
        form.setBackground(CARD);
        form.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));
        for (int i = 0; i < rotulos.length; i++) {
            form.add(criarLabel(rotulos[i]));
            form.add(campos[i]);
        }

        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        botoes.setBackground(FUNDO);

        JButton btnCancelar = criarBotao("Cancelar", false, 110);
        btnCancelar.addActionListener(e -> modal.dispose());

        JButton btnSalvar = criarBotao("Salvar", true, 110);
        btnSalvar.addActionListener(e -> {
            try {
                acao.executar();
            } catch (ErroValidacao ex) {
                JOptionPane.showMessageDialog(modal, ex.getMessage(), "Aviso", JOptionPane.WARNING_MESSAGE);
                return;
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(modal, "Erro no banco:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
                return;
            }
            modal.dispose();
            aoSalvar.run();
        });

        botoes.add(btnCancelar);
        botoes.add(btnSalvar);

        modal.add(form, BorderLayout.CENTER);
        modal.add(botoes, BorderLayout.SOUTH);
        modal.setSize(440, 130 + 62 * rotulos.length);
        modal.setLocationRelativeTo(this);
        modal.setVisible(true);
    }

    /** Executa uma ação de banco fora de um modal (ex.: aprovar ficha) e mostra o erro, se houver. */
    private void executarAcao(Acao acao, Runnable aoSucesso) {
        try {
            acao.executar();
        } catch (ErroValidacao ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Erro no banco:\n" + ex.getMessage(), "Erro", JOptionPane.ERROR_MESSAGE);
            return;
        }
        aoSucesso.run();
    }

    private void erroCarga(Exception ex) {
        JOptionPane.showMessageDialog(this, "Não foi possível carregar os dados:\n" + ex.getMessage(),
                "Erro", JOptionPane.ERROR_MESSAGE);
    }

    // ---------------------------------------------------------------
    // GRAVAÇÃO NO BANCO
    // ---------------------------------------------------------------
    private void gravarLivro(Long id, String titulo, String autor, long qtd) throws Exception {
        String sql = id == null
                ? "INSERT INTO livros (`Título`, Autor, Qtd_Disponivel) VALUES (?, ?, ?)"
                : "UPDATE livros SET `Título` = ?, Autor = ?, Qtd_Disponivel = ? WHERE ID_Livro = ?";
        try (Connection con = conectar();
                PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, titulo);
            ps.setString(2, autor);
            ps.setLong(3, qtd);
            if (id != null) {
                ps.setLong(4, id);
            }
            ps.executeUpdate();
        }
    }

    /**
     * Grava a ficha. Se a ficha estiver (ou ficar) aprovada, os créditos entram no cartão do cliente
     * na mesma transação. Na edição, só a diferença é ajustada.
     */
    private void gravarFicha(Long id, long idCliente, long idLivro, long creditos, boolean aprovada) throws Exception {
        try (Connection con = conectar()) {
            con.setAutoCommit(false);
            try {
                long idResp = idLogado();
                if (id == null) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO ficha_leitura (ID_Cliente, ID_Livro, ID_Funcionário, Créditos_Recebidos, Ativo) "
                                    + "VALUES (?, ?, ?, ?, ?)")) {
                        ps.setLong(1, idCliente);
                        ps.setLong(2, idLivro);
                        ps.setLong(3, idResp);
                        ps.setLong(4, creditos);
                        ps.setBoolean(5, aprovada);
                        ps.executeUpdate();
                    }
                    ajustarCreditos(con, idCliente, 0, idCliente, aprovada ? creditos : 0);
                } else {
                    long[] antiga = lerValores(con, SQL_FICHA_TRAVADA, id);
                    boolean eraAprovada = antiga[4] != 0;
                    ajustarCreditos(con, antiga[0], eraAprovada ? antiga[2] : 0, idCliente, aprovada ? creditos : 0);
                    // quem aprova passa a ser o responsável pela ficha
                    long resp = (!eraAprovada && aprovada) ? idResp : antiga[3];
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE ficha_leitura SET ID_Cliente = ?, ID_Livro = ?, Créditos_Recebidos = ?, "
                                    + "ID_Funcionário = ?, Ativo = ? WHERE ID_Ficha = ?")) {
                        ps.setLong(1, idCliente);
                        ps.setLong(2, idLivro);
                        ps.setLong(3, creditos);
                        ps.setLong(4, resp);
                        ps.setBoolean(5, aprovada);
                        ps.setLong(6, id);
                        ps.executeUpdate();
                    }
                }
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Aprova uma ficha pendente: créditos entram no cartão e o aprovador vira o responsável. */
    private void aprovarFicha(long id) throws Exception {
        try (Connection con = conectar()) {
            con.setAutoCommit(false);
            try {
                long idResp = idLogado();
                long[] ficha = lerValores(con, SQL_FICHA_TRAVADA, id);
                if (ficha[4] != 0) {
                    throw new ErroValidacao("Esta ficha já está aprovada.");
                }
                ajustarCreditos(con, ficha[0], 0, ficha[0], ficha[2]);
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE ficha_leitura SET Ativo = TRUE, ID_Funcionário = ? WHERE ID_Ficha = ?")) {
                    ps.setLong(1, idResp);
                    ps.setLong(2, id);
                    ps.executeUpdate();
                }
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Doação de livro: créditos para o cartão do cliente e +1 exemplar no estoque do livro.
     * Na edição, troca o livro corrige o estoque de um e do outro.
     */
    private void gravarDoacaoLivro(Long id, long idCliente, long idLivro, long creditos) throws Exception {
        try (Connection con = conectar()) {
            con.setAutoCommit(false);
            try {
                long idResp = idLogado();
                if (id == null) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO `doação_livros` (ID_Cliente, ID_Livro, ID_Funcionário, Créditos_Recebidos) "
                                    + "VALUES (?, ?, ?, ?)")) {
                        ps.setLong(1, idCliente);
                        ps.setLong(2, idLivro);
                        ps.setLong(3, idResp);
                        ps.setLong(4, creditos);
                        ps.executeUpdate();
                    }
                    ajustarCreditos(con, idCliente, 0, idCliente, creditos);
                    somarExemplares(con, idLivro, 1);
                } else {
                    long[] antiga = lerValores(con,
                            "SELECT ID_Cliente, ID_Livro, Créditos_Recebidos FROM `doação_livros` WHERE ID_Doação = ? FOR UPDATE",
                            id);
                    ajustarCreditos(con, antiga[0], antiga[2], idCliente, creditos);
                    if (antiga[1] != idLivro) {
                        somarExemplares(con, antiga[1], -1);
                        somarExemplares(con, idLivro, 1);
                    }
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE `doação_livros` SET ID_Cliente = ?, ID_Livro = ?, Créditos_Recebidos = ? "
                                    + "WHERE ID_Doação = ?")) {
                        ps.setLong(1, idCliente);
                        ps.setLong(2, idLivro);
                        ps.setLong(3, creditos);
                        ps.setLong(4, id);
                        ps.executeUpdate();
                    }
                }
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Doação de sangue: comprovante apresentado pelo cliente gera créditos no cartão. */
    private void gravarDoacaoSangue(Long id, long idCliente, long creditos) throws Exception {
        try (Connection con = conectar()) {
            con.setAutoCommit(false);
            try {
                long idResp = idLogado();
                if (id == null) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "INSERT INTO `doação_sangue` (ID_Cliente, ID_Funcionário, Créditos_Recebidos) VALUES (?, ?, ?)")) {
                        ps.setLong(1, idCliente);
                        ps.setLong(2, idResp);
                        ps.setLong(3, creditos);
                        ps.executeUpdate();
                    }
                    ajustarCreditos(con, idCliente, 0, idCliente, creditos);
                } else {
                    long[] antiga = lerValores(con,
                            "SELECT ID_Cliente, Créditos_Recebidos FROM `doação_sangue` WHERE ID_Doação = ? FOR UPDATE", id);
                    ajustarCreditos(con, antiga[0], antiga[1], idCliente, creditos);
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE `doação_sangue` SET ID_Cliente = ?, Créditos_Recebidos = ? WHERE ID_Doação = ?")) {
                        ps.setLong(1, idCliente);
                        ps.setLong(2, creditos);
                        ps.setLong(3, id);
                        ps.executeUpdate();
                    }
                }
                con.commit();
            } catch (Exception e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Ajusta o saldo de créditos do cartão quando um lançamento muda.
     * Mesmo cliente: soma só a diferença. Cliente trocado: tira do antigo e põe no novo.
     */
    private static void ajustarCreditos(Connection con, long clienteAntigo, long valorAntigo,
            long clienteNovo, long valorNovo) throws SQLException, ErroValidacao {
        if (clienteAntigo == clienteNovo) {
            somarCreditos(con, clienteNovo, valorNovo - valorAntigo);
        } else {
            somarCreditos(con, clienteAntigo, -valorAntigo);
            somarCreditos(con, clienteNovo, valorNovo);
        }
    }

    private static void somarCreditos(Connection con, long idCliente, long delta) throws SQLException, ErroValidacao {
        if (delta == 0) return;
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE `cartões` SET Creditos = Creditos + ? WHERE ID_Cliente = ? ORDER BY ID_Cartão LIMIT 1")) {
            ps.setLong(1, delta);
            ps.setLong(2, idCliente);
            if (ps.executeUpdate() == 0) {
                throw new ErroValidacao("Este cliente ainda não possui cartão. Crie o cartão antes de lançar créditos.");
            }
        }
    }

    private static void somarExemplares(Connection con, long idLivro, long delta) throws SQLException, ErroValidacao {
        if (delta == 0) return;
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE livros SET Qtd_Disponivel = Qtd_Disponivel + ? WHERE ID_Livro = ?")) {
            ps.setLong(1, delta);
            ps.setLong(2, idLivro);
            if (ps.executeUpdate() == 0) {
                throw new ErroValidacao("Livro não encontrado.");
            }
        }
    }

    /** Lê as colunas de um registro (travado com FOR UPDATE) como números, na ordem do SELECT. */
    private static long[] lerValores(Connection con, String sql, long id) throws SQLException, ErroValidacao {
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new ErroValidacao("Registro não encontrado. Atualize a tabela e tente novamente.");
                }
                long[] valores = new long[rs.getMetaData().getColumnCount()];
                for (int i = 0; i < valores.length; i++) {
                    valores[i] = rs.getLong(i + 1);
                }
                return valores;
            }
        }
    }

    /** ID do funcionário logado (quem registra ou aprova). */
    private static long idLogado() throws ErroValidacao {
        Funcionario f = Sessao.getLogado();
        if (f == null) {
            throw new ErroValidacao("Sua sessão expirou. Faça login novamente.");
        }
        return Long.parseLong(f.getId());
    }

    // ---------------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------------
    private static Connection conectar() throws SQLException {
        Connection con = Conexao.conectar();
        if (con == null) throw new SQLException("Não foi possível conectar ao banco de dados.");
        return con;
    }

    private static long lerInteiro(String texto, String mensagem) throws ErroValidacao {
        try {
            long v = Long.parseLong(texto.trim());
            if (v < 0) throw new ErroValidacao(mensagem);
            return v;
        } catch (NumberFormatException e) {
            throw new ErroValidacao(mensagem);
        }
    }

    private static Opcao escolhido(JComboBox<Opcao> cmb, String mensagem) throws ErroValidacao {
        Object o = cmb.getSelectedItem();
        if (!(o instanceof Opcao)) throw new ErroValidacao(mensagem);
        return (Opcao) o;
    }

    private static void selecionar(JComboBox<Opcao> cmb, long id) {
        for (int i = 0; i < cmb.getItemCount(); i++) {
            if (cmb.getItemAt(i).id == id) {
                cmb.setSelectedIndex(i);
                return;
            }
        }
    }

    private static String formatarId(long id) {
        return String.format("%03d", id);
    }

    private static long idDe(Object valor) {
        return Long.parseLong(String.valueOf(valor));
    }

    private static String formatarData(java.sql.Date data) {
        return data == null ? "" : data.toLocalDate().format(DATA_BR);
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    /** Erro esperado de regra de negócio/validação: mostrado como aviso, sem detalhes técnicos. */
    private static final class ErroValidacao extends Exception {
        private static final long serialVersionUID = 1L;

        ErroValidacao(String mensagem) {
            super(mensagem);
        }
    }

    @FunctionalInterface
    private interface Acao {
        void executar() throws Exception;
    }

    /** Item de lista (cliente ou livro): guarda o ID e mostra um texto. */
    private static final class Opcao {
        final long id;
        final String texto;

        Opcao(long id, String texto) {
            this.id = id;
            this.texto = texto;
        }

        @Override
        public String toString() {
            return texto;
        }
    }

    private JComboBox<Opcao> criarCombo(List<Opcao> opcoes) {
        JComboBox<Opcao> cmb = new JComboBox<>(opcoes.toArray(new Opcao[0]));
        estilizarCombo(cmb);
        return cmb;
    }

    private <T> void estilizarCombo(JComboBox<T> cmb) {
        cmb.setBackground(FUNDO);
        cmb.setForeground(Color.WHITE);
        cmb.setFont(new Font("Segoe UI", Font.PLAIN, 13));
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

    private JButton criarAba(String texto) {
        JButton b = new JButton(texto);
        b.setFont(new Font("Segoe UI", Font.BOLD, 14));
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void estilizarAba(JButton b, boolean ativa) {
        b.setForeground(ativa ? Color.WHITE : TEXTO_SUAVE);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, ativa ? AZUL : new Color(0, 0, 0, 0)),
                BorderFactory.createEmptyBorder(6, 4, 6, 4)));
    }

    // ---------------------------------------------------------------
    // ABA: tabela + busca + ghost loading (uma por aba)
    // ---------------------------------------------------------------
    private final class Aba {
        final String chave;
        final String singular;
        final String plural;
        final int visiveis;
        final Callable<List<Object[]>> carga;
        final DefaultTableModel modelo;
        final JTable tabela;
        final TableRowSorter<DefaultTableModel> sorter;
        final GhostPanel ghost;
        final CardLayout cardTab = new CardLayout();
        final JPanel cardTabela;
        boolean carregando;
        int cargaId;

        Aba(String chave, String singular, String plural, String[] colunas, int visiveis,
                Callable<List<Object[]>> carga, int... colunasEsquerda) {
            this.chave = chave;
            this.singular = singular;
            this.plural = plural;
            this.visiveis = visiveis;
            this.carga = carga;

            modelo = new DefaultTableModel(colunas, 0) {
                @Override
                public boolean isCellEditable(int r, int c) { return false; }
            };

            tabela = new JTable(modelo);
            tabela.setSelectionBackground(new Color(23, 55, 94));
            tabela.setSelectionForeground(Color.WHITE);
            sorter = new TableRowSorter<>(modelo);
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

            for (int c = 0; c < visiveis; c++) {
                boolean aEsquerda = false;
                for (int e : colunasEsquerda) {
                    if (e == c) aEsquerda = true;
                }
                tabela.getColumnModel().getColumn(c).setCellRenderer(aEsquerda ? esquerda : centro);
            }

            // Remove da tela as colunas auxiliares (o modelo continua com os valores)
            for (int c = colunas.length - 1; c >= visiveis; c--) {
                tabela.removeColumn(tabela.getColumnModel().getColumn(c));
            }

            ghost = new GhostPanel(visiveis);
            cardTabela = new JPanel(cardTab) {
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
            cardTabela.add(ghost, "ghost");
            cardTab.show(cardTabela, "tabela");
        }

        /** Monta a aba: barra de ações em cima e a tabela embaixo. */
        JPanel montar(JPanel barra) {
            JPanel raiz = new JPanel(new BorderLayout(0, 12));
            raiz.setOpaque(false);
            raiz.add(barra, BorderLayout.NORTH);
            raiz.add(cardTabela, BorderLayout.CENTER);
            return raiz;
        }

        void filtrar(String texto) {
            if (texto == null || texto.trim().isEmpty()) {
                sorter.setRowFilter(null);
            } else {
                int[] indices = new int[visiveis];
                for (int i = 0; i < visiveis; i++) indices[i] = i;
                sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(texto.trim()), indices));
            }
        }

        /** Valores de todas as colunas (inclusive as ocultas) da linha selecionada; null se nada selecionado. */
        Object[] linhaSelecionada() {
            int visual = tabela.getSelectedRow();
            if (visual < 0) {
                JOptionPane.showMessageDialog(TelaBiblioteca.this, "Selecione uma linha da tabela para editar.",
                        "Aviso", JOptionPane.WARNING_MESSAGE);
                return null;
            }
            int m = tabela.convertRowIndexToModel(visual);
            Object[] linha = new Object[modelo.getColumnCount()];
            for (int c = 0; c < linha.length; c++) {
                linha[c] = modelo.getValueAt(m, c);
            }
            return linha;
        }

        /** Busca em segundo plano, com ghost loading; a tabela só troca quando os dados chegam. */
        void carregar() {
            final int idCarga = ++cargaId;
            carregando = true;
            cardTab.show(cardTabela, "ghost");
            ghost.iniciar();
            atualizarTotal();

            async(carga, linhas -> {
                if (idCarga != cargaId) return;
                modelo.setRowCount(0);
                for (Object[] l : linhas) modelo.addRow(l);
                carregando = false;
                cardTab.show(cardTabela, "tabela");
                ghost.parar();
                atualizarTotal();
            }, ex -> {
                if (idCarga != cargaId) return;
                carregando = false;
                cardTab.show(cardTabela, "tabela");
                ghost.parar();
                atualizarTotal();
                erroCarga(ex);
            });
        }
    }

    // Ghost loading visual (mesmo efeito das outras telas)
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