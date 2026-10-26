package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.achievement

import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchCompletion
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEndedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettledEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettlementKind
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundWinDetail
import com.doublemoon1119.mahjongcraft.flow.api.event.RuleResultsResolvedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.WinGroupSource
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.RiichiGameEventPublicMarkerIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.BuiltInMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolver
import kotlin.uuid.Uuid

/**
 * 內建日麻的專屬成果判定。
 *
 * 役種與番數讀取日麻胡牌詳情欄位（[RiichiWinSettlementIds]）；立直與一發讀取和牌公開狀態標記。
 * 四人與三人日麻共用同一批成果。
 *
 * @property ruleModuleId 套用的日麻系列規則模組。
 */
class RiichiGameAchievementResolver(
    override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI,
) : GameAchievementResolver {

    override fun resolve(batch: CommittedMatchEventBatch): Map<Uuid, Set<String>> {
        val achievements = mutableMapOf<Uuid, MutableSet<String>>()
        fun add(playerId: Uuid, ids: Collection<String>) {
            if (ids.isNotEmpty()) achievements.getOrPut(playerId) { linkedSetOf() } += ids
        }
        batch.events.forEach { event ->
            when (event) {
                is RoundSettledEvent -> {
                    if (event.kind == RoundSettlementKind.WIN) {
                        event.winGroups
                            .filter { it.source == WinGroupSource.WIN_SETTLEMENT }
                            .forEach { group -> group.winners.forEach { winner -> add(winner.playerId, winAchievements(winner)) } }
                    }
                }

                is RuleResultsResolvedEvent ->
                    event.results
                        .filter { it.reasonId == RiichiRoundOutcomeIds.NAGASHI_MANGAN }
                        .forEach { result ->
                            val achieverIds = result.winDetails.map { it.playerId }
                                .ifEmpty { result.completion?.beneficiaryPlayerIds.orEmpty().toList() }
                            achieverIds.forEach { add(it, listOf(RiichiAchievementIds.NAGASHI_MANGAN)) }
                        }
                is MatchEndedEvent -> {
                    if (event.completion == MatchCompletion.COMPLETED) {
                        event.completedPlayerIds.forEach { add(it, listOf(RiichiAchievementIds.MATCH_COMPLETED)) }
                        val threshold = event.publicMarkers.firstOrNull {
                            it.id == RiichiGameEventPublicMarkerIds.BUST_THRESHOLD &&
                                it.quantity?.unitId == RiichiGameEventPublicMarkerIds.POINTS_UNIT
                        }?.quantity?.amount
                        val busted = event.publicMarkers.filter { it.id == RiichiGameEventPublicMarkerIds.BUSTED }.mapNotNull { it.playerId }
                        val scores = event.completedScores
                        if (event.reasonId == BuiltInMatchEndReasonIds.PLAYER_BUSTED) {
                            busted.forEach { playerId ->
                                val score = scores[playerId] ?: return@forEach
                                if (threshold != null && score >= threshold) return@forEach
                                add(playerId, bustAchievements(score, event))
                            }
                        }
                    }
                }
                else -> Unit
            }
        }
        return achievements
    }

    /** 一位贏家這次和牌的日麻成果。 */
    private fun winAchievements(details: RoundWinDetail): List<String> = buildList {
        val yaku = details.yakuTypes()
        val isNaturalYakuman = details.detailFields.any { it.id == RiichiWinSettlementIds.YAKUMAN_TOTAL_FIELD }
        val isRiichi = YakuType.Riichi in yaku || YakuType.DoubleRiichi in yaku || details.hasMarker(RiichiGameEventPublicMarkerIds.RIICHI)
        if (isRiichi) add(RiichiAchievementIds.RIICHI_WIN)
        if (YakuType.Ippatsu in yaku || (isNaturalYakuman && details.hasMarker(RiichiGameEventPublicMarkerIds.RIICHI) && details.hasMarker(RiichiGameEventPublicMarkerIds.IPPATSU))) {
            add(RiichiAchievementIds.IPPATSU)
        }
        if (isNaturalYakuman) {
            val yakuman = details.yakumanMultipliers()
            if (yakuman.isNotEmpty()) add(RiichiAchievementIds.YAKUMAN)
            yakuman.keys.mapNotNullTo(this, RiichiAchievementIds::yakuman)
            if (yakuman.values.any { it >= 2 }) add(RiichiAchievementIds.DOUBLE_YAKUMAN)
            if (yakuman.size >= 2) add(RiichiAchievementIds.MULTIPLE_YAKUMAN)
        } else {
            val han = details.totalHan() ?: return@buildList
            when {
                han == COUNTED_YAKUMAN_HAN -> add(RiichiAchievementIds.COUNTED_YAKUMAN_EXACT)
                han > COUNTED_YAKUMAN_HAN -> add(RiichiAchievementIds.COUNTED_YAKUMAN_OVER)
            }
        }
    }

    /** 被擊飛玩家的成果；最終點數未低於擊飛門檻的玩家沒有成果。 */
    private fun bustAchievements(score: Int, event: MatchEndedEvent): List<String> = buildList {
        add(RiichiAchievementIds.BUSTED)
        if (score <= RiichiAchievementIds.FAR_BUST_SCORE) add(RiichiAchievementIds.BUSTED_FAR)
        val context = event.roundContext
        if (context?.prevalentWindId == "mahjongcraft:wind/east" && context.roundNumber == 1) {
            add(RiichiAchievementIds.BUSTED_IN_EAST_ONE)
        }
    }

    /** 詳情中的役種；無法辨識的條目略過。 */
    private fun RoundWinDetail.yakuTypes(): List<YakuType> = detailFields
        .filter { it.id == RiichiWinSettlementIds.YAKU_FIELD }
        .flatMap { it.entries.orEmpty() }
        .mapNotNull { entry -> RiichiWinSettlementIds.yakuType(entry.id) }

    /**
     * 詳情中的役滿役種與倍數，古役役滿也包含在內；沒有個別進度的役種只計入役滿、雙倍役滿與複合役滿進度。
     *
     * @return 役滿役種對應的役滿倍數；無法辨識的條目略過。
     */
    private fun RoundWinDetail.yakumanMultipliers(): Map<YakuType, Int> = detailFields
        .filter { it.id == RiichiWinSettlementIds.YAKU_FIELD }
        .flatMap { it.entries.orEmpty() }
        .filter { it.quantity?.unitId == RiichiWinSettlementIds.YAKUMAN }
        .mapNotNull { entry -> RiichiWinSettlementIds.yakuType(entry.id)?.let { it to checkNotNull(entry.quantity).amount } }
        .toMap()

    /** 非役滿和牌的總番數；沒有翻符欄位時為 null。 */
    private fun RoundWinDetail.totalHan(): Int? = detailFields
        .firstOrNull { it.id == RiichiWinSettlementIds.HAN_FU_FIELD }
        ?.quantities?.firstOrNull { it.unitId == RiichiWinSettlementIds.HAN }?.amount

    /** 只接受明確指名這位贏家的公開狀態標記。 */
    private fun RoundWinDetail.hasMarker(id: String): Boolean = publicMarkers.any { it.id == id && it.playerId == playerId }

    private companion object {
        /** 累計役滿所需的最低總番數。 */
        const val COUNTED_YAKUMAN_HAN = 13
    }
}
