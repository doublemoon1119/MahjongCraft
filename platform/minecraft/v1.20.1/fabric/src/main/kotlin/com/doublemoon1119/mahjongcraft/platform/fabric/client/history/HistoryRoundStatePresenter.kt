package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayPlayerStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryWinnerDetailsDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.MeldTypeDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.RelativeDirectionDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import net.minecraft.text.Text
import net.minecraft.text.Texts

/** 將已驗證歷史桌況轉為共用牌組幾何，不重排或重新判定規則。 */
internal object HistoryRoundStatePresenter {
    /**
     * 取得保存當下的和牌手牌（立牌與分開的和牌張），不含副露；立牌已失去對應時不猜測原手牌。
     *
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
        return HistoryTileGroupLayoutCalculator.hand(hand.standingTiles, hand.winningTile, maxWidth)
    }

    /**
     * 玩家擺在自己桌前的公開牌組成的一列：先是各組副露，後接各個移出牌區，例如花牌；各組間保留間距，放不下時整組換行。
     *
     * 同時記下每組副露被鳴的牌來自哪個座位，以及加槓補上的牌。
     *
     * @param state 已驗證的交易後桌況。
     * @param player 擁有這些牌的玩家。
     * @param maxWidth 可用寬度。
     * @return 公開牌組與標記；沒有副露也沒有移出牌時為 null。
     */
    fun exposedTiles(
        state: HistoryRoundStateDto,
        player: HistoryReplayPlayerStateDto,
        maxWidth: Float,
    ): HistoryExposedTiles? {
        val areas = player.tileAreas.filter { it.tiles.isNotEmpty() }
        if (player.melds.isEmpty() && areas.isEmpty()) return null
        val groups = player.melds.map { HistoryTileGroupLayoutCalculator.meld(it, maxWidth) } +
            areas.map { HistoryTileGroupLayoutCalculator.hand(it.tiles, maxWidth = maxWidth) }
        val claimedFromSeat = player.melds.mapNotNull { meld ->
            val tile = meld.sourceTile ?: return@mapNotNull null
            val seat = sourceSeat(state, player, meld.sourceDirection ?: return@mapNotNull null) ?: return@mapNotNull null
            tile to seat
        }.toMap()
        val addedKanTiles = player.melds
            .filter { it.type == MeldTypeDto.AddedKan && it.tiles.size == ADDED_KAN_TILE_COUNT }
            .mapTo(mutableSetOf()) { it.tiles.last() }
        return HistoryExposedTiles(
            layout = combine(groups, maxWidth),
            hasMelds = player.melds.isNotEmpty(),
            areaIds = areas.map { it.areaId },
            claimedFromSeat = claimedFromSeat,
            addedKanTiles = addedKanTiles,
        )
    }

    /**
     * 牌河中被鳴走的牌對應鳴走它的玩家：被鳴走的牌必定是某組副露記錄的被鳴牌，副露的擁有者就是鳴走它的人。
     *
     * @param state 已驗證的交易後桌況。
     * @return 被鳴走的牌索引對應鳴走它的玩家初始座位。
     */
    fun takenBySeat(state: HistoryRoundStateDto): Map<Int, Int> = state.players
        .flatMap { player -> player.melds.mapNotNull { meld -> meld.sourceTile?.let { it to player.initialSeatIndex } } }
        .toMap()

    /**
     * 依保存的桌上順序，找出相對於 [player] 位於 [direction] 的玩家初始座位。
     *
     * @param state 已驗證的交易後桌況。
     * @param player 基準玩家。
     * @param direction 相對方位。
     * @return 該玩家的初始座位；自己或人數不支援該方位時為 null。
     */
    private fun sourceSeat(
        state: HistoryRoundStateDto,
        player: HistoryReplayPlayerStateDto,
        direction: RelativeDirectionDto,
    ): Int? {
        val count = state.players.size
        val ownerIndex = state.players.indexOfFirst { it.initialSeatIndex == player.initialSeatIndex }
        if (ownerIndex < 0) return null
        val offset = when (direction) {
            RelativeDirectionDto.Left -> count - 1
            RelativeDirectionDto.Right -> 1
            RelativeDirectionDto.Across -> if (count >= ACROSS_MIN_PLAYERS) 2 else return null
            RelativeDirectionDto.Self -> return null
        }
        return state.players[(ownerIndex + offset) % count].initialSeatIndex
    }

    /**
     * 玩家卡片中的手牌：贏家的和牌張分離顯示，榮和時和牌張取自和牌描述；其他玩家的最近摸牌分離顯示。
     *
     * 和牌描述與保存手牌對不上時不猜測，改為一般手牌。
     *
     * @param state 已驗證的交易後桌況。
     * @param player 卡片對應的玩家。
     * @param orderedTiles 依目前排序方式排列的立牌，不含最近摸牌。
     * @param maxWidth 可用寬度。
     * @return 手牌幾何。
     */
    fun cardHand(
        state: HistoryRoundStateDto,
        player: HistoryReplayPlayerStateDto,
        orderedTiles: List<Int>,
        maxWidth: Float,
    ): HistoryTileGroupLayout {
        val winningTile = state.outcome?.winnerDetails
            ?.firstOrNull { it.seatIndex == player.initialSeatIndex }
            ?.takeIf { winningHand(state, it, maxWidth) != null }
            ?.hand
            ?.winningTile
        val separated = winningTile ?: player.lastDrawn
        val tiles = orderedTiles + listOfNotNull(player.lastDrawn?.takeIf { it != separated })
        return HistoryTileGroupLayoutCalculator.hand(tiles, separated, maxWidth)
    }

    /**
     * 顯示保存順序的手牌，最近摸牌依實體索引分離且不重複。
     *
     * @param player 已驗證的玩家桌況。
     * @param maxWidth 可用寬度。
     * @return 手牌幾何。
     */
    fun hand(player: HistoryReplayPlayerStateDto, maxWidth: Float): HistoryTileGroupLayout = HistoryTileGroupLayoutCalculator.hand(player.handTiles, player.lastDrawn, maxWidth)

    /**
     * 保留牌河槽位；帶有登記為橫擺的公開標記的牌橫置。
     *
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
        tiles = player.discards.map { it.tile },
        maxWidth = maxWidth,
        sidewaysTiles = player.discards
            .filter { discard -> discard.markers.any { markerDisplays.find(it)?.sideways == true } }
            .map { it.tile }
            .toSet(),
    )

    /**
     * 組合完整群組，群組間保留間距並在必要時整組換行。
     *
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

/**
 * 玩家桌前公開牌的牌組與需要另外說明的牌。
 *
 * @property layout 副露與移出牌區依序組成的牌組。
 * @property hasMelds 是否有副露。
 * @property areaIds 牌組中有牌的移出牌區，依顯示順序。
 * @property claimedFromSeat 被鳴的牌對應打出那張牌的玩家初始座位。
 * @property addedKanTiles 加槓補上的牌。
 */
internal data class HistoryExposedTiles(
    val layout: HistoryTileGroupLayout,
    val hasMelds: Boolean,
    val areaIds: List<String>,
    val claimedFromSeat: Map<Int, Int>,
    val addedKanTiles: Set<Int>,
) {
    /**
     * 這一列的標題，依實際內容列出「副露」與各移出牌區的名稱。
     *
     * @param areaNameKey 依移出牌區識別碼取得名稱的翻譯 key；沒有登記時為 null。
     * @return 以間隔號連接的標題。
     */
    fun heading(areaNameKey: (String) -> String?): Text {
        val parts = listOfNotNull(Text.translatable(MinecraftHistoryScreenKeys.STATE_MELDS).takeIf { hasMelds }) +
            areaIds.map { Text.translatable(areaNameKey(it) ?: MinecraftHistoryScreenKeys.STATE_SET_ASIDE) }
        return Texts.join(parts, Text.literal(" · "))
    }

    /**
     * 各牌的說明：被鳴的牌來自哪位玩家，以及加槓補上的牌。
     *
     * @param nameOf 依初始座位取得玩家名稱。
     * @return 牌索引對應的說明。
     */
    fun notes(nameOf: (Int) -> Text): Map<Int, List<HistoryTileNote>> = buildMap<Int, MutableList<HistoryTileNote>> {
        claimedFromSeat.forEach { (tile, seat) ->
            getOrPut(tile) { mutableListOf() } += HistoryTileNote(
                kind = HistoryTileNoteKind.CLAIMED_FROM,
                text = Text.translatable(MinecraftHistoryScreenKeys.STATE_MELD_CLAIMED_FROM, nameOf(seat)),
            )
        }
        addedKanTiles.forEach { tile ->
            getOrPut(tile) { mutableListOf() } += HistoryTileNote(HistoryTileNoteKind.ADDED_KAN, Text.translatable(MinecraftHistoryScreenKeys.STATE_MELD_ADDED_KAN))
        }
    }
}

/** 加槓完整牌數；少於此數的保存資料不標示加槓補上的牌。 */
private const val ADDED_KAN_TILE_COUNT = 4

/** 有對家的最少人數。 */
private const val ACROSS_MIN_PLAYERS = 4
