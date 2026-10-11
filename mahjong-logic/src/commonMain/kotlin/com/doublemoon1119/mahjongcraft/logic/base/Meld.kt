package com.doublemoon1119.mahjongcraft.logic.base

/**
 * 代表玩家的副露（鳴牌組合）。
 *
 * 副露是指玩家透過鳴取他人的捨牌或宣告自己手中的槓牌，而將特定的牌組公開放置在桌面上的行為。
 * 在不同規則中，副露的呈現方式與對勝負的影響（如役種、番數）各有不同。
 *
 * @property type 副露的種類，例如 [MeldType.CHI] 或 [MeldType.PON]。
 * @property tiles 組成該副露的所有 [IdentifiedTile]。
 * @property sourceTile 從其他玩家處鳴取而來的特定牌。若為暗槓 ([MeldType.CLOSED_KAN]) 則通常為 null。
 * @property sourceDirection 鳴取來源的相對方位。
 */
data class Meld(
    val type: MeldType,
    val tiles: List<IdentifiedTile>,
    val sourceTile: IdentifiedTile? = null,
    val sourceDirection: RelativeDirection,
)
