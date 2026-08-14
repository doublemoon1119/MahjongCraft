package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import org.koin.core.annotation.Single
import java.util.concurrent.CompletableFuture
import kotlin.uuid.toKotlinUuid

/**
 * `/mahjongcraft room` 底下的房間與對局階段玩家指令：`join`、`leave`、`ready`、`start`。
 *
 * 右鍵／蹲下右鍵桌子已經是既有的建房／加入／離開互動方式，這裡提供功能相同的指令版本，作為與手勢
 * 並存的另一種操作方式，不是暫時的除錯工具，日後也不會被移除。集中在 `room` 子分類下，是因為對局
 * 階段（出牌、吃碰槓胡）之後也會有自己的一批指令，避免全部擠在 `/mahjongcraft` 根節點下。
 *
 * [join]／[leave] 需要玩家明確指定目標桌子（[TableCoordinateArgument]），不會自動選最近的一張；
 * Tab 補全只會列出玩家目前實際可互動範圍內的桌子（[ReachableMahjongTableResolver]），與右鍵桌子
 * 能生效的範圍一致。[ready]／[start] 改用玩家目前的房間歸屬解析目標房間，不需要玩家人在桌子附近
 * ——開局本身就會把玩家傳送到座位，先天不需要距離限制。
 *
 * @property tableResolver 依玩家可互動範圍找出候選麻將桌。
 * @property roomService 實際執行房間／對局動作的既有服務。
 * @property feedbackPublisher 找不到指定桌子時的回饋。
 */
@Single
class FabricRoomCommand(
    private val tableResolver: ReachableMahjongTableResolver,
    private val roomService: MahjongTableRoomService,
    private val feedbackPublisher: MinecraftPlayerFeedbackPublisher,
) {
    /** 將 `/mahjongcraft room join|leave|ready|start` 加入 Fabric command dispatcher。 */
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal(MinecraftModMetadata.MOD_ID)
                    .then(
                        literal("room")
                            .then(
                                literal("join")
                                    .then(
                                        argument(TABLE_ARGUMENT, StringArgumentType.word())
                                            .suggests(::suggestReachableTables)
                                            .executes { context -> join(context) },
                                    ),
                            )
                            .then(
                                literal("leave")
                                    .then(
                                        argument(TABLE_ARGUMENT, StringArgumentType.word())
                                            .suggests(::suggestReachableTables)
                                            .executes { context -> leave(context) },
                                    ),
                            )
                            .then(literal("ready").executes { context -> ready(context.source) })
                            .then(literal("start").executes { context -> start(context.source) }),
                    ),
            )
        }
    }

    /** 建立或加入指定座標的麻將桌房間，等同右鍵桌子。 */
    private fun join(context: CommandContext<ServerCommandSource>): Int = withResolvedTable(context) { player, table ->
        roomService.interact(table, player)
    }

    /** 離開指定座標麻將桌的等待階段，等同蹲下右鍵桌子。 */
    private fun leave(context: CommandContext<ServerCommandSource>): Int = withResolvedTable(context) { player, table ->
        roomService.leave(table, player)
    }

    /** 切換玩家目前所在房間的準備狀態。 */
    private fun ready(source: ServerCommandSource): Int = withPlayer(source) { player ->
        roomService.ready(player)
    }

    /** 房主在所有人皆已準備完成時開始遊戲。 */
    private fun start(source: ServerCommandSource): Int = withPlayer(source) { player ->
        roomService.start(player)
    }

    /** 解析指令帶入的座標引數，找不到對應且可互動的桌子時回報原因並中止。 */
    private fun withResolvedTable(
        context: CommandContext<ServerCommandSource>,
        action: (ServerPlayerEntity, MahjongTableBlockEntity) -> Unit,
    ): Int = withPlayer(context.source) { player ->
        val pos = TableCoordinateArgument.parse(StringArgumentType.getString(context, TABLE_ARGUMENT))
        val table = pos?.let { tableResolver.resolve(player, it) }
        if (table == null) {
            feedbackPublisher.publish(player.uuid.toKotlinUuid(), MinecraftPlayerFeedback.TableNotReachable)
            return@withPlayer
        }
        action(player, table)
    }

    /** 解析執行指令的玩家，非玩家（例如主控台）執行時直接以指令失敗回應。 */
    private fun withPlayer(source: ServerCommandSource, action: (ServerPlayerEntity) -> Unit): Int {
        val player = source.player ?: return COMMAND_FAILURE
        action(player)
        return COMMAND_SUCCESS
    }

    /** 列出執行指令玩家目前可互動範圍內所有桌子的座標，作為 `<table>` 引數的 Tab 補全建議。 */
    private fun suggestReachableTables(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        val player = context.source.player
        if (player != null) {
            tableResolver.findReachable(player).forEach { table ->
                builder.suggest(TableCoordinateArgument.format(table.pos))
            }
        }
        return builder.buildFuture()
    }

    private companion object {
        /** 桌子座標引數名稱。 */
        const val TABLE_ARGUMENT: String = "table"

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1

        /** Brigadier 失敗回傳值。 */
        const val COMMAND_FAILURE: Int = 0
    }
}
