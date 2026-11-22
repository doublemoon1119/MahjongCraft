package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.config

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.LoaderPlatform
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.config.ServerConfigLocation
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.config.ServerConfigPathProvider
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Path

/**
 * 由世界存檔或載入器的設定檔資料夾解析 server config 位置。
 *
 * @property platform 提供設定檔資料夾的載入器資訊。
 */
class LoaderServerConfigPathProvider(private val platform: LoaderPlatform) : ServerConfigPathProvider {
    /**
     * 單人世界使用存檔內設定；專用伺服器使用執行個體共用設定。
     *
     * @param server 目前 Minecraft server。
     * @return 設定檔的實際路徑與可供訊息使用的顯示路徑。
     */
    override fun get(server: MinecraftServer): ServerConfigLocation = resolve(
        isDedicated = server.isDedicatedServer,
        worldSavePath = server.getWorldPath(LevelResource.ROOT),
        loaderConfigPath = platform.configDirectory,
    )

    /**
     * 依環境與兩種根目錄建立可測試的設定位置。
     *
     * @param isDedicated 是否為 dedicated server。
     * @param worldSavePath 世界存檔根目錄。
     * @param loaderConfigPath 載入器的設定檔資料夾。
     * @return 專用伺服器共用設定或單人世界設定的檔案位置。
     */
    internal fun resolve(
        isDedicated: Boolean,
        worldSavePath: Path,
        loaderConfigPath: Path,
    ): ServerConfigLocation = if (isDedicated) {
        ServerConfigLocation(
            path = loaderConfigPath.resolve(MinecraftModMetadata.MOD_ID).resolve("server.toml"),
            displayPath = "<server directory>/config/${MinecraftModMetadata.MOD_ID}/server.toml",
        )
    } else {
        ServerConfigLocation(
            path = worldSavePath.resolve("serverconfig").resolve("${MinecraftModMetadata.MOD_ID}.toml"),
            displayPath = "<world save>/serverconfig/${MinecraftModMetadata.MOD_ID}.toml",
        )
    }
}
