package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.imageio.ImageIO
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證放進方塊貼圖集的材質不會降低整張貼圖集的 mipmap 層數。 */
class AtlasTextureSizeTest {
    /**
     * 方塊與物品材質的長寬都是 16 的倍數。
     *
     * 方塊貼圖集的 mipmap 層數取決於其中所有材質長寬能被多大的 2 的次方整除。只要有一張不是 16 的倍數，
     * 整張貼圖集、包含原版方塊，在遠處都會少掉幾層縮圖。
     */
    @Test
    fun `block and item textures keep every mipmap level`() {
        val textures = ATLAS_TEXTURE_DIRECTORIES.flatMap { directory ->
            val root = resourceDirectory(directory)
            Files.walk(root).use { paths -> paths.filter { it.extension == "png" }.toList() }
        }
        assertTrue(textures.isNotEmpty(), "No atlas textures were found")

        val undersized = textures.mapNotNull { path ->
            val image = path.inputStream().use(ImageIO::read)
            "${path.fileName} is ${image.width}x${image.height}".takeIf { image.width % MIP_ALIGNMENT != 0 || image.height % MIP_ALIGNMENT != 0 }
        }

        assertEquals(emptyList(), undersized)
    }

    /**
     * 取得測試 classpath 上的資源資料夾。
     *
     * @param path 資源資料夾的路徑。
     * @return 資料夾在檔案系統上的位置。
     */
    private fun resourceDirectory(path: String): Path = Paths.get(checkNotNull(javaClass.getResource(path)) { "Missing resource directory $path" }.toURI())

    private companion object {
        /** 材質進入方塊貼圖集的資料夾。 */
        val ATLAS_TEXTURE_DIRECTORIES = listOf("/assets/mahjongcraft/textures/block", "/assets/mahjongcraft/textures/item")

        /** 保留原版預設 4 層 mipmap 所需的長寬倍數。 */
        const val MIP_ALIGNMENT: Int = 16
    }
}
