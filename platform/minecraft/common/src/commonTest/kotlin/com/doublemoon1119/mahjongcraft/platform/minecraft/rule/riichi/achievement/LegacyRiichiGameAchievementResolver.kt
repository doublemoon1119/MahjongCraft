package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.achievement

import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.BuiltInMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.uuid.Uuid

/**
 * 日麻成果搬移前的固定對照實作，只供測試比較結果。
 *
 * 判定邏輯保留原本讀取提交事實的方式，不隨正式事件判定一起修改。
 */
internal class LegacyRiichiGameAchievementResolver {
    /** 依搬移前的事實與交易前桌況判定成果。 */
    fun resolve(facts: CommittedGameFacts): Map<Uuid, Set<String>> {
        val before = (facts.previousGame ?: checkNotNull(facts.game)).tableState
        val achievements = mutableMapOf<Uuid, MutableSet<String>>()
        fun add(playerId: Uuid, ids: Collection<String>) {
            if (ids.isNotEmpty()) achievements.getOrPut(playerId) { linkedSetOf() } += ids
        }
        facts.facts.forEach { draft ->
            when (val fact = draft.fact) {
                is HistoryFact.WinSettled -> fact.winDetails.forEach { details ->
                    add(details.playerId, winAchievements(details, before.players.firstOrNull { it.id == details.playerId }))
                }
                is HistoryFact.RuleEffectResolved -> if (fact.reasonId == RiichiRoundOutcomeIds.NAGASHI_MANGAN) {
                    val achieverIds = fact.winDetails.map { it.playerId }
                        .ifEmpty { fact.roundCompletion?.beneficiaryPlayerIds.orEmpty().toList() }
                    achieverIds.forEach { add(it, listOf(RiichiAchievementIds.NAGASHI_MANGAN)) }
                }
                is HistoryFact.MatchCompleted -> {
                    fact.finalScoresByPlayerId.keys.forEach { add(it, listOf(RiichiAchievementIds.MATCH_COMPLETED)) }
                    if (fact.reasonId == BuiltInMatchEndReasonIds.PLAYER_BUSTED) {
                        fact.finalScoresByPlayerId.forEach { (playerId, score) -> add(playerId, bustAchievements(score, before)) }
                    }
                }
                else -> Unit
            }
        }
        return achievements
    }

    /** 一位贏家的和牌成果，保留原本的役種與和牌前狀態判定。 */
    private fun winAchievements(details: HistoryWinDetails, winnerBefore: MahjongPlayer?): List<String> = buildList {
        val yaku = details.yakuTypes()
        val isNaturalYakuman = details.detailFields.any { it.id == RiichiWinSettlementIds.YAKUMAN_TOTAL_FIELD }
        val riichiState = winnerBefore?.playerRuleState as? RiichiPlayerState
        val isRiichi = YakuType.Riichi in yaku || YakuType.DoubleRiichi in yaku || riichiState?.isRiichi == true
        if (isRiichi) add(RiichiAchievementIds.RIICHI_WIN)
        if (YakuType.Ippatsu in yaku || (isNaturalYakuman && riichiState?.isRiichi == true && riichiState.isIppatsu)) {
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

    /** 以提交分數及交易前設定判定擊飛成果。 */
    private fun bustAchievements(score: Int, before: TableState): List<String> {
        val threshold = before.config.scoreConfig.bustThreshold ?: return emptyList()
        if (score >= threshold) return emptyList()
        return buildList {
            add(RiichiAchievementIds.BUSTED)
            if (score <= RiichiAchievementIds.FAR_BUST_SCORE) add(RiichiAchievementIds.BUSTED_FAR)
            if (before.prevalentWind == Wind.EAST && before.roundNumber == 1) add(RiichiAchievementIds.BUSTED_IN_EAST_ONE)
        }
    }

    /** 讀取可辨識的役種條目。 */
    private fun HistoryWinDetails.yakuTypes(): List<YakuType> = detailFields
        .filter { it.id == RiichiWinSettlementIds.YAKU_FIELD }
        .flatMap { field -> (field.value as? WinSettlementDetailValue.Entries)?.entries.orEmpty() }
        .mapNotNull { entry -> RiichiWinSettlementIds.yakuType(entry.id) }

    /** 以最後一筆條目的倍數保留每種役滿。 */
    private fun HistoryWinDetails.yakumanMultipliers(): Map<YakuType, Int> = detailFields
        .filter { it.id == RiichiWinSettlementIds.YAKU_FIELD }
        .flatMap { field -> (field.value as? WinSettlementDetailValue.Entries)?.entries.orEmpty() }
        .filter { it.quantity?.unitId == RiichiWinSettlementIds.YAKUMAN }
        .mapNotNull { entry -> RiichiWinSettlementIds.yakuType(entry.id)?.let { it to checkNotNull(entry.quantity).amount } }
        .toMap()

    /** 讀取第一個翻符欄位中的總番數。 */
    private fun HistoryWinDetails.totalHan(): Int? = detailFields
        .firstOrNull { it.id == RiichiWinSettlementIds.HAN_FU_FIELD }
        ?.let { field -> (field.value as? WinSettlementDetailValue.Quantities)?.quantities?.firstOrNull { it.unitId == RiichiWinSettlementIds.HAN }?.amount }

    private companion object {
        /** 累計役滿的番數界線。 */
        const val COUNTED_YAKUMAN_HAN = 13
    }
}
