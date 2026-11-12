package info.chrzanowski.customsplashscreen

import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.util.ui.StartupUiUtil.drawImage
import com.intellij.util.ui.UIUtil
import java.awt.Dialog
import java.awt.Frame
import java.awt.Graphics
import java.awt.event.*
import java.awt.image.BufferedImage

internal class SplashScreenPreview : Disposable {
    private var splash: Dialog? = null

    fun show(image: BufferedImage) {
        dispose()
        object : Dialog(null as Frame?, "splash") {
            override fun paint(g: Graphics) = drawImage(g, image, 0, 0, null)
        }.apply {
            isUndecorated = true
            background = JBColor(UIUtil.TRANSPARENT_COLOR, UIUtil.TRANSPARENT_COLOR)
            isAlwaysOnTop = true
            isResizable = false
            focusableWindowState = true
            setSize(image.width, image.height)
            setLocationRelativeTo(null)
            splash = this
            modalExclusionType = Dialog.ModalExclusionType.APPLICATION_EXCLUDE
            addFocusListener(object : FocusAdapter() {
                override fun focusLost(e: FocusEvent?) = close(this@apply)
            })
            addKeyListener(object : KeyAdapter() {
                override fun keyTyped(e: KeyEvent?) = close(this@apply)
                override fun keyPressed(e: KeyEvent?) = close(this@apply)
                override fun keyReleased(e: KeyEvent?) = close(this@apply)
            })
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent?) = close(this@apply)
                override fun mousePressed(e: MouseEvent?) = close(this@apply)
                override fun mouseReleased(e: MouseEvent?) = close(this@apply)
            })
            isVisible = true
            toFront()
            requestFocus()
        }
    }

    private fun close(window: Dialog) {
        if (splash === window) splash = null
        window.isAlwaysOnTop = false
        window.dispose()
    }

    override fun dispose() {
        splash?.let(::close)
    }
}
