package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.config

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.TestLoaderPlatform
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/** [LoaderServerConfigPathProvider] 的環境分流與安全顯示路徑測試。 */
class ServerConfigPathProviderTest {
    /** 單人整合伺服器應將設定放入目前世界存檔。 */
    @Test
    fun `test integrated server uses world config location`() {
        val location = LoaderServerConfigPathProvider(TestLoaderPlatform).resolve(
            isDedicated = false,
            worldSavePath = Path.of("world-save"),
            loaderConfigPath = Path.of("loader-config"),
        )

        assertEquals(Path.of("world-save/serverconfig/mahjongcraft.toml"), location.path)
        assertEquals("<world save>/serverconfig/mahjongcraft.toml", location.displayPath)
    }

    /** 專用伺服器應將設定放入載入器的共用設定目錄。 */
    @Test
    fun `test dedicated server uses loader config location`() {
        val location = LoaderServerConfigPathProvider(TestLoaderPlatform).resolve(
            isDedicated = true,
            worldSavePath = Path.of("world-save"),
            loaderConfigPath = Path.of("loader-config"),
        )

        assertEquals(Path.of("loader-config/mahjongcraft/server.toml"), location.path)
        assertEquals("<server directory>/config/mahjongcraft/server.toml", location.displayPath)
    }
}
