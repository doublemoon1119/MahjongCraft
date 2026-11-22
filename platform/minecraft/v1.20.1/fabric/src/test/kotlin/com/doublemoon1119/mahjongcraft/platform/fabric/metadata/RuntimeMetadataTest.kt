package com.doublemoon1119.mahjongcraft.platform.fabric.metadata

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.LoaderPlatform
import net.minecraft.SharedConstants
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** 驗證 [RuntimeMetadata] 以載入器回報的資訊組成初始化訊息。 */
class RuntimeMetadataTest {
    /** 讀取執行中的 Minecraft 版本前先偵測版本資訊。 */
    @BeforeTest
    fun detectVersion() {
        SharedConstants.tryDetectVersion()
    }

    /** 初始化訊息包含 mod 版本、載入器名稱與 Minecraft 版本。 */
    @Test
    fun `initialization message names the loader and versions`() {
        val metadata = RuntimeMetadata(TestPlatform)

        assertEquals("1.2.3", metadata.modVersion)
        assertEquals(
            expected = "MahjongCraft 1.2.3 (TestLoader, Minecraft ${SharedConstants.getCurrentVersion().name}) initialized.",
            actual = metadata.initializationMessage(),
        )
    }

    /** 只提供名稱與版本的載入器資訊。 */
    private object TestPlatform : LoaderPlatform {
        override val name: String = "TestLoader"
        override val isClient: Boolean = false
        override val isDevelopment: Boolean = false
        override val configDirectory: Path = Path.of("config")
        override val modVersion: String = "1.2.3"

        override fun isModLoaded(modId: String): Boolean = false
    }
}
