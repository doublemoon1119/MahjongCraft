package com.doublemoon1119.mahjongcraft.flow.server.game.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareAndDiscardUseCase
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_GAME_ACTION
import kotlin.uuid.Uuid

/**
 * 日麻立直宣告的薄適配器。
 *
 * 日麻規則模組提供立直效果，驗證、宣告捨牌、反應判定、歷史與呈現由共用用例處理。
 *
 * @property declareAndDiscardUseCase 宣告與捨牌的共用權威流程。
 */
class DeclareRiichiUseCase(
    private val declareAndDiscardUseCase: DeclareAndDiscardUseCase,
) {
    /**
     * 執行日麻立直宣告及宣告牌捨出。
     *
     * @param gameId 對局識別碼。
     * @param playerId 宣告玩家識別碼。
     * @param tileId 宣告牌的實體識別碼。
     * @return 成功時為 Unit，失敗時回傳流程錯誤。
     */
    suspend operator fun invoke(gameId: Uuid, playerId: Uuid, tileId: Uuid): Outcome<Unit, GameError> = declareAndDiscardUseCase(
        gameId = gameId,
        playerId = playerId,
        action = RIICHI_GAME_ACTION,
        tileId = tileId,
    )
}
