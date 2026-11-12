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
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.io.IOException
import javax.swing.ButtonGroup
import javax.swing.DefaultListCellRenderer
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

class SplashScreenConfigurable : SearchableConfigurable {

    private var enabled: JBCheckBox? = null
    private var imagePath: TextFieldWithBrowseButton? = null
    private var bundledImages: JBList<String>? = null
    private var useBundled: JRadioButton? = null
    private var useCustom: JRadioButton? = null
    private var previewButton: JButton? = null
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
        val thumbnails = runWithModalProgressBlocking(
            ModalTaskOwner.component(pathField),
            SplashScreenBundle["splash.settings.loading"],
        ) {
            withContext(Dispatchers.IO) {
                val savedImage = service<SplashScreenSettings>().state.bundledImage
                val resources = (BundledSplashImages.discover() + listOfNotNull(savedImage.takeIf { it.isNotEmpty() })).distinct()
                resources.associateWith { resource ->
                    try {
                        ImageIcon(SplashScreenImages.render(SplashScreenImages.load("", resource), 0.25f))
                    } catch (_: IOException) {
                        null
                    }
                }
            }
        }
        val bundledOption = JRadioButton(SplashScreenBundle["splash.settings.bundled"]).also { useBundled = it }
        val customOption = JRadioButton(SplashScreenBundle["splash.settings.custom"]).also { useCustom = it }
        ButtonGroup().apply {
            add(bundledOption)
            add(customOption)
        }
        val imageList = JBList<String>(*thumbnails.keys.toTypedArray()).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            layoutOrientation = JList.HORIZONTAL_WRAP
            visibleRowCount = -1
            fixedCellWidth = JBUI.scale(200)
            fixedCellHeight = JBUI.scale(150)
            cellRenderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean,
                ): Component {
                    super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    val resource = value as String
                    text = resource.removePrefix("splashes/").substringBeforeLast('.')
                    icon = thumbnails[resource]
                    horizontalAlignment = SwingConstants.CENTER
                    horizontalTextPosition = SwingConstants.CENTER
                    verticalTextPosition = SwingConstants.BOTTOM
                    return this
                }
            }
        }.also { bundledImages = it }
        val bundledPicker = if (thumbnails.isEmpty()) {
            JBLabel(SplashScreenBundle["splash.settings.bundled.empty"])
        } else {
            JBScrollPane(imageList).apply { preferredSize = JBUI.size(620, 220) }
        }
        val previewButton = JButton(SplashScreenBundle["splash.settings.preview"]).apply {
            addActionListener {
                preview.dispose()
                val selectedPath = pathField.text
                val selectedBundledImage = selectedBundledImage()
                try {
                    val image = runWithModalProgressBlocking(
                        ModalTaskOwner.component(this),
                        SplashScreenBundle["splash.settings.loading"],
                    ) {
                        withContext(Dispatchers.IO) {
                            SplashScreenImages.loadPreview(selectedPath, selectedBundledImage)
                        }
                    }
                    preview.show(image)
                } catch (error: IOException) {
                    Messages.showErrorDialog(this, error.localizedMessage, displayName)
                }
            }
        }.also { this.previewButton = it }
        enabledField.addItemListener { updateEnabledState() }
        bundledOption.addItemListener { updateEnabledState() }
        customOption.addItemListener { updateEnabledState() }
        val form = FormBuilder.createFormBuilder()
            .addComponent(enabledField)
            .addComponent(bundledOption)
            .addComponent(bundledPicker)
            .addComponent(customOption)
            .addLabeledComponent(SplashScreenBundle["splash.settings.path"], pathField)
            .addComponent(previewButton)
            .addComponent(JBLabel(SplashScreenBundle["splash.settings.help"]).apply { setCopyable(true) })
            .addComponent(JBLabel(SplashScreenBundle["splash.settings.experimental"]).apply { setCopyable(true) })
            .panel
        reset()
        return JPanel(BorderLayout()).apply { add(form, BorderLayout.NORTH) }
    }

    private fun selectedBundledImage(): String =
        if (useBundled?.isSelected == true) bundledImages?.selectedValue.orEmpty() else ""

    private fun updateEnabledState() {
        val isEnabled = enabled?.isSelected == true
        useBundled?.isEnabled = isEnabled && (bundledImages?.model?.size ?: 0) > 0
        useCustom?.isEnabled = isEnabled
        bundledImages?.isEnabled = isEnabled && useBundled?.isSelected == true
        imagePath?.isEnabled = isEnabled && useCustom?.isSelected == true
        previewButton?.isEnabled = isEnabled
    }

    override fun isModified(): Boolean {
        val enabledValue = enabled?.isSelected ?: return false
        val path = imagePath?.text ?: return false
        val state = service<SplashScreenSettings>().state
        return enabledValue != state.enabled || path != state.imagePath ||
            selectedBundledImage() != state.bundledImage
    }

    override fun apply() {
        val enabledValue = enabled?.isSelected ?: return
        val pathField = imagePath ?: return
        val path = pathField.text
        val bundledImage = selectedBundledImage()
        try {
            runWithModalProgressBlocking(
                ModalTaskOwner.component(pathField),
                SplashScreenBundle["splash.settings.applying"],
                TaskCancellation.nonCancellable(),
            ) {
                withContext(Dispatchers.IO) { service<SplashScreenService>().configure(enabledValue, path, bundledImage) }
            }
        } catch (error: IOException) {
            throw ConfigurationException(error.localizedMessage, SplashScreenBundle["splash.settings.title"])
        }
    }

    override fun reset() {
        val state = service<SplashScreenSettings>().state
        enabled?.isSelected = state.enabled
        imagePath?.text = state.imagePath
        bundledImages?.let { images ->
            if (state.bundledImage.isNotEmpty()) {
                images.setSelectedValue(state.bundledImage, true)
            } else if (images.model.size > 0) {
                images.selectedIndex = 0
            }
        }
        useBundled?.isSelected = state.bundledImage.isNotEmpty()
        useCustom?.isSelected = state.bundledImage.isEmpty()
        updateEnabledState()
    }

    override fun disposeUIResources() {
        disposable?.let(Disposer::dispose)
        disposable = null
        enabled = null
        imagePath = null
        bundledImages = null
        useBundled = null
        useCustom = null
        previewButton = null
    }
}