package com.doublemoon1119.mahjongcraft.platform.minecraft.achievement

import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventProjector
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryEventDraft
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryWinDetails
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證公開事件投影會合併相同放銃責任群組的贏家。 */
class GenericAchievementEventTest {
    /** 相同責任玩家的兩筆結算須合併後才判定雙響。 */
    @Test
    fun `same discarder groups merge winners before counting multi deal`() {
        val modules = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val game = game()
        val players = game.tableState.players
        val facts = CommittedGameFacts(
            venueId = game.id,
            previousGame = game,
            game = game,
            facts = listOf(
                HistoryEventDraft(players[2].id, HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, listOf(win(players[0].id)), listOf(players[2].id))),
                HistoryEventDraft(players[2].id, HistoryFact.WinSettled(BuiltInRoundOutcomeIds.RON, listOf(win(players[1].id)), listOf(players[2].id))),
            ),
        )
        val batch = checkNotNull(GameEventProjector(modules).projectBatch(facts))
        val result = GenericAchievementDetector.detect(batch)
        assertEquals(setOf(BuiltInAchievementIds.DEAL_IN, BuiltInAchievementIds.DOUBLE_DEAL_IN), result.getValue(players[2].id))
    }

    /** 建立固定四座位的測試場次。 */
    private fun game(): Game = Game(
        FakeTableStateFactory.create(
            players = listOf(Wind.EAST, Wind.SOUTH, Wind.WEST, Wind.NORTH).map { FakeMahjongPlayerFactory.create(initialSeat = it) },
            config = RiichiRuleConfig(),
        ),
        GameFlowConfig(),
    )

    /** 建立沒有計分詳情的贏家資料。 */
    private fun win(playerId: Uuid) = HistoryWinDetails(playerId, emptyList())
}
