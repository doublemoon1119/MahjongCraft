package com.doublemoon1119.mahjongcraft.flow.persistence.format.component

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.persistenceJson
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TableStatePersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.TurnPhase
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證玩家、桌況與副露使用同一套保存轉換，且不丟失副露身分。 */
class ComponentOwnerPersistenceTest {
    /** 完整 JSON 往返同時保留多個對象的組件與副露 ID。 */
    @Test
    fun `table player and meld components survive a complete state round trip`() {
        val tableFlag = ComponentType(
            id = "example:table/flag",
            valueClass = Boolean::class,
            copyValue = { it },
            target = BuiltInComponentOwners.TABLE,
        )
        val playerFlag = ComponentType(
            id = "example:player/flag",
            valueClass = Boolean::class,
            copyValue = { it },
            target = BuiltInComponentOwners.PLAYER,
        )
        val meldFlag = ComponentType(
            id = "example:meld/flag",
            valueClass = Boolean::class,
            copyValue = { it },
            target = BuiltInComponentOwners.MELD,
        )
        val registries = bundledPersistenceRegistries()
        listOf(tableFlag, playerFlag, meldFlag).forEach { component ->
            registries.components.register(
                ComponentPersistenceDefinition(
                    component = component,
                    serializer = ComponentBooleanPersistenceDto.serializer(),
                    toDto = ::ComponentBooleanPersistenceDto,
                    toDomain = { it.value },
                ),
            )
        }
        val tiles = List(3) { IdentifiedTile(Uuid.random(), Tile.Honor.North) }
        val meld = Meld(
            type = MeldType.Pon,
            tiles = tiles,
            sourceTile = tiles.first(),
            sourceDirection = RelativeDirection.Left,
            publiclyRevealedTileIds = tiles.mapTo(mutableSetOf()) { it.id },
            faceDownTileIds = emptySet(),
            components = ComponentSet.EMPTY.with(meldFlag, true),
        )
        val player = MahjongPlayer(
            id = Uuid.random(),
            initialSeatIndex = 0,
            discardPile = RiichiDiscardPile(),
            seatWind = Wind.EAST,
            hand = Hand(melds = listOf(meld)),
            components = ComponentSet.EMPTY.with(playerFlag, false),
        )
        val original = TableState(
            id = Uuid.random(),
            players = listOf(player),
            config = RiichiRuleConfig(),
            tileWall = TileWall(emptyList()),
            dealerPlayerId = player.id,
            components = ComponentSet.EMPTY.with(tableFlag, true),
            turnPhase = TurnPhase.AwaitingDraw,
        )
        val dto = original.toPersistenceDto(
            ruleConfigRegistry = registries.ruleConfigs,
            discardPileRegistry = registries.discardPiles,
            exhaustiveDrawReasonRegistry = registries.exhaustiveDrawReasons,
            extensionGameActionRegistry = registries.extensionGameActions,
            componentRegistry = registries.components,
        )
        val json = persistenceJson.encodeToString(TableStatePersistenceDto.serializer(), dto)
        val restored = persistenceJson.decodeFromString(TableStatePersistenceDto.serializer(), json).toDomain(
            ruleConfigRegistry = registries.ruleConfigs,
            discardPileRegistry = registries.discardPiles,
            exhaustiveDrawReasonRegistry = registries.exhaustiveDrawReasons,
            extensionGameActionRegistry = registries.extensionGameActions,
            componentRegistry = registries.components,
        )
        assertEquals(true, restored.components[tableFlag])
        assertEquals(false, restored.players.single().components[playerFlag])
        val restoredMeld = restored.players.single().hand.melds.single()
        assertEquals(meld.id, restoredMeld.id)
        assertEquals(true, restoredMeld.components[meldFlag])
        assertEquals(meld, restoredMeld)
    }
}
