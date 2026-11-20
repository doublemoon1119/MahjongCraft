package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryFact
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryOutboxEvent
import com.doublemoon1119.mahjongcraft.flow.common.game.history.HistoryTableResult
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundPosition
import com.doublemoon1119.mahjongcraft.flow.common.game.history.replay.HistoryRoundState
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.HistoryRecordingPersistenceMapper
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayCodec
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.CompactReplayRoundReader
import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay.ReplayReadResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallProjectionContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallSideLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableDiscardArea
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileArrangement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileArrangementInput
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfileDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfileRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toArrangementState
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import com.doublemoon1119.mahjongcraft.testing.logic.table.testRoundRandomness
import kotlinx.coroutines.test.runTest
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證牌譜回放擺出的牌桌與對局中同一份桌況擺出的位置相同。 */
class HistoryReplayArrangementTest {
    /** 標準牌河：開局與打出一張牌後，牌譜擺出的每個位置都和對局中相同；攤開手牌只改變手牌。 */
    @Test
    fun `replay arrangement matches the live table`() = runTest {
        assertMatchesLive(TableTileLayoutProfile.STANDARD)
    }

    /** 散放牌河：牌譜保存的位置表種子讓捨牌落在與對局中相同的位置。 */
    @Test
    fun `replay arrangement matches scattered discards`() = runTest {
        assertMatchesLive(TableTileLayoutProfile(discardArea = TableDiscardArea.SCATTERED))
    }

    /**
     * 比對開局與打出一張牌後兩份桌況的擺放。
     *
     * @param profile 這次使用的桌面牌位配置。
     */
    private suspend fun assertMatchesLive(profile: TableTileLayoutProfile) {
        val module = RiichiRuleModule(RULE_MODULE_ID, RiichiRuleConfig(redDoraCount = 0))
        val modules = MahjongModuleRegistryImpl().apply {
            register(MahjongModuleDefinition(RiichiRuleConfig::class, RULE_MODULE_ID) { config, id -> RiichiRuleModule(id, config) })
        }
        val profiles = TableTileLayoutProfileRegistryImpl().apply { register(TableTileLayoutProfileDefinition(RULE_MODULE_ID, profile)) }
        val recorder = ReplayTableNotesRecorder(modules, profiles, TablePropDescriberRegistryImpl())
        val table = GameInitializer.initialize(Uuid.random(), List(PLAYERS) { Uuid.random() }, module, randomness = testRoundRandomness()).tableState
        val discarder = table.currentPlayer
        val discarded = discarder.hand.tiles.first()
        val changed = table.copy(
            players = table.players.map { player ->
                if (player.id != discarder.id) {
                    player
                } else {
                    player.copy(hand = player.hand.copy(tiles = player.hand.tiles - discarded), discardPile = player.discardPile.discardTile(discarded))
                }
            },
            discardOrder = listOf(discarded.id),
        )
        val matchId = Uuid.random()
        val events = listOf(
            HistoryOutboxEvent(matchId, table.id, 1, 1L, 1L, 0L, null, HistoryFact.MatchStarted(table, GameFlowConfig(), emptyMap())),
            HistoryOutboxEvent(matchId, table.id, 1, 2L, 2L, 1L, null, HistoryFact.TableChanged(HistoryTableResult.Checkpoint("test:discard", changed))),
            HistoryOutboxEvent(matchId, table.id, 1, 3L, 3L, 2L, null, HistoryFact.MatchCompleted("test:completed", changed.players.associate { it.id to it.score })),
        )
        val registries = bundledPersistenceRegistries()
        val document = CompactReplayCodec.encodeCompact(events, HistoryRecordingPersistenceMapper(registries), registries, recorder)
        val reader = CompactReplayRoundReader(registries.componentViews)
        listOf(HistoryRoundPosition.Initial to table, HistoryRoundPosition.AfterTransaction(1) to changed).forEach { (position, live) ->
            val saved = assertIs<ReplayReadResult.Success<HistoryRoundState>>(reader.readState(document, matchId, 1, position)).value
            val dto = saved.toDto(DefaultNetworkDtoRegistries())
            val notes = assertNotNull(ReplayTableNotes.decode(dto.presentationNotes))
            val replayed = HistoryReplayArrangement.arrange(dto, notes, module, profile) { false }

            assertEquals(placements(live(live, module, profile)), placements(replayed.values.map { it.placement to it.pose }))
            // 攤開手牌只改變手牌，其他牌的位置與姿態不變。
            val opened = HistoryReplayArrangement.arrange(dto, notes, module, profile, sidewaysMarker = { false }, openHands = true)
            val handTiles = dto.players.flatMap { it.handTiles + listOfNotNull(it.lastDrawn) }.toSet()
            assertTrue(handTiles.isNotEmpty() && handTiles.all { opened.getValue(it).pose == TilePose.FACE_UP })
            assertEquals(replayed.filterKeys { it !in handTiles }, opened.filterKeys { it !in handTiles })
        }
    }

    /**
     * 對局中擺出同一份桌況。
     *
     * @param state 權威桌況。
     * @param module 規則模組。
     * @param profile 桌面牌位配置。
     * @return 每張牌的位置與姿態。
     */
    private fun live(state: TableState, module: RiichiRuleModule, profile: TableTileLayoutProfile): List<Pair<MahjongTileWallPlacement, TilePose>> {
        val projection = MahjongTileWallProjectionContext(
            controllerX = 0,
            controllerY = 0,
            controllerZ = 0,
            tableFacing = MahjongTableFacing.NORTH,
            sideLayout = assertNotNull(MahjongTileWallSideLayout.forRule(module, PLAYERS, state.dealerIndex)),
            tileLayout = profile,
            stacksPerSide = assertNotNull(MahjongTileTableLayout.wallStacksPerSide(module, PLAYERS)),
        )
        return TableTileArrangement.arrange(
            input = TableTileArrangementInput(
                controllerX = 0,
                controllerY = 0,
                controllerZ = 0,
                tableFacing = MahjongTableFacing.NORTH,
                seatLayout = MahjongTableSeatLayout.forPlayerCount(PLAYERS),
                table = state.toArrangementState(),
                tileLayout = profile,
                cornerWidthBySeat = emptyMap(),
                handPoseStates = emptyList(),
                wallProjection = projection,
                wallClearance = projection.clearance(assertNotNull(module.createPhysicalWallLayoutPolicy()).roundReach(state)),
                faceUpWallTileIds = emptySet(),
            ),
        ).values.map { it.placement to it.pose }
    }

    /**
     * 把位置與姿態排成可比較的清單，與牌的識別碼無關。
     *
     * @param tiles 每張牌的位置與姿態。
     * @return 依座標排序的清單。
     */
    private fun placements(tiles: Collection<Pair<MahjongTileWallPlacement, TilePose>>): List<String> = tiles.map { (placement, pose) ->
        "${placement.x.round()},${placement.y.round()},${placement.z.round()},${placement.yaw},$pose"
    }.sorted()

    /**
     * 去掉浮點誤差。
     *
     * @return 四捨五入到小數第六位的字串。
     */
    private fun Double.round(): String = (round(this * ROUNDING) / ROUNDING).toString()

    /** 共用常數。 */
    private companion object {
        /** 規則模組識別碼。 */
        const val RULE_MODULE_ID = "mahjongcraft:riichi"

        /** 玩家人數。 */
        const val PLAYERS = 4

        /** 座標比對的精度。 */
        const val ROUNDING = 1_000_000.0
    }
}
