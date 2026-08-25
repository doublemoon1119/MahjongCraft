package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/** 將權威流局結算資料轉為平台呈現請求的應用服務。 */
@Factory
class RoundSettlementPresentationService(
    @Provided private val presentationPublisher: GamePresentationPublisher,
) {
    /** 建立並發布一次統一流局結算呈現。 */
    fun publish(
        gameId: Uuid,
        previousState: TableState,
        currentState: TableState,
        module: MahjongRuleModule<*>,
        reason: ExhaustiveDrawReason,
        tenpaiPlayerIds: Set<Uuid>?,
        revealedHands: List<RevealedHandSettlement>,
    ) {
        presentationPublisher.publishRoundSettlement(
            gameId,
            RoundSettlementPresentationRequestFactory.create(
                previousState = previousState,
                currentState = currentState,
                module = module,
                reason = reason,
                tenpaiPlayerIds = tenpaiPlayerIds,
                revealedHands = revealedHands,
            ),
        )
    }
}
