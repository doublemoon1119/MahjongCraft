package com.doublemoon1119.mahjongcraft.platform.fabric.server.config

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.HistoryRetentionCoordinator
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.retentionPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigTomlCodec
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigUpdateResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.minecraft.server.MinecraftServer
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Fabric config directory 與共用 server config codec 之間的檔案 adapter。
 *
 * @property state 目前有效的伺服器設定。
 * @property codec 嚴格 TOML 解碼與驗證邊界。
 * @property pathProvider 目前 session 的固定設定路徑。
 * @property store 原子套用歷史政策的權威交易邊界。
 * @property dispatchers 檔案 I/O 與設定發布的執行緒邊界。
 * @property retentionCoordinator 排序有效設定發布與正在執行的歷史清理。
 */
@Single
class FabricServerConfigManager(
    @Provided private val state: MinecraftServerConfigState,
    @Provided private val codec: MinecraftServerConfigTomlCodec,
    private val pathProvider: FabricServerConfigPathProvider,
    private val store: AuthoritativeStateStore,
    private val dispatchers: CoroutineDispatchers,
    private val retentionCoordinator: HistoryRetentionCoordinator,
) {
    /** 排序多個 reload，避免先讀取的設定覆蓋後提交的設定。 */
    private val reloadMutex = Mutex()

    /** 記錄設定檔建立、載入與失敗原因。 */
    private val logger = mahjongCraftLogger(FabricServerConfigManager::class)

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

    /**
     * 綁定 [server] 的設定位置、重設前一個 session 的值並載入設定。
     *
     * @param server 目前 Minecraft server。
     * @return 設定初始化結果；載入失敗時保留程式預設值。
     */
    fun attach(server: MinecraftServer): MinecraftServerConfigUpdateResult = attach(pathProvider.get(server))

    /** 清除目前 server session 的設定位置與有效設定。 */
    fun detach() {
        location = null
        state.reset()
    }

    /**
     * 綁定明確位置，供平台測試驗證不依賴 Minecraft server 實例的檔案行為。
     *
     * @param location 此 server session 的 config 檔案位置。
     * @return 設定初始化結果；載入失敗時保留程式預設值。
     */
    internal fun attach(location: FabricServerConfigLocation): MinecraftServerConfigUpdateResult {
        this.location = location
        state.reset()
        return initialize()
    }

    /**
     * 初始化設定檔；缺少檔案時先建立帶註解的預設 template。
     *
     * 失敗時保留 [MinecraftServerConfigState] 的程式預設值，且不覆寫損壞檔案。
     *
     * @return 載入的設定與預設檔案建立標記，或含原因的失敗結果。
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

    /**
     * 重新讀取並原子發布完整設定與記錄政策；檔案錯誤不修改有效值。
     *
     * @return 更新後設定或載入失敗原因；協程取消仍向外傳遞。
     * @throws IllegalStateException 呼叫時尚未綁定伺服器設定位置。
     */
    suspend fun reload(): MinecraftServerConfigUpdateResult = reloadMutex.withLock {
        val attachedLocation = withContext(dispatchers.main) { requireLocation() }
        try {
            val config = withContext(dispatchers.io) {
                codec.decode(Files.readString(attachedLocation.path, StandardCharsets.UTF_8))
            }
            retentionCoordinator.apply(config.history.retentionPolicy()) {
                withContext(dispatchers.main) {
                    check(location == attachedLocation) { "Server config session changed during reload" }
                    store.applyHistoryRecordingPolicy(config.historyRecordingPolicy()) { state.replace(config) }
                }
            }
            MinecraftServerConfigUpdateResult.Success(config, createdDefaultFile = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            failure(
                userMessage = "Unable to load server config at ${attachedLocation.displayPath}: ${exception.message}",
                logMessage = "Unable to load server config at ${attachedLocation.path}: ${exception.message}",
                cause = exception,
            )
        }
    }

    /** @return 目前記憶體內有效設定的標準 TOML，不含註解。 */
    fun formattedCurrentToml(): String = codec.encode(state.current)

    /**
     * 讀取並解碼設定檔，成功後替換記憶體內的有效設定。
     *
     * @param createdDefaultFile 此次初始化是否剛建立預設檔案。
     * @return 載入的設定及建立標記，或含原因的失敗結果。
     */
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

    /**
     * 在設定檔缺少時原樣複製隨模組提供的預設範本。
     *
     * @return 已建立檔案時為 true；檔案原已存在時為 false。
     * @throws IllegalStateException 找不到打包的預設範本。
     * @throws java.io.IOException 建立目錄或複製檔案失敗。
     */
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

    /**
     * 記錄錯誤與例外，建立供呼叫端處理的失敗結果。
     *
     * @param userMessage 失敗結果攜帶的使用者訊息。
     * @param logMessage 記錄至伺服器日誌的診斷文字。
     * @param cause 原始失敗原因。
     * @return 保留使用者訊息與原因的失敗結果。
     */
    private fun failure(
        userMessage: String,
        logMessage: String,
        cause: Throwable,
    ): MinecraftServerConfigUpdateResult.Failure {
        logger.error(logMessage, cause)
        return MinecraftServerConfigUpdateResult.Failure(userMessage, cause)
    }

    /**
     * 取得目前伺服器啟動週期綁定的設定位置。
     *
     * @return 已綁定的設定位置。
     * @throws IllegalStateException 尚未綁定設定位置。
     */
    private fun requireLocation(): FabricServerConfigLocation = checkNotNull(location) {
        "Server config is not attached to a running server"
    }

    /** Config resource 與檔案寫入選項。 */
    private companion object {
        /** 打包於 Minecraft common resources 的帶註解預設 template。 */
        const val DEFAULT_TEMPLATE_RESOURCE: String = "/mahjongcraft/server-config-default.toml"
    }
}
