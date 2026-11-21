package com.neoforge.generator.detection

import com.intellij.openapi.project.Project
import java.io.File

class NeoForgeDetector {
    fun detect(project: Project): NeoForgeProjectInfo? {
        val rootPath = project.basePath ?: return null
        val root = File(rootPath)

        if (!root.exists()) return null
        val gradleFiles = listOf(
            File(root, "build.gradle"),
            File(root, "build.gradle.kts"),
            File(root, "gradle.properties")
        )
        val content = gradleFiles
            .filter { it.exists() }
            .joinToString("\n") { it.readText() }
        if (!content.contains("net.neoforged", ignoreCase = true)) {
            return null
        }

        val minecraftVersion = findValue(
            content,
            listOf("minecraft_version", "minecraftVersion")
        )
        val neoForgeVersion = findValue(
            content,
            listOf("neo_version", "neoForgeVersion", "neoforge_version")
        )
        val modId = findValue(
            content,
            listOf("mod_id", "modId")
        )
        val resourcesPath = File(root, "src/main/resources")

        return NeoForgeProjectInfo(
            isNeoForge = true,
            projectPath = root.absolutePath,
            minecraftVersion = minecraftVersion,
            neoForgeVersion = neoForgeVersion,
            modId = modId,
            resourcesPath = resourcesPath.absolutePath
        )
    }

    private fun findValue(content: String, keys: List<String>): String? {
        for (key in keys) {
            val regex = Regex(
                """(?m)^\s*${Regex.escape(key)}\s*=\s*["']?([^"'\s#]+)"""
            )
            val match = regex.find(content)
            if (match != null) {
                return match.groupValues[1]
            }
        }
        return null
    }
}