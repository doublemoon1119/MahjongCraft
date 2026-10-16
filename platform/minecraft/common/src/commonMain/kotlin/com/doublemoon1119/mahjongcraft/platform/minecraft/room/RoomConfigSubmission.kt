package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry

/**
 * 以伺服器的房間設定為基礎，重建客戶端送來的新設定，只接受房間畫面本來就能做到的修改。
 *
 * 基礎是房間目前的設定；換了規則時，基礎改為保留目前流程設定、規則換成新規則預設值的設定，與房間畫面切換規則時相同。
 * 接著依新規則設定呈現的可編輯欄位，逐一讀出送來的值，確認符合欄位編輯器的限制（[GameConfigEditorSpec.accepts]）後，
 * 以欄位自己的更新函式套進基礎。重建結果與送來的設定不同，代表客戶端改了畫面上不能改的內容，整份拒絕。
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
        if (!RulePresentationRequirements.isPlayable(submitted.ruleConfig, presentations, moduleRegistry)) {
            return Result.RuleUnavailable
        }
        val ruleModuleId = moduleRegistry.getModule(submitted.ruleConfig).id
        val definition = checkNotNull(presentations.find(ruleModuleId))
        val base = if (moduleRegistry.getModule(current.ruleConfig).id == ruleModuleId) {
            current
        } else {
            current.copy(ruleConfig = definition.defaultRuleConfig())
        }
        val rebuilt = definition.fields.filter { it.isEditable }.fold(base) { config, field ->
            val value = field.read(submitted)
            if (!field.editor.accepts(value)) return Result.Invalid
            try {
                checkNotNull(field.update)(config, value)
            } catch (_: IllegalArgumentException) {
                return Result.Invalid
            }
        }
        return if (rebuilt == submitted) Result.Accepted(rebuilt) else Result.Invalid
    }
}
