package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import kotlin.uuid.Uuid

/**
 * 單一副露的呈現資料，只帶位置呈現需要的最小資訊，不攜帶實際牌面——管理中牌張的牌面完全交給 client
 * 端依可見性快照另外呈現，平台呈現層只需要知道怎麼擺位置。
 *
 * @property type 副露種類，決定是否有需要側身呈現的鳴取牌。
 * @property tileIds 組成這組副露的所有牌 Uuid，依宣告時的原始順序排列。
 * @property calledTileId [tileIds] 之中鳴取自他家、需側身呈現的那張牌 Uuid；暗槓沒有鳴牌來源時為
 * `null`。
 * @property sourceDirection 鳴取來源的相對方位，決定 [calledTileId] 在組內橫放的位置（左／中／右）；
 * 暗槓沒有鳴牌來源時為 [RelativeDirection.Self]。
 * @property allTilesFaceDown 整組牌是否都朝下擺放；牌面資訊能否查看由可見性快照決定。
 * @property faceDownTileIds 需要朝下呈現的牌張 ID；與牌面資訊是否公開分開處理。
 */
data class MahjongMeldTileGroup(
    val type: MeldType,
    val tileIds: List<Uuid>,
    val calledTileId: Uuid?,
    val sourceDirection: RelativeDirection,
    val allTilesFaceDown: Boolean,
    val faceDownTileIds: Set<Uuid>,
)
