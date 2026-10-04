package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.achievement

import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.WinSettlementYakuTranslationKeys
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.BuiltInMatchEndReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import kotlin.uuid.Uuid

/**
 * 內建日麻的專屬成果判定。
 *
 * 役種與番數讀取日麻胡牌詳情欄位；立直與一發另外參考和牌前的玩家狀態，讓役滿和牌也能判定。
 */
object RiichiGameAchievementResolver : GameAchievementResolver {
    override val ruleModuleId: String = BuiltInRuleModuleIds.RIICHI

    /** 日麻胡牌詳情的役種欄位。 */
    private val YAKU_FIELD = "${MinecraftModMetadata.MOD_ID}:riichi_yaku"

    /** 日麻胡牌詳情的翻符欄位；只在非役滿和牌出現，第一個參數是總番數。 */
    private val HAN_FU_FIELD = "${MinecraftModMetadata.MOD_ID}:riichi_han_fu"

    /** 日麻胡牌詳情的役滿倍數欄位；只在自然役滿和牌出現。 */
    private val YAKUMAN_TOTAL_FIELD = "${MinecraftModMetadata.MOD_ID}:riichi_yakuman_total"

    /** 累計役滿所需的最低總番數。 */
    private const val COUNTED_YAKUMAN_HAN = 13

    /** 役種顯示鍵對回役種。 */
    private val yakuByKey: Map<String, YakuType> = YakuType.entries.associateBy(WinSettlementYakuTranslationKeys::keyFor)

    override fun resolve(facts: CommittedGameFacts): Map<Uuid, Set<String>> {
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
                is HistoryFact.RuleEffectResolved -> if (fact.reasonId == BuiltInRoundOutcomeIds.NAGASHI_MANGAN) {
                    val achieverIds = fact.winDetails.map { it.playerId }.ifEmpty { fact.roundCompletion?.beneficiaryPlayerIds.orEmpty().toList() }
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

    /** 一位贏家這次和牌的日麻成果。 */
    private fun winAchievements(details: HistoryWinDetails, winnerBefore: MahjongPlayer?): List<String> = buildList {
        val yaku = details.yakuTypes()
        val isNaturalYakuman = details.detailFields.any { it.id == YAKUMAN_TOTAL_FIELD }
        val riichiState = winnerBefore?.playerRuleState as? RiichiPlayerState
        val isRiichi = YakuType.Riichi in yaku || YakuType.DoubleRiichi in yaku || riichiState?.isRiichi == true
        if (isRiichi) add(RiichiAchievementIds.RIICHI_WIN)
        if (YakuType.Ippatsu in yaku || (isNaturalYakuman && riichiState?.isRiichi == true && riichiState.isIppatsu)) {
            add(RiichiAchievementIds.IPPATSU)
        }
        if (isNaturalYakuman) {
            val yakuman = yaku.filter { it in RiichiAchievementIds.yakumanTypes || it in RiichiAchievementIds.doubleYakumanTypes }
            if (yakuman.isNotEmpty()) add(RiichiAchievementIds.YAKUMAN)
            yakuman.mapNotNullTo(this, RiichiAchievementIds::yakuman)
            if (yakuman.any { it in RiichiAchievementIds.doubleYakumanTypes }) add(RiichiAchievementIds.DOUBLE_YAKUMAN)
            if (yakuman.distinct().size >= 2) add(RiichiAchievementIds.MULTIPLE_YAKUMAN)
        } else {
            val han = details.totalHan() ?: return@buildList
            when {
                han == COUNTED_YAKUMAN_HAN -> add(RiichiAchievementIds.COUNTED_YAKUMAN_EXACT)
                han > COUNTED_YAKUMAN_HAN -> add(RiichiAchievementIds.COUNTED_YAKUMAN_OVER)
            }
        }
    }

    /** 被擊飛玩家的成果；最終點數未低於擊飛門檻的玩家沒有成果。 */
    private fun bustAchievements(score: Int, before: TableState): List<String> {
        val threshold = before.config.scoreConfig.bustThreshold ?: return emptyList()
        if (score >= threshold) return emptyList()
        return buildList {
            add(RiichiAchievementIds.BUSTED)
            if (score <= RiichiAchievementIds.FAR_BUST_SCORE) add(RiichiAchievementIds.BUSTED_FAR)
            if (before.prevalentWind == Wind.EAST && before.roundNumber == 1) add(RiichiAchievementIds.BUSTED_IN_EAST_ONE)
        }
    }

    /** 詳情中的役種；無法辨識的條目略過。 */
    private fun HistoryWinDetails.yakuTypes(): List<YakuType> = detailFields
        .filter { it.id == YAKU_FIELD }
        .flatMap { field -> (field.value as? WinSettlementDetailValue.Entries)?.entries.orEmpty() }
        .mapNotNull { entry -> yakuByKey[entry.translationKey] }

    /** 非役滿和牌的總番數；沒有翻符欄位時為 null。 */
    private fun HistoryWinDetails.totalHan(): Int? = detailFields
        .firstOrNull { it.id == HAN_FU_FIELD }
        ?.let { field -> (field.value as? WinSettlementDetailValue.Text)?.arguments?.firstOrNull()?.toIntOrNull() }
}

/** 登記內建日麻的專屬成果判定。 */
fun GameAchievementResolverRegistry.registerBuiltInRiichiAchievements() {
    register(RiichiGameAchievementResolver)
}
