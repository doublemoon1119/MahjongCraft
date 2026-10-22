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
 *
 * @param tileId 要查詢的牌張 UUID。
 * @return 找到的權威牌資料；不在桌上時為 `null`。
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
 *
 * @param tileIds 要查詢的牌張 UUID。
 * @param tileAssetRegistry 牌種與素材 key 對照 registry。
 * @return 找得到的牌張 UUID 對應素材 key。
 */
fun TableState.tileAssetKeysById(
    tileIds: Iterable<Uuid>,
    tileAssetRegistry: MinecraftTileAssetRegistry,
): Map<Uuid, String> = tileIds.distinct()
    .mapNotNull { tileId -> findTile(tileId)?.let { tileId to it.tile.toAssetKey(tileAssetRegistry) } }
    .toMap()

/**
 * 由結算請求與權威桌況組出舞台輸入。
 *
 * [tableState] 為 `null`（桌況已不存在）時仍會回傳聽牌資產——那份資料來自請求本身；另外兩份需要查詢
 * 桌況，因此為空。
 *
 * @param cornerWidthsBySeat 各座位副露角落被規則桌面物件佔用的寬度；沒有列出的座位視為 `0.0`。
 * @param request 權威結算請求。
 * @param tableState 目前權威桌況；已不存在時傳 `null`。
 * @param tileAssetRegistry 牌種與素材 key 對照 registry。
 * @return 可用於建立結算舞台的分座位牌面與角落寬度輸入。
 */
fun exhaustiveDrawSettlementStageInputs(
    request: ExhaustiveDrawSettlementPresentationRequest,
    tableState: TableState?,
    cornerWidthsBySeat: Map<Int, Double>,
    tileAssetRegistry: MinecraftTileAssetRegistry,
): ExhaustiveDrawSettlementStageInputs = ExhaustiveDrawSettlementStageInputs(
    waitingTileAssetsBySeat = request.players.associate { player ->
        player.ranking.seatIndex to player.waitingTiles.map { it.toAssetKey(tileAssetRegistry) }
    },
    revealedTileAssetsById = tableState
        ?.tileAssetKeysById(request.players.flatMap { it.revealedHandTileIds }, tileAssetRegistry)
        .orEmpty(),
    reservedCornerWidthsBySeat = tableState?.reservedCornerWidthsBySeat(cornerWidthsBySeat).orEmpty(),
)

/**
 * 各座位桌角已被佔用的寬度。
 *
 * 角落物件寬度由呼叫端提供；副露寬度依該座位目前的副露內容計算，暗槓是否翻開由規則設定決定。
 *
 * @param cornerWidthsBySeat 各座位桌面物件佔用的角落寬度。
 * @return 各座位桌面物件與副露所佔用的總寬度。
 */
private fun TableState.reservedCornerWidthsBySeat(cornerWidthsBySeat: Map<Int, Double>): Map<Int, Double> = players.mapIndexed { seatIndex, player ->
    val melds = player.hand.melds.map { meld ->
        val presentation = meld.toPresentation(config.revealsClosedKanTiles)
        MahjongMeldTileGroup(
            presentation.type,
            presentation.tileIds,
            presentation.calledTileId,
            presentation.sourceDirection,
            presentation.allTilesFaceDown,
            presentation.faceDownTileIds,
        )
    }
    seatIndex to MahjongTileTableLayout.cornerAreaWidth(cornerWidthsBySeat[seatIndex] ?: 0.0, melds)
}.toMap()
