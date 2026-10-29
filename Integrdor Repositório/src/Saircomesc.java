import java.awt.Dialog;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.KeyEvent;

/**
 * ESC encerra o sistema em qualquer tela (login, sistema principal, autoatendimento).
 *
 * Usa um dispatcher global do Swing: assim a tecla funciona mesmo quando o foco
 * está num campo de texto ou num botão, o que um KeyListener local não consegue.
 * Exceção: se houver uma caixa de diálogo modal aberta, o ESC só a fecha.
 */
final class SairComEsc implements KeyEventDispatcher {

    private static boolean instalado = false;

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

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getID() != KeyEvent.KEY_PRESSED || e.getKeyCode() != KeyEvent.VK_ESCAPE) {
            return false;
        }
        if (dialogoModalAberto()) {
            return false; // deixa o diálogo tratar o ESC (fechar)
        }
        System.exit(0);
        return true;
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