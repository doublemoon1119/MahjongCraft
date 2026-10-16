package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceTableLayout

/**
 * 規則要在 Minecraft 麻將桌上進行對局必須滿足的條件。
 *
 * 桌上的牌只從牌山產生，因此規則必須提供牌山布局與開門規則，開門一次擲出的骰子數量也必須是桌上能擺放的數量
 * （[MahjongDiceTableLayout.supportedDiceCounts]）。只有設定呈現標為可選、且滿足這些條件的規則，才能用於房間設定與開局。
 */
object RulePresentationRequirements {
    /**
     * 找出規則模組無法在麻將桌上呈現的原因。
     *
     * @param module 要檢查的規則模組。
     * @return 不符合的原因；符合所有條件時為 null。
     */
    fun unmetRequirement(module: MahjongRuleModule<*>): String? {
        if (module.createWallLayout() == null) return "rule ${module.id} provides no wall layout"
        val openingPolicy = module.createWallOpeningPolicy() ?: return "rule ${module.id} provides no wall opening policy"
        val diceCount = openingPolicy.diceCount
        return if (diceCount in MahjongDiceTableLayout.supportedDiceCounts) {
            null
        } else {
            "rule ${module.id} rolls $diceCount dice, but the table can only place " +
                MahjongDiceTableLayout.supportedDiceCounts.sorted().joinToString(" or ")
        }
    }

    /**
     * 判斷房間設定的規則目前能否用於房間與開局：必須有可選的設定呈現，且規則模組可以在麻將桌上呈現。
     *
     * @param ruleConfig 房間設定使用的規則設定。
     * @param presentations 已凍結的設定呈現 registry。
     * @param moduleRegistry 已凍結的規則模組 registry。
     * @return 可以使用時為 true。
     */
    fun isPlayable(
        ruleConfig: MahjongRuleConfig,
        presentations: GameConfigPresentationRegistry,
        moduleRegistry: MahjongModuleRegistry,
    ): Boolean {
        val module = moduleRegistry.getModule(ruleConfig)
        return presentations.find(module.id)?.selectable == true && unmetRequirement(module) == null
    }

    /**
     * 啟動時檢查每個可選規則：以預設設定建立的規則模組 ID 必須與設定呈現相同，且能在麻將桌上呈現。
     *
     * 不可選的設定呈現不檢查；它們無法用於房間設定與開局。
     *
     * @param presentations 已完成登記的設定呈現 registry。
     * @param moduleRegistry 已完成登記的規則模組 registry。
     * @throws IllegalStateException 任一可選規則不符合條件時，訊息列出所有不符合的規則與原因。
     */
    fun verifySelectableRules(presentations: GameConfigPresentationRegistry, moduleRegistry: MahjongModuleRegistry) {
        val problems = presentations.ruleModuleIds.mapNotNull { ruleModuleId ->
            val definition = checkNotNull(presentations.find(ruleModuleId))
            if (!definition.selectable) return@mapNotNull null
            val module = moduleRegistry.getModule(definition.defaultRuleConfig())
            if (module.id != ruleModuleId) {
                "presentation $ruleModuleId creates rule ${module.id} from its default config"
            } else {
                unmetRequirement(module)
            }
        }
        check(problems.isEmpty()) { "Selectable mahjong rules cannot be played on the table: ${problems.joinToString("; ")}" }
    }
}
