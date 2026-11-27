package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase

import java.awt.Color
import java.awt.LinearGradientPaint
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import kotlin.math.roundToInt

/**
 * 宣傳截圖的後製：從原圖裁出各平台需要的比例，並產生疊上標題的 GitHub 預覽圖。
 *
 * - 2:1：GitHub 社群預覽圖與 Modrinth 的精選圖片。
 * - 16:9：Modrinth 與 CurseForge 的圖庫。
 * - GitHub 預覽圖：2:1 縮成 [GITHUB_WIDTH]×[GITHUB_HEIGHT]，疊上標題，存成不超過 [GITHUB_MAX_BYTES] 的 JPEG。
 *   標題多為白色與淺綠色，所以畫面上方先疊一層往下變淡的深色漸層，標題外圍再加一圈柔和的陰影，
 *   讓標題在明亮的天空或櫻花前也看得清楚。
 */
internal object ShowcaseImages {
    /** GitHub 社群預覽圖的寬度。 */
    const val GITHUB_WIDTH: Int = 1280

    /** GitHub 社群預覽圖的高度。 */
    const val GITHUB_HEIGHT: Int = 640

    /** GitHub 社群預覽圖的大小上限。 */
    const val GITHUB_MAX_BYTES: Int = 1_000_000

    /** 標題寬度佔畫面寬度的比例。 */
    private const val TITLE_WIDTH_FRACTION = 0.62

    /** 標題上緣與畫面上緣的距離佔畫面高度的比例。 */
    private const val TITLE_TOP_FRACTION = 0.07

    /** 上方深色漸層在畫面最上緣的不透明度。 */
    private const val SCRIM_TOP_ALPHA = 0.28f

    /** 上方深色漸層在 [SCRIM_MIDDLE_FRACTION] 處的不透明度。 */
    private const val SCRIM_MIDDLE_ALPHA = 0.15f

    /** 上方深色漸層的中間點，佔漸層高度的比例。 */
    private const val SCRIM_MIDDLE_FRACTION = 0.6f

    /** 上方深色漸層延伸到標題下緣之後多遠，佔畫面高度的比例。 */
    private const val SCRIM_MARGIN_FRACTION = 0.12

    /** 標題陰影往外擴張的像素，形成一圈深色的邊。 */
    private const val SHADOW_SPREAD = 3

    /** 標題陰影每次模糊取前後多少像素。 */
    private const val SHADOW_BLUR = 6

    /** 標題陰影模糊的次數。 */
    private const val SHADOW_BLUR_PASSES = 3

    /** 標題陰影的不透明度。 */
    private const val SHADOW_OPACITY = 0.85f

    /** 標題陰影往下偏移的像素。 */
    private const val SHADOW_OFFSET_Y = 2

    /** [titleShadow] 的圖比標題每一邊多出的像素，讓擴張與模糊不被截掉。 */
    const val TITLE_SHADOW_PADDING: Int = SHADOW_SPREAD + SHADOW_BLUR * SHADOW_BLUR_PASSES

    /** JPEG 品質的起始值。超過大小上限時逐步降低。 */
    private const val JPEG_START_QUALITY = 0.92f

    /** JPEG 品質每次降低的量。 */
    private const val JPEG_QUALITY_STEP = 0.06f

    /** JPEG 品質的下限。 */
    private const val JPEG_MIN_QUALITY = 0.5f

    /**
     * 置中裁出指定比例的範圍，盡量保留原圖的大小。
     *
     * @param width 原圖寬度。
     * @param height 原圖高度。
     * @param ratioWidth 比例的寬。
     * @param ratioHeight 比例的高。
     * @return 裁切範圍。
     */
    fun centerCrop(width: Int, height: Int, ratioWidth: Int, ratioHeight: Int): Rectangle {
        require(width > 0 && height > 0 && ratioWidth > 0 && ratioHeight > 0) { "Sizes and ratio must be positive" }
        val heightForFullWidth = width.toLong() * ratioHeight / ratioWidth
        return if (heightForFullWidth <= height) {
            val cropHeight = heightForFullWidth.toInt()
            Rectangle(0, (height - cropHeight) / 2, width, cropHeight)
        } else {
            val cropWidth = (height.toLong() * ratioWidth / ratioHeight).toInt()
            Rectangle((width - cropWidth) / 2, 0, cropWidth, height)
        }
    }

    /**
     * 標題在畫面中的位置：水平置中，靠近上緣，保持標題原本的長寬比。
     *
     * @param canvasWidth 畫面寬度。
     * @param canvasHeight 畫面高度。
     * @param titleWidth 標題圖片寬度。
     * @param titleHeight 標題圖片高度。
     * @return 標題繪製的範圍。
     */
    fun titleBounds(canvasWidth: Int, canvasHeight: Int, titleWidth: Int, titleHeight: Int): Rectangle {
        val width = (canvasWidth * TITLE_WIDTH_FRACTION).roundToInt()
        val height = (width.toDouble() * titleHeight / titleWidth).roundToInt()
        return Rectangle((canvasWidth - width) / 2, (canvasHeight * TITLE_TOP_FRACTION).roundToInt(), width, height)
    }

    /**
     * 後製一張截圖，輸出到截圖所在的資料夾。
     *
     * @param source 原始截圖。
     * @param title 標題圖片。沒有時不產生 GitHub 預覽圖。
     * @return 產生的檔案，相對於截圖所在的資料夾。
     */
    fun process(source: Path, title: BufferedImage?): List<String> {
        val image = checkNotNull(ImageIO.read(source.toFile())) { "Cannot read $source" }
        val directory = source.parent
        val baseName = source.fileName.toString().removeSuffix(".png")
        val wide = image.cropped(centerCrop(image.width, image.height, ratioWidth = 2, ratioHeight = 1))
        val gallery = image.cropped(centerCrop(image.width, image.height, ratioWidth = 16, ratioHeight = 9))
        val outputs = mutableListOf("$baseName-2x1.png", "$baseName-16x9.png")
        ImageIO.write(wide, "png", directory.resolve(outputs[0]).toFile())
        ImageIO.write(gallery, "png", directory.resolve(outputs[1]).toFile())
        if (title != null) {
            val name = "$baseName-github.jpg"
            Files.write(directory.resolve(name), githubPreview(wide, title))
            outputs += name
        }
        return outputs
    }

    /**
     * 把 2:1 的圖縮成 GitHub 預覽圖的大小，疊上深色漸層、標題陰影與標題，編碼成不超過大小上限的 JPEG。
     *
     * @param wide 2:1 的圖。
     * @param title 標題圖片。
     * @return JPEG 的內容。
     */
    fun githubPreview(wide: BufferedImage, title: BufferedImage): ByteArray {
        val canvas = BufferedImage(GITHUB_WIDTH, GITHUB_HEIGHT, BufferedImage.TYPE_INT_RGB)
        val graphics = canvas.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(wide, 0, 0, GITHUB_WIDTH, GITHUB_HEIGHT, null)
            val bounds = titleBounds(GITHUB_WIDTH, GITHUB_HEIGHT, title.width, title.height)
            val scrimBottom = bounds.y + bounds.height + (GITHUB_HEIGHT * SCRIM_MARGIN_FRACTION).roundToInt()
            graphics.paint = LinearGradientPaint(
                0f,
                0f,
                0f,
                scrimBottom.toFloat(),
                floatArrayOf(0f, SCRIM_MIDDLE_FRACTION, 1f),
                arrayOf(Color(0f, 0f, 0f, SCRIM_TOP_ALPHA), Color(0f, 0f, 0f, SCRIM_MIDDLE_ALPHA), Color(0f, 0f, 0f, 0f)),
            )
            graphics.fillRect(0, 0, GITHUB_WIDTH, scrimBottom)
            val scaledTitle = title.scaledTo(bounds.width, bounds.height)
            val shadowX = bounds.x - TITLE_SHADOW_PADDING
            val shadowY = bounds.y - TITLE_SHADOW_PADDING + SHADOW_OFFSET_Y
            graphics.drawImage(titleShadow(scaledTitle), shadowX, shadowY, null)
            graphics.drawImage(scaledTitle, bounds.x, bounds.y, null)
        } finally {
            graphics.dispose()
        }
        var quality = JPEG_START_QUALITY
        while (true) {
            val bytes = encodeJpeg(canvas, quality)
            if (bytes.size <= GITHUB_MAX_BYTES || quality <= JPEG_MIN_QUALITY) return bytes
            quality -= JPEG_QUALITY_STEP
        }
    }

    /**
     * 依標題的輪廓產生柔和的深色陰影，讓淺色的標題在明亮的背景上也看得清楚。
     *
     * 陰影先把輪廓往外擴張 [SHADOW_SPREAD] 像素，形成一圈深色的邊，再模糊成柔和的光暈。
     * 輸出的圖比標題每一邊多出 [TITLE_SHADOW_PADDING] 像素。
     *
     * @param title 已經縮放成繪製大小的標題，透明的地方不產生陰影。
     * @return 黑色、只有透明度變化的陰影圖。
     */
    fun titleShadow(title: BufferedImage): BufferedImage {
        val padding = TITLE_SHADOW_PADDING
        val width = title.width + padding * 2
        val height = title.height + padding * 2
        var alpha = FloatArray(width * height)
        for (y in 0 until title.height) {
            for (x in 0 until title.width) alpha[(y + padding) * width + x + padding] = (title.getRGB(x, y) ushr 24) / 255f
        }
        alpha = spread(alpha, width, height, horizontal = true)
        alpha = spread(alpha, width, height, horizontal = false)
        repeat(SHADOW_BLUR_PASSES) {
            alpha = boxBlur(alpha, width, height, horizontal = true)
            alpha = boxBlur(alpha, width, height, horizontal = false)
        }
        val shadow = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (index in alpha.indices) {
            val value = (alpha[index] * SHADOW_OPACITY * 255).roundToInt().coerceIn(0, 255)
            shadow.setRGB(index % width, index / width, value shl 24)
        }
        return shadow
    }

    /**
     * 沿一個方向取周圍 [SHADOW_SPREAD] 像素內的最大值，讓輪廓往外擴張。
     *
     * @param values 每個像素的值，逐列排列。
     * @param width 寬度。
     * @param height 高度。
     * @param horizontal 沿水平方向時為 true，沿垂直方向時為 false。
     * @return 擴張後的值。
     */
    private fun spread(values: FloatArray, width: Int, height: Int, horizontal: Boolean): FloatArray = alongLine(values, width, height, horizontal, SHADOW_SPREAD) { window -> window.max() }

    /**
     * 沿一個方向取周圍 [SHADOW_BLUR] 像素內的平均值。來回多次後接近高斯模糊。
     *
     * @param values 每個像素的值，逐列排列。
     * @param width 寬度。
     * @param height 高度。
     * @param horizontal 沿水平方向時為 true，沿垂直方向時為 false。
     * @return 模糊後的值。
     */
    private fun boxBlur(values: FloatArray, width: Int, height: Int, horizontal: Boolean): FloatArray = alongLine(values, width, height, horizontal, SHADOW_BLUR) { window -> window.sum() / window.size }

    /**
     * 對每個像素，取同一列或同一行上前後 [radius] 像素內的值計算新的值。超出圖外的部分當作 0。
     *
     * @param values 每個像素的值，逐列排列。
     * @param width 寬度。
     * @param height 高度。
     * @param horizontal 沿水平方向時為 true，沿垂直方向時為 false。
     * @param radius 前後各取多少像素。
     * @param combine 由範圍內的值算出新的值。
     * @return 新的值。
     */
    private inline fun alongLine(
        values: FloatArray,
        width: Int,
        height: Int,
        horizontal: Boolean,
        radius: Int,
        combine: (FloatArray) -> Float,
    ): FloatArray {
        val result = FloatArray(values.size)
        val window = FloatArray(radius * 2 + 1)
        for (y in 0 until height) {
            for (x in 0 until width) {
                for (offset in -radius..radius) {
                    val sampleX = if (horizontal) x + offset else x
                    val sampleY = if (horizontal) y else y + offset
                    val inside = sampleX in 0 until width && sampleY in 0 until height
                    window[offset + radius] = if (inside) values[sampleY * width + sampleX] else 0f
                }
                result[y * width + x] = combine(window)
            }
        }
        return result
    }

    /**
     * 以高品質縮放成指定大小，保留透明度。
     *
     * @param width 新的寬度。
     * @param height 新的高度。
     * @return 新的圖。
     */
    private fun BufferedImage.scaledTo(width: Int, height: Int): BufferedImage {
        val result = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = result.createGraphics()
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            graphics.drawImage(this, 0, 0, width, height, null)
        } finally {
            graphics.dispose()
        }
        return result
    }

    /**
     * 以指定品質編碼 JPEG。
     *
     * @param image 要編碼的圖。
     * @param quality 品質，0 到 1。
     * @return JPEG 的內容。
     */
    private fun encodeJpeg(image: BufferedImage, quality: Float): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpg").next()
        val output = ByteArrayOutputStream()
        try {
            ImageIO.createImageOutputStream(output).use { stream ->
                writer.output = stream
                val parameters = writer.defaultWriteParam.apply {
                    compressionMode = ImageWriteParam.MODE_EXPLICIT
                    compressionQuality = quality
                }
                writer.write(null, IIOImage(image, null, null), parameters)
            }
        } finally {
            writer.dispose()
        }
        return output.toByteArray()
    }

    /**
     * 複製出指定範圍的圖，不與原圖共用像素。
     *
     * @param bounds 範圍。
     * @return 新的圖。
     */
    private fun BufferedImage.cropped(bounds: Rectangle): BufferedImage {
        val result = BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_RGB)
        val graphics = result.createGraphics()
        try {
            graphics.drawImage(this, 0, 0, bounds.width, bounds.height, bounds.x, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height, null)
        } finally {
            graphics.dispose()
        }
        return result
    }
}
