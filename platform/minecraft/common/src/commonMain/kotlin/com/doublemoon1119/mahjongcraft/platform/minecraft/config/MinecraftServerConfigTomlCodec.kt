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
 */
@Serializable
private data class MinecraftServerConfigTomlDto(
    @SerialName("player-disconnection")
    val playerDisconnection: PlayerDisconnectionTomlDto = PlayerDisconnectionTomlDto(),
    val table: TablePolicyTomlDto = TablePolicyTomlDto(),
    @SerialName("mahjong-tile")
    val mahjongTile: MahjongTileTomlDto = MahjongTileTomlDto(),
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
