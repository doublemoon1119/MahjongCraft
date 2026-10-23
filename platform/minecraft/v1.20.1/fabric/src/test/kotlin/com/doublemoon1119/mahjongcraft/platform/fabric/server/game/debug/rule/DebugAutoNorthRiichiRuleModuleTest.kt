package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.MahjongRuleConfigDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.ActionScoring
import com.doublemoon1119.mahjongcraft.logic.module.ActionScoringContext
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PULL_NORTH_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpContext
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpDecision
import com.doublemoon1119.mahjongcraft.logic.table.DrawSource
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.DebugMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.DebugGameScenarioRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.createThreePlayerRiichiScenarioContext
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.seat
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledNetworkDtos
import kotlinx.serialization.PolymorphicSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.uuid.Uuid

/** 驗證開發用自動拔北三人日麻的自動拔北、收付與設定格式。 */
class DebugAutoNorthRiichiRuleModuleTest {
    /** 提供自動拔北情境的登錄表。 */
    private val scenarios = DebugGameScenarioRegistry()

    /** 規則模組沿用三人日麻的牌組與設定，只換掉摸牌後自動處理與即時收付。 */
    @Test
    fun `the module keeps three player riichi and adds both policies`() {
        val config = DebugAutoNorthRiichiRuleConfig(ThreePlayerRiichiRuleConfig(redDoraCount = 0))
        val module = DebugAutoNorthRiichiRuleModule(DebugAutoNorthRiichiRuleModule.ID, config)

        assertEquals(DebugAutoNorthRiichiRuleModule.ID, module.id)
        assertSame(config, module.config)
        assertEquals(0, module.config.redDoraCount)
        assertEquals(THREE_PLAYER_TILE_COUNT, module.createWallFactory(module.config.maxPlayers).create().getAllTiles().size)
        assertIs<DebugAutoNorthDrawFollowUpPolicy>(module.createDrawFollowUpPolicy())
        assertSame(DebugAutoNorthActionScoringPolicy, module.createActionScoringPolicy())
    }

    /** 摸到北時自動拔北，摸到其他牌時不處理。 */
    @Test
    fun `a drawn north is pulled automatically`() {
        val state = scenarioState("auto_north_riichi_chain")
        val north = state.tileWall.getAllTiles().first { it.tile == Tile.Honor.North }

        assertEquals(DrawFollowUpDecision.SetAside(PULL_NORTH_GAME_ACTION), state.followUpAfterDrawing(north))
        assertEquals(DrawFollowUpDecision.None, state.followUpAfterDrawing(state.tileWall.getAllTiles().first { it.tile != Tile.Honor.North }))
    }

    /** 最後一張活牌是北時已經不能拔北補牌，本局以一般流局結束。 */
    @Test
    fun `a north drawn as the last live tile ends the round`() {
        val state = scenarioState("auto_north_riichi_last_tile")
        val north = state.tileWall.getAllTiles().last()

        assertEquals(Tile.Honor.North, north.tile)
        assertEquals(
            DrawFollowUpDecision.EndRound(RiichiExhaustiveDrawReason.Normal),
            state.copy(tileWall = TileWall(emptyList()), physicalWallLayout = null).followUpAfterDrawing(north),
        )
    }

    /** 拔北的玩家付一根立直棒的點數進供託，桌上的立直棒多一根。 */
    @Test
    fun `pulling north pays into the riichi stick pot`() {
        val state = scenarioState("auto_north_riichi_chain").copy(dynamicRuleState = RiichiDynamicState(riichiStickCount = 1))
        val actorId = state.currentPlayer.id

        assertEquals(
            ActionScoring(mapOf(actorId to -1000), potDeposit = 1000, dynamicRuleState = RiichiDynamicState(riichiStickCount = 2)),
            DebugAutoNorthActionScoringPolicy.resolve(state.scoringContext(actorId, PULL_NORTH_GAME_ACTION, sourcePlayerId = null)),
        )
    }

    /** 明槓由放槓者付 1000 點，暗槓與加槓由另外兩家各付 500 點；碰不收付。 */
    @Test
    fun `kans pay the declarer and other calls do not score`() {
        val state = scenarioState("auto_north_riichi_kan_payments")
        val actorId = state.currentPlayer.id
        val (downstreamId, upstreamId) = state.seat(1).id to state.seat(2).id
        val kanTileId = state.currentPlayer.hand.tiles.first().id

        assertEquals(
            mapOf(downstreamId to -1000, actorId to 1000),
            DebugAutoNorthActionScoringPolicy.resolve(
                state.scoringContext(actorId, GameAction.Kan(GameAction.KanType.OPEN_KAN, kanTileId, emptyList()), sourcePlayerId = downstreamId),
            )?.scoreDeltas,
        )
        listOf(GameAction.KanType.CLOSED_KAN, GameAction.KanType.ADDED_KAN).forEach { type ->
            val scoring = DebugAutoNorthActionScoringPolicy.resolve(state.scoringContext(actorId, GameAction.Kan(type, kanTileId, emptyList()), sourcePlayerId = null))
            assertEquals(mapOf(downstreamId to -500, upstreamId to -500, actorId to 1000), scoring?.scoreDeltas, type.name)
            assertEquals(0, scoring?.potDeposit)
        }
        assertNull(DebugAutoNorthActionScoringPolicy.resolve(state.scoringContext(actorId, GameAction.Pon(kanTileId, emptyList()), sourcePlayerId = downstreamId)))
    }

    /** 規則設定可以經網路格式與存檔格式往返。 */
    @Test
    fun `the config round trips through network and persistence formats`() {
        val config = DebugAutoNorthRiichiRuleConfig(ThreePlayerRiichiRuleConfig(redDoraCount = 0, useLocalYaku = true))
        val debugExtension = DebugMahjongExtension(winRoundContinuationState = DebugWinRoundContinuationState())

        val networkRegistries = DefaultNetworkDtoRegistries().apply {
            registerBundledNetworkDtos()
            debugExtension.registerNetworkDtos(this)
            freeze()
        }
        val format = NetworkJson(networkRegistries)
        val serializer = PolymorphicSerializer(MahjongRuleConfigDto::class)
        val encoded = format.encodeToString(serializer, networkRegistries.ruleConfig.toDto(config))
        assertEquals(config, networkRegistries.ruleConfig.toDomain(format.decodeFromString(serializer, encoded)))

        val persistenceRegistries = bundledPersistenceRegistries().apply {
            debugExtension.registerPersistenceDtos(this)
            freeze()
        }
        assertEquals(config, persistenceRegistries.ruleConfigs.decode(persistenceRegistries.ruleConfigs.encode(config)))
    }

    /**
     * 建立指定自動拔北情境的桌況。
     *
     * @param idPath 情境識別碼中 namespace 後的路徑。
     * @return 建立後的對局桌況。
     */
    private fun scenarioState(idPath: String): TableState = scenarios.get("mahjongcraft:$idPath")!!
        .build(createThreePlayerRiichiScenarioContext())
        .game
        .tableState

    /**
     * 以目前玩家摸到 [drawnTile] 的桌況詢問自動處理；判斷不需要實體牌牆擺法，因此一併清除。
     *
     * @param drawnTile 摸到的牌；會從活牌中移除，並取代目前玩家摸牌位上的牌。
     * @return 自動處理的決策。
     */
    private fun TableState.followUpAfterDrawing(drawnTile: IdentifiedTile): DrawFollowUpDecision {
        val player = currentPlayer
        val drawnState = copy(
            tileWall = TileWall(tileWall.getAllTiles() - drawnTile),
            physicalWallLayout = null,
            players = players.map { if (it.id == player.id) it.copy(hand = it.hand.copy(lastDrawn = drawnTile)) else it },
        )
        val module = DebugAutoNorthRiichiRuleModule(DebugAutoNorthRiichiRuleModule.ID, config as DebugAutoNorthRiichiRuleConfig)
        return module.createDrawFollowUpPolicy().resolve(
            DrawFollowUpContext(
                tableState = drawnState,
                playerId = player.id,
                drawnTile = drawnTile,
                source = DrawSource.LiveWall,
                completedStepCount = 0,
            ),
        )
    }

    /**
     * 建立動作前後桌況相同的收付情境。
     *
     * @param actorPlayerId 執行動作的玩家。
     * @param action 已成立的動作。
     * @param sourcePlayerId 被鳴牌的玩家；不是鳴取捨牌時為 null。
     * @return 收付 policy 的輸入。
     */
    private fun TableState.scoringContext(
        actorPlayerId: Uuid,
        action: GameAction,
        sourcePlayerId: Uuid?,
    ): ActionScoringContext = ActionScoringContext(
        tableStateBeforeAction = this,
        tableStateAfterAction = this,
        actorPlayerId = actorPlayerId,
        action = action,
        sourcePlayerId = sourcePlayerId,
    )

    private companion object {
        /** 三人麻將牌組的張數。 */
        const val THREE_PLAYER_TILE_COUNT: Int = 108
    }
}
