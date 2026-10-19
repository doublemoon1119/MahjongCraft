package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry

/**
 * 以伺服器的房間設定為基礎，重建客戶端送來的新設定，只接受房間畫面本來就能做到的修改。
 *
 * 同一規則以房間目前設定為基礎，切換規則則先換成目標規則的預設值。先驗證並套用規則專屬欄位，
 * 再依重建後的規則設定決定哪些共用欄位可以修改，最後驗證並套用流程欄位。
 * 每個值都須符合欄位編輯器的限制（[GameConfigEditorSpec.accepts]）；重建結果與送來的設定不同時，
 * 代表客戶端改了不可編輯的內容，整份拒絕。
 */
object RoomConfigSubmission {
    /** 審查送來的房間設定的結果。 */
    sealed interface Result {
        /**
         * 設定可以套用。
         *
         * @property config 伺服器重建的設定，與送來的設定相同。
         */
        data class Accepted(val config: GameConfig) : Result

        /** 規則目前無法用於房間與開局（見 [RulePresentationRequirements.isPlayable]）。 */
        data object RuleUnavailable : Result

        /** 設定包含房間畫面無法產生的值，或改動了不可編輯的內容。 */
        data object Invalid : Result
    }

    /**
     * 審查客戶端送來的新房間設定。
     *
     * @param submitted 客戶端送來的設定。
     * @param current 伺服器上房間目前的設定。
     * @param presentations 已凍結的設定呈現 registry。
     * @param moduleRegistry 已凍結的規則模組 registry。
     * @return 審查結果。
     */
    fun review(
        submitted: GameConfig,
        current: GameConfig,
        presentations: GameConfigPresentationRegistry,
        moduleRegistry: MahjongModuleRegistry,
    ): Result {
        val ruleModuleId = moduleRegistry.getModule(submitted.ruleConfig).id
        val defaultDefinition = presentations.find(ruleModuleId) ?: return Result.RuleUnavailable
        if (!defaultDefinition.selectable) return Result.RuleUnavailable
        val sameRule = moduleRegistry.getModule(current.ruleConfig).id == ruleModuleId
        val baseRuleConfig = if (sameRule) current.ruleConfig else defaultDefinition.defaultRuleConfig()
        val base = current.copy(
            ruleConfig = baseRuleConfig,
            flowConfig = if (sameRule) {
                presentations.normalizeFlowConfig(
                    ruleModuleId = ruleModuleId,
                    ruleConfig = baseRuleConfig,
                    current = current.flowConfig,
                )
            } else {
                presentations.defaultFlowConfig(
                    ruleModuleId = ruleModuleId,
                    ruleConfig = baseRuleConfig,
                    current = current.flowConfig,
                )
            },
        )
        val rebuiltRule = defaultDefinition.fields
            .filter { it.categoryId != SharedGameFlowConfigPresentation.FLOW && it.isEditable }
            .fold(base) { config, field ->
                try {
                    val value = field.read(submitted)
                    if (!field.editor.accepts(value)) return Result.Invalid
                    checkNotNull(field.update)(config, value)
                } catch (_: IllegalArgumentException) {
                    return Result.Invalid
                }
            }
        if (!RulePresentationRequirements.isPlayable(rebuiltRule.ruleConfig, presentations, moduleRegistry)) {
            return Result.RuleUnavailable
        }
        val effectiveDefinition = presentations.find(ruleModuleId, rebuiltRule.ruleConfig)
            ?: return Result.RuleUnavailable
        val rebuilt = effectiveDefinition.fields
            .filter { it.categoryId == SharedGameFlowConfigPresentation.FLOW && it.isEditable }
            .fold(
                rebuiltRule.copy(
                    flowConfig = presentations.normalizeFlowConfig(
                        ruleModuleId = ruleModuleId,
                        ruleConfig = rebuiltRule.ruleConfig,
                        current = rebuiltRule.flowConfig,
                    ),
                ),
            ) { config, field ->
                try {
                    val value = field.read(submitted)
                    if (!field.editor.accepts(value)) return Result.Invalid
                    checkNotNull(field.update)(config, value)
                } catch (_: IllegalArgumentException) {
                    return Result.Invalid
                }
            }
        return if (rebuilt == submitted) Result.Accepted(rebuilt) else Result.Invalid
    }
}
