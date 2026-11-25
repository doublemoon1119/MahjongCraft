package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 從標準牌面與牌身貼圖產生透明牌的貼圖與模型。產生的檔案與其他資源一起保存在 src 底下，可再手動修圖。
 *
 * 只在標準牌面或牌身改動後手動執行，一般建置不會執行。對每種標準牌面 `mahjong_tile_<key>.png` 產生：
 * - `mahjong_tile_transparent_<key>.png`：只留圖案。牌面最外圈出現最多的顏色視為底色。每個像素依比底色暗多少決定
 *   不透明度，並扣除底色成分還原圖案顏色，讓圖案邊緣不留淡白光暈。不比底色暗的像素（底色、斜角亮面、雜點）全透明。
 * - `mahjong_tile_transparent_<key>_preview.png`：同樣的圖案墊上半透明白底，供平面顯示使用。
 * - `mahjong_tile_transparent_<key>.json`：透明牌面的 item model。
 *
 * 另外產生 `mahjong_tile_cover_transparent.png`：牌身的綠色牌背依深淺換成牌身白色後，整張降低不透明度。
 *
 * @property textureDirectory 牌面貼圖目錄，讀取標準貼圖並寫入透明版。
 * @property modelDirectory 牌面 item model 目錄，寫入透明版模型。
 */
abstract class GenerateTransparentTileTexturesTask : DefaultTask() {
    @get:Internal
    abstract val textureDirectory: DirectoryProperty

    @get:Internal
    abstract val modelDirectory: DirectoryProperty

    /** 產生全部透明牌貼圖、預覽貼圖、模型與透明牌身。 */
    @TaskAction
    fun generate() {
        val textures = textureDirectory.get().asFile
        val models = modelDirectory.get().asFile
        STANDARD_KEYS.forEach { key ->
            val pattern = removeBackground(read(textures.resolve("mahjong_tile_$key.png")))
            write(pattern, textures.resolve("mahjong_tile_transparent_$key.png"))
            write(withBackdrop(pattern), textures.resolve("mahjong_tile_transparent_${key}_preview.png"))
            models.resolve("mahjong_tile_transparent_$key.json").writeText(modelJson(key))
        }
        write(transparentBody(read(textures.resolve("mahjong_tile_cover.png"))), textures.resolve("mahjong_tile_cover_transparent.png"))
        logger.lifecycle("Wrote ${STANDARD_KEYS.size} transparent faces with previews and models, and the transparent body. Review them before committing.")
    }

    /**
     * 只留圖案：不比底色暗的像素全透明，其他像素依變暗程度決定不透明度並還原圖案顏色。
     *
     * @param source 標準牌面。
     * @return 去掉底色的牌面。
     */
    private fun removeBackground(source: BufferedImage): BufferedImage {
        val background = mostCommon(borderPixels(source))
        return map(source) { argb ->
            val colour = rgb(argb)
            val alpha = (0..2).maxOf { max(0, background[it] - colour[it]).toDouble() / background[it] }
            if (alpha < MINIMUM_PATTERN_ALPHA) {
                0
            } else {
                val ink = IntArray(3) { ((colour[it] - (1 - alpha) * background[it]) / alpha).roundToInt().coerceIn(0, 255) }
                argb((255 * alpha.coerceAtMost(1.0)).roundToInt(), ink)
            }
        }
    }

    /**
     * 圖案墊上半透明白底。
     *
     * @param pattern 去掉底色的牌面。
     * @return 平面預覽用的牌面。
     */
    private fun withBackdrop(pattern: BufferedImage): BufferedImage = map(pattern) { argb ->
        val alpha = (argb ushr 24) / 255.0
        val backdrop = PREVIEW_BACKDROP_ALPHA / 255.0
        val outAlpha = alpha + backdrop * (1 - alpha)
        val colour = rgb(argb)
        val blended = IntArray(3) { ((colour[it] * alpha + 255 * backdrop * (1 - alpha)) / outAlpha).roundToInt().coerceIn(0, 255) }
        argb((outAlpha * 255).roundToInt(), blended)
    }

    /**
     * 綠色牌背依深淺換成牌身白色，整張降低不透明度。
     *
     * @param source 標準牌身。
     * @return 透明牌身。
     */
    private fun transparentBody(source: BufferedImage): BufferedImage {
        val opaque = pixels(source).filter { (it ushr 24) > 0 }.map(::rgb)
        val white = mostCommon(opaque.filterNot(::isGreen))
        val green = mostCommon(opaque.filter(::isGreen))
        return map(source) { argb ->
            val colour = rgb(argb)
            val recoloured = if (isGreen(colour)) {
                val ratio = luminance(colour) / luminance(green)
                IntArray(3) { (white[it] * ratio).roundToInt().coerceIn(0, 255) }
            } else {
                colour
            }
            argb(((argb ushr 24) * BODY_ALPHA).roundToInt(), recoloured)
        }
    }

    private fun read(file: File): BufferedImage = requireNotNull(ImageIO.read(file)) { "Cannot read texture: $file" }

    private fun write(image: BufferedImage, file: File) {
        check(ImageIO.write(image, "png", file)) { "Cannot write texture: $file" }
    }

    private fun pixels(image: BufferedImage): List<Int> = (0 until image.height).flatMap { y -> (0 until image.width).map { x -> image.getRGB(x, y) } }

    private fun borderPixels(image: BufferedImage): List<IntArray> = (0 until image.height).flatMap { y ->
        (0 until image.width).filter { x -> x < BORDER_WIDTH || y < BORDER_WIDTH || x >= image.width - BORDER_WIDTH || y >= image.height - BORDER_WIDTH }
            .map { x -> rgb(image.getRGB(x, y)) }
    }

    private fun map(source: BufferedImage, transform: (Int) -> Int): BufferedImage {
        val result = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) result.setRGB(x, y, transform(source.getRGB(x, y)))
        }
        return result
    }

    private fun mostCommon(colours: List<IntArray>): IntArray = colours.groupingBy { it.toList() }.eachCount().maxBy { it.value }.key.toIntArray()

    private fun rgb(argb: Int): IntArray = intArrayOf((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

    private fun argb(alpha: Int, colour: IntArray): Int = (alpha shl 24) or (colour[0] shl 16) or (colour[1] shl 8) or colour[2]

    private fun isGreen(colour: IntArray): Boolean = colour[1] > colour[0] + GREEN_MARGIN && colour[1] > colour[2] + GREEN_MARGIN

    private fun luminance(colour: IntArray): Double = 0.2126 * colour[0] + 0.7152 * colour[1] + 0.0722 * colour[2]

    private fun modelJson(key: String): String = """
        |{
        |	"parent": "mahjongcraft:item/mahjong_tile_base",
        |	"textures": {
        |		"0": "mahjongcraft:item/mahjong_tile/mahjong_tile_transparent_$key",
        |		"1": "mahjongcraft:item/mahjong_tile/mahjong_tile_cover_transparent",
        |		"particle": "mahjongcraft:item/mahjong_tile/mahjong_tile_cover_transparent"
        |	}
        |}
    """.trimMargin()

    private companion object {
        /** 有透明版的標準牌面 key：萬、筒、條一到九與七種字牌。 */
        val STANDARD_KEYS: List<String> = listOf("m", "p", "s").flatMap { suit -> (1..9).map { "$suit$it" } } +
            listOf("east", "south", "west", "north", "white_dragon", "green_dragon", "red_dragon")

        /** 低於此不透明度的圖案像素視為底色雜訊，直接全透明。 */
        const val MINIMUM_PATTERN_ALPHA = 0.15

        /** 透明牌身的不透明度倍率。 */
        const val BODY_ALPHA = 0.18

        /** 平面預覽白底的不透明度（0 到 255）。 */
        const val PREVIEW_BACKDROP_ALPHA = 102

        /** 判斷綠色牌背時，綠色通道至少要比紅、藍通道多出的量。 */
        const val GREEN_MARGIN = 20

        /** 判斷底色時取樣的外圈寬度。 */
        const val BORDER_WIDTH = 2
    }
}
