import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

/**
 * Tela de LOG (só para administradores): um "terminal" que mostra tudo o que acontece
 * no sistema, em tempo real, em frases legíveis. Exemplo:
 *
 *   [06/11 17:42:10]  Murilo Pessoa cadastrou o livro  ›  "Dom Casmurro" de Machado de Assis | 3 exemplar(es)
 *
 * Os dados vêm da tabela `auditoria` (ver Auditoria). Ao abrir, carrega as últimas
 * ações; depois o App chama atualizarSilenciosamente() a cada 3s e só as ações NOVAS
 * são buscadas e acrescentadas no fim. A busca filtra o que já está carregado.
 */
public class TelaLog extends JPanel {

    // =========================================================================
    // CONSTANTES VISUAIS
    // =========================================================================
    private static final Color FUNDO = new Color(2, 12, 27);
    private static final Color TERMINAL = new Color(4, 9, 18);
    private static final Color BORDA = new Color(23, 42, 69);
    private static final Color CAMPO = new Color(13, 27, 46);
    private static final Color TEXTO_SUAVE = new Color(148, 163, 184);

    // Cores das linhas (por tipo de ação)
    private static final Color COR_HORA = new Color(100, 116, 139);
    private static final Color COR_NOME = new Color(125, 211, 252);
    private static final Color COR_TEXTO = new Color(226, 232, 240);
    private static final Color COR_DETALHE = new Color(148, 163, 184);
    private static final Color COR_DINHEIRO = new Color(74, 222, 128);
    private static final Color COR_EXCLUSAO = new Color(248, 113, 113);
    private static final Color COR_ACESSO = new Color(250, 204, 21);
    private static final Color COR_CADASTRO = new Color(196, 181, 253);

    private static final Font FONTE = new Font("Consolas", Font.PLAIN, 14);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss");

    /** Quantas ações aparecem ao abrir e o máximo guardado na tela. */
    private static final int CARGA_INICIAL = 300;
    private static final int MAXIMO_NA_TELA = 1000;

    // =========================================================================
    // CAMPOS
    // =========================================================================
    private final JTextPane terminal = new JTextPane();
    private final JScrollPane scroll;
    private final JLabel lblStatus = new JLabel(" ");

    /** Tudo o que já foi carregado (a busca filtra esta lista). */
    private final List<Auditoria.Registro> registros = new ArrayList<>();
    private long ultimoId = 0;
    private boolean buscando = false;
    private String filtro = "";

    // =========================================================================
    // CONSTRUTOR
    // =========================================================================
    public TelaLog() {
        setBackground(FUNDO);
        setLayout(new BorderLayout(15, 12));
        setBorder(BorderFactory.createEmptyBorder(20, 25, 15, 25));

        // Topo: título, subtítulo e busca
        JLabel titulo = new JLabel("Log do Sistema");
        titulo.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titulo.setForeground(Color.WHITE);
        JLabel subtitulo = new JLabel("Tudo o que acontece no sistema, em tempo real.");
        subtitulo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitulo.setForeground(TEXTO_SUAVE);

        JPanel textos = new JPanel(new BorderLayout(0, 4));
        textos.setOpaque(false);
        textos.add(titulo, BorderLayout.NORTH);
        textos.add(subtitulo, BorderLayout.CENTER);

        JPanel topo = new JPanel(new BorderLayout(15, 12));
        topo.setOpaque(false);
        topo.add(textos, BorderLayout.NORTH);
        topo.add(criarBusca(), BorderLayout.WEST);
        add(topo, BorderLayout.NORTH);

        // Centro: o "terminal"
        terminal.setEditable(false);
        terminal.setBackground(TERMINAL);
        terminal.setForeground(COR_TEXTO);
        terminal.setFont(FONTE);
        terminal.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        scroll = new JScrollPane(terminal);
        scroll.setBorder(BorderFactory.createLineBorder(BORDA, 1));
        scroll.getViewport().setBackground(TERMINAL);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        // Rodapé: status
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblStatus.setForeground(TEXTO_SUAVE);
        lblStatus.setPreferredSize(new Dimension(0, 25));
        add(lblStatus, BorderLayout.SOUTH);
    }

    // =========================================================================
    // CHAMADO PELO APP
    // =========================================================================

    /** Ao abrir a tela: recarrega as últimas ações do zero. */
    public void recarregar() {
        registros.clear();
        ultimoId = 0;
        terminal.setText("");
        buscarNovos(true);
    }

    /** A cada 3s (timer do App): busca só o que é novo. */
    public void atualizarSilenciosamente() {
        buscarNovos(false);
    }

    // =========================================================================
    // BANCO (em segundo plano)
    // =========================================================================

    private void buscarNovos(boolean cargaInicial) {
        if (buscando) {
            return;
        }
        buscando = true;
        if (cargaInicial) {
            lblStatus.setText("Carregando...");
        }
        final long depoisDe = ultimoId;
        new SwingWorker<List<Auditoria.Registro>, Void>() {
            @Override
            protected List<Auditoria.Registro> doInBackground() throws Exception {
                return Auditoria.consultar(depoisDe, CARGA_INICIAL);
            }

            @Override
            protected void done() {
                buscando = false;
                try {
                    List<Auditoria.Registro> novos = get();
                    if (depoisDe != ultimoId) {
                        buscarNovos(true); // a tela foi recarregada enquanto buscava: busca de novo
                        return;
                    }
                    acrescentar(novos);
                    lblStatus.setText(registros.size() + " ações carregadas · atualiza sozinho a cada 3 segundos");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    Throwable causa = e.getCause() != null ? e.getCause() : e;
                    lblStatus.setText("Sem conexão com o banco: " + causa.getMessage());
                }
            }
        }.execute();
    }

    private void acrescentar(List<Auditoria.Registro> novos) {
        if (novos.isEmpty()) {
            if (registros.isEmpty()) {
                redesenhar(); // mostra a mensagem de "nenhuma ação"
            }
            return;
        }
        boolean estavaNoFim = estaNoFim();
        boolean primeiraCarga = registros.isEmpty();
        registros.addAll(novos);
        ultimoId = novos.get(novos.size() - 1).id;

        if (primeiraCarga || registros.size() > MAXIMO_NA_TELA) {
            if (registros.size() > MAXIMO_NA_TELA) {
                registros.subList(0, registros.size() - MAXIMO_NA_TELA).clear();
            }
            redesenhar(); // reescreve do zero (tira a mensagem "nenhuma ação", se houver)
        } else {
            for (Auditoria.Registro r : novos) {
                if (passaNoFiltro(r)) {
                    escreverLinha(r);
                }
            }
        }
        if (estavaNoFim) {
            rolarParaFim();
        }
    }

    // =========================================================================
    // DESENHO DO TERMINAL
    // =========================================================================

    /** Limpa e reescreve tudo (usado ao abrir e ao mudar a busca). */
    private void redesenhar() {
        terminal.setText("");
        int mostradas = 0;
        for (Auditoria.Registro r : registros) {
            if (passaNoFiltro(r)) {
                escreverLinha(r);
                mostradas++;
            }
        }
        if (mostradas == 0 && !buscando) {
            escrever(registros.isEmpty() ? "Nenhuma ação registrada ainda." : "Nenhuma ação encontrada para \"" + filtro + "\".",
                    COR_HORA, false);
        }
        rolarParaFim();
    }

    /** [06/11 17:42:10]  Fulano fez tal coisa  ›  detalhes */
    private void escreverLinha(Auditoria.Registro r) {
        Frase f = frase(r);
        escrever("[" + (r.dataHora == null ? "--/-- --:--:--" : r.dataHora.format(HORA)) + "]  ", COR_HORA, false);
        if (f.sujeito != null) {
            escrever(f.sujeito + " ", COR_NOME, true);
        }
        escrever(f.texto, f.cor, false);
        if (r.detalhes != null && !r.detalhes.isEmpty()) {
            escrever("  ›  " + r.detalhes, COR_DETALHE, false);
        }
        escrever("\n", COR_TEXTO, false);
    }

    private void escrever(String texto, Color cor, boolean negrito) {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setForeground(a, cor);
        StyleConstants.setBold(a, negrito);
        StyleConstants.setFontFamily(a, FONTE.getFamily());
        StyleConstants.setFontSize(a, FONTE.getSize());
        StyledDocument doc = terminal.getStyledDocument();
        try {
            doc.insertString(doc.getLength(), texto, a);
        } catch (BadLocationException ignorar) {
            // não acontece: sempre inserimos no fim
        }
    }

    private boolean estaNoFim() {
        JScrollBar barra = scroll.getVerticalScrollBar();
        return barra.getValue() + barra.getVisibleAmount() >= barra.getMaximum() - 30;
    }

    private void rolarParaFim() {
        SwingUtilities.invokeLater(() -> terminal.setCaretPosition(terminal.getDocument().getLength()));
    }

    // =========================================================================
    // BUSCA
    // =========================================================================

    private boolean passaNoFiltro(Auditoria.Registro r) {
        if (filtro.isEmpty()) {
            return true;
        }
        Frase f = frase(r);
        String tudo = ((f.sujeito == null ? "" : f.sujeito) + " " + f.texto + " " + (r.detalhes == null ? "" : r.detalhes)
                + " " + (r.usuario == null ? "" : r.usuario)).toLowerCase();
        return tudo.contains(filtro.toLowerCase());
    }

    private JPanel criarBusca() {
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
        painel.setPreferredSize(new Dimension(420, 38));
        painel.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));

        JLabel lupa = new JLabel("🔍");
        lupa.setForeground(TEXTO_SUAVE);

        String dica = "Buscar por funcionário, ação ou detalhe (ex.: recarga, Murilo, cartão 007)...";
        JTextField txt = new JTextField() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(new Color(100, 116, 139));
                    g2.setFont(getFont());
                    int y = (getHeight() + g2.getFontMetrics().getAscent() - g2.getFontMetrics().getDescent()) / 2;
                    g2.drawString(dica, getInsets().left, y);
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
            public void keyReleased(KeyEvent e) {
                String novo = txt.getText().trim();
                if (!novo.equals(filtro)) {
                    filtro = novo;
                    redesenhar();
                }
            }
        });

        painel.add(lupa, BorderLayout.WEST);
        painel.add(txt, BorderLayout.CENTER);
        return painel;
    }

    // =========================================================================
    // TRADUÇÃO: código da ação -> frase em português
    // =========================================================================

    /** Quem fez (pode ser null), o que fez e a cor da linha. */
    private static final class Frase {
        final String sujeito;
        final String texto;
        final Color cor;

        Frase(String sujeito, String texto, Color cor) {
            this.sujeito = sujeito;
            this.texto = texto;
            this.cor = cor;
        }
    }

    private static Frase frase(Auditoria.Registro r) {
        String quem = r.nome == null || r.nome.isEmpty() ? "Sistema" : r.nome;
        String acao = r.acao == null ? "" : r.acao;

        switch (acao) {
            case "LOGIN":
                return new Frase(quem, "entrou no sistema", COR_ACESSO);
            case "LOGOUT":
                return new Frase(quem, "saiu do sistema", COR_ACESSO);
            case "LOGIN_FALHOU":
                return new Frase(null, "Tentativa de login com senha errada para o usuário \""
                        + (r.usuario == null ? "?" : r.usuario) + "\"", COR_EXCLUSAO);
            case "LOGIN_BLOQUEADO":
                return new Frase(null, "Funcionário bloqueado tentou entrar: \""
                        + (r.usuario == null ? "?" : r.usuario) + "\"", COR_EXCLUSAO);
            case "RECARGA":
                return new Frase(quem, "fez uma recarga", COR_DINHEIRO);
            case "RECARGA_AUTOATENDIMENTO":
                return new Frase("Cliente", "fez uma recarga no autoatendimento", COR_DINHEIRO);
            case "PARTIDA":
                return new Frase(quem, "registrou uma partida", COR_TEXTO);
            case "PARTIDA_SEM_SALDO":
                return new Frase(quem, "tentou registrar uma partida sem saldo", COR_DETALHE);
            case "RESGATE":
                return new Frase(quem, "fez um resgate de prêmios", COR_DINHEIRO);
            case "AUTOATENDIMENTO_ABERTO":
                return new Frase(quem, "abriu o autoatendimento", COR_ACESSO);
            case "AUTOATENDIMENTO_FECHADO":
                return new Frase(quem, "fechou o autoatendimento", COR_ACESSO);
            case "APROVACAO_FICHA_LEITURA":
                return new Frase(quem, "aprovou uma ficha de leitura", COR_DINHEIRO);
            case "DOACAO_LIVRO":
                return new Frase(quem, "registrou uma doação de livro", COR_DINHEIRO);
            case "EDICAO_DOACAO_LIVRO":
                return new Frase(quem, "editou uma doação de livro", COR_CADASTRO);
            case "DOACAO_SANGUE":
                return new Frase(quem, "registrou uma doação de sangue", COR_DINHEIRO);
            case "EDICAO_DOACAO_SANGUE":
                return new Frase(quem, "editou uma doação de sangue", COR_CADASTRO);
            default:
                break;
        }
        if (acao.startsWith("CADASTRO_")) {
            return new Frase(quem, "cadastrou " + objeto(acao.substring(9)), COR_CADASTRO);
        }
        if (acao.startsWith("EDICAO_")) {
            return new Frase(quem, "editou " + objeto(acao.substring(7)), COR_CADASTRO);
        }
        if (acao.startsWith("EXCLUSAO_")) {
            return new Frase(quem, "excluiu " + objeto(acao.substring(9)), COR_EXCLUSAO);
        }
        return new Frase(quem, acao.toLowerCase().replace('_', ' '), COR_TEXTO);
    }

    private static String objeto(String codigo) {
        switch (codigo) {
            case "CLIENTE": return "o cliente";
            case "CARTAO": return "o cartão";
            case "MAQUINA": return "a máquina";
            case "LIVRO": return "o livro";
            case "PREMIO": return "o prêmio";
            case "FUNCIONARIO": return "o funcionário";
            case "CARGO": return "o cargo";
            case "FORMA_PAGAMENTO": return "a forma de pagamento";
            case "FICHA_LEITURA": return "uma ficha de leitura";
            default: return codigo.toLowerCase().replace('_', ' ');
        }
    }
}
