package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.uuid.Uuid

/**
 * 荒牌流局結算舞台需要、但只有權威桌況才算得出來的輸入。
 *
 * @property waitingTileAssetsBySeat 各座位聽牌的牌面資產，順序與請求中的等待牌相同。
 * @property revealedTileAssetsById 公開手牌的牌面資產。
 * @property handShiftsBySeat 各座位立牌整排往玩家左手邊平移的距離（負值往右手邊）；桌況已不存在時為空。
 */
data class ExhaustiveDrawSettlementStageInputs(
    val waitingTileAssetsBySeat: Map<Int, List<String>>,
    val revealedTileAssetsById: Map<Uuid, String>,
    val handShiftsBySeat: Map<Int, Double>,
)
