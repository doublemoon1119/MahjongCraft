package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry

/**
 * [GameConfigPresentationRegistry] 的記憶體實作。
 *
 * @property roundPreparationResolvers 核心註冊的局前準備解析器；用來決定哪些規則有準備時間欄位。
 */
class GameConfigPresentationRegistryImpl(
    private val roundPreparationResolvers: RoundPreparationResolverRegistry,
) : GameConfigPresentationRegistry {
    private val definitions = linkedMapOf<String, GameConfigPresentationDefinition>()

    override var isFrozen: Boolean = false
        private set

    override val ruleModuleIds: Set<String> get() = definitions.keys.toSet()

    override fun register(definition: GameConfigPresentationDefinition) {
        check(!isFrozen) { "Game config presentation registry is frozen" }
        val normalized = SharedGameFlowConfigPresentation.normalize(
            definition = definition,
            roundPreparationConfig = roundPreparationResolvers.findDefaultConfig(definition.ruleModuleId),
        )
        require(definitions.putIfAbsent(normalized.ruleModuleId, normalized.snapshot()) == null) {
            "Duplicate game config presentation: ${definition.ruleModuleId}"
        }
    }

    override fun find(ruleModuleId: String): GameConfigPresentationDefinition? = definitions[ruleModuleId]?.snapshot()

    override fun defaultFlowConfig(ruleModuleId: String, current: GameFlowConfig): GameFlowConfig = current.copy(
        roundPreparation = roundPreparationResolvers.findDefaultConfig(ruleModuleId),
    )

    override fun freeze() {
        isFrozen = true
    }

    /** 複製設定與選項集合，讓登記及查詢各自持有獨立快照。 */
    private fun GameConfigPresentationDefinition.snapshot(): GameConfigPresentationDefinition = copy(
        categories = categories.toList(),
        fields = fields.map { field ->
            val editor = field.editor
            field.copy(
                editor = if (editor is GameConfigEditorSpec.SingleChoice) {
                    editor.copy(
                        optionIds = editor.optionIds.toList(),
                        optionTranslationKeys = editor.optionTranslationKeys.toMap(),
                    )
                } else {
                    editor
                },
            )
        },
    )
}
