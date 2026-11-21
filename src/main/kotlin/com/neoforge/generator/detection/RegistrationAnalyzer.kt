package com.neoforge.generator.detection

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil

data class RegistrationMethodInfo(
    val method: PsiMethod,
    val registersBlocks: Boolean,
    val registersItems: Boolean,
    val createsBlockItem: Boolean,
    val calls: List<String>,
    val isGeneric: Boolean
)

data class RegistrationAnalysis(
    val methods: List<RegistrationMethodInfo>,
    val blockRegistrationMethods: List<PsiMethod>,
    val itemRegistrationMethods: List<PsiMethod>,
    val blockItemMethods: List<PsiMethod>,
    val eventBusRegistrationMethods: List<PsiMethod>
)

class RegistrationAnalyzer {
    fun analyze(
        project: Project,
        registryInfo: NeoForgeRegistryInfo
    ): RegistrationAnalysis {
        val scope = GlobalSearchScope.projectScope(project)
        val psiManager = PsiManager.getInstance(project)

        val javaFiles = FileTypeIndex.getFiles(
            JavaFileType.INSTANCE,
            scope
        )

        val classes = mutableListOf<PsiClass>()

        for (file in javaFiles) {
            val psiFile = psiManager.findFile(file) ?: continue
            classes.addAll(
                PsiTreeUtil.findChildrenOfType(
                    psiFile,
                    PsiClass::class.java
                )
            )
        }

        val allMethods = classes.flatMap { it.methods.toList() }

        val blockCalls = mutableSetOf<PsiMethod>()
        val itemCalls = mutableSetOf<PsiMethod>()
        val blockItemCalls = mutableSetOf<PsiMethod>()
        val eventBusCalls = mutableSetOf<PsiMethod>()

        for (method in allMethods) {
            val calls = PsiTreeUtil.findChildrenOfType(
                method,
                PsiMethodCallExpression::class.java
            )

            for (call in calls) {
                val methodName =
                    call.methodExpression.referenceName ?: continue
                val qualifier =
                    call.methodExpression.qualifierExpression
                val resolvedField =
                    (qualifier as? PsiReferenceExpression)
                        ?.resolve() as? PsiField
                val isBlockRegistry =
                    resolvedField?.isEquivalentTo(
                        registryInfo.blockRegistry.field
                    ) == true
                val isItemRegistry =
                    resolvedField?.isEquivalentTo(
                        registryInfo.itemRegistry.field
                    ) == true

                if (isBlockRegistry &&
                    methodName in setOf(
                        "register",
                        "registerBlock",
                        "registerSimpleBlock"
                    )
                ) {
                    blockCalls.add(method)
                }

                if (isItemRegistry &&
                    methodName in setOf(
                        "register",
                        "registerItem",
                        "registerSimpleItem",
                        "registerSimpleBlockItem"
                    )
                ) {
                    itemCalls.add(method)
                }

                if (methodName in setOf(
                        "RegisterBlockItem",
                        "registerBlockItem"
                    )
                ) {
                    blockItemCalls.add(method)
                }

                if (methodName == "registerSimpleBlockItem" &&
                    isItemRegistry
                ) {
                    blockItemCalls.add(method)
                }

                if (isBlockItemCreation(method)) {
                    blockItemCalls.add(method)
                }

                if ((isBlockRegistry || isItemRegistry) &&
                    isEventBusRegistration(call)
                ) {
                    eventBusCalls.add(method)
                }
            }

            if (isBlockItemCreation(method)) {
                blockItemCalls.add(method)
            }
        }

        var changed: Boolean

        do {
            changed = false

            for (method in allMethods) {

                val calls = PsiTreeUtil.findChildrenOfType(
                    method,
                    PsiMethodCallExpression::class.java
                )

                for (call in calls) {

                    val target = call.resolveMethod() ?: continue

                    if (target in blockCalls &&
                        blockCalls.add(method)
                    ) {
                        changed = true
                    }

                    if (target in itemCalls &&
                        itemCalls.add(method)
                    ) {
                        changed = true
                    }

                    if (target in blockItemCalls &&
                        blockItemCalls.add(method)
                    ) {
                        changed = true
                    }

                    if (target in eventBusCalls &&
                        eventBusCalls.add(method)
                    ) {
                        changed = true
                    }
                }
            }

        } while (changed)

        val registrationMethods = (
                blockCalls +
                        itemCalls +
                        blockItemCalls +
                        eventBusCalls
                ).distinct()

        val methodInfos = registrationMethods.map { method ->

            val calls = PsiTreeUtil.findChildrenOfType(
                method,
                PsiMethodCallExpression::class.java
            ).mapNotNull {
                it.methodExpression.referenceName
            }

            RegistrationMethodInfo(
                method = method,
                registersBlocks = method in blockCalls,
                registersItems = method in itemCalls,
                createsBlockItem = method in blockItemCalls,
                calls = calls,
                isGeneric = method.typeParameters.isNotEmpty()
            )
        }

        return RegistrationAnalysis(
            methods = methodInfos,
            blockRegistrationMethods = blockCalls.toList(),
            itemRegistrationMethods = itemCalls.toList(),
            blockItemMethods = blockItemCalls.toList(),
            eventBusRegistrationMethods = eventBusCalls.toList()
        )
    }

    private fun isBlockItemCreation(method: PsiMethod): Boolean {

        val expressions = PsiTreeUtil.findChildrenOfType(
            method,
            PsiNewExpression::class.java
        )

        return expressions.any { expression ->

            val resolvedClass =
                expression.classReference?.resolve() as? PsiClass

            resolvedClass?.qualifiedName ==
                    "net.minecraft.world.item.BlockItem"
        }
    }

    private fun isEventBusRegistration(
        call: PsiMethodCallExpression
    ): Boolean {

        if (call.methodExpression.referenceName != "register") {
            return false
        }

        val arguments = call.argumentList.expressions

        if (arguments.size != 1) {
            return false
        }

        return arguments[0].type?.canonicalText
            ?.contains("IEventBus") ?: false
    }
}