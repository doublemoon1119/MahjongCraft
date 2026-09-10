package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.platform.fabric.text.toDisplayText
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
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
 * `/mahjongcraft game` 底下的對局階段玩家指令：`hand`、`discard`、`action`。
 *
 * 集中在 `game` 子分類、獨立於 `room`（見 `FabricRoomCommand` KDoc 早就預告的分工）——`room` 專屬
 * 房間等待階段，這裡專屬進行中的對局。摸牌不開放指令（全自動觸發，見 [MahjongTableGameActionService]
 * KDoc）；碰／吃／槓／胡／自摸／過統一透過 [action] 一個指令處理，候選項目由
 * [GameActionCandidateResolver] 依目前搶槓／回應捨牌／自己回合的情境動態算出，玩家不需要自己組
 * 動作內容。
 *
 * `discard`／`action` 的候選 token 直接是可讀的牌面／動作簡寫（見
 * [GameActionCandidateResolver] KDoc），不需要 tooltip 也看得懂要打什麼；第三方牌種的候選 token 可能
 * 帶命名空間冒號，因此引數用 [StringArgumentType.string]（而非 [StringArgumentType.word]）並在補全時
 * 用 [StringArgumentType.escapeIfRequired]，比照 `FabricRoomCommand` 策略引數的既有慣例。
 *
 * @property candidateResolver 依玩家目前桌況列出手牌／合法動作候選項目。
 * @property gameActionService 實際執行對局動作的服務。
 * @property feedbackPublisher 候選 token 無法解析時的回饋。
 * @property exhaustiveDrawReasonDisplayNameRegistry 解析規則專屬流局動作名稱。
 * @property tileDisplayNameRegistry 解析候選 tooltip 用的牌面顯示名稱。
 * @property tileAssetRegistry 解析候選 tooltip 牌面 emoji 用的 asset key。
 * @property tileEmojiRegistry 解析候選 tooltip 牌面 emoji 字元。
 * @property scope 橋接 suggestion provider 內部 suspend 查詢與 Brigadier 同步 API 的協程 scope。
 */
@Single
class FabricGameCommand(
    private val candidateResolver: GameActionCandidateResolver,
    private val gameActionService: MahjongTableGameActionService,
    private val feedbackPublisher: MinecraftPlayerFeedbackPublisher,
    private val gameActionDisplayNameRegistry: GameActionDisplayNameRegistry,
    private val exhaustiveDrawReasonDisplayNameRegistry: ExhaustiveDrawReasonDisplayNameRegistry,
    private val tileDisplayNameRegistry: TileDisplayNameRegistry,
    private val tileAssetRegistry: MinecraftTileAssetRegistry,
    private val tileEmojiRegistry: TileEmojiRegistry,
    private val scope: AppCoroutineScope,
) {
    /** 將 `/mahjongcraft game hand|discard|action` 加入 Fabric command dispatcher。 */
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
                                literal("action")
                                    .then(
                                        argument(ACTION_ARGUMENT, StringArgumentType.string())
                                            .suggests(::suggestActions)
                                            .executes { context -> act(context, emptyList()) }
                                            .then(
                                                argument(ACTION_TILES_ARGUMENT, StringArgumentType.greedyString())
                                                    .suggests(::suggestActionTiles)
                                                    .executes { context ->
                                                        act(context, parseTileTokens(context, ACTION_TILES_ARGUMENT))
                                                    },
                                            ),
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

    /** 執行指令帶入的候選動作與額外選牌。 */
    private fun act(context: CommandContext<ServerCommandSource>, tileTokens: List<String>): Int = withPlayer(context.source) { player ->
        val token = StringArgumentType.getString(context, ACTION_ARGUMENT)
        val playerId = player.uuid.toKotlinUuid()
        scope.launch {
            val candidate = candidateResolver.listActionCandidates(playerId).firstOrNull { it.token == token }
            if (candidate == null) {
                feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.IllegalGameAction)
                return@launch
            }
            val eligibleTiles = candidateResolver.listTileSelectionCandidates(playerId, candidate)
            val selectedTiles = tileTokens.map { tileToken ->
                eligibleTiles.firstOrNull { it.token == tileToken }?.tileId ?: run {
                    feedbackPublisher.publish(playerId, MinecraftPlayerFeedback.IllegalGameAction)
                    return@launch
                }
            }
            gameActionService.act(player, candidate, selectedTiles)
        }
    }

    /** 解析執行指令的玩家，非玩家（例如主控台）執行時直接以指令失敗回應。 */
    private fun withPlayer(source: ServerCommandSource, action: (ServerPlayerEntity) -> Unit): Int {
        val player = source.player ?: return COMMAND_FAILURE
        action(player)
        return COMMAND_SUCCESS
    }

    /** 列出執行指令玩家目前手牌，作為 `discard` 的候選建議，tooltip 顯示牌面文字。 */
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
                    candidate.tile.toDisplayText(tileDisplayNameRegistry, tileAssetRegistry, tileEmojiRegistry),
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
                    candidate.action.toDisplayText(
                        candidate.referenceTile,
                        gameActionDisplayNameRegistry,
                        tileDisplayNameRegistry,
                        tileAssetRegistry,
                        tileEmojiRegistry,
                        exhaustiveDrawReasonDisplayNameRegistry,
                    ),
                )
            }
            future.complete(builder.build())
        }
        return future
    }

    /** 列出目前 action 引數指定動作可接受的額外手牌候選。 */
    private fun suggestActionTiles(
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
            val completedTokens = builder.remaining
                .trimStart()
                .split(Regex("\\s+"))
                .dropLast(1)
                .filter(String::isNotBlank)
            val currentTokenStart = builder.remaining.lastIndexOf(' ').let { index ->
                if (index < 0) builder.start else builder.start + index + 1
            }
            val currentTokenBuilder = builder.createOffset(currentTokenStart)
            val playerId = player.uuid.toKotlinUuid()
            val actionToken = StringArgumentType.getString(context, ACTION_ARGUMENT)
            val candidate = candidateResolver.listActionCandidates(playerId).firstOrNull { it.token == actionToken }
            candidate?.let { candidateResolver.listTileSelectionCandidates(playerId, it) }
                .orEmpty()
                .filterNot { it.token in completedTokens }
                .forEach { tile -> currentTokenBuilder.suggestTile(tile) }
            future.complete(currentTokenBuilder.build())
        }
        return future
    }

    /** 將手牌候選加入目前的 Brigadier 建議清單。 */
    private fun SuggestionsBuilder.suggestTile(candidate: HandTileCandidate) {
        suggest(
            StringArgumentType.escapeIfRequired(candidate.token),
            candidate.tile.toDisplayText(tileDisplayNameRegistry, tileAssetRegistry, tileEmojiRegistry),
        )
    }

    /** 將 greedy string 引數拆成不含空白的選牌 token。 */
    private fun parseTileTokens(context: CommandContext<ServerCommandSource>, argumentName: String): List<String> = StringArgumentType.getString(context, argumentName).split(Regex("\\s+")).filter(String::isNotBlank)

    private companion object {
        /**
         * 手牌引數名稱（`discard` 使用）。值是 [GameActionCandidateResolver] 依玩家當下
         * 手牌動態算出的 candidate token，對應一張實體牌；跟 `FabricDebugAnimationCommand` 的 `tile`
         * 引數（全域固定的素材 asset key，與任何玩家手牌無關）是兩個不同概念，不應合併實作。
         */
        const val TILE_ARGUMENT: String = "tile"

        /** 候選動作引數名稱（`action` 使用）。 */
        const val ACTION_ARGUMENT: String = "action"

        /** 動作額外選牌的 greedy string 引數名稱。 */
        const val ACTION_TILES_ARGUMENT: String = "tiles"

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1

        /** Brigadier 失敗回傳值。 */
        const val COMMAND_FAILURE: Int = 0
    }
}
