package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigCommand
import com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigManager
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.fabricmc.loader.api.FabricLoader
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * MahjongCraft client 設定的檔案存取，只存在單機／client 環境，跟 server 端設定（[FabricServerConfigManager]）完全
 * 分開存放（不同檔案），互不影響；缺少檔案時的建立方式是同一套機制——原樣複製打包在 resources 裡、
 * 帶完整註解的 template（`client-config-default.toml`），不是用 codec 從程式預設值重新編碼，這樣
 * 使用者第一次打開檔案就能看到功能說明，不用回頭查文件或程式碼。
 *
 * `/mahjongcraft_client config reload|show`（見 [FabricClientConfigCommand]）跟
 * server 端 `/mahjongcraft config reload|show`（見 [FabricServerConfigCommand]）效果
 * 完全對稱：[load] 回傳跟 `MinecraftServerConfigUpdateResult` 結構相同的 [MahjongClientConfigUpdateResult]，
 * 失敗時 runtime 仍保留先前設定（不會被清成程式預設值），[formattedCurrentToml] 對應
 * `FabricServerConfigManager.formattedCurrentToml`，差別只在這裡沒有權限限制（玩家自己本機的設定，
 * 任何人都能執行）。
 *
 * 跟 server 設定不同的是：client 設定可由指令與 GUI 更新。[save] 會在同目錄建立暫存檔並原子替換，
 * 同時只更新受控欄位的值，保留模板註解與人工排版。
 */
@Single
class MahjongClientConfigStore() {
    /** 測試時覆寫的隔離設定路徑；正式執行環境維持 `null`。 */
    private var pathOverride: Path? = null

    /** [path] 供檔案 I/O 使用。 */
    val path: Path
        get() = pathOverride ?: defaultConfigPath()

    /** 建立使用指定路徑的測試 store，不讀取 Fabric 執行環境。 */
    private constructor(path: Path) : this() {
        pathOverride = path
    }

    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /** 拒絕未知欄位、空值與空文件的 TOML codec，理由同 server 設定的 `MinecraftServerConfigTomlCodec`。 */
    private val toml = Toml(
        inputConfig = TomlInputConfig(
            ignoreUnknownNames = false,
            allowEmptyValues = false,
            allowNullValues = false,
            allowEmptyToml = false,
        ),
    )

    /** [path] 不含主機目錄資訊、可供指令輸出的邏輯路徑，理由同 server 設定的 `displayPath`。 */
    val displayPath: String = "<Minecraft instance>/config/${MinecraftModMetadata.MOD_ID}/client.toml"

    /** 目前記憶體內有效的 client 設定；載入失敗或檔案不存在時為程式預設值。 */
    var current: MahjongClientConfigState = MahjongClientConfigState()
        private set

    /** 每次成功載入或保存後遞增，供設定畫面偵測外部權威設定變更。 */
    var revision: Long = 0L
        private set

    /**
     * 從磁碟載入設定；檔案不存在時先原樣複製帶註解的 template，再讀取剛建立的檔案。複製、讀取或解析
     * 失敗時保留（不覆寫）記憶體內目前的有效設定，也不覆寫已存在但格式錯誤的檔案，理由同 server 設定
     * 的 `MinecraftServerConfigManager.load`。
     */
    fun load(): MahjongClientConfigUpdateResult {
        val createdDefaultFile = try {
            createDefaultFileIfMissing()
        } catch (exception: Exception) {
            return MahjongClientConfigUpdateResult.Failure(
                "Unable to create client config at $displayPath: ${exception.message}",
                exception,
            )
        }
        return try {
            val config = toml.decodeFromString<MahjongClientConfigState>(Files.readString(path, StandardCharsets.UTF_8))
            current = config
            revision += 1
            MahjongClientConfigUpdateResult.Success(config, createdDefaultFile)
        } catch (exception: Exception) {
            MahjongClientConfigUpdateResult.Failure(
                "Unable to load client config at $displayPath: ${exception.message}",
                exception,
            )
        }
    }

    /** 將目前記憶體內的有效設定輸出成不含註解的標準 TOML，對應 server 設定的同名方法。 */
    fun formattedCurrentToml(): String = toml.encodeToString(current)

    /**
     * 切換牌面角落輔助標籤開關並立即寫回磁碟；只替換檔案裡 `tile-labels-enabled` 那一行的值，其餘
     * 內容（含註解）原樣保留。寫入失敗時磁碟與記憶體內的有效設定都維持原值。
     */
    fun setTileLabelsEnabled(enabled: Boolean): MahjongClientConfigUpdateResult = save(current.copy(tileLabelsEnabled = enabled))

    /**
     * 切換自動整理手牌開關並立即寫回磁碟，手法同 [setTileLabelsEnabled]；呼叫端（`FabricHandSortCommand`）
     * 除了呼叫這個方法，還要另外把偏好同步給伺服器——這裡只負責 client 本機持久化。
     */
    fun setAutoSortHandEnabled(enabled: Boolean): MahjongClientConfigUpdateResult = save(current.copy(autoSortHandEnabled = enabled))

    /**
     * 原子保存完整受控草稿；成功替換磁碟檔案後才更新 runtime 設定與 [revision]。
     * 已知欄位只替換等號右側，因此保留模板註解、空行及欄位順序。
     */
    fun save(config: MahjongClientConfigState): MahjongClientConfigUpdateResult {
        var temporaryPath: Path? = null
        return try {
            createDefaultFileIfMissing()
            val original = Files.readString(path, StandardCharsets.UTF_8)
            val updated = updateBoolean(original, TILE_LABELS_ENABLED_KEY, TILE_LABELS_ENABLED_LINE, config.tileLabelsEnabled)
                .let { updateBoolean(it, AUTO_SORT_HAND_ENABLED_KEY, AUTO_SORT_HAND_ENABLED_LINE, config.autoSortHandEnabled) }
            check(toml.decodeFromString<MahjongClientConfigState>(updated) == config) {
                "Updated client config did not decode to the requested state"
            }
            temporaryPath = Files.createTempFile(path.parent, "client-", ".toml.tmp")
            Files.writeString(temporaryPath, updated, StandardCharsets.UTF_8)
            Files.move(temporaryPath, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            temporaryPath = null
            current = config
            revision += 1
            MahjongClientConfigUpdateResult.Success(config)
        } catch (exception: Exception) {
            logger.warn("Unable to save client config at {}: {}", path, exception.message)
            MahjongClientConfigUpdateResult.Failure(
                "Unable to save client config at $displayPath: ${exception.message}",
                exception,
            )
        } finally {
            temporaryPath?.let { runCatching { Files.deleteIfExists(it) } }
        }
    }

    /** 只替換 [key] 的 Boolean 值；缺少受控欄位時拒絕猜測 TOML section。 */
    private fun updateBoolean(content: String, key: String, lineRegex: Regex, enabled: Boolean): String {
        check(lineRegex.containsMatchIn(content)) { "Missing controlled client config field '$key'" }
        return lineRegex.replace(content) { match ->
            match.groupValues[1] + enabled + match.groupValues[2]
        }
    }

    /** 在設定檔缺少時原樣複製打包的帶註解 template。 */
    private fun createDefaultFileIfMissing(): Boolean {
        if (Files.exists(path)) return false
        Files.createDirectories(path.parent)
        val template = checkNotNull(javaClass.getResourceAsStream(DEFAULT_TEMPLATE_RESOURCE)) {
            "Missing bundled client config template $DEFAULT_TEMPLATE_RESOURCE"
        }
        template.use { input -> Files.copy(input, path) }
        return true
    }

    internal companion object {
        /** 正式 client config 的預設路徑。 */
        fun defaultConfigPath(): Path = FabricLoader.getInstance().configDir
            .resolve(MinecraftModMetadata.MOD_ID)
            .resolve("client.toml")

        /** 建立使用指定路徑的測試 store。 */
        fun createForTesting(path: Path): MahjongClientConfigStore = MahjongClientConfigStore(path)

        /** 打包於 Minecraft common resources 的帶註解預設 template。 */
        const val DEFAULT_TEMPLATE_RESOURCE: String = "/mahjongcraft/client-config-default.toml"

        /** [MahjongClientConfigState.tileLabelsEnabled] 對應的 TOML 欄位鍵，跟其 `@SerialName` 保持一致。 */
        const val TILE_LABELS_ENABLED_KEY: String = "tile-labels-enabled"

        /** 比對檔案中 [TILE_LABELS_ENABLED_KEY] 那一行，用於 [setTileLabelsEnabled] 的原地替換。 */
        val TILE_LABELS_ENABLED_LINE = Regex(
            """(?m)^(\s*$TILE_LABELS_ENABLED_KEY\s*=\s*)\S+(\s*(?:#.*)?)$""",
        )

        /** [MahjongClientConfigState.autoSortHandEnabled] 對應的 TOML 欄位鍵，跟其 `@SerialName` 保持一致。 */
        const val AUTO_SORT_HAND_ENABLED_KEY: String = "auto-sort-hand-enabled"

        /** 比對檔案中 [AUTO_SORT_HAND_ENABLED_KEY] 那一行，用於 [setAutoSortHandEnabled] 的原地替換。 */
        val AUTO_SORT_HAND_ENABLED_LINE = Regex(
            """(?m)^(\s*$AUTO_SORT_HAND_ENABLED_KEY\s*=\s*)\S+(\s*(?:#.*)?)$""",
        )
    }
}
