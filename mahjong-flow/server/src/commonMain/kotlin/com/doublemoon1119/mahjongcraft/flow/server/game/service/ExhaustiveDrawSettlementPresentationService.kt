package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 將權威流局結算資料轉為平台呈現請求的應用服務。
 *
 * @property presentationPublisher 發布流局結算呈現請求。
 */
@Factory
class ExhaustiveDrawSettlementPresentationService(
    @Provided private val presentationPublisher: GamePresentationPublisher,
) {
    /**
     * 建立並發布一次統一流局結算呈現；[aiPlayerIds] 為由 AI 操控的玩家。
     *
     * @param gameId 對局識別碼。
     * @param previousState 結算前的權威桌況。
     * @param currentState 結算後的權威桌況。
     * @param module 本局使用的規則模組。
     * @param aiPlayerIds 由 AI 操控的玩家識別碼。
     * @param reason 本次流局原因。
     * @param tenpaiPlayerIds 一般荒牌流局的聽牌玩家；途中流局時為 null。
     * @param revealedHands 規則判定可公開的手牌結算資料。
     */
    fun publish(
        gameId: Uuid,
        previousState: TableState,
        currentState: TableState,
        module: MahjongRuleModule<*>,
        aiPlayerIds: Set<Uuid>,
        reason: ExhaustiveDrawReason,
        tenpaiPlayerIds: Set<Uuid>?,
        revealedHands: List<RevealedHandSettlement>,
    ) {
        presentationPublisher.publishExhaustiveDrawSettlement(
            gameId,
            ExhaustiveDrawSettlementPresentationRequestFactory.create(
                previousState = previousState,
                currentState = currentState,
                module = module,
                aiPlayerIds = aiPlayerIds,
                reason = reason,
                tenpaiPlayerIds = tenpaiPlayerIds,
                revealedHands = revealedHands,
            ),
        )
    }
}
