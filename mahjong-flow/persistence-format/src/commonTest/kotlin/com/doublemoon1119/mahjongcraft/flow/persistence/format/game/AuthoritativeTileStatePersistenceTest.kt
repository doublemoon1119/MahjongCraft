package com.doublemoon1119.mahjongcraft.flow.persistence.format.game
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.HandPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TileWallPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileValues
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證 Game 權威牌張、手牌與牌山 persistence DTO 的 encoded round-trip。 */
class AuthoritativeTileStatePersistenceTest {
    /** 驗證 UUID、赤牌、Extension 花牌、最後摸牌與副露來源完整恢復。 */
    @Test
    fun `hand round-trips with complete authoritative tile state`() {
        val source = tile(Tile.Honor.East)
        val ponTiles = listOf(tile(Tile.Honor.East), tile(Tile.Honor.East), source)
        val hand = Hand(
            tiles = listOf(
                redFive(Tile.Suit.Character),
                tile(Tile.Extension(TaiwanTileTypes.PLUM)),
            ),
            melds = listOf(
                Meld(
                    type = MeldType.Pon,
                    tiles = ponTiles,
                    sourceTile = source,
                    sourceDirection = RelativeDirection.Left,
                    publiclyRevealedTileIds = ponTiles.map { it.id }.toSet(),
                    faceDownTileIds = emptySet(),
                ),
            ),
            lastDrawn = tile(Tile.Numeric(Tile.Suit.Dot, 9)),
        )

        val dto = hand.toPersistenceDto(bundledPersistenceRegistries().components)
        val encoded = Json.encodeToString(HandPersistenceDto.serializer(), dto)
        val restored = Json.decodeFromString(HandPersistenceDto.serializer(), encoded).toDomain(bundledPersistenceRegistries().components)

        assertEquals(hand, restored)
    }

    /** 驗證牌山的每張牌 UUID 與前後順序完整恢復。 */
    @Test
    fun `tile wall round-trips without exposing a snapshot projection`() {
        val wall = TileWall(
            listOf(
                tile(Tile.Honor.White),
                tile(Tile.Numeric(Tile.Suit.Bamboo, 1)),
                tile(Tile.Extension(TaiwanTileTypes.CHRYSANTHEMUM)),
            ),
        )

        val dto = wall.toPersistenceDto(bundledPersistenceRegistries().components)
        val encoded = Json.encodeToString(TileWallPersistenceDto.serializer(), dto)
        val restored = Json.decodeFromString(TileWallPersistenceDto.serializer(), encoded).toDomain(bundledPersistenceRegistries().components)

        assertEquals(wall, restored)
    }

    /**
     * 為指定牌種建立具有隨機識別碼的測試牌。
     *
     * @param tile 測試牌的牌種。
     * @return 帶有獨立 UUID 的測試牌。
     */
    private fun tile(tile: Tile): IdentifiedTile = IdentifiedTile(Uuid.random(), tile)
}

/**
 * 建立帶赤寶牌特性與赤五外觀的牌張。
 *
 * @param suit 赤五的花色。
 * @return 新的赤五牌張。
 */
private fun redFive(suit: Tile.Suit): IdentifiedTile = RiichiTileValues.redFive(suit).let { IdentifiedTile(Uuid.random(), it.tile, it.components) }
