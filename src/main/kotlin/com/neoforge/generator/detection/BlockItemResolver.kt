package com.neoforge.generator.detection

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil

data class BlockItemAssociation(
    val blockField: PsiField?,
    val registrationMethod: PsiMethod,
    val creationMethod: PsiMethod,
    val registrationName: String?,
    val isIndirect: Boolean
)

data class BlockItemResolution(
    val blockFields: List<PsiField>,
    val associations: List<BlockItemAssociation>,
    val blocksWithoutItem: List<PsiField>,
    val unresolvedAssociations: List<BlockItemAssociation>
)

private data class AssociationKey(
    val blockField: PsiField?,
    val registrationMethod: PsiMethod,
    val registrationName: String?
)

class BlockItemResolver {
    private fun <T : PsiElement> childrenOfType(
        element: PsiElement,
        type: Class<T>
    ): List<T> {
        return PsiTreeUtil.findChildrenOfType(element, type).toList()
    }

    fun resolve(
        project: Project,
        registryInfo: NeoForgeRegistryInfo
    ): BlockItemResolution {
        val scope = GlobalSearchScope.projectScope(project)
        val manager = PsiManager.getInstance(project)
        val files = FileTypeIndex.getFiles(
            JavaFileType.INSTANCE,
            scope
        )
        val classes = mutableListOf<PsiClass>()

        for (file in files) {
            val psiFile = manager.findFile(file) ?: continue
            classes.addAll(
                childrenOfType(psiFile, PsiClass::class.java)
            )
        }

        val allMethods = mutableListOf<PsiMethod>()

        for (psiClass in classes) {
            allMethods.addAll(psiClass.methods.toList())
        }

        val blockFields = mutableListOf<PsiField>()

        for (psiClass in classes) {
            for (field in psiClass.fields) {
                if (field.type.canonicalText.contains("DeferredBlock")) {
                    blockFields.add(field)
                }
            }
        }

        val associations = mutableListOf<BlockItemAssociation>()

        for (method in allMethods) {
            val calls = childrenOfType(
                method,
                PsiMethodCallExpression::class.java
            )
            for (call in calls) {
                val name =
                    call.methodExpression.referenceName ?: continue
                val qualifier =
                    call.methodExpression.qualifierExpression
                            as? PsiReferenceExpression

                val registryField =
                    qualifier?.resolve() as? PsiField
                if (registryField?.isEquivalentTo(
                        registryInfo.itemRegistry.field
                    ) != true
                ) {
                    continue
                }

                val arguments = call.argumentList.expressions

                val blockItemConstructor = childrenOfType(
                    call,
                    PsiNewExpression::class.java
                ).firstOrNull { expression ->
                    val resolved =
                        expression.classReference?.resolve() as? PsiClass
                    resolved?.qualifiedName ==
                            "net.minecraft.world.item.BlockItem"
                }
                if (name == "registerSimpleBlockItem") {
                    val blockField = arguments
                        .getOrNull(1)
                        ?.let { findReferencedBlockField(it) }

                    associations.add(
                        BlockItemAssociation(
                            blockField = blockField,
                            registrationMethod = method,
                            creationMethod = method,
                            registrationName = arguments
                                .getOrNull(0)
                                ?.text
                                ?.removeSurrounding("\""),
                            isIndirect = false
                        )
                    )
                    continue
                }

                if (name == "register" &&
                    blockItemConstructor != null
                ) {
                    val blockExpression =
                        blockItemConstructor.argumentList?.expressions
                            ?.firstOrNull()
                    val blockField =
                        blockExpression?.let {
                            findBlockField(
                                it,
                                method,
                                allMethods,
                                classes
                            )
                        }

                    associations.add(
                        BlockItemAssociation(
                            blockField = blockField,
                            registrationMethod = method,
                            creationMethod = method,
                            registrationName = resolveRegistrationName(
                                arguments.firstOrNull(),
                                method,
                                classes
                            ),
                            isIndirect = blockField == null ||
                                    blockField.containingClass != method.containingClass
                        )
                    )
                }
            }
        }

        val uniqueAssociations =
            associations.distinctBy<BlockItemAssociation, AssociationKey> {
                    association ->
                AssociationKey(
                    association.blockField,
                    association.registrationMethod,
                    association.registrationName
                )
            }

        val unresolved = uniqueAssociations.filter {
            it.blockField == null
        }
        val resolved = uniqueAssociations.filter {
            it.blockField != null
        }
        val associatedFields = resolved.mapNotNull {
            it.blockField
        }
        val missing = blockFields.filter { field ->
            associatedFields.none {
                it.isEquivalentTo(field)
            }
        }

        return BlockItemResolution(
            blockFields = blockFields,
            associations = resolved,
            blocksWithoutItem = missing,
            unresolvedAssociations = unresolved
        )
    }

    private fun findReferencedBlockField(
        expression: PsiExpression
    ): PsiField? {
        if (expression is PsiReferenceExpression) {
            val resolved = expression.resolve()
            if (resolved is PsiField &&
                resolved.type.canonicalText.contains("DeferredBlock")
            ) {
                return resolved
            }
        }

        if (expression is PsiMethodCallExpression) {
            val qualifier =
                expression.methodExpression.qualifierExpression
            if (qualifier is PsiReferenceExpression) {
                val resolved = qualifier.resolve()
                if (resolved is PsiField &&
                    resolved.type.canonicalText.contains("DeferredBlock")
                ) {
                    return resolved
                }
            }
        }
        return null
    }

    private fun findBlockField(
        expression: PsiExpression,
        method: PsiMethod,
        allMethods: List<PsiMethod>,
        classes: List<PsiClass>
    ): PsiField? {
        findReferencedBlockField(expression)?.let {
            return it
        }

        if (expression is PsiMethodCallExpression) {
            val qualifier =
                expression.methodExpression.qualifierExpression
                        as? PsiReferenceExpression

            val resolved = qualifier?.resolve()
            if (resolved is PsiLocalVariable) {

                val initializer = resolved.initializer
                if (initializer is PsiMethodCallExpression &&
                    initializer.methodExpression.referenceName == "register"
                ) {
                    return findCallingField(
                        method,
                        allMethods,
                        classes
                    )
                }
            }
        }
        return null
    }


    private fun findCallingField(
        targetMethod: PsiMethod,
        allMethods: List<PsiMethod>,
        classes: List<PsiClass>
    ): PsiField? {
        for (psiClass in classes) {
            for (field in psiClass.fields) {
                val initializer = field.initializer ?: continue
                val calls = mutableListOf<PsiMethodCallExpression>()

                if (initializer is PsiMethodCallExpression) {
                    calls.add(initializer)
                }

                calls.addAll(
                    PsiTreeUtil.findChildrenOfType(
                        initializer,
                        PsiMethodCallExpression::class.java
                    )
                )
                if (calls.any { call ->
                        call.resolveMethod()
                            ?.isEquivalentTo(targetMethod) == true
                    }
                ) {
                    return field
                }
            }
        }
        for (method in allMethods) {
            val calls = PsiTreeUtil.findChildrenOfType(
                method,
                PsiMethodCallExpression::class.java
            )
            if (calls.any { call ->
                    call.resolveMethod()
                        ?.isEquivalentTo(targetMethod) == true
                }
            ) {
                continue
            }
        }
        return null
    }


    private fun resolveRegistrationName(
        expression: PsiExpression?,
        method: PsiMethod,
        classes: List<PsiClass>
    ): String? {
        if (expression == null) return null
        if (expression is PsiLiteralExpression) {
            return expression.value as? String
        }

        val reference = expression as? PsiReferenceExpression
            ?: return expression.text
        val parameter = reference.resolve() as? PsiParameter
            ?: return expression.text
        val parameterIndex = method.parameterList.parameters
            .indexOfFirst {
                it.isEquivalentTo(parameter)
            }

        if (parameterIndex < 0) return expression.text

        for (psiClass in classes) {
            for (field in psiClass.fields) {
                val initializer = field.initializer ?: continue
                val rootCall = initializer as? PsiMethodCallExpression
                val calls = mutableListOf<PsiMethodCallExpression>()
                if (rootCall != null) calls.add(rootCall)

                calls.addAll(
                    PsiTreeUtil.findChildrenOfType(
                        initializer,
                        PsiMethodCallExpression::class.java
                    )
                )

                for (call in calls) {
                    val resolvedMethod = call.resolveMethod()
                        ?: continue
                    if (!resolvedMethod.isEquivalentTo(method)) continue

                    val argument = call.argumentList.expressions
                        .getOrNull(parameterIndex)
                        ?: continue
                    if (argument is PsiLiteralExpression) {
                        return argument.value as? String
                    }
                }
            }
        }
        return expression.text
    }
}
