package com.doublemoon1119.mahjongcraft.platform.minecraft.config

import com.akuleshov7.ktoml.Toml
import com.akuleshov7.ktoml.TomlInputConfig
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Server config TOML 解碼或驗證失敗。 */
class InvalidMinecraftServerConfigException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/** 在不同 Minecraft loader 間共用的嚴格 server config TOML codec。 */
class MinecraftServerConfigTomlCodec {
    /** 拒絕未知欄位、空值與空文件的 TOML decoder。 */
    private val toml = Toml(
        inputConfig = TomlInputConfig(
            ignoreUnknownNames = false,
            allowEmptyValues = false,
            allowNullValues = false,
            allowEmptyToml = false,
        ),
    )

    /** 將 [content] 解碼並驗證成完整的 [MinecraftServerConfig]。 */
    fun decode(content: String): MinecraftServerConfig {
        val dto = try {
            toml.decodeFromString<MinecraftServerConfigTomlDto>(content)
        } catch (exception: Exception) {
            throw InvalidMinecraftServerConfigException(
                "Unable to parse server config TOML: ${exception.message ?: exception::class.simpleName}",
                exception,
            )
        }
        return dto.toConfig()
    }

    /** 將 [config] 格式化為不含註解且可再次解碼的標準 TOML。 */
    fun encode(config: MinecraftServerConfig): String = toml.encodeToString(
        MinecraftServerConfigTomlDto.fromConfig(config),
    )
}

/**
 * TOML 根節點，只負責穩定的設定檔結構。
 *
 * @property playerDisconnection 尚未開始的遊戲之玩家斷線設定。
 * @property table 麻將桌破壞與缺失資料設定。
 * @property mahjongTile 麻將牌世界呈現設定。
 * @property history 歷史資料記錄與封存設定。
 */
@Serializable
private data class MinecraftServerConfigTomlDto(
    @SerialName("player-disconnection")
    val playerDisconnection: PlayerDisconnectionTomlDto = PlayerDisconnectionTomlDto(),
    val table: TablePolicyTomlDto = TablePolicyTomlDto(),
    @SerialName("mahjong-tile")
    val mahjongTile: MahjongTileTomlDto = MahjongTileTomlDto(),
    val history: HistoryTomlDto = HistoryTomlDto(),
) {
    /** 將字串欄位驗證並轉成 runtime config。 */
    fun toConfig(): MinecraftServerConfig = MinecraftServerConfig(
        disconnectedPlayerPolicy = enumValue(
            field = "player-disconnection.policy",
            value = playerDisconnection.policy,
            values = DisconnectedPlayerPolicy.entries,
            configValue = DisconnectedPlayerPolicy::configValue,
        ),
        disconnectedPlayerTimeoutSeconds = playerDisconnection.timeoutSeconds.also { timeout ->
            val allowedRange = LongRange(
                MinecraftServerConfig.MIN_DISCONNECTED_PLAYER_TIMEOUT_SECONDS,
                MinecraftServerConfig.MAX_DISCONNECTED_PLAYER_TIMEOUT_SECONDS,
            )
            requireConfig(timeout in allowedRange) {
                "player-disconnection.timeout-seconds must be between " +
                    "${MinecraftServerConfig.MIN_DISCONNECTED_PLAYER_TIMEOUT_SECONDS} and " +
                    "${MinecraftServerConfig.MAX_DISCONNECTED_PLAYER_TIMEOUT_SECONDS}, but was $timeout"
            }
        },
        tableBreakPolicy = enumValue(
            field = "table.break-policy",
            value = table.breakPolicy,
            values = TableBreakPolicy.entries,
            configValue = TableBreakPolicy::configValue,
        ),
        orphanedTablePolicy = enumValue(
            field = "table.orphaned-policy",
            value = table.orphanedPolicy,
            values = OrphanedTablePolicy.entries,
            configValue = OrphanedTablePolicy::configValue,
        ),
        mahjongTilePhysicalCollisionEnabled = mahjongTile.physicalCollisionEnabled,
        history = history.toConfig(),
    )

    /** 建立反映目前 runtime config 的完整 TOML DTO。 */
    companion object {
        /** 將 [config] 映射成可序列化的穩定設定檔結構。 */
        fun fromConfig(config: MinecraftServerConfig): MinecraftServerConfigTomlDto = MinecraftServerConfigTomlDto(
            playerDisconnection = PlayerDisconnectionTomlDto(
                policy = config.disconnectedPlayerPolicy.configValue,
                timeoutSeconds = config.disconnectedPlayerTimeoutSeconds,
            ),
            table = TablePolicyTomlDto(
                breakPolicy = config.tableBreakPolicy.configValue,
                orphanedPolicy = config.orphanedTablePolicy.configValue,
            ),
            mahjongTile = MahjongTileTomlDto(
                physicalCollisionEnabled = config.mahjongTilePhysicalCollisionEnabled,
            ),
            history = HistoryTomlDto.fromConfig(config.history),
        )
    }
}

/**
 * 尚未開始的遊戲之玩家斷線 TOML 欄位。
 *
 * @property policy 斷線後的座位處理政策。
 * @property timeoutSeconds 延遲離開秒數。
 */
@Serializable
private data class PlayerDisconnectionTomlDto(
    val policy: String = DisconnectedPlayerPolicy.LEAVE_IMMEDIATELY.configValue,
    @SerialName("timeout-seconds")
    val timeoutSeconds: Long = MinecraftServerConfig.DEFAULT_DISCONNECTED_PLAYER_TIMEOUT_SECONDS,
)

/**
 * 麻將桌生命週期 TOML 欄位。
 *
 * @property breakPolicy 玩家破壞麻將桌時使用的政策。
 * @property orphanedPolicy 預期麻將桌缺失時使用的政策。
 */
@Serializable
private data class TablePolicyTomlDto(
    @SerialName("break-policy")
    val breakPolicy: String = TableBreakPolicy.DENY_WHILE_OCCUPIED.configValue,
    @SerialName("orphaned-policy")
    val orphanedPolicy: String = OrphanedTablePolicy.REMOVE_ALL.configValue,
)

/**
 * 麻將牌世界呈現 TOML 欄位。
 *
 * @property physicalCollisionEnabled 麻將牌是否阻擋玩家及其他非麻將牌 entity。
 */
@Serializable
private data class MahjongTileTomlDto(
    @SerialName("physical-collision-enabled")
    val physicalCollisionEnabled: Boolean = true,
)

/** 歷史資料記錄與封存 TOML 欄位。
 *
 * @property enabled 是否啟用歷史資料記錄；停用會停止目前記錄，重新啟用只會記錄之後的新牌局。
 * @property includeAiMatches 是否記錄含有任一 AI 座位的牌局。
 * @property includeInterruptedMatches 是否保留無法接續的中止或部分紀錄；正常關服後可接續的對局不算中止。
 * @property maxMatches 最多保留的已完成牌局數量，零表示不限制數量。
 * @property retentionDays 已完成牌局的保留天數，零表示不限制天數。
 * @property maxDiskMiB 歷史資料磁碟空間上限，必須為正值。
 */
@Serializable
private data class HistoryTomlDto(
    val enabled: Boolean = MinecraftHistoryConfig.DEFAULT_ENABLED,
    @SerialName("include-ai-matches") val includeAiMatches: Boolean = MinecraftHistoryConfig.DEFAULT_INCLUDE_AI_MATCHES,
    @SerialName("include-interrupted-matches") val includeInterruptedMatches: Boolean = MinecraftHistoryConfig.DEFAULT_INCLUDE_INTERRUPTED_MATCHES,
    @SerialName("max-matches") val maxMatches: Int = MinecraftHistoryConfig.DEFAULT_MAX_MATCHES,
    @SerialName("retention-days") val retentionDays: Int = MinecraftHistoryConfig.DEFAULT_RETENTION_DAYS,
    @SerialName("max-disk-mib") val maxDiskMiB: Long = MinecraftHistoryConfig.DEFAULT_MAX_DISK_MIB,
) {
    /** 驗證 TOML 欄位並建立歷史資料設定。 */
    fun toConfig(): MinecraftHistoryConfig = try {
        MinecraftHistoryConfig(enabled, includeAiMatches, includeInterruptedMatches, maxMatches, retentionDays, maxDiskMiB)
    } catch (exception: IllegalArgumentException) {
        throw InvalidMinecraftServerConfigException(exception.message ?: "Invalid history configuration", exception)
    }

    /** 將歷史資料設定映射成 TOML 欄位。 */
    companion object {
        /**
         * 建立序列化用的歷史資料設定。
         *
         * @param config 已通過驗證的歷史設定。
         * @return 使用穩定 TOML 欄位名稱的設定資料。
         */
        fun fromConfig(config: MinecraftHistoryConfig): HistoryTomlDto = HistoryTomlDto(
            config.enabled,
            config.includeAiMatches,
            config.includeInterruptedMatches,
            config.maxMatches,
            config.retentionDays,
            config.maxDiskMiB,
        )
    }
}

/** 將 config 字串驗證並映射到 enum。 */
private fun <T> enumValue(
    field: String,
    value: String,
    values: List<T>,
    configValue: (T) -> String,
): T = values.firstOrNull { configValue(it) == value } ?: throw InvalidMinecraftServerConfigException(
    "$field has unsupported value '$value'; allowed values: ${values.joinToString { configValue(it) }}",
)

/** 以 config 專用例外回報驗證失敗。 */
private inline fun requireConfig(condition: Boolean, lazyMessage: () -> String) {
    if (!condition) throw InvalidMinecraftServerConfigException(lazyMessage())
}
