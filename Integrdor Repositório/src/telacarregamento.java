
import java.awt.*;
import java.awt.geom.*;
import javax.swing.*;

public class telacarregamento extends JFrame {

    private int animacao = 0;
    private final Color azul = new Color(35, 125, 255);
    private final Color fundo = new Color(2, 25, 62);
    private final Color branco = new Color(225, 240, 255);
    private final Timer temporizador;

    public telacarregamento() {
        setTitle("Arcade - Carregando");
        setUndecorated(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setExtendedState(MAXIMIZED_BOTH);

        JPanel painel = new JPanel() {
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                int cx = getWidth() / 2, cy = getHeight() / 2;
                int x = cx - 38, y = cy - 100, w = 76, h = 88;

                g2.setColor(azul);
                g2.setStroke(new BasicStroke(4.5f));
                g2.draw(new Ellipse2D.Double(x, y, w, 25));
                g2.drawLine(x, y + 12, x, y + h - 12);
                g2.drawLine(x + w, y + 12, x + w, y + h - 12);

                for (int i : new int[] { 23, 43, h - 25 })
                    g2.draw(new Arc2D.Double(x, y + i, w, 25,
                            180, 180, Arc2D.OPEN));

                int py = y + h + 48;
                for (int i = 0; i < 8; i++) {
                    double a = Math.toRadians(-90 + i * 45);
                    int px = cx + (int) (Math.cos(a) * 38) - 5;
                    int pyy = py + (int) (Math.sin(a) * 38) - 5;
                    int c = Math.max(45, 255 - ((i - animacao + 8) % 8) * 28);
                    g2.setColor(new Color(c / 2, c, 255));
                    g2.fillOval(px, pyy, 11, 11);
                }

                String texto = "Carregando banco de dados...";
                g2.setFont(new Font("Segoe UI", Font.PLAIN, 30));
                g2.setColor(branco);
                g2.drawString(texto,
                        cx - g2.getFontMetrics().stringWidth(texto) / 2,
                        py + 95);

                g2.dispose();
            }
        };

        painel.setBackground(fundo);
        setContentPane(painel);

        temporizador = new Timer(90, e -> {
            animacao = (animacao + 1) % 8;
            painel.repaint();
        });
        temporizador.start();
    }

    public void fechar() {
        temporizador.stop();
        dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(TelaLogin::new);
    }
}