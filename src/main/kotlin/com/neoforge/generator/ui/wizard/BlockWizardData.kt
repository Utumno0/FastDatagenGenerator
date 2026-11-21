package com.neoforge.generator.ui.wizard

data class BlockWizardData(
    var blockName: String = "",
    var registryName: String = "",
    var hardness: Float = 3.0f,
    var resistance: Float = 3.0f,
    var requiresTool: Boolean = false,
    var toolType: String = "Pickaxe",
    var texturePath: String? = null,
    var modelType: String = "Cube"
)