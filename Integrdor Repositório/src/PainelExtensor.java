import java.awt.Color;
import java.awt.Font;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URL;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class PainelExtensor extends JPanel {

    private final int larguraFechado;
    private final int larguraAberto;
    private final JLabel labelTexto;
    private final JLabel labelIcone;

    private Timer timerAnimacao;
    private int larguraAtual;
    private final int VELOCIDADE_ANIMACAO = 10;
    private final int DELAY_MS = 10;

    // Interface para lidar com a ação de clique
    private Runnable acaoClique;

    public PainelExtensor(String texto, String nomeImagem, int posicaoY, int larguraFechado, int larguraAberto) {
        this.larguraFechado = larguraFechado;
        this.larguraAberto = larguraAberto;
        this.larguraAtual = larguraFechado;

        setBackground(new Color(24, 75, 150));
        setLayout(null);
        setBounds(0, posicaoY, larguraFechado, 70);

        ImageIcon icone = carregarIcone(nomeImagem);
        labelIcone = new JLabel(icone);
        labelIcone.setHorizontalAlignment(SwingConstants.CENTER);
        labelIcone.setVerticalAlignment(SwingConstants.CENTER);
        labelIcone.setBounds(0, 0, larguraFechado, 70);
        add(labelIcone);

        labelTexto = new JLabel(texto);
        labelTexto.setForeground(Color.WHITE);
        labelTexto.setFont(new Font("Segoe UI", Font.BOLD, 15));
        labelTexto.setVerticalAlignment(SwingConstants.CENTER);
        labelTexto.setVisible(false);

        int xTexto = larguraFechado + 5;
        int larguraTexto = larguraAberto - xTexto;
        labelTexto.setBounds(xTexto, 0, larguraTexto, 70);
        add(labelTexto);

        MouseAdapter ma = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                iniciarAnimacao(true);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                java.awt.Point p = e.getPoint();
                if (e.getSource() != PainelExtensor.this) {
                    p = SwingUtilities.convertPoint((java.awt.Component) e.getSource(), e.getPoint(),
                            PainelExtensor.this);
                }
                if (!contains(p)) {
                    iniciarAnimacao(false);
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (acaoClique != null) {
                    acaoClique.run();
                }
            }
        };

        addMouseListener(ma);
        labelIcone.addMouseListener(ma);
        labelTexto.addMouseListener(ma);
    }

    // Método para definir o que acontece ao clicar no botão
    public void setAcaoClique(Runnable acao) {
        this.acaoClique = acao;
    }

    private void iniciarAnimacao(boolean expandir) {
        if (timerAnimacao != null && timerAnimacao.isRunning()) {
            timerAnimacao.stop();
        }

        if (expandir) {
            labelTexto.setVisible(true);
        }

        timerAnimacao = new Timer(DELAY_MS, e -> {
            if (expandir) {
                larguraAtual += VELOCIDADE_ANIMACAO;
                if (larguraAtual >= larguraAberto) {
                    larguraAtual = larguraAberto;
                    timerAnimacao.stop();
                }
            } else {
                larguraAtual -= VELOCIDADE_ANIMACAO;
                if (larguraAtual <= larguraFechado) {
                    larguraAtual = larguraFechado;
                    labelTexto.setVisible(false);
                    timerAnimacao.stop();
                }
            }

            setSize(larguraAtual, getHeight());
            if (getParent() != null) {
                getParent().repaint();
            }
        });

        timerAnimacao.start() ; 
    }

    private ImageIcon carregarIcone(String nomeImagem) {
        // Carrega o recurso através do ClassLoader no classpath
        URL imgURL = getClass().getResource("/images/" + nomeImagem);

        if (imgURL != null) {
            Image imagem = new ImageIcon(imgURL).getImage();
            Image imagemRedimensionada = imagem.getScaledInstance(34, 34, Image.SCALE_SMOOTH);
            return new ImageIcon(imagemRedimensionada);
        }

        System.err.println("Imagem não encontrada: /images/" + nomeImagem);
        return new ImageIcon();
    }
}