package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase

import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.Random
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證宣傳截圖後製的裁切、標題位置與輸出。 */
class ShowcaseImagesTest {
    /** 16:9 的截圖裁成 2:1 時保留全寬，上下各裁掉一半的差。 */
    @Test
    fun `a 16 by 9 screenshot is cropped to 2 by 1 keeping the full width`() {
        assertEquals(Rectangle(0, 80, 2560, 1280), ShowcaseImages.centerCrop(2560, 1440, ratioWidth = 2, ratioHeight = 1))
    }

    /** 已經是目標比例時不裁切。 */
    @Test
    fun `a screenshot already at the ratio is kept whole`() {
        assertEquals(Rectangle(0, 0, 2560, 1440), ShowcaseImages.centerCrop(2560, 1440, ratioWidth = 16, ratioHeight = 9))
    }

    /** 比目標比例高時保留全高，左右置中裁切。 */
    @Test
    fun `a narrow screenshot is cropped on both sides`() {
        assertEquals(Rectangle(80, 0, 1440, 810), ShowcaseImages.centerCrop(1600, 810, ratioWidth = 16, ratioHeight = 9))
    }

    /** 標題水平置中、靠近上緣，並保持原本的長寬比。 */
    @Test
    fun `the title is centred near the top and keeps its aspect ratio`() {
        val bounds = ShowcaseImages.titleBounds(canvasWidth = 1280, canvasHeight = 640, titleWidth = 3826, titleHeight = 622)

        assertEquals(1280 - bounds.width, bounds.x * 2, "horizontal centre")
        assertTrue(bounds.y in 1..640 / 5, "near the top: ${bounds.y}")
        assertEquals(3826.0 / 622.0, bounds.width.toDouble() / bounds.height, 0.02)
    }

    /** 雜訊很多、難以壓縮的畫面，GitHub 預覽圖仍然符合大小與尺寸。 */
    @Test
    fun `the GitHub preview stays within the size limit`() {
        val random = Random(1)
        val wide = BufferedImage(2560, 1280, BufferedImage.TYPE_INT_RGB).apply {
            for (y in 0 until height) for (x in 0 until width) setRGB(x, y, random.nextInt(0x1000000))
        }
        val title = BufferedImage(400, 60, BufferedImage.TYPE_INT_ARGB)

        val bytes = ShowcaseImages.githubPreview(wide, title)

        assertTrue(bytes.size <= ShowcaseImages.GITHUB_MAX_BYTES, "size ${bytes.size}")
        val decoded = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(ShowcaseImages.GITHUB_WIDTH, decoded.width)
        assertEquals(ShowcaseImages.GITHUB_HEIGHT, decoded.height)
    }

    /** 白色背景上，畫面上緣被深色漸層壓暗，漸層以下維持原本的亮度。 */
    @Test
    fun `the GitHub preview darkens the top and fades out below the title`() {
        val wide = BufferedImage(2560, 1280, BufferedImage.TYPE_INT_RGB).apply {
            createGraphics().run {
                color = Color.WHITE
                fillRect(0, 0, width, height)
                dispose()
            }
        }

        val preview = ImageIO.read(ByteArrayInputStream(ShowcaseImages.githubPreview(wide, BufferedImage(400, 60, BufferedImage.TYPE_INT_ARGB))))

        assertTrue(preview.brightness(10, 2) < 200, "top corner: ${preview.brightness(10, 2)}")
        assertTrue(preview.brightness(10, 600) > 245, "lower area: ${preview.brightness(10, 600)}")
    }

    /** 標題陰影包住標題外圍一圈，離標題越遠越淡，遠處完全透明。 */
    @Test
    fun `the title shadow surrounds the title and fades away`() {
        val title = BufferedImage(40, 20, BufferedImage.TYPE_INT_ARGB).apply {
            createGraphics().run {
                color = Color.WHITE
                fillRect(10, 5, 20, 10)
                dispose()
            }
        }
        val padding = ShowcaseImages.TITLE_SHADOW_PADDING

        val shadow = ShowcaseImages.titleShadow(title)

        assertEquals(title.width + padding * 2, shadow.width)
        assertEquals(title.height + padding * 2, shadow.height)
        val centreY = padding + 10
        val edge = shadow.alphaAt(padding + 8, centreY)
        val farther = shadow.alphaAt(padding + 2, centreY)
        assertTrue(edge > 60, "next to the title: $edge")
        assertTrue(farther in 1 until edge, "farther away: $farther")
        assertEquals(0, shadow.alphaAt(0, 0), "corner")
    }

    /** 後製在截圖旁輸出兩種比例，有標題時另外輸出 GitHub 預覽圖。 */
    @Test
    fun `processing writes both crops and the GitHub preview`() {
        val directory = createTempDirectory("mahjongcraft-showcase-images")
        try {
            val source = directory.resolve("showcase-table.png")
            val screenshot = BufferedImage(2560, 1440, BufferedImage.TYPE_INT_RGB).apply {
                createGraphics().run {
                    color = Color(40, 90, 60)
                    fillRect(0, 0, width, height)
                    dispose()
                }
            }
            ImageIO.write(screenshot, "png", source.toFile())

            val outputs = ShowcaseImages.process(source, BufferedImage(400, 60, BufferedImage.TYPE_INT_ARGB))

            assertEquals(listOf("showcase-table-2x1.png", "showcase-table-16x9.png", "showcase-table-github.jpg"), outputs)
            outputs.forEach { assertTrue(directory.resolve(it).exists(), "$it exists") }
            assertEquals(1280, ImageIO.read(directory.resolve(outputs[0]).toFile()).height)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    /** 沒有標題時只輸出兩種比例。 */
    @Test
    fun `processing without a title skips the GitHub preview`() {
        val directory = createTempDirectory("mahjongcraft-showcase-images")
        try {
            val source = directory.resolve("shot.png")
            ImageIO.write(BufferedImage(1920, 1080, BufferedImage.TYPE_INT_RGB), "png", source.toFile())

            assertEquals(listOf("shot-2x1.png", "shot-16x9.png"), ShowcaseImages.process(source, title = null))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    /** 一個像素紅綠藍的平均亮度，0 到 255。 */
    private fun BufferedImage.brightness(x: Int, y: Int): Int {
        val rgb = getRGB(x, y)
        return ((rgb shr 16 and 0xFF) + (rgb shr 8 and 0xFF) + (rgb and 0xFF)) / 3
    }

    /** 一個像素的不透明度，0 到 255。 */
    private fun BufferedImage.alphaAt(x: Int, y: Int): Int = getRGB(x, y) ushr 24
}
