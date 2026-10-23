package com.doublemoon1119.mahjongcraft.logic.table

import kotlin.uuid.Uuid

/**
 * 摸牌後自動處理暫停在搶和視窗時，還沒詢問過規則的一張摸到的牌。
 *
 * @property tileId 這張牌。
 * @property source 摸到這張牌的來源；繼續處理時照原樣交給規則。
 */
data class PendingFollowUpDraw(
    val tileId: Uuid,
    val source: DrawSource,
)
