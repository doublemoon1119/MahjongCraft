package com.doublemoon1119.mahjongcraft.platform.fabric.server.network

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ExhaustiveDrawSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundResultKindDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundResultPayloadDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundResultPlayerDto
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 在和牌或流局結算當下，把結算前後的分數與名次送給入座的真人玩家。
 *
 * 數值取自結算呈現請求的 [ScoreRankingPresentation]，由伺服器依結算前後的權威狀態決定；client 不需要比對自己保存的快照。
 *
 * @property serverHolder 查找目前有效的收件玩家。
 * @property stateStore 查詢對局目前的規則設定。
 * @property moduleRegistry 由規則設定取得規則模組 ID。
 * @property json 線路序列化設定。
 */
@Single
class RoundResultSender(
    private val serverHolder: FabricServerHolder,
    private val stateStore: AuthoritativeStateStore,
    private val moduleRegistry: MahjongModuleRegistry,
    private val json: Json,
) {
    /** 記錄找不到對局而沒有送出的結算。 */
    private val logger = mahjongCraftLogger(RoundResultSender::class)

    /**
     * 送出一次和牌結算。
     *
     * @param gameId 對局。
     * @param request 和牌結算呈現請求。
     * @param roundContinues 本局是否在這次和牌之後仍然繼續。
     */
    fun sendWin(gameId: Uuid, request: WinSettlementPresentationRequest, roundContinues: Boolean) = send(
        gameId = gameId,
        outcomeId = request.outcomeId,
        kind = RoundResultKindDto.WIN,
        roundContinues = roundContinues,
        ranking = request.ranking,
    )

    /**
     * 送出一次流局結算，包含途中流局。
     *
     * @param gameId 對局。
     * @param request 流局結算呈現請求。
     */
    fun sendDraw(gameId: Uuid, request: ExhaustiveDrawSettlementPresentationRequest) = send(
        gameId = gameId,
        outcomeId = request.reasonId,
        kind = RoundResultKindDto.DRAW,
        roundContinues = false,
        ranking = request.scoreRanking,
    )

    /** 組成結算結果並送給 [ranking] 中的真人玩家；對局已不存在時不送出。 */
    private fun send(
        gameId: Uuid,
        outcomeId: String,
        kind: RoundResultKindDto,
        roundContinues: Boolean,
        ranking: ScoreRankingPresentation,
    ) {
        val game = stateStore.state.value.games[gameId]
        if (game == null) {
            logger.debug("Round result for gameId={} not sent: the game no longer exists", gameId)
            return
        }
        val payload = roundResultPayload(
            gameId = gameId,
            ruleModuleId = moduleRegistry.getModule(game.tableState.config).id,
            outcomeId = outcomeId,
            kind = kind,
            roundContinues = roundContinues,
            ranking = ranking,
        )
        roundResultRecipients(ranking).forEach { playerId ->
            serverHolder.findPlayer(playerId)?.let { player -> MahjongChannels.roundResult.sendTo(player, json, payload) }
        }
    }
}

/**
 * 由結算排行組成送給 client 的結算結果。
 *
 * @param gameId 對局。
 * @param ruleModuleId 對局規則模組 ID。
 * @param outcomeId 和牌結果 ID 或流局原因 ID。
 * @param kind 結算是和牌還是流局。
 * @param roundContinues 本局是否在這次和牌之後仍然繼續。
 * @param ranking 結算前後的分數與名次。
 * @return 依固定座位順序排列玩家的結算結果。
 */
internal fun roundResultPayload(
    gameId: Uuid,
    ruleModuleId: String,
    outcomeId: String,
    kind: RoundResultKindDto,
    roundContinues: Boolean,
    ranking: ScoreRankingPresentation,
): RoundResultPayloadDto = RoundResultPayloadDto(
    gameId = gameId.toString(),
    ruleModuleId = ruleModuleId,
    outcomeId = outcomeId,
    kind = kind,
    roundContinues = roundContinues,
    players = ranking.players.sortedBy { it.seatIndex }.map { player ->
        RoundResultPlayerDto(
            playerId = player.playerId.toString(),
            seatIndex = player.seatIndex,
            isAi = player.isAi,
            previousScore = player.previousScore,
            currentScore = player.currentScore,
            previousRank = player.previousRank,
            currentRank = player.currentRank,
        )
    },
)

/** 結算結果的收件玩家：[ranking] 中不由 AI 操控的玩家。 */
internal fun roundResultRecipients(ranking: ScoreRankingPresentation): List<Uuid> = ranking.players.filterNot { it.isAi }.map { it.playerId }
