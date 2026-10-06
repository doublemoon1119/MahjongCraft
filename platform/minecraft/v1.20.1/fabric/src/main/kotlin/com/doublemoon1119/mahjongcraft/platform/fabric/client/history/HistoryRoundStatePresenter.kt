package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinnerDetailsDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistry

/** 將已驗證歷史桌況轉為共用牌組幾何，不重排或重新判定規則。 */
internal object HistoryRoundStatePresenter {
    /**
     * 取得保存當下的完整和牌牌組；立牌已失去對應時不猜測原手牌。
     * @param state 已驗證的交易後桌況。
     * @param winner 該筆結算的贏家描述。
     * @param maxWidth 可用寬度。
     * @return 完整和牌牌組，或缺少描述／對應手牌時為 null。
     */
    fun winningHand(state: HistoryRoundStateDto, winner: HistoryWinnerDetailsDto, maxWidth: Float): HistoryTileGroupLayout? {
        val hand = winner.hand ?: return null
        val player = state.players.firstOrNull { it.initialSeatIndex == winner.seatIndex } ?: return null
        val available = player.handTiles + listOfNotNull(player.lastDrawn)
        if (!available.containsAll(hand.standingTiles)) return null
        val expectedStanding = available.toSet() - setOfNotNull(hand.winningTile)
        if (expectedStanding != hand.standingTiles.toSet()) return null
        val groups = listOf(HistoryTileGroupLayoutCalculator.hand(hand.standingTiles, hand.winningTile, maxWidth)) +
            player.melds.map { HistoryTileGroupLayoutCalculator.meld(it, maxWidth) }
        return combine(groups, maxWidth)
    }

    /**
     * 顯示保存順序的手牌，最近摸牌依實體索引分離且不重複。
     * @param player 已驗證的玩家桌況。
     * @param maxWidth 可用寬度。
     * @return 手牌幾何。
     */
    fun hand(player: HistoryReplayPlayerStateDto, maxWidth: Float): HistoryTileGroupLayout = HistoryTileGroupLayoutCalculator.hand(player.handTiles, player.lastDrawn, maxWidth)

    /**
     * 保留牌河槽位；帶有登記為橫擺的公開標記的牌橫置。
     * @param player 已驗證的玩家桌況。
     * @param maxWidth 可用寬度。
     * @param markerDisplays 牌河公開標記的呈現方式。
     * @return 包含已被取走槽位的牌河幾何。
     */
    fun discards(
        player: HistoryReplayPlayerStateDto,
        maxWidth: Float,
        markerDisplays: HistoryDiscardMarkerDisplayRegistry,
    ): HistoryTileGroupLayout = HistoryTileGroupLayoutCalculator.hand(
        player.discards.map { it.tile },
        maxWidth = maxWidth,
        sidewaysTiles = player.discards
            .filter { discard -> discard.markers.any { markerDisplays.find(it)?.sideways == true } }
            .map { it.tile }
            .toSet(),
    )

    /**
     * 組合完整群組，群組間保留間距並在必要時整組換行。
     * @param groups 依保存順序排列的群組。
     * @param maxWidth 可用寬度。
     * @return 所有群組的完整幾何。
     */
    fun combine(groups: List<HistoryTileGroupLayout>, maxWidth: Float): HistoryTileGroupLayout {
        var x = 0f
        var y = 0f
        var lineHeight = 0f
        var totalWidth = 0f
        val placements = mutableListOf<HistoryTilePlacement>()
        groups.filter { it.placements.isNotEmpty() }.forEach { group ->
            if (x > 0f && x + group.width > maxWidth) {
                x = 0f
                y += lineHeight + GROUP_GAP
                lineHeight = 0f
            }
            placements += group.placements.map { it.copy(x = it.x + x, y = it.y + y) }
            totalWidth = maxOf(totalWidth, x + group.width)
            lineHeight = maxOf(lineHeight, group.height)
            x += group.width + GROUP_GAP
        }
        return HistoryTileGroupLayout(placements, totalWidth, y + lineHeight)
    }

    /** 手牌與各副露群組的間距。 */
    private const val GROUP_GAP = 8f
}
