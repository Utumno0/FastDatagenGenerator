package com.neoforge.generator.detection

data class NeoForgeProjectInfo(
    val isNeoForge: Boolean,
    val projectPath: String,
    val minecraftVersion: String? = null,
    val neoForgeVersion: String? = null,
    val modId: String? = null,
    val resourcesPath: String? = null
)