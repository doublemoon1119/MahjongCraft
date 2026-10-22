package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import kotlin.uuid.Uuid

/**
 * 供 [GamePresentationPublisher.publishPlayerTilesUpdated] 使用的單一副露資料。
 *
 * 只帶擺放副露需要的資訊（種類、牌 ID、鳴取的牌與來源方位、逐牌朝向），不帶 [Meld] 的牌面；牌面由各玩家的可見性快照決定。
 *
 * @property type 副露種類。
 * @property tileIds 組成這組副露的所有牌 ID，維持成立時保存的展示順序；加槓補上的第四張固定排在最後。鳴取的牌由 [calledTileId]
 * 指出，不靠位置辨識。
 * @property calledTileId 鳴取自他家的牌 ID；暗槓沒有鳴牌來源時為 `null`。
 * @property sourceDirection 鳴取來源的相對方位；暗槓沒有鳴牌來源時為 [RelativeDirection.Self]。
 * @property allTilesFaceDown 整組牌是否都朝下擺放，不代表觀察者是否知道牌面。
 * @property faceDownTileIds 需要在桌面上朝下擺放的牌張 ID。這與牌面資訊是否公開是分開的概念。
 */
data class MeldPresentation(
    val type: MeldType,
    val tileIds: List<Uuid>,
    val calledTileId: Uuid?,
    val sourceDirection: RelativeDirection,
    val allTilesFaceDown: Boolean,
    val faceDownTileIds: Set<Uuid>,
)
