package com.doublemoon1119.mahjongcraft.flow.persistence.dto.game
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.HandPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.game.TileWallPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證 Game 權威牌張、手牌與牌山 persistence DTO 的 encoded round-trip。 */
class AuthoritativeTileStatePersistenceTest {
    /** 驗證 UUID、赤牌、花牌、最後摸牌與副露來源完整恢復。 */
    @Test
    fun `hand round-trips with complete authoritative tile state`() {
        val source = tile(Tile.Honor.East)
        val hand = Hand(
            tiles = listOf(tile(RiichiTileTypes.redFive(Tile.Suit.Character)), tile(Tile.Flower.Plum)),
            melds = listOf(
                Meld(
                    type = MeldType.PON,
                    tiles = listOf(tile(Tile.Honor.East), tile(Tile.Honor.East), source),
                    sourceTile = source,
                    sourceDirection = RelativeDirection.Left,
                ),
            ),
            lastDrawn = tile(Tile.Numeric(Tile.Suit.Dot, 9)),
        )

        val dto = hand.toPersistenceDto()
        val encoded = Json.encodeToString(HandPersistenceDto.serializer(), dto)
        val restored = Json.decodeFromString(HandPersistenceDto.serializer(), encoded).toDomain()

        assertEquals(hand, restored)
    }

    /** 驗證牌山的每張牌 UUID 與前後順序完整恢復。 */
    @Test
    fun `tile wall round-trips without exposing a snapshot projection`() {
        val wall = TileWall(
            listOf(
                tile(Tile.Honor.White),
                tile(Tile.Numeric(Tile.Suit.Bamboo, 1)),
                tile(Tile.Flower.Chrysanthemum),
            ),
        )

        val dto = wall.toPersistenceDto()
        val encoded = Json.encodeToString(TileWallPersistenceDto.serializer(), dto)
        val restored = Json.decodeFromString(TileWallPersistenceDto.serializer(), encoded).toDomain()

        assertEquals(wall, restored)
    }

    /** 建立具有隨機穩定識別碼的測試牌。 */
    private fun tile(tile: Tile): IdentifiedTile = IdentifiedTile(Uuid.random(), tile)
}
