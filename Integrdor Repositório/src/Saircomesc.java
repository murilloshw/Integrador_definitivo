import java.awt.Dialog;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * ESC pede para encerrar o sistema (login e sistema principal).
 *
 * Usa um dispatcher global do Swing: assim a tecla funciona mesmo quando o foco
 * está num campo de texto ou num botão, o que um KeyListener local não consegue.
 *
 * Regras:
 * - Antes de fechar, pergunta "Deseja sair?" (evita fechar sem querer).
 * - Se houver uma caixa de diálogo modal aberta, o ESC só a fecha.
 * - Durante o AUTOATENDIMENTO o ESC NÃO fecha o sistema: ele abre a janela que pede
 *   usuário e senha de administrador (a mesma do botão de saída).
 */
final class SairComEsc implements KeyEventDispatcher {

    private static boolean instalado = false;

    /**
     * Quando não é null, o ESC executa esta ação em vez de perguntar "Deseja sair?".
     * O App coloca aqui a janela de administrador enquanto o autoatendimento está aberto.
     */
    private static volatile Runnable acaoNoLugarDeSair = null;

    /** Evita abrir duas perguntas "Deseja sair?" ao mesmo tempo. */
    private static boolean perguntando = false;

    private SairComEsc() {
    }

    /** Pode ser chamado mais de uma vez (login e sistema principal): instala só uma vez. */
    static synchronized void instalar() {
        if (instalado) {
            return;
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new SairComEsc());
        instalado = true;
    }

    /**
     * Troca o que o ESC faz. Passe null para voltar ao normal ("Deseja sair?").
     * Ex.: SairComEsc.desviarPara(telaAutoatendimento::abrirSaidaFuncionario);
     */
    static void desviarPara(Runnable acao) {
        acaoNoLugarDeSair = acao;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getID() != KeyEvent.KEY_PRESSED || e.getKeyCode() != KeyEvent.VK_ESCAPE) {
            return false;
        }
        if (dialogoModalAberto()) {
            return false; // deixa o diálogo tratar o ESC (fechar)
        }
        Runnable acao = acaoNoLugarDeSair;
        if (acao != null) {
            SwingUtilities.invokeLater(acao); // autoatendimento: pede login de administrador
            return true;
        }
        if (!perguntando) {
            perguntando = true;
            // invokeLater: abre a pergunta fora do despacho da tecla
            SwingUtilities.invokeLater(SairComEsc::confirmarSaida);
        }
        return true;
    }

    private static void confirmarSaida() {
        try {
            Window dona = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
            int resposta = JOptionPane.showConfirmDialog(dona,
                    "Deseja realmente sair do sistema?",
                    "Sair",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (resposta == JOptionPane.YES_OPTION) {
                System.exit(0);
            }
        } finally {
            perguntando = false;
        }
    }

    private static boolean dialogoModalAberto() {
        for (Window janela : Window.getWindows()) {
            if (janela instanceof Dialog && ((Dialog) janela).isModal() && janela.isShowing()) {
                return true;
            }
        }
        return false;
    }
}
