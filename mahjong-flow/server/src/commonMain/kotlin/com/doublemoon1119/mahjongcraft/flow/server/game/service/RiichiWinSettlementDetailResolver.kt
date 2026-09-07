package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ResolvedRoundOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailField
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementDetailValue
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementTranslationKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementYakuTranslationKeys
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueResult
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.TableState

/** 日麻專屬的胡牌詳情解析器：一般胡牌的役種／翻符／寶牌，以及流局滿貫的特殊結算內容。 */
object RiichiWinSettlementDetailResolver : WinSettlementDetailResolver {
    override fun resolve(state: TableState, handValue: HandValueResult): WinSettlementResolvedDetails {
        val fields = riichiDetails(state, handValue)
        return WinSettlementResolvedDetails(TEMPLATE_KEY, fields)
    }

    override fun resolveSpecialOutcome(state: TableState, outcome: ResolvedRoundOutcome): WinSettlementResolvedDetails? {
        if (outcome.id != BuiltInRoundOutcomeIds.NAGASHI_MANGAN) return null
        return WinSettlementResolvedDetails(
            TEMPLATE_KEY,
            listOf(
                WinSettlementDetailField(
                    YAKU_FIELD,
                    WinSettlementDetailValue.Entries(listOf(WinSettlementDetailValue.Entries.Entry(WinSettlementYakuTranslationKeys.NAGASHI_MANGAN))),
                ),
            ),
        )
    }

    internal fun riichiDetails(state: TableState, handValue: HandValueResult): List<WinSettlementDetailField> {
        val result = handValue as? RiichiHandValueResult ?: return emptyList()
        val indicators = (state.dynamicRuleState as? RiichiDynamicState)?.getDoraIndicators(state)
        return buildList {
            add(
                WinSettlementDetailField(
                    YAKU_FIELD,
                    WinSettlementDetailValue.Entries(
                        result.yakuResults.map { yaku ->
                            if (result.isYakuman) {
                                WinSettlementDetailValue.Entries.Entry(
                                    translationKey = yakuTranslationKey(yaku.yaku),
                                    trailingTranslationKey = yakumanMultiplierTranslationKey(-yaku.han),
                                    trailingTranslationArgument = (-yaku.han).takeIf { it !in 1..6 }?.toString(),
                                )
                            } else {
                                WinSettlementDetailValue.Entries.Entry(yakuTranslationKey(yaku.yaku), yaku.han.toString())
                            }
                        },
                    ),
                ),
            )
            if (!result.isYakuman) {
                add(WinSettlementDetailField(HAN_FU_FIELD, riichiHanFuValue(result.totalHan, result.totalFu)))
            } else {
                val multiplier = -result.totalHan
                add(
                    WinSettlementDetailField(
                        YAKUMAN_TOTAL_FIELD,
                        WinSettlementDetailValue.Text(
                            yakumanMultiplierTranslationKey(multiplier),
                            multiplier.takeIf { it !in 1..6 }?.let { listOf(it.toString()) }.orEmpty(),
                        ),
                    ),
                )
            }
            add(WinSettlementDetailField(DORA_FIELD, WinSettlementDetailValue.Tiles(indicators?.first.orEmpty().map { it.id })))
            add(WinSettlementDetailField(URA_DORA_FIELD, WinSettlementDetailValue.Tiles(indicators?.second.orEmpty().map { it.id })))
        }
    }

    /** 建立一般胡牌的翻符顯示；滿貫以上沒有權威符數時只顯示翻數。 */
    internal fun riichiHanFuValue(totalHan: Int, totalFu: Int): WinSettlementDetailValue.Text = if (totalFu > 0) {
        WinSettlementDetailValue.Text(
            WinSettlementTranslationKeys.HAN_FU,
            listOf(totalHan.toString(), totalFu.toString()),
        )
    } else {
        WinSettlementDetailValue.Text(
            WinSettlementTranslationKeys.HAN,
            listOf(totalHan.toString()),
        )
    }

    /** 對應既有玩家可見役種翻譯鍵；名稱差異單一來源見 [WinSettlementYakuTranslationKeys]。 */
    private fun yakuTranslationKey(type: YakuType): String = WinSettlementYakuTranslationKeys.keyFor(type)

    private fun yakumanMultiplierTranslationKey(multiplier: Int): String = if (multiplier in 1..6) {
        "mahjongcraft.game.score.yakuman_${multiplier}x"
    } else {
        "mahjongcraft.game.score.yakuman_nx"
    }

    const val TEMPLATE_KEY = "mahjongcraft:riichi"
    const val YAKU_FIELD = "mahjongcraft:riichi_yaku"
    const val HAN_FU_FIELD = "mahjongcraft:riichi_han_fu"
    const val YAKUMAN_TOTAL_FIELD = "mahjongcraft:riichi_yakuman_total"
    const val DORA_FIELD = "mahjongcraft:riichi_dora"
    const val URA_DORA_FIELD = "mahjongcraft:riichi_ura_dora"
}

/** 登記 bundled 日麻的胡牌詳情解析器。 */
fun WinSettlementDetailResolverRegistry.registerRiichiWinSettlementDetailResolver() {
    register(BuiltInRuleModuleIds.RIICHI, RiichiWinSettlementDetailResolver)
}
