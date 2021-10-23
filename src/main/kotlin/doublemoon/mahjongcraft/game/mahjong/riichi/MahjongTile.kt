package doublemoon.mahjongcraft.game.mahjong.riichi

import doublemoon.mahjongcraft.MOD_ID
import doublemoon.mahjongcraft.entity.MahjongTileEntity
import doublemoon.mahjongcraft.id
import net.minecraft.text.LiteralText
import net.minecraft.text.MutableText
import net.minecraft.text.TranslatableText
import net.minecraft.util.Identifier
import org.mahjong4j.tile.Tile
import org.mahjong4j.tile.TileType
import java.util.*

/**
 * 所有麻將牌
 * */
enum class MahjongTile {
    M1,
    M2,
    M3,
    M4,
    M5,
    M6,
    M7,
    M8,
    M9,

    P1,
    P2,
    P3,
    P4,
    P5,
    P6,
    P7,
    P8,
    P9,

    S1,
    S2,
    S3,
    S4,
    S5,
    S6,
    S7,
    S8,
    S9,

    EAST,//東
    SOUTH,//南
    WEST,//西
    NORTH,//北

    WHITE_DRAGON,//白
    GREEN_DRAGON,//発
    RED_DRAGON,//中

    M5_RED,
    P5_RED,
    S5_RED,

    UNKNOWN;

    /**
     * 這張牌的牌面的材質位置
     * */
    val surfaceIdentifier: Identifier
        get() = id("textures/item/mahjong_tile/mahjong_tile_${name.lowercase()}.png")

    /**
     * 是否是赤牌
     * */
    val isRed: Boolean
        get() = when (this) {
            M5_RED -> true
            P5_RED -> true
            S5_RED -> true
            else -> false
        }

    /**
     * 對應 [MahjongTile] 和 [MahjongTileEntity] 的 code 編號 (用來決定牌的外觀),
     * 直接使用 [ordinal]
     * */
    val code: Int = ordinal

    /**
     * 對應 mahjong4j 之中的 [Tile],
     * 只有紅寶牌是 原本不是紅寶牌的牌 (ex: 赤五餅原本是五餅) 以及 [UNKNOWN] 是指定一萬 (應該不會用到),
     * 剩下的牌按照原本的 [code] 直接使用
     * */
    val mahjong4jTile: Tile
        get() {
            val tileCode = when (code) {
                M5_RED.code -> M5.code
                P5_RED.code -> P5.code
                S5_RED.code -> S5.code
                UNKNOWN.code -> M1.code
                else -> code
            }
            return Tile.valueOf(tileCode)
        }

    /**
     * 這張牌的排列順序,
     * 只有紅寶牌有調整, 剩下都按照 [code]
     * */
    val sortOrder: Int
        get() = when (this) {
            M5_RED -> 4
            P5_RED -> 13
            S5_RED -> 22
            else -> code
        }

    /**
     * 取得在順序上的下一張牌,
     * ex: 北->東, 1 萬-> 2 萬, 9 餅 -> 1 餅
     */
    val nextTile: MahjongTile
        get() {
            with(mahjong4jTile) {
                val nextTileCode = when (type) {
                    TileType.FONPAI -> if (this == Tile.PEI) Tile.TON.code else code + 1
                    TileType.SANGEN -> if (this == Tile.CHN) Tile.HAK.code else code + 1
                    else -> if (number == 9) code - 8 else code + 1
                }
                return values()[nextTileCode]
            }
        }

    /**
     * 取得在順序上的上一張牌,
     * ex: 北->東, 1 萬-> 2 萬, 9 餅 -> 1 餅
     */
    val previousTile: MahjongTile
        get() {
            with(mahjong4jTile) {
                val previousTileCode = when (type) {
                    TileType.FONPAI -> if (this == Tile.TON) Tile.PEI.code else code - 1
                    TileType.SANGEN -> if (this == Tile.HAK) Tile.CHN.code else code - 1
                    else -> if (number == 1) code + 8 else code - 1
                }
                return values()[previousTileCode]
            }
        }

    val displayName: MutableText
        get() {
            return when (mahjong4jTile.type) {
                TileType.MANZU -> TranslatableText("$MOD_ID.game.tile.man", mahjong4jTile.number)
                TileType.PINZU -> TranslatableText("$MOD_ID.game.tile.pin", mahjong4jTile.number)
                TileType.SOHZU -> TranslatableText("$MOD_ID.game.tile.sou", mahjong4jTile.number)
                TileType.FONPAI, TileType.SANGEN -> TranslatableText("$MOD_ID.game.tile.${name.lowercase()}")
                else -> LiteralText("")
            }
        }

    val displayNameString: String
        get() = displayName.string

    companion object {

        fun random(): MahjongTile = values().random()

        /**
         * 一般的牌山, 沒有赤寶牌
         * */
        val normalWall = mutableListOf<MahjongTile>().apply {
            values().forEach { tile ->
                repeat(4) { this += tile }
                if (tile == RED_DRAGON) return@apply
            }
        }

        /**
         * 有 3 張赤寶牌的牌山
         * */
        val redFive3Wall = normalWall.toMutableList().apply {
            this -= M5
            this -= P5
            this -= S5
            this += M5_RED
            this += P5_RED
            this += S5_RED
        }

        /**
         * 有 4 張赤寶牌的牌山, (5 餅 * 2)
         * */
        val redFive4Wall = redFive3Wall.toMutableList().apply {
            this -= P5
            this += P5_RED
        }
    }
}