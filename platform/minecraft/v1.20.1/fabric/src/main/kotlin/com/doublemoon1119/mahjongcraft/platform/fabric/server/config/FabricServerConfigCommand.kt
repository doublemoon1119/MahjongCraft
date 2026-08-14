package com.doublemoon1119.mahjongcraft.platform.fabric.server.config

import com.doublemoon1119.mahjongcraft.platform.fabric.server.entity.MahjongTileCollisionService
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfigUpdateResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory

/** 註冊限制管理員使用的 server config reload 與 show 指令。 */
@Single
class FabricServerConfigCommand(
    private val configManager: FabricServerConfigManager,
    private val mahjongTileCollisionService: MahjongTileCollisionService,
) {
    /** 記錄 config 指令執行者與結果。 */
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)

    /**
     * 將 `/mahjongcraft config reload|show` 加入 Fabric command dispatcher。
     *
     * 權限限制掛在 `config` 子節點而非 `mahjongcraft` 根節點，讓其他不需要管理員權限的
     * `/mahjongcraft` 子指令（例如房間階段的玩家指令）可以共用同一個根節點註冊，不受這裡的權限
     * 要求影響。
     */
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal(MinecraftModMetadata.MOD_ID)
                    .then(
                        literal("config")
                            .requires { source -> source.hasPermissionLevel(REQUIRED_PERMISSION_LEVEL) }
                            .then(literal("reload").executes { context -> reload(context.source) })
                            .then(literal("show").executes { context -> show(context.source) }),
                    ),
            )
        }
    }

    /** 重新載入設定並向執行者回報結果。 */
    private fun reload(source: ServerCommandSource): Int = when (val result = configManager.reload()) {
        is MinecraftServerConfigUpdateResult.Success -> {
            mahjongTileCollisionService.applyToLoaded(source.server, result.config)
            logger.info("Server config reloaded by {}", source.name)
            source.sendFeedback({ prefixed("Server config reloaded", Formatting.GREEN) }, false)
            COMMAND_SUCCESS
        }
        is MinecraftServerConfigUpdateResult.Failure -> {
            logger.warn("Server config reload requested by {} failed: {}", source.name, result.message)
            source.sendError(prefixed(result.message, Formatting.RED))
            COMMAND_FAILURE
        }
    }

    /** 顯示目前記憶體內實際生效的標準 TOML。 */
    private fun show(source: ServerCommandSource): Int {
        logger.info("Effective server config displayed to {}", source.name)
        source.sendFeedback({ prefixed("Effective server config", Formatting.AQUA) }, false)
        source.sendFeedback({ Text.literal("Path: ${configManager.displayPath}").formatted(Formatting.DARK_GRAY) }, false)
        configManager.formattedCurrentToml().lineSequence().forEach { line ->
            source.sendFeedback({ formatTomlLine(line) }, false)
        }
        return COMMAND_SUCCESS
    }

    /** 建立統一帶有 mod 名稱前綴的 config 指令回饋。 */
    private fun prefixed(message: String, color: Formatting): MutableText = Text
        .literal("[${MinecraftModMetadata.MOD_NAME}] ")
        .formatted(Formatting.GOLD)
        .append(Text.literal(message).formatted(color))

    /** 以 section、key 與 value 套用不同顏色，空行則維持空白。 */
    private fun formatTomlLine(line: String): MutableText = when {
        line.isBlank() -> Text.empty()
        line.startsWith("[") -> Text.literal(line).formatted(Formatting.AQUA)
        " = " in line -> {
            val (key, value) = line.split(" = ", limit = 2)
            Text.literal(key).formatted(Formatting.GRAY)
                .append(Text.literal(" = ").formatted(Formatting.DARK_GRAY))
                .append(Text.literal(value).formatted(Formatting.GREEN))
        }
        else -> Text.literal(line)
    }

    /** 指令權限與 Brigadier 回傳值。 */
    private companion object {
        /** Minecraft operator level 2。 */
        const val REQUIRED_PERMISSION_LEVEL: Int = 2

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1

        /** Brigadier 失敗回傳值。 */
        const val COMMAND_FAILURE: Int = 0
    }
}
