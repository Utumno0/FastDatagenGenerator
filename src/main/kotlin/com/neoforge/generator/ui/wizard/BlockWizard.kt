package com.neoforge.generator.ui.wizard

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import javax.swing.*
import com.intellij.openapi.ui.Messages
import javax.swing.filechooser.FileNameExtensionFilter
import java.io.File
import javax.imageio.ImageIO
import java.awt.Image
import java.awt.event.ActionEvent

class BlockWizard(private val project: Project) : DialogWrapper(project) {
    private val NextString = "Next >"
    private val PreviousString = "< Previous"

    private val data = BlockWizardData()
    private val cardPanel = JPanel(java.awt.CardLayout())

    private val nameField = JBTextField()
    private val registryField = JBTextField()

    private val hardnessField = JBTextField("3.0")
    private val resistanceField = JBTextField("3.0")
    private val requiresToolBox = JCheckBox("Requires correct tool")
    private val toolSelector = JComboBox(
        arrayOf("Pickaxe", "Axe", "Shovel", "Hoe")
    )

    private val texturePreview = JLabel("No texture selected")
    private val textureButton = JButton("Import PNG Texture")

    private var currentStep = 0
    private val steps = arrayOf(
        "GENERAL",
        "PROPERTIES",
        "TEXTURE",
        "GENERATION"
    )

    init {
        title = "NeoForge Generator - New Block"
        setOKButtonText(NextString)
        setCancelButtonText("Cancel ✕")

        cardPanel.add(createGeneralPanel(), steps[0])
        cardPanel.add(createPropertiesPanel(), steps[1])
        cardPanel.add(createTexturePanel(), steps[2])
        cardPanel.add(createGenerationPanel(), steps[3])

        init()
        pack()
    }

    private fun compactField(field: JComponent): JPanel {
        val container = JPanel(
            java.awt.BorderLayout()
        )
        container.maximumSize = java.awt.Dimension(
            Int.MAX_VALUE,
            32
        )
        container.add(field, java.awt.BorderLayout.CENTER)
        return container
    }

    private fun configurePanel(panel: JPanel, height: Int) {
        panel.border = javax.swing.BorderFactory.createEmptyBorder(
            10, 10, 10, 10
        )

        panel.preferredSize = java.awt.Dimension(380, height)
    }

    private fun createGeneralPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        panel.add(JLabel("Block Name"))
        panel.add(compactField(nameField))
        panel.add(Box.createVerticalStrut(10))

        panel.add(JLabel("Registry Name"))
        panel.add(compactField(registryField))

        configurePanel(panel, 120)
        return panel
    }

    private fun createPropertiesPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        panel.add(JLabel("Hardness"))
        panel.add(compactField(hardnessField))
        panel.add(Box.createVerticalStrut(10))

        panel.add(JLabel("Resistance"))
        panel.add(compactField(resistanceField))
        panel.add(Box.createVerticalStrut(10))

        panel.add(requiresToolBox)
        panel.add(JLabel("Required Tool"))
        panel.add(compactField(toolSelector))

        configurePanel(panel, 200)
        return panel
    }


    private fun createTexturePanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.add(JLabel("Block Texture"))
        panel.add(Box.createVerticalStrut(10))

        textureButton.addActionListener {
            val chooser = JFileChooser()
            chooser.dialogTitle = "Select Block Texture"
            chooser.fileFilter = FileNameExtensionFilter(
                "PNG Images",
                "png"
            )

            if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                val selectedFile = chooser.selectedFile

                try {
                    val image = ImageIO.read(selectedFile)
                        ?: throw IllegalArgumentException("Invalid PNG image")

                    data.texturePath = selectedFile.absolutePath

                    val preview = image.getScaledInstance(
                        128,
                        128,
                        Image.SCALE_SMOOTH
                    )

                    texturePreview.icon = ImageIcon(preview)
                    texturePreview.text = ""

                } catch (e: Exception) {
                    Messages.showErrorDialog(
                        project,
                        "Unable to load this texture: ${e.message}",
                        "Texture Import Error"
                    )
                }
            }
        }

        panel.add(textureButton)
        panel.add(Box.createVerticalStrut(15))
        panel.add(texturePreview)

        configurePanel(panel, 220)
        return panel
    }

    private fun createGenerationPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        panel.add(
            JLabel("Your block is ready to be generated.")
        )

        configurePanel(panel, 80)
        return panel
    }

    override fun createCenterPanel(): JComponent {
        cardPanel.preferredSize = null
        return cardPanel
    }

    override fun doOKAction() {
        if (currentStep == 0) {
            if (nameField.text.isBlank() ||
                registryField.text.isBlank()) {
                return
            }
            data.blockName = nameField.text.trim()
            data.registryName = registryField.text.trim()
        }

        if (currentStep == 1) {
            data.hardness =
                hardnessField.text.toFloatOrNull() ?: 3.0f
            data.resistance =
                resistanceField.text.toFloatOrNull() ?: 3.0f
            data.requiresTool = requiresToolBox.isSelected
            data.toolType = toolSelector.selectedItem.toString()
        }

        if (currentStep < steps.lastIndex) {
            currentStep++

            (cardPanel.layout as java.awt.CardLayout)
                .show(cardPanel, steps[currentStep])

            setOKButtonText(
                if (currentStep == steps.lastIndex) "Finish"
                else NextString
            )
        } else {
            super.doOKAction()
        }
    }

    fun getBlockData(): BlockWizardData {
        return data
    }

    override fun createActions(): Array<Action> {
        val defaultActions = super.createActions()
        val previousAction = object : AbstractAction(PreviousString) {
            override fun actionPerformed(e: ActionEvent) {
                goToPreviousStep()
            }
        }

        return arrayOf(
            previousAction,
            defaultActions[0],
            *defaultActions.drop(1).toTypedArray()
        )
    }

    private fun goToPreviousStep() {
        if (currentStep > 0) {
            currentStep--
            (cardPanel.layout as java.awt.CardLayout)
                .show(cardPanel, steps[currentStep])
            setOKButtonText(NextString)
        }
    }
}