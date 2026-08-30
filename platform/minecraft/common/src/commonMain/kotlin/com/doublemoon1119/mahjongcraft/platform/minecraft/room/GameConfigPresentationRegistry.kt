package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/** 設定畫面與聊天摘要共用的受控欄位值。 */
sealed interface GameConfigPresentationValue {
    data class BooleanValue(val enabled: Boolean) : GameConfigPresentationValue
    data class IntegerValue(val number: Int?) : GameConfigPresentationValue
    data class ChoiceValue(val optionId: String) : GameConfigPresentationValue
}

/** 設定欄位的受控編輯器種類。 */
sealed interface GameConfigEditorSpec {
    data object BooleanToggle : GameConfigEditorSpec

    data class IntegerInput(
        val minimum: Int,
        val maximum: Int,
        val step: Int = 1,
        val nullable: Boolean = false,
        val unitTranslationKey: String? = null,
    ) : GameConfigEditorSpec {
        init {
            require(minimum <= maximum) { "Config integer minimum must not exceed maximum" }
            require(step > 0) { "Config integer step must be positive" }
        }
    }

    data class SingleChoice(val optionIds: List<String>) : GameConfigEditorSpec {
        init {
            require(optionIds.isNotEmpty()) { "Config choice must contain at least one option" }
            require(optionIds.distinct().size == optionIds.size) { "Config choice options must be unique" }
            require(optionIds.all { ':' in it }) { "Config choice option IDs must be namespaced" }
        }
    }
}

/** 單一設定分類。 */
data class GameConfigCategoryDefinition(
    val id: String,
    val nameTranslationKey: String,
) {
    init {
        require(':' in id) { "Config category ID must be namespaced: $id" }
        require(nameTranslationKey.isNotBlank()) { "Config category translation key must not be blank" }
    }
}

/** 可由 GUI、hover 與差異診斷共用的單一設定欄位。 */
data class GameConfigFieldDefinition(
    val id: String,
    val categoryId: String,
    val nameTranslationKey: String,
    val descriptionTranslationKey: String,
    val editor: GameConfigEditorSpec,
    val isEditable: Boolean,
    /** 依目前草稿決定欄位是否可操作；唯讀顯示仍會保留。 */
    val isEnabled: (GameConfig) -> Boolean = { true },
    val read: (GameConfig) -> GameConfigPresentationValue,
    val update: ((GameConfig, GameConfigPresentationValue) -> GameConfig)? = null,
) {
    init {
        require(':' in id) { "Config field ID must be namespaced: $id" }
        require(':' in categoryId) { "Config field category ID must be namespaced: $categoryId" }
        require(nameTranslationKey.isNotBlank()) { "Config field name translation key must not be blank" }
        require(descriptionTranslationKey.isNotBlank()) { "Config field description translation key must not be blank" }
        require(!isEditable || update != null) { "Editable config field must provide an updater: $id" }
    }
}

/** 一個規則模組的完整設定呈現定義；規則名稱仍由 RuleModuleDisplayNameRegistry 提供。 */
data class GameConfigPresentationDefinition(
    val ruleModuleId: String,
    val descriptionTranslationKey: String,
    val selectable: Boolean,
    val unavailableReasonTranslationKey: String? = null,
    val defaultRuleConfig: () -> MahjongRuleConfig,
    val categories: List<GameConfigCategoryDefinition>,
    val fields: List<GameConfigFieldDefinition>,
) {
    init {
        require(':' in ruleModuleId) { "Rule module ID must be namespaced: $ruleModuleId" }
        require(selectable || unavailableReasonTranslationKey != null) {
            "Unselectable rule presentation must provide an unavailable reason: $ruleModuleId"
        }
        val categoryIds = categories.map { it.id }
        require(categoryIds.distinct().size == categoryIds.size) { "Duplicate config category ID in $ruleModuleId" }
        require(fields.map { it.id }.distinct().size == fields.size) { "Duplicate config field ID in $ruleModuleId" }
        require(fields.all { it.categoryId in categoryIds }) { "Config field references an unknown category in $ruleModuleId" }
    }
}

/** 供內建與第三方規則登記設定呈現定義的凍結式 registry。 */
interface GameConfigPresentationRegistry {
    val isFrozen: Boolean
    val ruleModuleIds: Set<String>

    fun register(definition: GameConfigPresentationDefinition)
    fun find(ruleModuleId: String): GameConfigPresentationDefinition?
    fun freeze()
}

/** [GameConfigPresentationRegistry] 的記憶體實作。 */
class GameConfigPresentationRegistryImpl : GameConfigPresentationRegistry {
    private val definitions = linkedMapOf<String, GameConfigPresentationDefinition>()

    override var isFrozen: Boolean = false
        private set
    override val ruleModuleIds: Set<String> get() = definitions.keys

    override fun register(definition: GameConfigPresentationDefinition) {
        check(!isFrozen) { "Game config presentation registry is frozen" }
        require(definitions.putIfAbsent(definition.ruleModuleId, definition) == null) {
            "Duplicate game config presentation: ${definition.ruleModuleId}"
        }
    }

    override fun find(ruleModuleId: String): GameConfigPresentationDefinition? = definitions[ruleModuleId]

    override fun freeze() {
        isFrozen = true
    }
}
