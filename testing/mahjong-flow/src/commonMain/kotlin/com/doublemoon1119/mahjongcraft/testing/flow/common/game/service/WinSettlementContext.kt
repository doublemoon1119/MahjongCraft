package com.doublemoon1119.mahjongcraft.testing.flow.common.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher

/**
 * [FakeGamePresentationPublisher] 紀錄的單一筆 [GamePresentationPublisher.publishWinSettlement] 呼叫資料。
 *
 * @property request 胡牌結算呈現請求。
 */
data class WinSettlementContext(val request: WinSettlementPresentationRequest)
