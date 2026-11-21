package com.neoforge.generator.detection

import com.intellij.psi.search.FileTypeIndex
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ide.highlighter.JavaFileType

data class DetectedRegistry(
    val ownerClass: PsiClass,
    val field: PsiField,
    val type: RegistryType
)

enum class RegistryType {
    BLOCKS,
    ITEMS
}

data class NeoForgeRegistryInfo(
    val modClass: PsiClass,
    val modId: String,
    val blockRegistry: DetectedRegistry,
    val itemRegistry: DetectedRegistry
)

class RegistryDetector {

    fun detect(project: Project): NeoForgeRegistryInfo? {

        val scope = GlobalSearchScope.projectScope(project)

        val javaFiles = FileTypeIndex.getFiles(
            JavaFileType.INSTANCE,
            scope
        )

        val classes = javaFiles
            .mapNotNull { PsiManager.getInstance(project).findFile(it) }
            .flatMap { file ->
                PsiTreeUtil.findChildrenOfType(
                    file,
                    PsiClass::class.java
                )
            }

        val modClass = classes.firstOrNull { psiClass ->

            psiClass.modifierList?.annotations?.any {
                it.qualifiedName == "net.neoforged.fml.common.Mod"
            } == true
        } ?: return null

        val modAnnotation = modClass.modifierList
            ?.annotations
            ?.firstOrNull {
                it.qualifiedName == "net.neoforged.fml.common.Mod"
            } ?: return null

        val modId = resolveModId(
            modAnnotation,
            classes
        ) ?: return null

        val registries = classes.flatMap { psiClass ->

            psiClass.fields.mapNotNull { field ->

                val typeName = field.type.canonicalText

                val registryType = when {
                    typeName.contains("DeferredRegister.Blocks") ->
                        RegistryType.BLOCKS

                    typeName.contains("DeferredRegister.Items") ->
                        RegistryType.ITEMS

                    else -> null
                } ?: return@mapNotNull null

                DetectedRegistry(
                    ownerClass = psiClass,
                    field = field,
                    type = registryType
                )
            }
        }

        val allMethods = classes.flatMap { it.methods.toList() }

        val blockRegistry = selectRegistry(
            registries.filter { it.type == RegistryType.BLOCKS },
            allMethods,
            listOf("BLOCKS", "MODBLOCKS")
        ) ?: return null

        val itemRegistry = selectRegistry(
            registries.filter { it.type == RegistryType.ITEMS },
            allMethods,
            listOf("ITEMS", "MODITEMS")
        ) ?: return null

        return NeoForgeRegistryInfo(
            modClass = modClass,
            modId = modId,
            blockRegistry = blockRegistry,
            itemRegistry = itemRegistry
        )
    }

    private fun resolveModId(
        annotation: PsiAnnotation,
        classes: List<PsiClass>
    ): String? {

        val value = annotation.findAttributeValue("value")
            ?: return null

        if (value is PsiLiteralExpression) {
            return value.value as? String
        }

        if (value is PsiReferenceExpression) {

            val resolved = value.resolve()

            if (resolved is PsiField) {
                return resolved.computeConstantValue() as? String
            }
        }

        return null
    }

    private fun selectRegistry(
        candidates: List<DetectedRegistry>,
        methods: List<PsiMethod>,
        preferredNames: List<String>
    ): DetectedRegistry? {

        return candidates.maxWithOrNull(
            compareBy<DetectedRegistry> { registry ->
                methods.sumOf { method ->

                    PsiTreeUtil.findChildrenOfType(
                        method,
                        PsiMethodCallExpression::class.java
                    ).count { call ->

                        val qualifier = call.methodExpression
                            .qualifierExpression as? PsiReferenceExpression

                        val field = qualifier?.resolve() as? PsiField

                        field?.isEquivalentTo(registry.field) == true &&
                                call.methodExpression.referenceName in setOf(
                            "register",
                            "registerSimpleItem",
                            "registerSimpleBlockItem",
                            "registerBlock"
                        )
                    }
                }
            }.thenBy { registry ->
                -preferredNames.indexOf(registry.field.name)
                    .let { if (it < 0) Int.MAX_VALUE else it }
            }
        )
    }
}