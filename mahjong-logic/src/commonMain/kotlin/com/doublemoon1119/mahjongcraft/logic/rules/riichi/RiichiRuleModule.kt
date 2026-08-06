package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.RiichiDeclarationResult
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 日本麻將規則模組實作。
 *
 * 負責串接日本麻將特有的組件，包含 [RiichiWallFactory] 與 [RiichiDiscardPile]...等等。
 */
class RiichiRuleModule(
    override val id: String,
    override val config: RiichiRuleConfig
) : MahjongRuleModule<RiichiRuleConfig> {
    /**
     * 建立日本麻將牌山工廠。
     *
     * @return [RiichiWallFactory] 實體。
     */
    override fun createWallFactory(): RiichiWallFactory {
        return RiichiWallFactory(config)
    }

    /**
     * 建立日本麻將專用的牌河。
     *
     * @return [RiichiDiscardPile] 實體。
     */
    override fun createDiscardPile(): RiichiDiscardPile {
        return RiichiDiscardPile()
    }

    /**
     * 建立日本麻將的向聽數計算器。
     *
     * @return [RiichiShantenCalculator] 實體。
     */
    override fun createShantenCalculator(): RiichiShantenCalculator {
        return RiichiShantenCalculator()
    }

    /**
     * 建立日本麻將的合法動作判定器。
     *
     * @return [RiichiLegalActionValidator] 實體。
     */
    override fun createLegalActionValidator(): RiichiLegalActionValidator {
        return RiichiLegalActionValidator(
            shantenCalculator = createShantenCalculator(),
            handValueCalculator = createHandValueCalculator(),
            contextCalculator = createHandValueContextCalculator()
        )
    }

    /**
     * 建立日本麻將的手牌價值計算機。
     *
     * @return [RiichiHandValueCalculator] 實體。
     */
    override fun createHandValueCalculator(): RiichiHandValueCalculator {
        return RiichiHandValueCalculator(useLocalYaku = config.useLocalYaku)
    }

    /**
     * 建立日本麻將的手牌價值上下文計算機。
     *
     * @return [RiichiHandValueContextCalculator] 實體。
     */
    override fun createHandValueContextCalculator(): RiichiHandValueContextCalculator {
        return RiichiHandValueContextCalculator(config)
    }

    /**
     * 建立日本麻將的初始動態桌況狀態。
     *
     * @return 全新的 [RiichiDynamicState]（立直棒數量為 0）。
     */
    override fun createInitialDynamicState(): RiichiDynamicState {
        return RiichiDynamicState()
    }

    /**
     * 建立日本麻將的初始玩家規則狀態。
     *
     * @return 全新的 [RiichiPlayerState]（尚未立直、無包牌責任）。
     */
    override fun createInitialPlayerRuleState(): RiichiPlayerState {
        return RiichiPlayerState()
    }

    /**
     * 套用日本麻將立直宣告的狀態變化：標記捨牌紀錄、更新立直/雙立直/一發狀態、立直棒 +1。
     *
     * @return 套用宣告後的新玩家實例與新的 [RiichiDynamicState]；若 [player]/[tableState] 缺少
     *         日麻所需的規則狀態（理論上不會發生，僅作防呆）則回傳 null。
     */
    override fun declareRiichi(
        tableState: TableState,
        player: MahjongPlayer,
        discardResult: Hand.DiscardResult
    ): RiichiDeclarationResult? {
        val riichiState = player.playerRuleState as? RiichiPlayerState ?: return null
        val riichiDiscardPile = player.discardPile as? RiichiDiscardPile ?: return null
        val riichiDynamicState = tableState.dynamicRuleState as? RiichiDynamicState ?: return null

        val isDoubleRiichi = tableState.isFirstGoAround(player)
        val updatedPlayerRuleState = riichiState.copy(
            riichiTile = if (isDoubleRiichi) null else discardResult.tile,
            doubleRiichiTile = if (isDoubleRiichi) discardResult.tile else null,
            isIppatsu = true
        )
        val updatedPlayer = player.copy(
            hand = discardResult.hand,
            discardPile = riichiDiscardPile.discard(RiichiDiscardEntry(discardResult.tile, isRiichi = true)),
            score = player.score - 1000,
            playerRuleState = updatedPlayerRuleState
        )

        return RiichiDeclarationResult(
            player = updatedPlayer,
            dynamicRuleState = riichiDynamicState.copy(riichiStickCount = riichiDynamicState.riichiStickCount + 1)
        )
    }

    /**
     * 若玩家已立直且仍在一發窗口內，摸牌代表這個窗口已經結束（本巡未能胡牌），故清除一發資格。
     */
    override fun onPlayerDrew(player: MahjongPlayer): MahjongPlayer {
        val riichiState = player.playerRuleState as? RiichiPlayerState ?: return player
        if (!riichiState.isIppatsu) return player
        return player.copy(playerRuleState = riichiState.copy(isIppatsu = false))
    }

    /**
     * 任何一次鳴牌都會讓場上所有玩家的一發資格失效。
     */
    override fun onMeldClaimed(players: List<MahjongPlayer>): List<MahjongPlayer> {
        return players.map { player ->
            val riichiState = player.playerRuleState as? RiichiPlayerState ?: return@map player
            if (!riichiState.isIppatsu) return@map player
            player.copy(playerRuleState = riichiState.copy(isIppatsu = false))
        }
    }

    /**
     * 檢查本次碰／明槓是否觸發大三元／大四喜的包牌責任，若觸發則寫入 [claimingPlayer] 的 [RiichiPlayerState]。
     */
    override fun applyPaoLiabilityIfTriggered(
        claimingPlayer: MahjongPlayer,
        calledTile: IdentifiedTile,
        sourceDirection: RelativeDirection
    ): MahjongPlayer {
        val riichiState = claimingPlayer.playerRuleState as? RiichiPlayerState ?: return claimingPlayer
        val liability = PaoDetector.check(claimingPlayer.hand, calledTile.tile, sourceDirection) ?: return claimingPlayer
        return claimingPlayer.copy(playerRuleState = riichiState.copy(paoLiability = liability))
    }
}
