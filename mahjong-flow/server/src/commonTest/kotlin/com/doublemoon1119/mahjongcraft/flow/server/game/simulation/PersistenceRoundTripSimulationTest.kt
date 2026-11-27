package com.doublemoon1119.mahjongcraft.flow.server.game.simulation

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.persistence.format.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledComponentValidator
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.failOnSkippedHistoryEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.fail

/**
 * 整場台灣麻將的每一次寫入都經過存檔格式來回轉換，讀回的對局必須與原本完全相同。
 *
 * - 讀回的對局完全相同時，從任何一步停機再讀檔繼續，都會打出同樣的牌局，不需要每一步重新打完。
 * - 台灣麻將有補花、槓後補牌與保留區補位，是存檔中牌牆位置變化最多的規則。
 */
class PersistenceRoundTripSimulationTest {
    /** 每一次寫入後的對局存檔再讀回，與原本相同，而且整場正常結束。 */
    @Test
    fun `every write of a Taiwan match survives a save and load`() = runTest {
        val codec = AuthoritativeStatePersistenceCodec(
            registries = bundledPersistenceRegistries(),
            componentValidator = bundledComponentValidator(),
            skippedHistoryEventReporter = failOnSkippedHistoryEvent,
        )
        var writes = 0
        val roundTrip: (Game) -> Unit = { game ->
            writes++
            val restored = checkNotNull(codec.decode(codec.encode(rooms = emptyList(), games = listOf(game))).games[game.id])
            val expected = comparable(game)
            val actual = comparable(restored)
            if (expected != actual) fail("Write $writes of match ${game.matchId} changed after a save and load: ${firstDifference(expected, actual)}")
        }
        SEEDS.forEach { seed ->
            val result = MatchSimulator(TaiwanRuleConfig(), onGameWritten = roundTrip).play(List(PLAYER_COUNT) { BuiltInAiStrategyKeys.BEGINNER }, seed = seed)
            assertNull(result.failure, result.failure?.describe())
        }
    }

    /**
     * 對局內容的比較用文字：完整的物件文字去掉只代表記憶體位置的部分，再加上每位玩家牌河記錄的內容。
     *
     * - 牌河記錄沒有內容比較，物件文字只顯示記憶體位置，所以另外列出每筆記錄的牌、是否被鳴走與組件。
     *
     * @param game 要比較的對局。
     * @return 內容相同時相同的文字。
     */
    private fun comparable(game: Game): String = buildString {
        append(game.toString().replace(IDENTITY_SUFFIX, "@"))
        game.tableState.players.forEach { player ->
            append("\nDISCARDS ${player.id}: ")
            player.discardPile.entries.forEach { entry -> append("${entry.tile}/${entry.isTaken}/${entry.components}; ") }
        }
    }

    /**
     * 兩段比較用文字第一個不同處前後的內容，避免失敗訊息印出整個對局。
     *
     * @param expected 存檔前的比較用文字。
     * @param actual 讀回後的比較用文字。
     * @return 不同處的位置與前後內容。
     */
    private fun firstDifference(expected: String, actual: String): String {
        val at = expected.indices.firstOrNull { it >= actual.length || expected[it] != actual[it] } ?: expected.length
        val from = (at - DIFFERENCE_CONTEXT).coerceAtLeast(0)
        return "at $at, expected …${expected.substring(from, (at + DIFFERENCE_CONTEXT).coerceAtMost(expected.length))}… " +
            "but was …${actual.substring(from, (at + DIFFERENCE_CONTEXT).coerceAtMost(actual.length))}…"
    }

    private companion object {
        /** 失敗訊息在不同處前後各列出的字元數。 */
        const val DIFFERENCE_CONTEXT = 300

        /** 物件文字中代表記憶體位置的雜湊值。 */
        val IDENTITY_SUFFIX = Regex("@[0-9a-f]+")

        /** 台灣麻將的玩家人數。 */
        const val PLAYER_COUNT = 4

        /** 打的對局。每個數字決定一場不同的牌局。 */
        val SEEDS: List<Int> = (1..5).toList()
    }
}
