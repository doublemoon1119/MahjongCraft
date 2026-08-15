package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.fabric.text.toDisplayText
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import kotlinx.coroutines.launch
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.network.ServerPlayerEntity
import org.koin.core.annotation.Single
import java.util.concurrent.CompletableFuture
import kotlin.uuid.toKotlinUuid

/**
 * `/mahjongcraft game` 底下的對局階段玩家指令：`hand`、`discard`、`riichi`、`action`。
 *
 * 集中在 `game` 子分類、獨立於 `room`（見 `FabricRoomCommand` KDoc 早就預告的分工）——`room` 專屬
 * 房間等待階段，這裡專屬進行中的對局。摸牌不開放指令（全自動觸發，見 [MahjongTableGameActionService]
 * KDoc）；碰／吃／槓／胡／自摸／過統一透過 [action] 一個指令處理，候選項目由
 * [GameActionCandidateResolver] 依目前搶槓／回應捨牌／自己回合的情境動態算出，玩家不需要自己組
 * 動作內容。
 *
 * `discard`／`riichi`／`action` 的候選 token 直接是可讀的牌面／動作簡寫（見
 * [GameActionCandidateResolver] KDoc），不需要 tooltip 也看得懂要打什麼；第三方牌種的候選 token 可能
 * 帶命名空間冒號，因此引數用 [StringArgumentType.string]（而非 [StringArgumentType.word]）並在補全時
 * 用 [StringArgumentType.escapeIfRequired]，比照 `FabricRoomCommand` 策略引數的既有慣例。
 *
 * @property candidateResolver 依玩家目前桌況列出手牌／合法動作候選項目。
 * @property gameActionService 實際執行對局動作的服務。
 * @property feedbackPublisher 候選 token 無法解析時的回饋。
 * @property tileDisplayNameRegistry 解析候選 tooltip 用的牌面顯示名稱。
 * @property scope 橋接 suggestion provider 內部 suspend 查詢與 Brigadier 同步 API 的協程 scope。
 */
@Single
class FabricGameCommand(
    private val candidateResolver: GameActionCandidateResolver,
    private val gameActionService: MahjongTableGameActionService,
    private val feedbackPublisher: MinecraftPlayerFeedbackPublisher,
    private val tileDisplayNameRegistry: TileDisplayNameRegistry,
    private val scope: AppCoroutineScope,
) {
    /** 將 `/mahjongcraft game hand|discard|riichi|action` 加入 Fabric command dispatcher。 */
    fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal(MinecraftModMetadata.MOD_ID)
                    .then(
                        literal("game")
                            .then(literal("hand").executes { context -> hand(context.source) })
                            .then(
                                literal("discard")
                                    .then(
                                        argument(TILE_ARGUMENT, StringArgumentType.string())
                                            .suggests(::suggestHandTiles)
                                            .executes { context -> discard(context) },
                                    ),
                            )
                            .then(
                                literal("riichi")
                                    .then(
                                        argument(TILE_ARGUMENT, StringArgumentType.string())
                                            .suggests(::suggestHandTiles)
                                            .executes { context -> riichi(context) },
                                    ),
                            )
                            .then(
                                literal("action")
                                    .then(
                                        argument(ACTION_ARGUMENT, StringArgumentType.string())
                                            .suggests(::suggestActions)
                                            .executes { context -> act(context) },
                                    ),
                            ),
                    ),
            )
        }
    }

    /** 顯示執行指令玩家目前的手牌與可執行動作。 */
    private fun hand(source: ServerCommandSource): Int = withPlayer(source) { player ->
        gameActionService.showHand(player)
    }

    /** 打出指令帶入的候選手牌。 */
    private fun discard(context: CommandContext<ServerCommandSource>): Int = withPlayer(context.source) { player ->
        val token = StringArgumentType.getString(context, TILE_ARGUMENT)
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val tileId = candidateResolver.listHandTileCandidates(playerId).firstOrNull { it.token == token }?.tileId
            if (tileId == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.IllegalGameAction)
                return@launch
            }
            gameActionService.discard(player, tileId)
        }
    }

    /** 宣告立直，打出指令帶入的候選手牌作為立直宣告牌。 */
    private fun riichi(context: CommandContext<ServerCommandSource>): Int = withPlayer(context.source) { player ->
        val token = StringArgumentType.getString(context, TILE_ARGUMENT)
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val tileId = candidateResolver.listHandTileCandidates(playerId).firstOrNull { it.token == token }?.tileId
            if (tileId == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.IllegalGameAction)
                return@launch
            }
            gameActionService.riichi(player, tileId)
        }
    }

    /** 執行指令帶入的候選動作（吃／碰／槓／胡／過／九種九牌）。 */
    private fun act(context: CommandContext<ServerCommandSource>): Int = withPlayer(context.source) { player ->
        val token = StringArgumentType.getString(context, ACTION_ARGUMENT)
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val candidate = candidateResolver.listActionCandidates(playerId).firstOrNull { it.token == token }
            if (candidate == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.IllegalGameAction)
                return@launch
            }
            gameActionService.act(player, candidate)
        }
    }

    /** 解析執行指令的玩家，非玩家（例如主控台）執行時直接以指令失敗回應。 */
    private fun withPlayer(source: ServerCommandSource, action: (ServerPlayerEntity) -> Unit): Int {
        val player = source.player ?: return COMMAND_FAILURE
        action(player)
        return COMMAND_SUCCESS
    }

    /** 列出執行指令玩家目前手牌，作為 `discard`／`riichi` 的候選建議，tooltip 顯示牌面文字。 */
    private fun suggestHandTiles(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        val future = CompletableFuture<Suggestions>()
        val player = context.source.player
        if (player == null) {
            future.complete(builder.build())
            return future
        }
        scope.launch {
            candidateResolver.listHandTileCandidates(player.uuid.toKotlinUuid()).forEach { candidate ->
                builder.suggest(
                    StringArgumentType.escapeIfRequired(candidate.token),
                    candidate.tile.toDisplayText(tileDisplayNameRegistry),
                )
            }
            future.complete(builder.build())
        }
        return future
    }

    /** 列出執行指令玩家目前合法的特殊動作，作為 `action` 的候選建議，tooltip 顯示動作文字。 */
    private fun suggestActions(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        val future = CompletableFuture<Suggestions>()
        val player = context.source.player
        if (player == null) {
            future.complete(builder.build())
            return future
        }
        scope.launch {
            candidateResolver.listActionCandidates(player.uuid.toKotlinUuid()).forEach { candidate ->
                builder.suggest(
                    StringArgumentType.escapeIfRequired(candidate.token),
                    candidate.action.toDisplayText(candidate.referenceTile, tileDisplayNameRegistry),
                )
            }
            future.complete(builder.build())
        }
        return future
    }

    private companion object {
        /** 手牌引數名稱（`discard`／`riichi` 共用）。 */
        const val TILE_ARGUMENT: String = "tile"

        /** 候選動作引數名稱（`action` 使用）。 */
        const val ACTION_ARGUMENT: String = "action"

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1

        /** Brigadier 失敗回傳值。 */
        const val COMMAND_FAILURE: Int = 0
    }
}
