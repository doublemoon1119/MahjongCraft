package com.doublemoon1119.mahjongcraft.platform.fabric.server.config

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigTomlCodec
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigUpdateResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.minecraft.server.MinecraftServer
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Fabric config directory 與共用 server config codec 之間的檔案 adapter。 */
@Single
class FabricServerConfigManager(
    @Provided private val state: MinecraftServerConfigState,
    @Provided private val codec: MinecraftServerConfigTomlCodec,
    private val pathProvider: FabricServerConfigPathProvider,
) {
    /** 記錄設定檔建立、載入與失敗原因。 */
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /** 目前 server session 的設定位置；未啟動 server 時為 `null`。 */
    private var location: FabricServerConfigLocation? = null

    /** 目前 server config TOML 的完整路徑。 */
    val path: Path
        get() = requireLocation().path

    /** 目前 server config TOML 可供指令輸出的安全邏輯路徑。 */
    val displayPath: String
        get() = requireLocation().displayPath

    /** 目前記憶體內實際生效的 server config。 */
    val current: MinecraftServerConfig
        get() = state.current

    /** 綁定 [server] 的設定位置、重設前一個 session 的值並載入設定。 */
    fun attach(server: MinecraftServer): MinecraftServerConfigUpdateResult = attach(pathProvider.get(server))

    /** 清除目前 server session 的設定位置與有效設定。 */
    fun detach() {
        location = null
        state.reset()
    }

    /** 綁定明確位置，供平台測試驗證不依賴 Minecraft server 實例的檔案行為。 */
    internal fun attach(location: FabricServerConfigLocation): MinecraftServerConfigUpdateResult {
        this.location = location
        state.reset()
        return initialize()
    }

    /**
     * 初始化設定檔；缺少檔案時先建立帶註解的預設 template。
     *
     * 失敗時保留 [MinecraftServerConfigState] 的程式預設值，且不覆寫損壞檔案。
     */
    private fun initialize(): MinecraftServerConfigUpdateResult {
        val createdDefaultFile = try {
            createDefaultFileIfMissing()
        } catch (exception: Exception) {
            return failure(
                userMessage = "Unable to create server config at $displayPath: ${exception.message}",
                logMessage = "Unable to create default server config at $path: ${exception.message}",
                cause = exception,
            )
        }
        return load(createdDefaultFile)
    }

    /** 重新讀取完整 TOML；只有解碼與驗證全部成功才替換有效設定。 */
    fun reload(): MinecraftServerConfigUpdateResult = load(createdDefaultFile = false)

    /** 將目前記憶體內的有效設定輸出成不含註解的標準 TOML。 */
    fun formattedCurrentToml(): String = codec.encode(state.current)

    /** 讀取、解碼並交易式替換有效設定。 */
    private fun load(createdDefaultFile: Boolean): MinecraftServerConfigUpdateResult = try {
        val content = Files.readString(path, StandardCharsets.UTF_8)
        val config = codec.decode(content)
        state.replace(config)
        MinecraftServerConfigUpdateResult.Success(config, createdDefaultFile)
    } catch (exception: Exception) {
        failure(
            userMessage = "Unable to load server config at $displayPath: ${exception.message}",
            logMessage = "Unable to load server config at $path: ${exception.message}",
            cause = exception,
        )
    }

    /** 在設定檔缺少時原樣複製 resource template。 */
    private fun createDefaultFileIfMissing(): Boolean {
        if (Files.exists(path)) return false
        Files.createDirectories(path.parent)
        val template = checkNotNull(javaClass.getResourceAsStream(DEFAULT_TEMPLATE_RESOURCE)) {
            "Missing bundled server config template $DEFAULT_TEMPLATE_RESOURCE"
        }
        template.use { input ->
            Files.copy(input, path)
        }
        return true
    }

    /** 建立失敗結果並保留完整 server log。 */
    private fun failure(
        userMessage: String,
        logMessage: String,
        cause: Throwable,
    ): MinecraftServerConfigUpdateResult.Failure {
        logger.error(logMessage, cause)
        return MinecraftServerConfigUpdateResult.Failure(userMessage, cause)
    }

    /** 取得目前位置；server session 尚未綁定時拒絕檔案操作。 */
    private fun requireLocation(): FabricServerConfigLocation = checkNotNull(location) {
        "Server config is not attached to a running server"
    }

    /** Config resource 與檔案寫入選項。 */
    private companion object {
        /** 打包於 Minecraft common resources 的帶註解預設 template。 */
        const val DEFAULT_TEMPLATE_RESOURCE: String = "/mahjongcraft/server-config-default.toml"
    }
}
