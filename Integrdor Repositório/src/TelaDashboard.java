import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.text.NumberFormat;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;

/**
 * Tela de Início: resumo do arcade com os números principais, as recargas da
 * semana, as máquinas mais jogadas e os prêmios acabando.
 *
 * Os dados são lidos em segundo plano e a tela não abre mensagens de erro: se o
 * banco cair, continua mostrando os últimos números e avisa no canto superior.
 */
public class TelaDashboard extends JPanel {

    private static final Color FUNDO = new Color(2, 12, 27);
    private static final Color CARD = new Color(10, 22, 40);
    private static final Color BORDA = new Color(23, 42, 69);
    private static final Color AZUL = new Color(14, 116, 233);
    private static final Color TEXTO_SUAVE = new Color(148, 163, 184);
    private static final Color ALERTA = new Color(251, 191, 36);
    private static final Color CRITICO = new Color(248, 113, 113);
    /** Ordem fixa das fatias, escolhida para não confundir quem tem daltonismo. */
    private static final Color[] CORES_RANKING = {
            AZUL, new Color(217, 89, 38), new Color(25, 158, 112), new Color(201, 133, 0), new Color(213, 81, 129)
    };
    private static final Color COR_OUTROS = new Color(100, 116, 139);

    /** O App chama a cada 3s; a tela só vai ao banco neste intervalo. */
    private static final long INTERVALO_CONSULTA_MS = 15_000;
    private static final int DIAS_GRAFICO = 7;
    private static final int LIMITE_ESTOQUE_BAIXO = 5;
    private static final int LINHAS_POR_LISTA = 5;

    private static final Locale BRASIL = new Locale("pt", "BR");
    private static final NumberFormat MOEDA = NumberFormat.getCurrencyInstance(BRASIL);
    private static final NumberFormat INTEIRO = NumberFormat.getIntegerInstance(BRASIL);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    /** Tudo o que a tela mostra, já lido do banco. */
    static class Dados {
        long clientes;
        long cartoesAtivos;
        long maquinasLigadas;
        long maquinasTotal;
        long premiosEstoque;
        long premiosAtivos;
        long premiosEstoqueBaixo;
        long partidasHoje;
        long jogadoresHoje;
        BigDecimal recargasHoje = BigDecimal.ZERO;
        long qtdRecargasHoje;
        long creditosCirculacao;
        long pontosCirculacao;
        long resgatesHoje;
        long pontosResgatadosHoje;
        Map<LocalDate, BigDecimal> recargasPorDia = new LinkedHashMap<>();
        List<Object[]> maquinasMaisJogadas = new ArrayList<>();
        List<Object[]> premiosAcabando = new ArrayList<>();
        /** { nome, valor }, do maior para o menor; a última linha pode ser "Outros". */
        List<Object[]> rankingHoje = new ArrayList<>();
        boolean rankingEmPontos;
    }

    private final JLabel lblStatus = new JLabel(" ");
    private final Indicador indClientes = new Indicador("Clientes cadastrados");
    private final Indicador indCartoes = new Indicador("Cartões ativos");
    private final Indicador indMaquinas = new Indicador("Máquinas ligadas");
    private final Indicador indPremios = new Indicador("Prêmios em estoque");
    private final Indicador indPartidas = new Indicador("Partidas hoje");
    private final Indicador indRecargas = new Indicador("Recargas hoje");
    private final Indicador indCreditos = new Indicador("Créditos em circulação");
    private final Indicador indResgates = new Indicador("Resgates hoje");
    private final GraficoRecargas grafico = new GraficoRecargas();
    private final RankingHoje ranking = new RankingHoje();
    private final Lista listaMaquinas = new Lista();
    private final Lista listaPremios = new Lista();

    private boolean carregando = false;
    private long ultimaConsulta = 0;
    private LocalTime ultimoSucesso;

    public TelaDashboard() {
        setBackground(FUNDO);
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        add(criarTopo(), BorderLayout.NORTH);

        JPanel corpo = new JPanel(new BorderLayout(0, 12));
        corpo.setOpaque(false);
        corpo.add(criarIndicadores(), BorderLayout.NORTH);
        corpo.add(criarDetalhes(), BorderLayout.CENTER);
        add(corpo, BorderLayout.CENTER);

        recarregar();
    }

    private JPanel criarTopo() {
        JPanel topo = new JPanel(new BorderLayout());
        topo.setOpaque(false);

        JLabel lblTitulo = new JLabel("Início");
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        lblTitulo.setForeground(Color.WHITE);

        Funcionario logado = Sessao.getLogado();
        JLabel lblSaudacao = new JLabel(logado != null
                ? "Olá, " + logado.getNome() + ". Veja como está o arcade hoje."
                : "Veja como está o arcade hoje.");
        lblSaudacao.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSaudacao.setForeground(TEXTO_SUAVE);

        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        textos.add(lblTitulo);
        textos.add(lblSaudacao);

        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatus.setForeground(TEXTO_SUAVE);
        lblStatus.setHorizontalAlignment(SwingConstants.RIGHT);

        topo.add(textos, BorderLayout.WEST);
        topo.add(lblStatus, BorderLayout.EAST);
        return topo;
    }

    private JPanel criarIndicadores() {
        JPanel grade = new JPanel(new GridLayout(2, 4, 12, 12));
        grade.setOpaque(false);
        grade.add(indClientes);
        grade.add(indCartoes);
        grade.add(indMaquinas);
        grade.add(indPremios);
        grade.add(indPartidas);
        grade.add(indRecargas);
        grade.add(indCreditos);
        grade.add(indResgates);
        return grade;
    }

    private JPanel criarDetalhes() {
        JPanel detalhes = new JPanel(new GridBagLayout());
        detalhes.setOpaque(false);

        JPanel coluna = new JPanel(new GridLayout(2, 1, 0, 12));
        coluna.setOpaque(false);
        coluna.add(criarSecao("Máquinas mais jogadas (" + DIAS_GRAFICO + " dias)", listaMaquinas));
        coluna.add(criarSecao("Prêmios acabando", listaPremios));
        JPanel secaoGrafico = criarSecao("Recargas dos últimos " + DIAS_GRAFICO + " dias", grafico);

        // Tamanho preferido zerado: a proporção das colunas não muda com o tamanho dos nomes
        coluna.setPreferredSize(new Dimension(0, 0));
        secaoGrafico.setPreferredSize(new Dimension(0, 0));
        ranking.setPreferredSize(new Dimension(0, 0));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.gridx = 0;
        c.weightx = 0.42;
        c.insets = new java.awt.Insets(0, 0, 0, 12);
        detalhes.add(secaoGrafico, c);
        c.gridx = 1;
        c.weightx = 0.28;
        detalhes.add(ranking, c);
        c.gridx = 2;
        c.weightx = 0.30;
        c.insets = new java.awt.Insets(0, 0, 0, 0);
        detalhes.add(coluna, c);
        return detalhes;
    }

    private JPanel criarSecao(String titulo, JPanel conteudo) {
        JPanel secao = new PainelCard();
        secao.setLayout(new BorderLayout(0, 8));
        secao.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel lblTitulo = new JLabel(titulo);
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblTitulo.setForeground(Color.WHITE);

        secao.add(lblTitulo, BorderLayout.NORTH);
        secao.add(conteudo, BorderLayout.CENTER);
        return secao;
    }

    /** Busca os dados agora (usado ao abrir a tela). */
    public void recarregar() {
        if (carregando) {
            return;
        }
        carregando = true;
        ultimaConsulta = System.currentTimeMillis();

        new SwingWorker<Dados, Void>() {
            @Override
            protected Dados doInBackground() throws Exception {
                return consultar();
            }

            @Override
            protected void done() {
                carregando = false;
                try {
                    aplicar(get());
                } catch (Exception ex) {
                    mostrarFalha();
                }
            }
        }.execute();
    }

    /** Chamado pelo timer do App enquanto a tela está aberta. */
    public void atualizarSilenciosamente() {
        if (System.currentTimeMillis() - ultimaConsulta >= INTERVALO_CONSULTA_MS) {
            recarregar();
        }
    }

    private void mostrarFalha() {
        lblStatus.setForeground(ALERTA);
        lblStatus.setText(ultimoSucesso != null
                ? "Sem conexão com o banco. Dados de " + ultimoSucesso.format(HORA)
                : "Sem conexão com o banco. Tentando novamente...");
    }

    /** Lê tudo do banco. Roda fora da thread da interface. */
    static Dados consultar() throws SQLException {
        // O banco grava em UTC: "hoje" precisa ser o dia no fuso de quem está usando.
        ZoneId fuso = ZoneId.systemDefault();
        int minutosFuso = fuso.getRules().getOffset(Instant.now()).getTotalSeconds() / 60;
        LocalDate hoje = LocalDate.now(fuso);
        LocalDate inicioGrafico = hoje.minusDays(DIAS_GRAFICO - 1);

        String diaRecarga = diaLocal("Data_Hora", minutosFuso);
        String diaPartida = diaLocal("p.Data_Hora", minutosFuso);
        // Partida com 0 créditos em máquina paga é tentativa sem saldo (ver TelaPartidas).
        String partidaValida = "NOT (p.`Créditos_Gastos` = 0 AND COALESCE(m.Valor_Partida, 0) > 0)";
        String partidasComMaquina = "FROM partidas p LEFT JOIN `máquinas` m ON m.`ID_Máquina` = p.`ID_Máquina` ";

        String sqlResumo = "SELECT "
                + "(SELECT COUNT(*) FROM clientes) AS clientes, "
                + "(SELECT COUNT(*) FROM `cartões` WHERE Ativo = TRUE) AS cartoes, "
                + "(SELECT COALESCE(SUM(Creditos), 0) FROM `cartões` WHERE Ativo = TRUE) AS creditos, "
                + "(SELECT COALESCE(SUM(Pontos), 0) FROM `cartões` WHERE Ativo = TRUE) AS pontos, "
                + "(SELECT COUNT(*) FROM `máquinas` WHERE Ativo = TRUE) AS maquinas_ligadas, "
                + "(SELECT COUNT(*) FROM `máquinas`) AS maquinas, "
                + "(SELECT COALESCE(SUM(Qtd_Disponivel), 0) FROM premios WHERE Ativo = TRUE) AS estoque, "
                + "(SELECT COUNT(*) FROM premios WHERE Ativo = TRUE) AS premios, "
                + "(SELECT COUNT(*) FROM premios WHERE Ativo = TRUE AND Qtd_Disponivel <= " + LIMITE_ESTOQUE_BAIXO + ") AS estoque_baixo, "
                + "(SELECT COUNT(*) " + partidasComMaquina + "WHERE " + partidaValida + " AND " + diaPartida + " = ?) AS partidas, "
                + "(SELECT COUNT(DISTINCT p.`ID_Cartão`) " + partidasComMaquina + "WHERE " + partidaValida + " AND " + diaPartida + " = ?) AS jogadores, "
                + "(SELECT COALESCE(SUM(Valor), 0) FROM recargas WHERE " + diaRecarga + " = ?) AS recargas_valor, "
                + "(SELECT COUNT(*) FROM recargas WHERE " + diaRecarga + " = ?) AS recargas_qtd, "
                + "(SELECT COUNT(*) FROM resgates WHERE " + diaRecarga + " = ?) AS resgates, "
                + "(SELECT COALESCE(SUM(Pontos_Gastos), 0) FROM resgates WHERE " + diaRecarga + " = ?) AS resgates_pontos";

        String sqlRecargas = "SELECT " + diaRecarga + " AS dia, SUM(Valor) AS total FROM recargas "
                + "WHERE " + diaRecarga + " >= ? GROUP BY dia";

        String sqlMaquinas = "SELECT m.Nome AS nome, COUNT(*) AS qtd FROM partidas p "
                + "JOIN `máquinas` m ON m.`ID_Máquina` = p.`ID_Máquina` "
                + "WHERE " + partidaValida + " AND " + diaPartida + " >= ? "
                + "GROUP BY m.`ID_Máquina`, m.Nome ORDER BY qtd DESC, m.Nome LIMIT " + LINHAS_POR_LISTA;

        String sqlPremios = "SELECT Nome AS nome, Qtd_Disponivel AS qtd FROM premios "
                + "WHERE Ativo = TRUE AND Qtd_Disponivel <= " + LIMITE_ESTOQUE_BAIXO + " "
                + "ORDER BY Qtd_Disponivel, Nome LIMIT " + LINHAS_POR_LISTA;

        String sqlRanking = "SELECT COALESCE(cl.Nome_Completo, CONCAT('Cartão ', p.`ID_Cartão`)) AS nome, "
                + "SUM(p.Pontos_Obtidos) AS pontos, SUM(p.`Créditos_Gastos`) AS creditos FROM partidas p "
                + "JOIN `cartões` c ON c.`ID_Cartão` = p.`ID_Cartão` "
                + "LEFT JOIN clientes cl ON cl.ID_Cliente = c.ID_Cliente "
                + "WHERE " + diaPartida + " = ? GROUP BY c.ID_Cliente, nome";

        Dados d = new Dados();
        for (int i = 0; i < DIAS_GRAFICO; i++) {
            d.recargasPorDia.put(inicioGrafico.plusDays(i), BigDecimal.ZERO);
        }

        Connection con = Conexao.conectar();
        if (con == null) {
            throw new SQLException("Não foi possível conectar ao banco de dados.");
        }
        try (con) {
            try (PreparedStatement ps = con.prepareStatement(sqlResumo)) {
                for (int i = 1; i <= 6; i++) {
                    ps.setString(i, hoje.toString());
                }
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    d.clientes = rs.getLong("clientes");
                    d.cartoesAtivos = rs.getLong("cartoes");
                    d.creditosCirculacao = rs.getLong("creditos");
                    d.pontosCirculacao = rs.getLong("pontos");
                    d.maquinasLigadas = rs.getLong("maquinas_ligadas");
                    d.maquinasTotal = rs.getLong("maquinas");
                    d.premiosEstoque = rs.getLong("estoque");
                    d.premiosAtivos = rs.getLong("premios");
                    d.premiosEstoqueBaixo = rs.getLong("estoque_baixo");
                    d.partidasHoje = rs.getLong("partidas");
                    d.jogadoresHoje = rs.getLong("jogadores");
                    d.recargasHoje = rs.getBigDecimal("recargas_valor");
                    d.qtdRecargasHoje = rs.getLong("recargas_qtd");
                    d.resgatesHoje = rs.getLong("resgates");
                    d.pontosResgatadosHoje = rs.getLong("resgates_pontos");
                }
            }

            try (PreparedStatement ps = con.prepareStatement(sqlRecargas)) {
                ps.setString(1, inicioGrafico.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        d.recargasPorDia.put(LocalDate.parse(rs.getString("dia")), rs.getBigDecimal("total"));
                    }
                }
            }

            try (PreparedStatement ps = con.prepareStatement(sqlMaquinas)) {
                ps.setString(1, inicioGrafico.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        d.maquinasMaisJogadas.add(new Object[] { rs.getString("nome"), rs.getLong("qtd") });
                    }
                }
            }

            try (PreparedStatement ps = con.prepareStatement(sqlPremios); ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    d.premiosAcabando.add(new Object[] { rs.getString("nome"), rs.getLong("qtd") });
                }
            }

            try (PreparedStatement ps = con.prepareStatement(sqlRanking)) {
                ps.setString(1, hoje.toString());
                List<Object[]> linhas = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        linhas.add(new Object[] { rs.getString("nome"), rs.getLong("pontos"), rs.getLong("creditos") });
                    }
                }
                montarRanking(d, linhas);
            }
        }
        return d;
    }

    /**
     * Mostra os pontos ganhos no dia. Enquanto nenhuma partida do dia tiver pontos
     * gravados, mostra os créditos gastos (a TelaPartidas ainda grava 0 pontos).
     */
    private static void montarRanking(Dados d, List<Object[]> linhas) {
        d.rankingEmPontos = linhas.stream().anyMatch(l -> (long) l[1] > 0);
        int coluna = d.rankingEmPontos ? 1 : 2;

        List<Object[]> ordenadas = new ArrayList<>();
        for (Object[] l : linhas) {
            if ((long) l[coluna] > 0) {
                ordenadas.add(new Object[] { l[0], l[coluna] });
            }
        }
        ordenadas.sort((a, b) -> Long.compare((long) b[1], (long) a[1]));

        long outros = 0;
        for (int i = 0; i < ordenadas.size(); i++) {
            if (i < CORES_RANKING.length) {
                d.rankingHoje.add(ordenadas.get(i));
            } else {
                outros += (long) ordenadas.get(i)[1];
            }
        }
        if (outros > 0) {
            d.rankingHoje.add(new Object[] { "Outros", outros });
        }
    }

    /** Expressão SQL que converte uma coluna em UTC para a data no fuso local. */
    private static String diaLocal(String coluna, int minutosFuso) {
        return "DATE(DATE_ADD(" + coluna + ", INTERVAL " + minutosFuso + " MINUTE))";
    }

    /** Coloca os dados na tela. Roda na thread da interface. */
    void aplicar(Dados d) {
        indClientes.definir(INTEIRO.format(d.clientes), "no total");
        indCartoes.definir(INTEIRO.format(d.cartoesAtivos), "em uso pelos clientes");
        indMaquinas.definir(INTEIRO.format(d.maquinasLigadas), "de " + INTEIRO.format(d.maquinasTotal) + " cadastradas");
        indPartidas.definir(INTEIRO.format(d.partidasHoje), plural(d.jogadoresHoje, "cartão jogando", "cartões jogando"));
        indRecargas.definir(MOEDA.format(d.recargasHoje), plural(d.qtdRecargasHoje, "recarga", "recargas"));
        indCreditos.definir(INTEIRO.format(d.creditosCirculacao), INTEIRO.format(d.pontosCirculacao) + " pontos acumulados");
        indResgates.definir(INTEIRO.format(d.resgatesHoje), INTEIRO.format(d.pontosResgatadosHoje) + " pontos trocados");

        if (d.premiosEstoqueBaixo > 0) {
            indPremios.definir(INTEIRO.format(d.premiosEstoque), plural(d.premiosEstoqueBaixo, "prêmio acabando", "prêmios acabando"), ALERTA);
        } else {
            indPremios.definir(INTEIRO.format(d.premiosEstoque), plural(d.premiosAtivos, "prêmio ativo", "prêmios ativos"));
        }

        grafico.definir(d.recargasPorDia);
        ranking.definir(d.rankingHoje, d.rankingEmPontos);

        listaMaquinas.limpar();
        for (Object[] linha : d.maquinasMaisJogadas) {
            listaMaquinas.adicionar((String) linha[0], plural((long) linha[1], "partida", "partidas"), TEXTO_SUAVE);
        }
        listaMaquinas.concluir("Nenhuma partida nos últimos " + DIAS_GRAFICO + " dias.");

        listaPremios.limpar();
        for (Object[] linha : d.premiosAcabando) {
            long qtd = (long) linha[1];
            if (qtd <= 0) {
                listaPremios.adicionar((String) linha[0], "Esgotado", CRITICO);
            } else {
                listaPremios.adicionar((String) linha[0], plural(qtd, "restante", "restantes"), ALERTA);
            }
        }
        listaPremios.concluir(d.premiosAtivos == 0
                ? "Nenhum prêmio ativo cadastrado."
                : "Nenhum prêmio com estoque baixo.");

        ultimoSucesso = LocalTime.now();
        lblStatus.setForeground(TEXTO_SUAVE);
        lblStatus.setText("Atualizado às " + ultimoSucesso.format(HORA));
    }

    private static String plural(long qtd, String singular, String varios) {
        return INTEIRO.format(qtd) + " " + (qtd == 1 ? singular : varios);
    }

    private static class PainelCard extends JPanel {
        PainelCard() {
            setOpaque(false);
        }

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
    }

    /** Card com um número em destaque. */
    private static class Indicador extends PainelCard {
        private final JLabel lblValor = new JLabel("—");
        private final JLabel lblDetalhe = new JLabel(" ");

        Indicador(String titulo) {
            setLayout(new GridLayout(3, 1));
            setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

            JLabel lblTitulo = new JLabel(titulo);
            lblTitulo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            lblTitulo.setForeground(TEXTO_SUAVE);

            lblValor.setFont(new Font("Segoe UI", Font.BOLD, 22));
            lblValor.setForeground(Color.WHITE);

            lblDetalhe.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            lblDetalhe.setForeground(TEXTO_SUAVE);

            add(lblTitulo);
            add(lblValor);
            add(lblDetalhe);
        }

        void definir(String valor, String detalhe) {
            definir(valor, detalhe, TEXTO_SUAVE);
        }

        void definir(String valor, String detalhe, Color corDetalhe) {
            lblValor.setText(valor);
            lblDetalhe.setText(detalhe);
            lblDetalhe.setForeground(corDetalhe);
        }
    }

    /** Linhas "nome ... valor" com altura fixa, para as listas não esticarem. */
    private static class Lista extends JPanel {
        Lista() {
            this(LINHAS_POR_LISTA, 0);
        }

        /** linhas = 0: a altura acompanha a quantidade de itens. */
        Lista(int linhas, int espaco) {
            setOpaque(false);
            setLayout(new GridLayout(linhas, 1, 0, espaco));
            concluir("Carregando...");
        }

        void limpar() {
            removeAll();
        }

        void adicionar(String nome, String valor, Color corValor) {
            adicionar(nome, valor, corValor, null);
        }

        void adicionar(String nome, String valor, Color corValor, Color marca) {
            JLabel lblNome = new JLabel(nome);
            lblNome.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            lblNome.setForeground(Color.WHITE);
            if (marca != null) {
                lblNome.setIcon(new MarcaLegenda(marca));
                lblNome.setIconTextGap(8);
            }

            JLabel lblValor = new JLabel(valor);
            lblValor.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            lblValor.setForeground(corValor);

            JPanel linha = new JPanel(new BorderLayout(10, 0));
            linha.setOpaque(false);
            linha.add(lblNome, BorderLayout.CENTER);
            linha.add(lblValor, BorderLayout.EAST);
            add(linha);
        }

        /** Mostra a mensagem se a lista ficou vazia e redesenha. */
        void concluir(String mensagemVazia) {
            if (getComponentCount() == 0) {
                JLabel lblVazio = new JLabel(mensagemVazia);
                lblVazio.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                lblVazio.setForeground(TEXTO_SUAVE);
                add(lblVazio);
            }
            revalidate();
            repaint();
        }
    }

    /** Quadradinho colorido da legenda. */
    private static class MarcaLegenda implements Icon {
        private final Color cor;

        MarcaLegenda(Color cor) {
            this.cor = cor;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(cor);
            g2.fillRoundRect(x, y, getIconWidth(), getIconHeight(), 4, 4);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 10;
        }

        @Override
        public int getIconHeight() {
            return 10;
        }
    }

    /** Card com a rosca de quem mais pontuou no dia e a legenda com os valores. */
    private static class RankingHoje extends PainelCard {
        private final JLabel lblTitulo = new JLabel("Quem mais ganhou pontos hoje");
        private final Rosca rosca = new Rosca();
        private final Lista legenda = new Lista(0, 4);

        RankingHoje() {
            setLayout(new BorderLayout(0, 8));
            setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

            lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 13));
            lblTitulo.setForeground(Color.WHITE);

            add(lblTitulo, BorderLayout.NORTH);
            add(rosca, BorderLayout.CENTER);
            add(legenda, BorderLayout.SOUTH);
        }

        void definir(List<Object[]> linhas, boolean emPontos) {
            boolean mostrarPontos = emPontos || linhas.isEmpty();
            String unidade = mostrarPontos ? "pontos" : "créditos";
            lblTitulo.setText(mostrarPontos ? "Quem mais ganhou pontos hoje" : "Quem mais gastou créditos hoje");

            long total = 0;
            for (Object[] l : linhas) {
                total += (long) l[1];
            }

            legenda.limpar();
            for (int i = 0; i < linhas.size(); i++) {
                long valor = (long) linhas.get(i)[1];
                String detalhe = INTEIRO.format(valor) + " · " + Math.round(valor * 100.0 / total) + "%";
                legenda.adicionar((String) linhas.get(i)[0], detalhe, TEXTO_SUAVE, corDaFatia(i));
            }
            legenda.concluir("Nenhuma partida registrada hoje.");

            rosca.definir(linhas, total, unidade);
        }

        private static Color corDaFatia(int i) {
            return i < CORES_RANKING.length ? CORES_RANKING[i] : COR_OUTROS;
        }

        private static class Rosca extends JPanel {
            private static final double FOLGA_GRAUS = 2;

            private List<Object[]> linhas = new ArrayList<>();
            private long total = 0;
            private String unidade = "pontos";

            Rosca() {
                setOpaque(false);
            }

            void definir(List<Object[]> linhas, long total, String unidade) {
                this.linhas = linhas;
                this.total = total;
                this.unidade = unidade;
                repaint();
            }

            @Override
            protected void paintComponent(Graphics g) {
                int diametro = Math.min(getWidth(), getHeight()) - 8;
                if (diametro <= 0) {
                    return;
                }
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                float espessura = Math.max(14f, diametro * 0.16f);
                double raio = (diametro - espessura) / 2.0;
                double cx = getWidth() / 2.0;
                double cy = getHeight() / 2.0;
                g2.setStroke(new BasicStroke(espessura, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

                if (total == 0) {
                    g2.setColor(BORDA);
                    g2.draw(new Arc2D.Double(cx - raio, cy - raio, raio * 2, raio * 2, 0, 360, Arc2D.OPEN));
                } else {
                    double folga = linhas.size() > 1 ? FOLGA_GRAUS : 0;
                    double inicio = 90;
                    for (int i = 0; i < linhas.size(); i++) {
                        double extensao = (long) linhas.get(i)[1] * 360.0 / total;
                        g2.setColor(corDaFatia(i));
                        g2.draw(new Arc2D.Double(cx - raio, cy - raio, raio * 2, raio * 2, inicio - folga / 2,
                                -Math.max(extensao - folga, 1), Arc2D.OPEN));
                        inicio -= extensao;
                    }
                }

                String numero = INTEIRO.format(total);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 20));
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(numero, (float) (cx - fm.stringWidth(numero) / 2.0), (float) cy + 2);

                g2.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                g2.setColor(TEXTO_SUAVE);
                fm = g2.getFontMetrics();
                g2.drawString(unidade, (float) (cx - fm.stringWidth(unidade) / 2.0), (float) cy + 18);
                g2.dispose();
            }
        }
    }

    /** Barras por dia. Só hoje e o maior dia têm rótulo; os demais aparecem ao passar o mouse. */
    private static class GraficoRecargas extends JPanel {
        private static final int MARGEM_TOPO = 24;
        private static final int MARGEM_BASE = 26;
        private static final int LARGURA_MAX_BARRA = 56;

        private List<LocalDate> dias = new ArrayList<>();
        private List<BigDecimal> valores = new ArrayList<>();

        GraficoRecargas() {
            setOpaque(false);
            setToolTipText(""); // ativa o getToolTipText abaixo
        }

        void definir(Map<LocalDate, BigDecimal> recargasPorDia) {
            dias = new ArrayList<>(recargasPorDia.keySet());
            valores = new ArrayList<>(recargasPorDia.values());
            repaint();
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            if (dias.isEmpty()) {
                return null;
            }
            int i = Math.min(e.getX() * dias.size() / Math.max(getWidth(), 1), dias.size() - 1);
            return dias.get(i).format(DIA_MES) + ": " + MOEDA.format(valores.get(i));
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (dias.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int base = getHeight() - MARGEM_BASE;
            int alturaUtil = base - MARGEM_TOPO;
            int larguraColuna = getWidth() / dias.size();
            int larguraBarra = Math.min(larguraColuna / 2, LARGURA_MAX_BARRA);

            BigDecimal maior = BigDecimal.ZERO;
            for (BigDecimal v : valores) {
                maior = maior.max(v);
            }
            boolean semDados = maior.signum() == 0;
            LocalDate hoje = LocalDate.now();

            g2.setColor(BORDA);
            g2.drawLine(0, base, getWidth(), base);

            for (int i = 0; i < dias.size(); i++) {
                int centro = larguraColuna * i + larguraColuna / 2;
                BigDecimal valor = valores.get(i);
                boolean ehHoje = dias.get(i).equals(hoje);
                int topoBarra = base;

                if (valor.signum() > 0) {
                    int altura = Math.max(3, (int) Math.round(valor.doubleValue() / maior.doubleValue() * alturaUtil));
                    topoBarra = base - altura;
                    int x = centro - larguraBarra / 2;
                    g2.setColor(AZUL);
                    g2.setClip(x, topoBarra, larguraBarra, altura);
                    g2.fillRoundRect(x, topoBarra, larguraBarra, altura + 8, 8, 8);
                    g2.setClip(null);
                }

                g2.setFont(new Font("Segoe UI", ehHoje ? Font.BOLD : Font.PLAIN, 11));
                g2.setColor(ehHoje ? Color.WHITE : TEXTO_SUAVE);
                centralizar(g2, ehHoje ? "Hoje" : dias.get(i).format(DIA_MES), centro, base + 18);

                if (valor.signum() > 0 && (ehHoje || valor.compareTo(maior) == 0)) {
                    g2.setColor(Color.WHITE);
                    centralizar(g2, MOEDA.format(valor), centro, topoBarra - 6);
                }
            }

            if (semDados) {
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                g2.setColor(TEXTO_SUAVE);
                centralizar(g2, "Nenhuma recarga nos últimos " + DIAS_GRAFICO + " dias.", getWidth() / 2, base / 2);
            }
            g2.dispose();
        }

        private void centralizar(Graphics2D g2, String texto, int centro, int y) {
            FontMetrics fm = g2.getFontMetrics();
            int x = Math.max(0, Math.min(centro - fm.stringWidth(texto) / 2, getWidth() - fm.stringWidth(texto)));
            g2.drawString(texto, x, y);
        }
    }
}
