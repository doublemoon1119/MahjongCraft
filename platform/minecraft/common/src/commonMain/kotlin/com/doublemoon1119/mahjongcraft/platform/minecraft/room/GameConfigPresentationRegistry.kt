package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig

/**
 * 供內建與第三方規則登記設定呈現定義的凍結式 registry。
 *
 * 登記時會自動附加共用流程分類與欄位；規則不得重複宣告分類 `mahjongcraft:flow`，或欄位
 * `mahjongcraft:base_seconds`、`mahjongcraft:reserve_seconds`、
 * `mahjongcraft:spectating_policy`、`mahjongcraft:spectator_hand_visibility`。
 * `mahjongcraft:preparation_base_seconds` 也為保留欄位；只有核心準備解析器提供預設時間的規則才會取得。
 * 沒有準備流程的規則不會有這個欄位，也不能透過它修改設定。
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
     * 查詢設定呈現定義。
     *
     * @param ruleModuleId 要查詢的規則模組 ID。
     * @return 已登記的呈現定義，或未知 ID 時的 null。
     */
    fun find(ruleModuleId: String): GameConfigPresentationDefinition?

    /**
     * 依規則建立流程設定的預設值；會保留一般流程設定，只替換核心註冊的局前準備設定。
     *
     * @param ruleModuleId 規則模組識別碼。
     * @param current 目前可沿用的一般流程設定。
     * @return 該規則應使用的流程設定；沒有準備設定的規則會清除局前準備設定。
     */
    fun defaultFlowConfig(
        ruleModuleId: String,
        current: GameFlowConfig = GameFlowConfig(),
    ): GameFlowConfig

    /** 凍結 registry。 */
    fun freeze()
}
