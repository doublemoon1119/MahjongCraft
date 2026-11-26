package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata

/** 規則中立的 round outcome 完整識別碼。 */
object BuiltInRoundOutcomeIds {
    /** 一般自摸。 */
    val TSUMO: String = MahjongCraftMetadata.id("tsumo")

    /** 一般榮和（含搶槓）。 */
    val RON: String = MahjongCraftMetadata.id("ron")
}
