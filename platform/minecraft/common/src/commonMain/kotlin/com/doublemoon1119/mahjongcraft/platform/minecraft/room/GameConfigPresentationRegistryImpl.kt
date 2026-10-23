package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/**
 * [GameConfigPresentationRegistry] 的記憶體實作。
 *
 * @property ruleDecisionResolvers 核心註冊的規則選擇解析器；依當前規則設定決定是否需要規則選擇時間欄位。
 */
class GameConfigPresentationRegistryImpl(
    private val ruleDecisionResolvers: RuleDecisionResolverRegistry,
) : GameConfigPresentationRegistry {
    private val definitions = linkedMapOf<String, GameConfigPresentationDefinition>()

    override var isFrozen: Boolean = false
        private set

    override val ruleModuleIds: Set<String> get() = definitions.keys.toSet()

    override fun register(definition: GameConfigPresentationDefinition) {
        check(!isFrozen) { "Game config presentation registry is frozen" }
        val normalized = SharedGameFlowConfigPresentation.normalize(
            definition = definition,
            ruleDecisionConfig = ruleDecisionResolvers.findDefaultConfig(
                definition.ruleModuleId,
                definition.defaultRuleConfig(),
            ),
        )
        require(definitions.putIfAbsent(normalized.ruleModuleId, definition.snapshot()) == null) {
            "Duplicate game config presentation: ${definition.ruleModuleId}"
        }
    }

    override fun find(ruleModuleId: String): GameConfigPresentationDefinition? = definitions[ruleModuleId]?.let { definition ->
        find(ruleModuleId, definition.defaultRuleConfig())
    }

    override fun find(ruleModuleId: String, ruleConfig: MahjongRuleConfig): GameConfigPresentationDefinition? = definitions[ruleModuleId]?.let { definition ->
        SharedGameFlowConfigPresentation.normalize(
            definition = definition,
            ruleDecisionConfig = ruleDecisionResolvers.findDefaultConfig(ruleModuleId, ruleConfig),
        ).snapshot()
    }

    override fun defaultFlowConfig(ruleModuleId: String, current: GameFlowConfig): GameFlowConfig = definitions[ruleModuleId]?.let { definition ->
        defaultFlowConfig(
            ruleModuleId = ruleModuleId,
            ruleConfig = definition.defaultRuleConfig(),
            current = current,
        )
    } ?: current.copy(ruleDecision = null)

    override fun defaultFlowConfig(
        ruleModuleId: String,
        ruleConfig: MahjongRuleConfig,
        current: GameFlowConfig,
    ): GameFlowConfig = current.copy(
        ruleDecision = ruleDecisionResolvers.findDefaultConfig(ruleModuleId, ruleConfig),
    )

    override fun normalizeFlowConfig(
        ruleModuleId: String,
        ruleConfig: MahjongRuleConfig,
        current: GameFlowConfig,
    ): GameFlowConfig {
        val defaultRuleDecision = ruleDecisionResolvers.findDefaultConfig(ruleModuleId, ruleConfig)
        val ruleDecision = when {
            defaultRuleDecision == null -> null
            current.ruleDecision?.baseSeconds in SharedGameFlowConfigPresentation.ruleDecisionTimeRange -> current.ruleDecision
            else -> defaultRuleDecision
        }
        return current.copy(ruleDecision = ruleDecision)
    }

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
