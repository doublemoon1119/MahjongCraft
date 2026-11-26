package com.doublemoon1119.mahjongcraft.testing.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import kotlin.uuid.Uuid

/**
 * 一次胡牌慶祝演出呼叫的紀錄。
 *
 * @property request 胡牌慶祝演出請求。
 */
data class WinCelebrationContext(val request: WinCelebrationRequest) {

    /** 共享胡牌張。 */
    val winningTileId: Uuid get() = request.winningTileId

    /** 是否為自摸。 */
    val isTsumo: Boolean get() = request.isTsumo
}
