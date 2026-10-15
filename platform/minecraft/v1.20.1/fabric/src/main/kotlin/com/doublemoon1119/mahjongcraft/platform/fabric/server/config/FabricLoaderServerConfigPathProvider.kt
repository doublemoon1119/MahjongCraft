package com.doublemoon1119.mahjongcraft.platform.fabric.server.config

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import net.minecraft.util.WorldSavePath
import java.nio.file.Path

/** 由世界存檔或 Fabric Loader config directory 解析 server config 位置。 */
class FabricLoaderServerConfigPathProvider : FabricServerConfigPathProvider {
    /**
     * 單人世界使用存檔內設定；專用伺服器使用執行個體共用設定。
     *
     * @param server 目前 Minecraft server。
     * @return 設定檔的實際路徑與可供訊息使用的顯示路徑。
     */
    override fun get(server: MinecraftServer): FabricServerConfigLocation = resolve(
        isDedicated = server.isDedicated,
        worldSavePath = server.getSavePath(WorldSavePath.ROOT),
        loaderConfigPath = FabricLoader.getInstance().configDir,
    )

    /**
     * 依環境與兩種根目錄建立可測試的設定位置。
     *
     * @param isDedicated 是否為 dedicated server。
     * @param worldSavePath 世界存檔根目錄。
     * @param loaderConfigPath Fabric Loader config 目錄。
     * @return 專用伺服器共用設定或單人世界設定的檔案位置。
     */
    internal fun resolve(
        isDedicated: Boolean,
        worldSavePath: Path,
        loaderConfigPath: Path,
    ): FabricServerConfigLocation = if (isDedicated) {
        FabricServerConfigLocation(
            path = loaderConfigPath.resolve(MinecraftModMetadata.MOD_ID).resolve("server.toml"),
            displayPath = "<server directory>/config/${MinecraftModMetadata.MOD_ID}/server.toml",
        )
    } else {
        FabricServerConfigLocation(
            path = worldSavePath.resolve("serverconfig").resolve("${MinecraftModMetadata.MOD_ID}.toml"),
            displayPath = "<world save>/serverconfig/${MinecraftModMetadata.MOD_ID}.toml",
        )
    }
}
