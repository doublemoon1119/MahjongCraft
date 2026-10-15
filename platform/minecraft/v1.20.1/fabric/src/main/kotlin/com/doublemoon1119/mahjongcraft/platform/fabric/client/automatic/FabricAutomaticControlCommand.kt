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
import java.util.concurrent.CompletableFuture

/**
 * 註冊客戶端自動操作指令，並在配對的伺服器回覆抵達後提供聊天回饋。
 *
 * @property commandService 處理控制項變更與查詢完成結果的服務。
 * @property displayResolver 取得控制項本地化名稱的解析器。
 */
class FabricAutomaticControlCommand(
    private val commandService: ClientAutomaticControlCommandService,
    private val displayResolver: AutomaticControlDisplayResolver,
) {
    /** 指令已送出且尚待回覆的聊天回饋資料。 */
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

    /**
     * 建立指定模式的指令節點。
     *
     * @param name 節點的固定文字。
     * @param mode 節點執行時使用的開關模式。
     * @return 綁定執行回呼的指令節點建構器。
     */
    private fun modeLiteral(name: String, mode: AutomaticControlCommandMode) = ClientCommandManager.literal(name).executes { context ->
        execute(context, mode)
    }

    /**
     * 執行指定模式，並立即回饋本機結果或保存待回覆資料。
     *
     * @param context 含控制項識別碼與回覆來源的指令情境。
     * @param mode 要套用的開關模式。
     * @return Brigadier 成功或失敗值。
     */
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

    /**
     * 依顯示登錄順序補全目前可調整的控制項識別碼。
     *
     * @param context Brigadier 傳入的指令情境；此補全不依其內容判斷。
     * @param builder 累積補全建議的建構器。
     * @return 已加入控制項名稱提示的補全結果。
     */
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

    /**
     * 每個客戶端 tick 檢查此指令送出的請求是否完成。
     *
     * @param client 本次 tick 的客戶端，供回覆目前玩家使用。
     */
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

    /**
     * 傳送客戶端指令錯誤並回傳 Brigadier 失敗值。
     *
     * @param source 接收錯誤訊息的指令來源。
     * @param message 要顯示的錯誤文字。
     * @return Brigadier 失敗值。
     */
    private fun failure(source: FabricClientCommandSource, message: Text): Int {
        source.sendError(message)
        return COMMAND_FAILURE
    }

    /**
     * 建立已套用狀態訊息。
     *
     * @param controlId 更新的控制項識別碼。
     * @param previousEnabled 更新前已確認的狀態。
     * @param enabled 更新後已確認的狀態。
     * @return 帶成功顏色與前後狀態的訊息。
     */
    private fun updatedMessage(controlId: String, previousEnabled: Boolean, enabled: Boolean): MutableText = Text.translatable(
        MinecraftMessageKeys.AUTOMATIC_CONTROL_UPDATED,
        displayResolver.resolve(controlId).label,
        stateText(previousEnabled),
        stateText(enabled),
    ).formatted(Formatting.GREEN)

    /**
     * 建立狀態未改變訊息。
     *
     * @param controlId 查詢的控制項識別碼。
     * @param enabled 目前已確認的狀態。
     * @return 帶提示顏色的未變更訊息。
     */
    private fun unchangedMessage(controlId: String, enabled: Boolean): MutableText = Text.translatable(
        MinecraftMessageKeys.AUTOMATIC_CONTROL_UNCHANGED,
        displayResolver.resolve(controlId).label,
        stateText(enabled),
    ).formatted(Formatting.YELLOW)

    /**
     * 建立帶顏色的開啟／關閉文字。
     *
     * @param enabled 是否啟用。
     * @return 對應狀態的本地化文字。
     */
    private fun stateText(enabled: Boolean): MutableText = Text.translatable(
        if (enabled) MinecraftMessageKeys.AUTOMATIC_CONTROL_STATE_ON else MinecraftMessageKeys.AUTOMATIC_CONTROL_STATE_OFF,
    ).formatted(if (enabled) Formatting.GREEN else Formatting.RED)

    /**
     * 指令送出後等待配對回覆的資料。
     *
     * @property requestId 請求識別碼。
     * @property controlId 正在更新的控制項識別碼。
     * @property previousEnabled 送出時已確認的狀態。
     * @property requestedEnabled 請求要求的狀態。
     */
    private data class PendingFeedback(
        val requestId: String,
        val controlId: String,
        val previousEnabled: Boolean,
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
