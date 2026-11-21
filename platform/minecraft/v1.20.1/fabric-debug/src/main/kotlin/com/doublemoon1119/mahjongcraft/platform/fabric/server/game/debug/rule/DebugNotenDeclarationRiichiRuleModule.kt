package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.ExhaustiveDrawSettlementResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/**
 * 只在開發環境登記的四人日麻變體：沒有立直的聽牌玩家可以在一般流局結算前宣告不聽，其餘規則與 [RiichiRuleModule] 相同。
 *
 * 用來在遊戲內驗證一般流局結算前與本局結算後的規則選擇。宣告不聽記錄為 [DebugDeclareNotenAction]；一般流局結算時，
 * 有這個動作的玩家不公開手牌、照不聽支付罰符。其餘成員都交給以 [DebugNotenDeclarationRiichiRuleConfig.base] 建立的
 * 四人日麻模組。
 *
 * @property id 規則模組 ID。
 * @property config 開發用規則配置。
 * @property delegate 提供其餘規則的四人日麻模組。
 */
internal class DebugNotenDeclarationRiichiRuleModule private constructor(
    override val id: String,
    override val config: DebugNotenDeclarationRiichiRuleConfig,
    private val delegate: RiichiFamilyRuleModule<DebugNotenDeclarationRiichiRuleConfig>,
) : RiichiFamilyRuleModule<DebugNotenDeclarationRiichiRuleConfig> by delegate {
    /**
     * 以 [DebugNotenDeclarationRiichiRuleConfig.base] 建立委派的四人日麻模組。
     *
     * @param id 規則模組 ID。
     * @param config 開發用規則配置。
     */
    constructor(id: String, config: DebugNotenDeclarationRiichiRuleConfig) : this(id, config, riichiDelegate(id, config))

    override fun declareExhaustiveDraw(tableState: TableState): ExhaustiveDrawSettlementResult = delegate.declareExhaustiveDraw(
        tableState = tableState,
        notenDeclaredPlayerIds = tableState.activePlayers
            .filter { player -> DECLARE_NOTEN in player.actionHistory }
            .mapTo(mutableSetOf()) { it.id },
    )

    companion object {
        /** 開發用可宣告不聽四人日麻的規則模組 ID。 */
        val ID: String = MahjongCraftMetadata.id("debug_noten_declaration_riichi")

        /** 記錄在動作紀錄中的宣告不聽。 */
        private val DECLARE_NOTEN: GameAction = GameAction.Extension(DebugDeclareNotenAction)
    }
}

/**
 * 建立 [DebugNotenDeclarationRiichiRuleModule] 委派的四人日麻模組。
 *
 * 規則模組介面只有 `config` 使用型別參數，而 [DebugNotenDeclarationRiichiRuleModule] 自行提供 `config`，因此委派對象的
 * 型別參數不會被讀到，轉型不影響執行。
 *
 * @param id 規則模組 ID。
 * @param config 開發用規則配置。
 * @return 以 [DebugNotenDeclarationRiichiRuleConfig.base] 建立的四人日麻模組。
 */
@Suppress("UNCHECKED_CAST")
private fun riichiDelegate(
    id: String,
    config: DebugNotenDeclarationRiichiRuleConfig,
): RiichiFamilyRuleModule<DebugNotenDeclarationRiichiRuleConfig> = RiichiRuleModule(id, config.base) as RiichiFamilyRuleModule<DebugNotenDeclarationRiichiRuleConfig>
