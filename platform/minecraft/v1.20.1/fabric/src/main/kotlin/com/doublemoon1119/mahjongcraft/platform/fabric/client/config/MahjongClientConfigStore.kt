package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import net.fabricmc.loader.api.FabricLoader
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * MahjongCraft client 設定的檔案存取，只存在單機／client 環境，跟 server 端設定
 * （[com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigManager]）完全
 * 分開存放（不同檔案），互不影響；缺少檔案時的建立方式是同一套機制——原樣複製打包在 resources 裡、
 * 帶完整註解的 template（`client-config-default.toml`），不是用 codec 從程式預設值重新編碼，這樣
 * 使用者第一次打開檔案就能看到功能說明，不用回頭查文件或程式碼。
 *
 * `/mahjongcraft_client config reload|show`（見
 * [com.doublemoon1119.mahjongcraft.platform.fabric.client.config.FabricClientConfigCommand]）跟
 * server 端 `/mahjongcraft config reload|show`（見
 * [com.doublemoon1119.mahjongcraft.platform.fabric.server.config.FabricServerConfigCommand]）效果
 * 完全對稱：[load] 回傳跟 `MinecraftServerConfigUpdateResult` 結構相同的 [MahjongClientConfigUpdateResult]，
 * 失敗時 runtime 仍保留先前設定（不會被清成程式預設值），[formattedCurrentToml] 對應
 * `FabricServerConfigManager.formattedCurrentToml`，差別只在這裡沒有權限限制（玩家自己本機的設定，
 * 任何人都能執行）。
 *
 * 跟 server 設定不同的是：server 設定只由玩家手動編輯、指令只負責重新讀取，mod 本身從不寫回檔案；這裡的
 * `/mahjongcraft_client label toggle` 需要能直接切換設定，因此 [setTileLabelsEnabled] 會寫回磁碟——
 * 但用正規表示式只替換 `tile-labels-enabled` 那一行的值，不是整份重新編碼，這樣即使切換過設定，
 * 檔案裡的說明註解也不會被抹掉。
 */
@Single
class MahjongClientConfigStore {
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

    /** [path] 供檔案 I/O 使用。 */
    val path: Path = FabricLoader.getInstance().configDir
        .resolve(MinecraftModMetadata.MOD_ID)
        .resolve("client.toml")

    /** [path] 不含主機目錄資訊、可供指令輸出的邏輯路徑，理由同 server 設定的 `displayPath`。 */
    val displayPath: String = "<Minecraft instance>/config/${MinecraftModMetadata.MOD_ID}/client.toml"

    /** 目前記憶體內有效的 client 設定；載入失敗或檔案不存在時為程式預設值。 */
    var current: MahjongClientConfigState = MahjongClientConfigState()
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
     * 內容（含註解）原樣保留。寫入失敗只記錄警告，不影響記憶體內已切換的值。
     */
    fun setTileLabelsEnabled(enabled: Boolean) {
        current = current.copy(tileLabelsEnabled = enabled)
        try {
            val content = Files.readString(path, StandardCharsets.UTF_8)
            val replacement = "$TILE_LABELS_ENABLED_KEY = $enabled"
            val updatedContent = if (TILE_LABELS_ENABLED_LINE.containsMatchIn(content)) {
                TILE_LABELS_ENABLED_LINE.replace(content, replacement)
            } else {
                content.trimEnd('\n') + "\n$replacement\n"
            }
            Files.writeString(path, updatedContent, StandardCharsets.UTF_8)
        } catch (exception: Exception) {
            logger.warn("Unable to save client config at {}: {}", path, exception.message)
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

    private companion object {
        /** 打包於 Minecraft common resources 的帶註解預設 template。 */
        const val DEFAULT_TEMPLATE_RESOURCE: String = "/mahjongcraft/client-config-default.toml"

        /** [MahjongClientConfigState.tileLabelsEnabled] 對應的 TOML 欄位鍵，跟其 `@SerialName` 保持一致。 */
        const val TILE_LABELS_ENABLED_KEY: String = "tile-labels-enabled"

        /** 比對檔案中 [TILE_LABELS_ENABLED_KEY] 那一行，用於 [setTileLabelsEnabled] 的原地替換。 */
        val TILE_LABELS_ENABLED_LINE = Regex("""(?m)^$TILE_LABELS_ENABLED_KEY\s*=.*$""")
    }
}
