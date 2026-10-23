package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig

/**
 * 供內建與第三方規則登記設定呈現定義的凍結式 registry。
 *
 * 查詢時依規則設定附加共用流程分類與欄位；規則不得重複宣告分類 `mahjongcraft:flow`，或欄位
 * `mahjongcraft:base_seconds`、`mahjongcraft:reserve_seconds`、
 * `mahjongcraft:spectating_policy`、`mahjongcraft:spectator_hand_visibility`。
 * `mahjongcraft:rule_decision_base_seconds` 也為保留欄位；只有規則選擇流程依目前設定提供預設時間時才會取得。
 * 目前設定不需要玩家選擇時，不會有這個欄位，也不能透過它修改設定。
 * 查詢得到的定義包含這些共用欄位，房間操作、設定摘要、牌譜與伺服器驗證共用同一份內容。
 */
interface GameConfigPresentationRegistry {
    /** 目前已登記規則模組 ID 的快照。 */
    val registrationKeys: Set<String> get() = ruleModuleIds.toSet()

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /** 目前已登記的規則模組 ID。 */
    val ruleModuleIds: Set<String>

    /**
     * 登記設定呈現定義。
     *
     * @param definition 要登記的規則設定呈現定義。
     * @throws IllegalArgumentException 規則重複登記，或提供了保留的共用流程分類、欄位 ID。
     */
    fun register(definition: GameConfigPresentationDefinition)

    /**
     * 依規則的預設設定查詢呈現定義；已有完整規則設定時，應使用帶有設定參數的查詢。
     *
     * @param ruleModuleId 要查詢的規則模組 ID。
     * @return 已登記的呈現定義，或未知 ID 時的 null。
     */
    fun find(ruleModuleId: String): GameConfigPresentationDefinition?

    /**
     * 依規則模組與完整規則設定查詢呈現定義。
     *
     * @param ruleModuleId 要查詢的規則模組識別碼。
     * @param ruleConfig 目前完整的規則設定；可影響可用的共用流程欄位。
     * @return 符合該設定的呈現定義，或未知 ID 時為 null。
     */
    fun find(ruleModuleId: String, ruleConfig: MahjongRuleConfig): GameConfigPresentationDefinition?

    /**
     * 依規則建立流程設定的預設值；會保留一般流程設定，只替換核心註冊的規則選擇設定。
     *
     * @param ruleModuleId 規則模組識別碼。
     * @param current 目前可沿用的一般流程設定。
     * @return 該規則應使用的流程設定；沒有規則選擇的規則會清除規則選擇設定。
     */
    fun defaultFlowConfig(
        ruleModuleId: String,
        current: GameFlowConfig = GameFlowConfig(),
    ): GameFlowConfig

    /**
     * 依完整規則設定建立流程設定預設值。
     *
     * @param ruleModuleId 規則模組識別碼。
     * @param ruleConfig 目前完整的規則設定。
     * @param current 目前可沿用的一般流程設定。
     * @return 該規則設定應使用的流程設定。
     */
    fun defaultFlowConfig(
        ruleModuleId: String,
        ruleConfig: MahjongRuleConfig,
        current: GameFlowConfig = GameFlowConfig(),
    ): GameFlowConfig

    /**
     * 依規則目前是否需要規則選擇，整理既有流程設定。
     *
     * 規則仍需要玩家選擇時保留先前設定的有效秒數；尚未設定或數值失效時才使用預設值。
     * 規則不需要玩家選擇時會清除舊設定。
     *
     * @param ruleModuleId 規則模組識別碼。
     * @param ruleConfig 目前完整的規則設定。
     * @param current 目前流程設定。
     * @return 與規則設定一致的流程設定。
     */
    fun normalizeFlowConfig(
        ruleModuleId: String,
        ruleConfig: MahjongRuleConfig,
        current: GameFlowConfig,
    ): GameFlowConfig

    /** 凍結 registry。 */
    fun freeze()
}
