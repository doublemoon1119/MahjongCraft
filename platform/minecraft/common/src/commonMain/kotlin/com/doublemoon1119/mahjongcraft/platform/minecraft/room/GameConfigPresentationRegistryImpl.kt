package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import org.koin.core.annotation.Single
/** [GameConfigPresentationRegistry] 的記憶體實作。 */
@Single(binds = [GameConfigPresentationRegistry::class])
class GameConfigPresentationRegistryImpl : GameConfigPresentationRegistry {
    private val definitions = linkedMapOf<String, GameConfigPresentationDefinition>()

    override var isFrozen: Boolean = false
        private set

    override val ruleModuleIds: Set<String> get() = definitions.keys.toSet()

    override fun register(definition: GameConfigPresentationDefinition) {
        check(!isFrozen) { "Game config presentation registry is frozen" }
        require(definitions.putIfAbsent(definition.ruleModuleId, definition.snapshot()) == null) {
            "Duplicate game config presentation: ${definition.ruleModuleId}"
        }
    }

    override fun find(ruleModuleId: String): GameConfigPresentationDefinition? = definitions[ruleModuleId]?.snapshot()

    override fun freeze() {
        isFrozen = true
    }

    /** 複製設定與選項集合，讓登記及查詢各自持有獨立快照。 */
    private fun GameConfigPresentationDefinition.snapshot(): GameConfigPresentationDefinition = copy(
        categories = categories.toList(),
        fields = fields.map { field ->
            val editor = field.editor
            field.copy(editor = if (editor is GameConfigEditorSpec.SingleChoice) editor.copy(optionIds = editor.optionIds.toList()) else editor)
        },
    )
}
