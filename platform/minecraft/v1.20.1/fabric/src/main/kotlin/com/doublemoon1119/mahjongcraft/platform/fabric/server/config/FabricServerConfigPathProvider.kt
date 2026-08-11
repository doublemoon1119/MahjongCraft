package com.doublemoon1119.mahjongcraft.platform.fabric.server.config

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.MinecraftServer
import net.minecraft.util.WorldSavePath
import org.koin.core.annotation.Single
import java.nio.file.Path

/**
 * Fabric server config 的實際位置與可安全顯示的位置。
 *
 * @property path 供檔案 I/O 使用的完整路徑。
 * @property displayPath 不包含主機目錄資訊、可供指令輸出的邏輯路徑。
 */
data class FabricServerConfigLocation(
    val path: Path,
    val displayPath: String,
)

/** 依目前 Fabric server 類型提供 MahjongCraft server config 位置。 */
interface FabricServerConfigPathProvider {
    /** 解析 [server] 這次 session 使用的 server config 位置。 */
    fun get(server: MinecraftServer): FabricServerConfigLocation
}

/** 由世界存檔或 Fabric Loader config directory 解析 server config 位置。 */
@Single(binds = [FabricServerConfigPathProvider::class])
class FabricLoaderServerConfigPathProvider : FabricServerConfigPathProvider {
    /** 單人世界使用存檔內設定；專用伺服器使用執行個體共用設定。 */
    override fun get(server: MinecraftServer): FabricServerConfigLocation = resolve(
        isDedicated = server.isDedicated,
        worldSavePath = server.getSavePath(WorldSavePath.ROOT),
        loaderConfigPath = FabricLoader.getInstance().configDir,
    )

    /** 依環境與兩種根目錄建立可測試的設定位置。 */
    internal fun resolve(
        isDedicated: Boolean,
        worldSavePath: Path,
        loaderConfigPath: Path,
    ): FabricServerConfigLocation = if (isDedicated) {
        FabricServerConfigLocation(
            path = loaderConfigPath
                .resolve(MinecraftModMetadata.MOD_ID)
                .resolve("server.toml"),
            displayPath = "<server directory>/config/${MinecraftModMetadata.MOD_ID}/server.toml",
        )
    } else {
        FabricServerConfigLocation(
            path = worldSavePath
                .resolve("serverconfig")
                .resolve("${MinecraftModMetadata.MOD_ID}.toml"),
            displayPath = "<world save>/serverconfig/${MinecraftModMetadata.MOD_ID}.toml",
        )
    }
}
