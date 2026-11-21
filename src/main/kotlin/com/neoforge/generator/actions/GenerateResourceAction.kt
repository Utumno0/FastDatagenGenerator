
package com.neoforge.generator.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.neoforge.generator.detection.BlockItemResolver
import com.neoforge.generator.detection.NeoForgeDetector
import com.neoforge.generator.detection.RegistrationAnalyzer
import com.neoforge.generator.ui.wizard.BlockWizard
import com.neoforge.generator.detection.RegistryDetector

class GenerateResourceAction : AnAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val info = NeoForgeDetector().detect(project)

        if (info == null) {
            Messages.showWarningDialog(
                project,
                "No NeoForge project detected.",
                "NeoForge Generator"
            )
            return
        }

        val wizard = BlockWizard(project)
        if (wizard.showAndGet()) {
            val data = wizard.getBlockData()

            Messages.showInfoMessage(
                project,
                "Block: ${data.blockName}\nRegistry: ${data.registryName}",
                "Block Information"
            )
        }

        val registries = RegistryDetector().detect(project)

        if (registries == null) {
            Messages.showWarningDialog(
                project,
                "Unable to detect NeoForge registries.",
                "Registry Detection"
            )
            return
        }

        val analysis = RegistrationAnalyzer().analyze(
            project,
            registries
        )

        val blockItemResolver = BlockItemResolver()
        val blockItemResolution = blockItemResolver.resolve(
            project,
            registries
        )


        val report = buildString {
            appendLine("========== REGISTRATION ANALYSIS ==========")
            appendLine()
            analysis.methods.forEach { info ->
                appendLine("Method: ${info.method.name}")
                appendLine("Class: ${info.method.containingClass?.name}")
                appendLine("Blocks: ${info.registersBlocks}")
                appendLine("Items: ${info.registersItems}")
                appendLine("BlockItem: ${info.createsBlockItem}")
                appendLine("Generic: ${info.isGeneric}")
                appendLine("Calls: ${info.calls}")
                appendLine()
            }
            appendLine("========== BLOCK ITEM RESOLUTION ==========")
            appendLine()
            appendLine("Detected blocks: ${blockItemResolution.blockFields.size}")
            appendLine("Detected BlockItems: ${blockItemResolution.associations.size}")
            appendLine("Blocks without Item: ${blockItemResolution.blocksWithoutItem.size}")
            appendLine("Unresolved associations: ${blockItemResolution.unresolvedAssociations.size}")
            appendLine()
            appendLine("========== ASSOCIATIONS ==========")
            blockItemResolution.associations.forEach { association ->
                appendLine(
                    "Block: ${association.blockField?.name} -> " +
                            "Item: ${association.registrationName}"
                )
                appendLine("Indirect: ${association.isIndirect}")
                appendLine()
            }
            appendLine("========== MISSING BLOCK ITEMS ==========")
            blockItemResolution.blocksWithoutItem.forEach { field ->
                appendLine(
                    "${field.containingClass?.name}.${field.name}"
                )
            }
            appendLine()
            appendLine("========== UNRESOLVED ==========")
            blockItemResolution.unresolvedAssociations.forEach { association ->
                appendLine("Registration: ${association.registrationName}")
                appendLine("Method: ${association.registrationMethod.name}")
            }
        }

        Messages.showMessageDialog(
            project,
            report.ifBlank { "No registration methods found." },
            "Registration Analysis",
            null
        )
    }
}