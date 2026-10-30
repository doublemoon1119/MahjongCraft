package com.doublemoon1119.mahjongcraft.example.pairing

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.TileWallFactory
import com.doublemoon1119.mahjongcraft.logic.tile.TileComposition
import com.doublemoon1119.mahjongcraft.logic.tile.TileCompositionEntry
import kotlin.random.Random

/**
 * 以選中的萬子點數建立簡易配對牌山，並檢查牌數足夠供完整玩家名單起手及摸牌。
 *
 * @property playerCount 本局完整玩家人數。
 * @property wallTileValues 加入牌山的一萬至八萬點數，每種四張。
 * @property publicFacesEnabled 是否讓每種點數的前三張帶公開牌面標記；第四張（含倒置一萬）保持普通。
 * @property random 用來洗牌的亂數來源。
 */
class ExamplePairingWallFactory(
    private val playerCount: Int,
    private val wallTileValues: Set<Int> = (1..8).toSet(),
    private val publicFacesEnabled: Boolean = false,
    private val random: Random = Random.Default,
) : TileWallFactory {
    override fun create(): TileWall = composition().createWall(random)

    override fun composition(): TileComposition {
        require(wallTileValues.all { it in 1..8 }) { "Pairing wall values must be between 1 and 8" }
        require(playerCount in 2..4) { "Example pairing supports two to four players" }
        require(wallTileValues.size * 4 >= playerCount * 3 + 1) {
            "Selected tile values do not provide enough tiles for $playerCount players"
        }
        val tiles = buildList {
            for (value in wallTileValues.sorted()) {
                repeat(4) { copyIndex ->
                    val tile = if (value == 1 && copyIndex == 3) {
                        ExamplePairingTileType.invertedOneMan()
                    } else {
                        Tile.Numeric(Tile.Suit.Character, value)
                    }
                    val components = if (publicFacesEnabled && copyIndex < PUBLIC_FACE_COPIES) PUBLIC_FACE_COMPONENTS else ComponentSet.EMPTY
                    add(TileValue(tile, components))
                }
            }
        }
        return TileComposition(tiles.groupingBy { it }.eachCount().map { (value, count) -> TileCompositionEntry(value, count) })
    }

    /** 公開牌面的張數與標記。 */
    private companion object {
        /** 每種點數帶公開牌面標記的張數。 */
        const val PUBLIC_FACE_COPIES = 3

        /** 公開牌面牌張的固定組件。 */
        val PUBLIC_FACE_COMPONENTS: ComponentSet = ComponentSet.EMPTY.with(ExamplePairingComponents.PUBLIC_FACE, Unit)
    }
}
