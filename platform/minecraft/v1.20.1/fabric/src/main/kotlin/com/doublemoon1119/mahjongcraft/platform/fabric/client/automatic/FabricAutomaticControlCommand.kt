package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.CLIENT_COMMAND_ROOT
import com.doublemoon1119.mahjongcraft.platform.fabric.text.configSaveFailureMessage
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.command.argument.IdentifierArgumentType
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import org.koin.core.annotation.Single
import java.util.concurrent.CompletableFuture

/** 註冊自動操作 client command，並在 matching ACK 抵達後提供聊天回饋。 */
@Single
class FabricAutomaticControlCommand(
    private val commandService: ClientAutomaticControlCommandService,
    private val displayResolver: AutomaticControlDisplayResolver,
) {
    private var pendingFeedback: PendingFeedback? = null

    /** 註冊 command tree 與 ACK 輪詢；只能在 client entrypoint 呼叫一次。 */
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(
                ClientCommandManager.literal(CLIENT_COMMAND_ROOT).then(
                    ClientCommandManager.literal(AUTOMATIC_CONTROL_SUBCOMMAND).then(
                        ClientCommandManager.argument(CONTROL_ID_ARGUMENT, IdentifierArgumentType.identifier())
                            .suggests(::suggestControlIds)
                            .then(modeLiteral(ON_LITERAL, AutomaticControlCommandMode.ON))
                            .then(modeLiteral(OFF_LITERAL, AutomaticControlCommandMode.OFF))
                            .then(modeLiteral(TOGGLE_LITERAL, AutomaticControlCommandMode.TOGGLE)),
                    ),
                ),
            )
        }
        ClientTickEvents.END_CLIENT_TICK.register(::pollCompletion)
    }

    /** 建立一個狀態模式 literal。 */
    private fun modeLiteral(name: String, mode: AutomaticControlCommandMode) = ClientCommandManager.literal(name).executes { context ->
        execute(context, mode)
    }

    /** 執行指定模式，並立即回饋本地結果或保存待 ACK 資料。 */
    private fun execute(
        context: CommandContext<FabricClientCommandSource>,
        mode: AutomaticControlCommandMode,
    ): Int {
        val controlId = context.getArgument(CONTROL_ID_ARGUMENT, Identifier::class.java).toString()
        return when (val result = commandService.execute(controlId, mode)) {
            is ClientAutomaticControlCommandResult.Updated -> {
                context.source.sendFeedback(updatedMessage(result.controlId, result.previousEnabled, result.enabled))
                COMMAND_SUCCESS
            }
            is ClientAutomaticControlCommandResult.Unchanged -> {
                context.source.sendFeedback(unchangedMessage(result.controlId, result.enabled))
                COMMAND_SUCCESS
            }
            is ClientAutomaticControlCommandResult.Submitted -> {
                pendingFeedback = PendingFeedback(result.requestId, result.controlId, result.previousEnabled, result.enabled)
                context.source.sendFeedback(
                    Text.translatable(
                        MinecraftMessageKeys.AUTOMATIC_CONTROL_WAITING,
                        displayResolver.resolve(result.controlId).label,
                        stateText(result.previousEnabled),
                        stateText(result.enabled),
                    ),
                )
                COMMAND_SUCCESS
            }
            ClientAutomaticControlCommandResult.Pending -> failure(
                context.source,
                Text.translatable(MinecraftMessageKeys.AUTOMATIC_CONTROL_PENDING),
            )
            ClientAutomaticControlCommandResult.Unavailable -> failure(
                context.source,
                Text.translatable(MinecraftMessageKeys.AUTOMATIC_CONTROL_UNAVAILABLE),
            )
            is ClientAutomaticControlCommandResult.Unsupported -> failure(
                context.source,
                Text.translatable(
                    MinecraftMessageKeys.AUTOMATIC_CONTROL_UNSUPPORTED,
                    displayResolver.resolve(result.controlId).label,
                ),
            )
            is ClientAutomaticControlCommandResult.SaveFailed -> failure(
                context.source,
                configSaveFailureMessage(
                    Text.translatable(MinecraftClientConfigScreenKeys.AUTO_SORT_HAND),
                    result.failure.message,
                ),
            )
            is ClientAutomaticControlCommandResult.PreferenceSyncFailed -> failure(
                context.source,
                Text.translatable(MinecraftMessageKeys.AUTOMATIC_CONTROL_PREFERENCE_SYNC_FAILED),
            )
            ClientAutomaticControlCommandResult.RoundControlSendFailed -> failure(
                context.source,
                Text.translatable(MinecraftMessageKeys.AUTOMATIC_CONTROL_SEND_FAILED),
            )
        }
    }

    /** 依顯示 registry 排序並補全目前可調整的控制 ID。 */
    @Suppress("UNUSED_PARAMETER")
    private fun suggestControlIds(
        context: CommandContext<FabricClientCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        displayResolver.resolveAll(commandService.availableControlIds()).forEach { display ->
            builder.suggest(display.controlId, display.label)
        }
        return builder.buildFuture()
    }

    /** 每個 client tick 檢查 command 自己送出的 request 是否完成。 */
    private fun pollCompletion(client: MinecraftClient) {
        val pending = pendingFeedback ?: return
        val completion = commandService.takeCompletion(pending.requestId, pending.controlId)
        if (completion == null) {
            if (!commandService.isPending(pending.requestId)) pendingFeedback = null
            return
        }
        pendingFeedback = null
        val message = when (completion.result) {
            AutomaticControlUpdateResultKindDto.ACCEPTED -> updatedMessage(
                pending.controlId,
                pending.previousEnabled,
                completion.enabled ?: pending.requestedEnabled,
            )
            AutomaticControlUpdateResultKindDto.REJECTED -> Text.translatable(MinecraftMessageKeys.AUTOMATIC_CONTROL_REJECTED)
            AutomaticControlUpdateResultKindDto.STALE -> Text.translatable(MinecraftMessageKeys.AUTOMATIC_CONTROL_STALE)
            AutomaticControlUpdateResultKindDto.UNAVAILABLE -> Text.translatable(MinecraftMessageKeys.AUTOMATIC_CONTROL_UNAVAILABLE)
        }
        client.player?.sendMessage(message, false)
    }

    /** 傳送 client command 錯誤並回傳 Brigadier 失敗值。 */
    private fun failure(source: FabricClientCommandSource, message: Text): Int {
        source.sendError(message)
        return COMMAND_FAILURE
    }

    /** 建立已套用狀態訊息。 */
    private fun updatedMessage(controlId: String, previousEnabled: Boolean, enabled: Boolean): MutableText = Text.translatable(
        MinecraftMessageKeys.AUTOMATIC_CONTROL_UPDATED,
        displayResolver.resolve(controlId).label,
        stateText(previousEnabled),
        stateText(enabled),
    ).formatted(Formatting.GREEN)

    /** 建立狀態未改變訊息。 */
    private fun unchangedMessage(controlId: String, enabled: Boolean): MutableText = Text.translatable(
        MinecraftMessageKeys.AUTOMATIC_CONTROL_UNCHANGED,
        displayResolver.resolve(controlId).label,
        stateText(enabled),
    ).formatted(Formatting.YELLOW)

    /** 建立帶顏色的開啟／關閉文字。 */
    private fun stateText(enabled: Boolean): MutableText = Text.translatable(
        if (enabled) MinecraftMessageKeys.AUTOMATIC_CONTROL_STATE_ON else MinecraftMessageKeys.AUTOMATIC_CONTROL_STATE_OFF,
    ).formatted(if (enabled) Formatting.GREEN else Formatting.RED)

    /** Command 送出後等待 matching ACK 所需的最小資料。 */
    private data class PendingFeedback(
        /** request-scoped 識別碼。 */
        val requestId: String,
        /** 正在更新的控制 ID。 */
        val controlId: String,
        /** 送出時已確認的控制狀態。 */
        val previousEnabled: Boolean,
        /** 請求要求的狀態。 */
        val requestedEnabled: Boolean,
    )

    private companion object {
        /** `automatic_control` 子指令。 */
        const val AUTOMATIC_CONTROL_SUBCOMMAND = "automatic_control"

        /** 控制 ID argument 名稱。 */
        const val CONTROL_ID_ARGUMENT = "control_id"

        /** 明確開啟 literal。 */
        const val ON_LITERAL = "on"

        /** 明確關閉 literal。 */
        const val OFF_LITERAL = "off"

        /** 反轉狀態 literal。 */
        const val TOGGLE_LITERAL = "toggle"

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS = 1

        /** Brigadier 失敗回傳值。 */
        const val COMMAND_FAILURE = 0
    }
}
