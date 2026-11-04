import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.sql.SQLException;

import javax.swing.JButton;
import javax.swing.JOptionPane;

/**
 * Ajudas comuns para os botões "Excluir" das telas de cadastro.
 * Cada tela faz o DELETE do seu próprio registro; aqui ficam o botão, a confirmação e a mensagem de erro.
 */
final class Exclusao {

    private Exclusao() {
    }

    /** Botão vermelho "Excluir", no mesmo estilo dos botões do topo das telas. */
    static JButton criarBotao() {
        JButton b = new JButton("Excluir");
        b.setFont(new Font("Segoe UI", Font.BOLD, 13));
        b.setForeground(Color.WHITE);
        b.setBackground(new Color(190, 35, 45));
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setPreferredSize(new Dimension(110, 38));
        b.setToolTipText("Excluir selecionado");
        b.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** Pede confirmação antes de apagar. Retorna true só se o usuário confirmar. */
    static boolean confirmar(Component pai, String item) {
        int resposta = JOptionPane.showConfirmDialog(pai,
                "Excluir " + item + "?\nEsta ação não pode ser desfeita.",
                "Confirmar exclusão",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        return resposta == JOptionPane.YES_OPTION;
    }

    /** Mensagem clara quando o banco recusa a exclusão (há outros registros ligados a este item). */
    static void erro(Component pai, Exception ex) {
        String msg;
        if (ex instanceof SQLException && "23000".equals(((SQLException) ex).getSQLState())) {
            msg = "Não é possível excluir: existem outros registros ligados a este item\n"
                    + "(por exemplo cartões, recargas, partidas ou resgates).\n\n"
                    + "Se ele não for mais usado, desative-o em vez de excluir";
        } else {
            msg = "Não foi possível excluir:\n" + ex.getMessage();
        }
        JOptionPane.showMessageDialog(pai, msg, "Exclusão não permitida", JOptionPane.ERROR_MESSAGE);
    }
}