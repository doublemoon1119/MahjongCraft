package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule

import com.doublemoon1119.mahjongcraft.logic.module.ActionScoringPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpPolicy
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * 只在開發環境登記的三人日麻變體：摸到北時自動拔北並補牌，拔北與槓牌成立當下收付點數，其餘規則與
 * [ThreePlayerRiichiRuleModule] 相同。
 *
 * 用來在遊戲內驗證摸牌後自動處理與動作即時收付的流程與呈現。除了這兩項以外的成員都交給以
 * [DebugAutoNorthRiichiRuleConfig.base] 建立的三人日麻模組。
 *
 * @property id 規則模組 ID。
 * @property config 開發用規則配置。
 */
internal class DebugAutoNorthRiichiRuleModule(
    override val id: String,
    override val config: DebugAutoNorthRiichiRuleConfig,
) : RiichiFamilyRuleModule<DebugAutoNorthRiichiRuleConfig> by threePlayerRiichiDelegate(id, config) {
    /** 摸到北時自動拔北；不能拔北時以一般流局結束本局。 */
    override fun createDrawFollowUpPolicy(): DrawFollowUpPolicy = DebugAutoNorthDrawFollowUpPolicy(createLegalActionValidator())

    /** 拔北付進供託，槓牌由放槓者或另外兩家付給開槓者。 */
    override fun createActionScoringPolicy(): ActionScoringPolicy = DebugAutoNorthActionScoringPolicy

    companion object {
        /** 開發用自動拔北三人日麻的規則模組 ID。 */
        val ID: String = MahjongCraftMetadata.id("debug_auto_north_riichi")
    }
}

/**
 * 建立 [DebugAutoNorthRiichiRuleModule] 委派的三人日麻模組。
 *
 * 規則模組介面只有 `config` 使用型別參數，而 [DebugAutoNorthRiichiRuleModule] 自行提供 `config`，因此委派對象的型別參數
 * 不會被讀到，轉型不影響執行。
 *
 * @param id 規則模組 ID。
 * @param config 開發用規則配置。
 * @return 以 [DebugAutoNorthRiichiRuleConfig.base] 建立的三人日麻模組。
 */
@Suppress("UNCHECKED_CAST")
private fun threePlayerRiichiDelegate(
    id: String,
    config: DebugAutoNorthRiichiRuleConfig,
): RiichiFamilyRuleModule<DebugAutoNorthRiichiRuleConfig> = ThreePlayerRiichiRuleModule(id, config.base) as RiichiFamilyRuleModule<DebugAutoNorthRiichiRuleConfig>
