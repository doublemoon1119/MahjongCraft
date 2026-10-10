package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import kotlin.uuid.Uuid

/**
 * 荒牌流局結算舞台需要、但只有權威桌況才算得出來的輸入。
 *
 * @property waitingTileAssetsBySeat 各座位聽牌的牌面資產，順序與請求中的等待牌相同。
 * @property revealedTileAssetsById 公開手牌的牌面資產。
 * @property reservedCornerWidthsBySeat 各座位桌角已被角落物件與副露佔用的寬度，讓舞台避開這塊區域。
 */
data class ExhaustiveDrawSettlementStageInputs(
    val waitingTileAssetsBySeat: Map<Int, List<String>>,
    val revealedTileAssetsById: Map<Uuid, String>,
    val reservedCornerWidthsBySeat: Map<Int, Double>,
)
