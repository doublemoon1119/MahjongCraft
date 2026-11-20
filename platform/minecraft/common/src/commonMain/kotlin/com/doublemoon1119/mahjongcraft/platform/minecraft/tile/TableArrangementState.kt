package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import kotlin.uuid.Uuid

/**
 * 整桌擺牌需要的桌況：每一區有哪些牌、牌牆格位與打牌順序，不含規則設定與分數等其他內容。
 *
 * 對局中由權威桌況轉出，回放由牌譜保存的桌況轉出，兩者用同一套擺牌計算。
 *
 * @property players 依座位排列的玩家牌區。
 * @property wallTileIds 牌牆的牌，依牌序排列。
 * @property reservedTileIds 保留區的牌，依牌序排列。
 * @property wallPlacements 牌牆與保留區的牌在實體牌牆中的格位；規則沒有實體牌牆時為 null。
 * @property dealerIndex 莊家座位。
 * @property discardOrder 全桌依打出先後排列的捨牌，包含已被鳴走的牌。
 * @property scatterPatternSeed 散放牌河這一局使用的位置表種子；牌河不散放時不使用。
 */
data class TableArrangementState(
    val players: List<Player>,
    val wallTileIds: List<Uuid>,
    val reservedTileIds: List<Uuid>,
    val wallPlacements: Map<Uuid, TileWallPlacement>?,
    val dealerIndex: Int,
    val discardOrder: List<Uuid>,
    val scatterPatternSeed: Int,
) {
    /**
     * 一位玩家桌前的牌。
     *
     * @property id 玩家識別碼，用來區分各家的牌列。
     * @property standingTileIds 立牌，依目前順序排列。
     * @property drawnTileId 摸牌位的牌；沒有時為 null。
     * @property melds 副露，依宣告順序排列。
     * @property setAsideTiles 移出手牌的牌與所屬區域識別碼，依區域與放入順序排列。
     * @property discards 牌河，依打出順序排列，包含已被鳴走的牌。
     * @property sidewaysMarkedTileId 牌河中要橫放的牌；沒有時為 null。
     */
    data class Player(
        val id: Uuid,
        val standingTileIds: List<Uuid>,
        val drawnTileId: Uuid?,
        val melds: List<MahjongMeldTileGroup>,
        val setAsideTiles: List<SetAsideTile>,
        val discards: List<Discard>,
        val sidewaysMarkedTileId: Uuid?,
    )

    /**
     * 一張移出手牌的牌。
     *
     * @property tileId 牌。
     * @property areaId 所屬區域識別碼。
     */
    data class SetAsideTile(val tileId: Uuid, val areaId: String)

    /**
     * 牌河中的一張牌。
     *
     * @property tileId 牌。
     * @property isTaken 是否已被鳴走；鳴走的牌不擺在牌河，但散放位置仍依它計算。
     */
    data class Discard(val tileId: Uuid, val isTaken: Boolean)
}
