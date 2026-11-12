package info.chrzanowski.customsplashscreen;

import com.intellij.openapi.Disposable;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

@SuppressWarnings("KotlinInternalInJava")
final class SplashScreenPreview implements Disposable {

    private Dialog splash;

    void show(BufferedImage image) {
        dispose();
        Dialog window = new com.intellij.platform.ide.bootstrap.Splash(image, true);
        splash = window;
        window.setModalExclusionType(Dialog.ModalExclusionType.APPLICATION_EXCLUDE);
        window.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                close(window);
            }
        });
        window.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent e) {
                close(window);
            }

            @Override
            public void keyPressed(KeyEvent e) {
                close(window);
            }

            @Override
            public void keyReleased(KeyEvent e) {
                close(window);
            }
        });
        window.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                close(window);
            }

            @Override
            public void mousePressed(MouseEvent e) {
                close(window);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                close(window);
            }
        });
        window.setVisible(true);
        window.toFront();
        window.requestFocus();
    }

    private void close(Dialog window) {
        if (splash == window) splash = null;
        window.setAlwaysOnTop(false);
        window.dispose();
    }

    @Override
    public void dispose() {
        if (splash != null) close(splash);
    }
}
