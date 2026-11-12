package info.chrzanowski.customsplashscreen

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.io.IOException
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class SplashScreenConfigurable : SearchableConfigurable {

    private var enabled: JBCheckBox? = null
    private var imagePath: TextFieldWithBrowseButton? = null
    private var disposable: Disposable? = null

    override fun getId() = "org.jetbrains.plugins.template.splash"

    override fun getDisplayName() = SplashScreenBundle["splash.settings.title"]

    override fun createComponent(): JComponent {
        val uiDisposable = Disposer.newDisposable().also { disposable = it }
        val preview = SplashScreenPreview()
        Disposer.register(uiDisposable, preview)
        val enabledField = JBCheckBox(SplashScreenBundle["splash.settings.enabled"]).also { enabled = it }
        val pathField = TextFieldWithBrowseButton(null, uiDisposable).also { imagePath = it }
        pathField.addBrowseFolderListener(
            null,
            FileChooserDescriptorFactory.singleFile()
                .withTitle(SplashScreenBundle["splash.settings.choose"])
                .withExtensionFilter(SplashScreenBundle["splash.settings.images"], "png", "jpg", "jpeg", "gif", "bmp"),
        )
        val previewButton = JButton(SplashScreenBundle["splash.settings.preview"]).apply {
            addActionListener {
                preview.dispose()
                val selectedPath = pathField.text
                try {
                    val image = runWithModalProgressBlocking(
                        ModalTaskOwner.component(this),
                        SplashScreenBundle["splash.settings.loading"],
                    ) {
                        withContext(Dispatchers.IO) { SplashScreenImages.loadPreview(selectedPath) }
                    }
                    preview.show(image)
                } catch (error: IOException) {
                    Messages.showErrorDialog(this, error.localizedMessage, displayName)
                }
            }
        }
        enabledField.addItemListener {
            pathField.isEnabled = enabledField.isSelected
            previewButton.isEnabled = enabledField.isSelected
        }
        val form = FormBuilder.createFormBuilder()
            .addComponent(enabledField)
            .addLabeledComponent(SplashScreenBundle["splash.settings.path"], pathField)
            .addComponent(previewButton)
            .addComponent(JBLabel(SplashScreenBundle["splash.settings.help"]).apply { setCopyable(true) })
            .addComponent(JBLabel(SplashScreenBundle["splash.settings.experimental"]).apply { setCopyable(true) })
            .panel
        reset()
        previewButton.isEnabled = enabledField.isSelected
        return JPanel(BorderLayout()).apply { add(form, BorderLayout.NORTH) }
    }

    override fun isModified(): Boolean {
        val enabledValue = enabled?.isSelected ?: return false
        val path = imagePath?.text ?: return false
        val state = service<SplashScreenSettings>().state
        return enabledValue != state.enabled || path != state.imagePath
    }

    override fun apply() {
        val enabledValue = enabled?.isSelected ?: return
        val pathField = imagePath ?: return
        val path = pathField.text
        try {
            runWithModalProgressBlocking(
                ModalTaskOwner.component(pathField),
                SplashScreenBundle["splash.settings.applying"],
                TaskCancellation.nonCancellable(),
            ) {
                withContext(Dispatchers.IO) { service<SplashScreenService>().configure(enabledValue, path) }
            }
        } catch (error: IOException) {
            throw ConfigurationException(error.localizedMessage, SplashScreenBundle["splash.settings.title"])
        }
    }

    override fun reset() {
        val state = service<SplashScreenSettings>().state
        enabled?.isSelected = state.enabled
        imagePath?.text = state.imagePath
        imagePath?.isEnabled = state.enabled
    }

    override fun disposeUIResources() {
        disposable?.let(Disposer::dispose)
        disposable = null
        enabled = null
        imagePath = null
    }
}