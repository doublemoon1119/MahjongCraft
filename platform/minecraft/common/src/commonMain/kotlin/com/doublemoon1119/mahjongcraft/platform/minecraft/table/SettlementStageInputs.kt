package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongMeldTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
import kotlin.uuid.Uuid

/**
 * 依 UUID 從所有權威牌區尋找牌面。
 *
 * 涵蓋四家手牌（含副露）、四家牌河、活牌牆與王牌區；不在桌上的牌張 ID 回傳 `null`。
 */
fun TableState.findTile(tileId: Uuid): IdentifiedTile? = players.asSequence()
    .flatMap { player -> (player.hand.allTiles + player.discardPile.entries.map { it.tile }).asSequence() }
    .plus(tileWall.getAllTiles().asSequence())
    .plus(reservedWallTiles.asSequence())
    .firstOrNull { it.id == tileId }

/**
 * 取得指定牌張的牌面資產。
 *
 * 結算舞台只拿得到牌張 ID，牌面內容要回權威桌況查；找不到的牌張直接略過，不佔一個沒有資產的項目。
 */
fun TableState.tileAssetKeysById(
    tileIds: Iterable<Uuid>,
    tileAssetRegistry: MinecraftTileAssetRegistry,
): Map<Uuid, String> = tileIds.distinct()
    .mapNotNull { tileId -> findTile(tileId)?.let { tileId to it.tile.toAssetKey(tileAssetRegistry) } }
    .toMap()

/**
 * 荒牌流局結算舞台需要、但只有權威桌況才算得出來的輸入。
 *
 * @property waitingTileAssetsBySeat 各座位聽牌的牌面資產，順序與請求中的等待牌相同。
 * @property revealedTileAssetsById 公開手牌的牌面資產。
 * @property reservedCornerWidthsBySeat 各座位桌角已被積棒與副露佔用的寬度，讓舞台避開這塊區域。
 */
data class ExhaustiveDrawSettlementStageInputs(
    val waitingTileAssetsBySeat: Map<Int, List<String>>,
    val revealedTileAssetsById: Map<Uuid, String>,
    val reservedCornerWidthsBySeat: Map<Int, Double>,
)

/**
 * 由結算請求與權威桌況組出舞台輸入。
 *
 * [tableState] 為 `null`（桌況已不存在）時仍會回傳聽牌資產——那份資料來自請求本身；另外兩份需要查詢
 * 桌況，因此為空。
 */
fun exhaustiveDrawSettlementStageInputs(
    request: ExhaustiveDrawSettlementPresentationRequest,
    tableState: TableState?,
    tileAssetRegistry: MinecraftTileAssetRegistry,
): ExhaustiveDrawSettlementStageInputs = ExhaustiveDrawSettlementStageInputs(
    waitingTileAssetsBySeat = request.players.associate { player ->
        player.ranking.seatIndex to player.waitingTiles.map { it.toAssetKey(tileAssetRegistry) }
    },
    revealedTileAssetsById = tableState
        ?.tileAssetKeysById(request.players.flatMap { it.revealedHandTileIds }, tileAssetRegistry)
        .orEmpty(),
    reservedCornerWidthsBySeat = tableState?.reservedCornerWidthsBySeat().orEmpty(),
)

/**
 * 各座位桌角已被佔用的寬度。
 *
 * 連莊棒只算在莊家身上；副露寬度依該座位目前的副露內容計算，暗槓是否翻開由規則設定決定。
 */
private fun TableState.reservedCornerWidthsBySeat(): Map<Int, Double> = players.mapIndexed { seatIndex, player ->
    val melds = player.hand.melds.map { meld ->
        val presentation = meld.toPresentation(config.revealsClosedKanTiles)
        MahjongMeldTileGroup(
            presentation.type,
            presentation.tileIds,
            presentation.calledTileId,
            presentation.sourceDirection,
            presentation.allTilesFaceDown,
        )
    }
    val comboStickCount = if (seatIndex == dealerIndex) comboCount else 0
    seatIndex to (MahjongTileTableLayout.stickAreaWidth(comboStickCount) + MahjongTileTableLayout.meldAreaWidth(melds))
}.toMap()
