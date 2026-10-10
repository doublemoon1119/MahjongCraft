package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import kotlin.uuid.Uuid

/**
 * 供 [GamePresentationPublisher.publishPlayerTilesUpdated] 使用的單一副露資料。
 *
 * 只帶擺放副露需要的資訊（種類、牌 ID、鳴取的牌與來源方位），不帶 [Meld] 的牌面；牌面由各玩家的可見性快照決定。
 *
 * @property type 副露種類。
 * @property tileIds 組成這組副露的所有牌 ID，依規則牌序排列；加槓補上的第四張固定排在最後。鳴取的牌由 [calledTileId]
 * 指出，不靠位置辨識。
 * @property calledTileId 鳴取自他家的牌 ID；暗槓沒有鳴牌來源時為 `null`。
 * @property sourceDirection 鳴取來源的相對方位；暗槓沒有鳴牌來源時為 [RelativeDirection.Self]。
 * @property allTilesFaceDown 只在 [type] 為 [MeldType.CLOSED_KAN] 時有意義：規則是否連暗槓的牌都不公開；公開暗槓牌的規則
 * （例如日麻）為 `false`。其餘副露種類固定為 `false`。
 */
data class MeldPresentation(
    val type: MeldType,
    val tileIds: List<Uuid>,
    val calledTileId: Uuid?,
    val sourceDirection: RelativeDirection,
    val allTilesFaceDown: Boolean,
)
