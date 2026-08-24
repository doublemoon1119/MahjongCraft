package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDiceEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDicePoint
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationCinematicTimeline
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.TileAnimationSteps
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceRollAnimationSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ALL_TILE_ASSET_KEYS
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallPlacement
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.normalizedTileAssetKey
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.Entity
import net.minecraft.server.command.CommandManager.argument
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.math.BlockPos
import org.koin.core.annotation.Single
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.random.Random
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * `/mahjongcraft debug ...`：常駐、op 限定的動畫測試指令群組，涵蓋所有既有呈現層動畫與胡牌慶祝演出的
 * 預覽版本，讓這些動畫不必真的湊到對應遊戲情境（真的自摸、真的湊到四家開局等）才能觸發，也不需要
 * 每次臨時寫一個測試指令、commit 前再刪掉。
 *
 * 每個子指令都完全自成一體：在呼叫者面前臨時生成幾個全新的 [MahjongTileEntity]／[MahjongDiceEntity]
 * （不呼叫 `assignToTable`、不掛在任何桌子／對局底下），直接對這些臨時 entity 重播對應的動畫排程邏輯
 * （[TileAnimationSteps]，跟正式桌子綁定的 presenter 共用同一份），動畫播完後自行清除這些臨時 entity。
 * 完全不觸碰 `GameRepository`／`PlayerMembershipRepository`／任何房間或對局 use case——呼叫者不需要
 * 站在任何桌子附近，也不需要加入房間或身處進行中的對局，站在隨便一個已載入的世界座標就能直接測試
 * 演出效果，且每次呼叫都是全新一批 entity，可以無限重複呼叫。
 *
 * 牌面（[MahjongTileEntity.tileAssetKey]）可由呼叫者透過每個子指令下方可選的 `tile` 引數指定——這些
 * 臨時 entity 刻意不呼叫 `assignToTable`（[MahjongTileEntity.managedByGame] 維持 `false`），client 端
 * `MahjongTileEntityRenderer` 對「自由放置」的牌一律直接讀 entity 自身的 `tileAssetKey`，不像牌局
 * 管理中的牌那樣需要透過 `TableStateSnapshot` 可見性快照才能解析牌面——這正是既有「自由放置裝飾牌」
 * 使用的同一條路徑，因此這裡不需要真正的對局資料也能正確顯示指定的牌面。省略 `tile` 引數時使用隨機
 * 抽出的內建牌面。
 *
 * 整組指令樹只在 [MinecraftEnvironment.isDevelopment] 為 `true` 時才註冊——正式打包發布的產物裡整棵
 * debug 指令樹根本沒被註冊過，不是「有指令但用權限藏起來」而已。額外再疊一層 op 權限
 * （`hasPermissionLevel(2)`）當作雙重保險，避免「不小心用非開發建置跑本地測試伺服器」時被一般玩家
 * 誤用。
 *
 * @property minecraftEnvironment 查詢目前是否為開發環境，決定整組指令樹要不要註冊。
 * @property effectScheduler 排定 `win` 胡牌慶祝演出的降臨特效。
 */
@Single
class FabricDebugAnimationCommand(
    private val minecraftEnvironment: MinecraftEnvironment,
    private val effectScheduler: FabricWinCelebrationEffectScheduler,
    private val showcaseScheduler: FabricWinCelebrationShowcaseScheduler,
    private val showcaseRegistry: WinCelebrationShowcaseRegistry,
) {
    /** 每個子指令自行排定的清除任務，見 [scheduleCleanup] KDoc。 */
    private val cleanupTasks = ConcurrentLinkedQueue<CleanupTask>()

    /** 註冊整組 debug 指令樹；只有開發環境才真的呼叫 `dispatcher.register`，見類別 KDoc。 */
    fun register() {
        if (!minecraftEnvironment.isDevelopment) return
        registerCleanupTicking()
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            dispatcher.register(
                literal(MinecraftModMetadata.MOD_ID).then(
                    literal(DEBUG_SUBCOMMAND)
                        .requires { it.hasPermissionLevel(OP_PERMISSION_LEVEL) }
                        .then(
                            literal(WIN_SUBCOMMAND)
                                .then(withOptionalTileArgument(literal(TSUMO_ARGUMENT)) { source, tileArg -> previewWin(source, isTsumo = true, tileArg) })
                                .then(withOptionalTileArgument(literal(RON_ARGUMENT)) { source, tileArg -> previewWin(source, isTsumo = false, tileArg) }),
                        )
                        .then(
                            literal(SHOWCASE_SUBCOMMAND)
                                .then(
                                    withOptionalCueArgument(literal(TSUMO_ARGUMENT), allowMultiple = false) { source, cue ->
                                        previewShowcase(source, isTsumo = true, listOf(cue ?: DEFAULT_SHOWCASE_CUE))
                                    },
                                )
                                .then(
                                    withOptionalCueArgument(literal(RON_ARGUMENT), allowMultiple = true) { source, cues ->
                                        previewShowcase(source, isTsumo = false, (cues ?: DEFAULT_SHOWCASE_CUE).split(",").take(3))
                                    },
                                )
                                .then(
                                    literal(MULTI_RON_ARGUMENT).then(
                                        argument(WINNER_COUNT_ARGUMENT, IntegerArgumentType.integer(2, 3))
                                            .executes { context ->
                                                val count = IntegerArgumentType.getInteger(context, WINNER_COUNT_ARGUMENT)
                                                previewShowcase(context.source, isTsumo = false, List(count) { DEFAULT_SHOWCASE_CUE })
                                            }
                                            .then(
                                                argument(CUE_ARGUMENT, StringArgumentType.word())
                                                    .suggests(::suggestShowcaseCueList)
                                                    .executes { context ->
                                                        val count = IntegerArgumentType.getInteger(context, WINNER_COUNT_ARGUMENT)
                                                        val supplied = StringArgumentType.getString(context, CUE_ARGUMENT).split(",").filter(String::isNotBlank)
                                                        val cues = expandShowcaseCues(supplied, count, DEFAULT_SHOWCASE_CUE)
                                                        previewShowcase(context.source, isTsumo = false, cues)
                                                    },
                                            ),
                                    ),
                                )
                                .then(
                                    literal(PHASE_ARGUMENT).then(
                                        argument(PHASE_NAME_ARGUMENT, StringArgumentType.word())
                                            .executes { context -> previewShowcasePhase(context.source, StringArgumentType.getString(context, PHASE_NAME_ARGUMENT), DEFAULT_SHOWCASE_CUE) }
                                            .then(
                                                argument(CUE_ARGUMENT, StringArgumentType.word())
                                                    .suggests(::suggestSingleShowcaseCue)
                                                    .executes { context ->
                                                        previewShowcasePhase(context.source, StringArgumentType.getString(context, PHASE_NAME_ARGUMENT), StringArgumentType.getString(context, CUE_ARGUMENT))
                                                    },
                                            ),
                                    ),
                                ),
                        )
                        .then(literal(DICE_SUBCOMMAND).executes { ctx -> previewDice(ctx.source) })
                        .then(withOptionalTileArgument(literal(DEAL_SUBCOMMAND), ::previewDeal))
                        .then(withOptionalTileArgument(literal(DRAW_SUBCOMMAND), ::previewDraw))
                        .then(withOptionalTileArgument(literal(DISCARD_SUBCOMMAND), ::previewDiscard))
                        .then(
                            literal(MELD_SUBCOMMAND)
                                .then(withOptionalTileArgument(literal(CHI_ARGUMENT)) { source, tileArg -> previewMeld(source, MeldType.CHI, tileArg) })
                                .then(withOptionalTileArgument(literal(PON_ARGUMENT)) { source, tileArg -> previewMeld(source, MeldType.PON, tileArg) })
                                .then(withOptionalTileArgument(literal(KAN_ARGUMENT)) { source, tileArg -> previewMeld(source, MeldType.OPEN_KAN, tileArg) }),
                        ),
                ),
            )
        }
    }

    /**
     * 幫一個子指令節點同時掛上「不帶 `tile` 引數」與「帶 `tile` 引數」兩種執行路徑——省略引數時
     * [onExecute] 收到的 `tileArg` 為 `null`，由呼叫端自行決定預設牌面，見 [resolveAssetKey]。
     */
    private fun withOptionalTileArgument(
        node: LiteralArgumentBuilder<ServerCommandSource>,
        onExecute: (ServerCommandSource, String?) -> Int,
    ): LiteralArgumentBuilder<ServerCommandSource> = node
        .executes { ctx -> onExecute(ctx.source, null) }
        .then(
            argument(TILE_ARGUMENT, StringArgumentType.word())
                .executes { ctx -> onExecute(ctx.source, StringArgumentType.getString(ctx, TILE_ARGUMENT)) },
        )

    /** 幫 showcase 節點掛上可選的 cue 或逗號分隔 cue 清單。 */
    private fun withOptionalCueArgument(
        node: LiteralArgumentBuilder<ServerCommandSource>,
        allowMultiple: Boolean,
        onExecute: (ServerCommandSource, String?) -> Int,
    ): LiteralArgumentBuilder<ServerCommandSource> = node
        .executes { ctx -> onExecute(ctx.source, null) }
        .then(
            argument(CUE_ARGUMENT, StringArgumentType.word())
                .suggests(if (allowMultiple) ::suggestShowcaseCueList else ::suggestSingleShowcaseCue)
                .executes { ctx -> onExecute(ctx.source, StringArgumentType.getString(ctx, CUE_ARGUMENT)) },
        )

    /** 列出單一 showcase cue 的 Tab 補全候選。 */
    private fun suggestSingleShowcaseCue(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> = suggestShowcaseCues(context, builder, allowMultiple = false)

    /** 列出逗號分隔 showcase cue 清單目前最後一段的 Tab 補全候選。 */
    private fun suggestShowcaseCueList(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> = suggestShowcaseCues(context, builder, allowMultiple = true)

    /** 從正式 registry 建立 showcase cue 補全，不另外維護 cue 字串清單。 */
    private fun suggestShowcaseCues(
        context: CommandContext<ServerCommandSource>,
        builder: SuggestionsBuilder,
        allowMultiple: Boolean,
    ): CompletableFuture<Suggestions> {
        buildShowcaseCueSuggestions(builder.remaining, showcaseRegistry.cueKeys, allowMultiple).forEach(builder::suggest)
        return builder.buildFuture()
    }

    /** `showcase <tsumo|ron>`：在玩家面前生成一至三翼完整鞘翅煙火 showcase。 */
    private fun previewShowcase(source: ServerCommandSource, isTsumo: Boolean, cues: List<String>, initialElapsedTicks: Int = 0): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val layout = virtualTableLayout(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())
        val controllerPos = BlockPos(layout.controllerX, layout.controllerY, layout.controllerZ)
        val tableId = Uuid.random()
        val wingTiles = cues.mapIndexed { seat, cue ->
            val tiles = List(DEFAULT_RULE_CONFIG.initialHandSize) { index ->
                val asset = ALL_TILE_ASSET_KEYS.dropLast(1)[(index + seat * 5) % (ALL_TILE_ASSET_KEYS.size - 1)]
                val tile = spawnFreeTile(world, layout.handPlacement(seat, DEFAULT_RULE_CONFIG.initialHandSize, index), MahjongTilePose.STANDING, asset)
                tile to asset
            }
            Triple(seat, cue.toCueKey(), tiles)
        }
        val winningAsset = ALL_TILE_ASSET_KEYS.first()
        val winningPlacement = if (isTsumo) layout.drawnTilePlacement(DEFAULT_RULE_CONFIG.initialHandSize) else layout.discardPlacement(0)
        val winningTile = spawnFreeTile(world, winningPlacement, MahjongTilePose.FACE_UP, winningAsset)
        showcaseScheduler.schedule(
            world = world,
            tableId = tableId,
            controllerPos = controllerPos,
            stagePlacement = layout.showcaseStagePlacement(),
            startGameTime = world.time - initialElapsedTicks,
            winningTileId = winningTile.uuid.toKotlinUuid(),
            winningTileAssetKey = winningAsset,
            wings = wingTiles.map { (seat, cue, tiles) ->
                FabricWinCelebrationShowcaseScheduler.Wing(seat, cue, tiles.map { it.first.uuid.toKotlinUuid() to it.second })
            },
        ) ?: return COMMAND_FAILURE
        // Stage 已同步保存所有牌面與起始位置；debug 臨時牌不必繼續 tick 到演出結束。
        (wingTiles.flatMap { it.third.map(Pair<MahjongTileEntity, String>::first) } + winningTile).forEach(Entity::discard)
        return COMMAND_SUCCESS
    }

    /** `showcase phase <launch|orbit|place|ignite|explode|reveal>`：直接從指定 phase 開始。 */
    private fun previewShowcasePhase(source: ServerCommandSource, phase: String, cue: String): Int {
        val initialElapsedTicks = when (phase.lowercase()) {
            "launch" -> WinCelebrationCinematicTimeline.LAUNCH_START
            "orbit" -> WinCelebrationCinematicTimeline.ORBIT_BUILDUP_START
            "place" -> WinCelebrationCinematicTimeline.TNT_PLACEMENT_START
            "ignite" -> WinCelebrationCinematicTimeline.IGNITION_START
            "explode" -> WinCelebrationCinematicTimeline.EXPLOSION_START
            "reveal" -> WinCelebrationCinematicTimeline.TITLE_REVEAL_START
            else -> return COMMAND_FAILURE
        }
        return previewShowcase(source, isTsumo = true, cues = listOf(cue), initialElapsedTicks = initialElapsedTicks.toInt())
    }

    /** 將 debug 短名稱補成完整 cue key。 */
    private fun String.toCueKey(): String = if (":" in this) this else "mahjongcraft:$this"

    /** 向 Fabric 登記每 tick 一次的臨時 entity 清除驅動，理由見 [scheduleCleanup] KDoc。 */
    private fun registerCleanupTicking() {
        ServerTickEvents.END_SERVER_TICK.register {
            val iterator = cleanupTasks.iterator()
            while (iterator.hasNext()) {
                val task = iterator.next()
                if (task.world.time >= task.endGameTime) {
                    task.entities.forEach(Entity::discard)
                    iterator.remove()
                }
            }
        }
    }

    /**
     * `win <tsumo|ron>`：完全自成一體地重播胡牌慶祝演出——依標準日麻初始手牌張數，臨時生成與正式
     * 對局相同數量的立牌
     * 「手牌」（先打亂順序生成，再用 [TileAnimationSteps.scheduleReorder] 飛到整理後的格位，實際演出
     * 「強制理牌重排」這一步），接著依 [isTsumo] 走跟正式對局一模一樣的時間軸：自摸時胡牌張重排至
     * 手牌右側保留間距的正式摸牌位，先單獨倒下、等待、其餘手牌一起倒下；榮和時省略胡牌張單獨倒下
     * 這一步，額外在牌河生成
     * 一張已經面朝上的胡牌張（模擬放銃者牌河/副露區的那張），跟正式對局的差異完全一致，見
     * `FabricGamePresentationPublisher.publishWinCelebration` KDoc。降臨特效鎖定的目標固定是胡牌張，
     * 沿用 [effectScheduler] 排定，特效播完後透過 `onComplete` 回呼清除這次生成的全部臨時 entity。
     */
    private fun previewWin(source: ServerCommandSource, isTsumo: Boolean, tileArg: String?): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val assetKey = resolveAssetKey(tileArg)
        val layout = virtualTableLayout(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())
        val handSize = DEFAULT_RULE_CONFIG.initialHandSize
        val handPlacements = (0 until handSize).map { slot -> layout.handPlacement(handSize = handSize, tileIndex = slot) }
        val sortedPlacements = if (isTsumo) {
            listOf(layout.drawnTilePlacement(standingTileCount = handSize)) + handPlacements
        } else {
            handPlacements
        }
        val handTiles = sortedPlacements.shuffled().map { shuffledPlacement ->
            spawnFreeTile(world, shuffledPlacement, MahjongTilePose.STANDING, assetKey)
        }

        val reorderStartGameTime = world.time
        val reorderEndGameTime = reorderStartGameTime + MahjongTileTableLayout.WIN_REORDER_FLIGHT_DURATION_TICKS
        handTiles.forEachIndexed { index, tile -> TileAnimationSteps.scheduleReorder(tile, sortedPlacements[index], reorderStartGameTime) }

        val winningTile: MahjongTileEntity
        val handLaydownEndGameTime: Long
        if (isTsumo) {
            winningTile = handTiles.first()
            val winTileLaydownStartGameTime = reorderEndGameTime
            val winTileLaydownEndGameTime = winTileLaydownStartGameTime + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
            TileAnimationSteps.scheduleLaydown(winningTile, winTileLaydownStartGameTime)
            val restLaydownStartGameTime = winTileLaydownEndGameTime + MahjongTileTableLayout.WIN_PRE_HAND_LAYDOWN_DELAY_TICKS
            (handTiles - winningTile).forEach { tile -> TileAnimationSteps.scheduleLaydown(tile, restLaydownStartGameTime) }
            handLaydownEndGameTime = restLaydownStartGameTime + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
        } else {
            val discardedPlacement = layout.discardPlacement(discardIndex = 0)
            winningTile = spawnFreeTile(world, discardedPlacement, MahjongTilePose.FACE_UP, assetKey)
            val handLaydownStartGameTime = reorderEndGameTime + MahjongTileTableLayout.WIN_PRE_HAND_LAYDOWN_DELAY_TICKS
            handTiles.forEach { tile -> TileAnimationSteps.scheduleLaydown(tile, handLaydownStartGameTime) }
            handLaydownEndGameTime = handLaydownStartGameTime + MahjongTileTableLayout.WIN_LAYDOWN_DURATION_TICKS
        }

        val effectStartGameTime = handLaydownEndGameTime + MahjongTileTableLayout.WIN_PRE_EFFECT_DELAY_TICKS
        val effectEndGameTime = effectStartGameTime + MahjongTileTableLayout.WIN_EFFECT_DURATION_TICKS
        val allSpawnedTiles = if (isTsumo) handTiles else handTiles + winningTile
        effectScheduler.schedule(
            world = world,
            targetTileId = winningTile.uuid.toKotlinUuid(),
            startGameTime = effectStartGameTime,
            endGameTime = effectEndGameTime,
            onComplete = { allSpawnedTiles.forEach(Entity::discard) },
        )
        return COMMAND_SUCCESS
    }

    /** `dice`：臨時生成 [DEBUG_DICE_COUNT] 顆骰子，重播擲骰動畫；動畫（含觀看緩衝）播完後自行清除。 */
    private fun previewDice(source: ServerCommandSource): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val layout = virtualTableLayout(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())
        val tableId = Uuid.random()
        val placements = MahjongDiceTableLayout.placements(
            controllerX = layout.controllerX,
            controllerY = layout.controllerY,
            controllerZ = layout.controllerZ,
            tableId = tableId,
            tableFacing = layout.tableFacing,
            throwSide = MahjongTableSide.SOUTH,
            rollSequence = 0,
            diceCount = DEBUG_DICE_COUNT,
        )
        val dice = placements.map { placement ->
            MahjongDiceEntity(world = world).apply {
                refreshPositionAndAngles(
                    placement.finalPosition.x,
                    placement.finalPosition.y,
                    placement.finalPosition.z,
                    0.0f,
                    0.0f,
                )
            }.also(world::spawnEntity)
        }
        dice.zip(placements).forEach { (entity, placement) ->
            entity.startRoll(
                finalPoint = MahjongDicePoint.entries.random(),
                seed = Random.nextLong(),
                startDelayTicks = placement.startDelayTicks,
                startOffset = placement.startOffset,
            )
        }
        val lifetimeTicks = DiceRollAnimationSpec.DEFAULT_DURATION_TICKS + DiceRollAnimationSpec.EXTRA_VIEWING_TICKS
        scheduleCleanup(world, world.time + lifetimeTicks, dice)
        return COMMAND_SUCCESS
    }

    /**
     * `deal`：依標準日麻初始手牌張數，在正式牌牆座標臨時生成蓋牌，重播開局發牌
     * 動畫（起飛→落下→翻牌）飛到手牌列；不模擬多座位/多批次，全部視為單一批次、單一座位，理由見
     * [TileAnimationSteps.scheduleDealBatch] KDoc——它本身只吃單張牌與絕對時刻，不需要真正的多座位
     * 協調也能正確播放同一段動畫。
     */
    private fun previewDeal(source: ServerCommandSource, tileArg: String?): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val assetKey = resolveAssetKey(tileArg)
        val layout = virtualTableLayout(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())
        val handSize = DEFAULT_RULE_CONFIG.initialHandSize

        val finalPlacements = (0 until handSize).map { slot ->
            layout.handPlacement(handSize = handSize, tileIndex = slot)
        }
        val sourcePlacements = (0 until handSize).map { slot ->
            layout.wallPlacement(tileIndex = slot)
        }
        val tiles = sourcePlacements.map { placement -> spawnFreeTile(world, placement, MahjongTilePose.FACE_DOWN, assetKey) }

        val liftAbsoluteGameTime = world.time
        val flipAbsoluteGameTime = liftAbsoluteGameTime +
            MahjongTileTableLayout.DEAL_LIFT_DURATION_TICKS +
            MahjongTileTableLayout.DEAL_SNAP_GAP_TICKS +
            MahjongTileTableLayout.DEAL_DROP_DURATION_TICKS +
            MahjongTileTableLayout.DEAL_FLIP_GAP_TICKS
        tiles.forEachIndexed { index, tile ->
            TileAnimationSteps.scheduleDealBatch(tile, finalPlacements[index], finalPlacements[index], liftAbsoluteGameTime, flipAbsoluteGameTime)
        }
        val endGameTime = flipAbsoluteGameTime + MahjongTileTableLayout.DEAL_FLIP_DURATION_TICKS + PREVIEW_VIEWING_BUFFER_TICKS
        scheduleCleanup(world, endGameTime, tiles)
        return COMMAND_SUCCESS
    }

    /**
     * `draw`：臨時生成一張牌在模擬牌牆位置（蓋牌），重播摸牌動畫（起飛→隱形傳送→翻面→落下）飛到
     * 摸牌位。
     */
    private fun previewDraw(source: ServerCommandSource, tileArg: String?): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val assetKey = resolveAssetKey(tileArg)
        val layout = virtualTableLayout(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())

        val sourcePlacement = layout.wallPlacement(tileIndex = 0)
        val finalPlacement = layout.drawnTilePlacement(standingTileCount = DEFAULT_RULE_CONFIG.initialHandSize)
        val tile = spawnFreeTile(world, sourcePlacement, MahjongTilePose.FACE_DOWN, assetKey)
        TileAnimationSteps.scheduleDrawnTile(tile, finalPlacement)
        val endGameTime = world.time +
            MahjongTileTableLayout.DRAW_LIFT_DURATION_TICKS +
            MahjongTileTableLayout.DRAW_SNAP_GAP_TICKS +
            MahjongTileTableLayout.DRAW_DROP_DURATION_TICKS +
            PREVIEW_VIEWING_BUFFER_TICKS
        scheduleCleanup(world, endGameTime, listOf(tile))
        return COMMAND_SUCCESS
    }

    /** `discard`：臨時生成一張手牌位置的立牌，重播捨牌動畫（連續可見拋物線飛行）飛到模擬牌河位置。 */
    private fun previewDiscard(source: ServerCommandSource, tileArg: String?): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val assetKey = resolveAssetKey(tileArg)
        val layout = virtualTableLayout(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())

        val sourcePlacement = layout.handPlacement(handSize = DEFAULT_RULE_CONFIG.initialHandSize, tileIndex = 0)
        val finalPlacement = layout.discardPlacement(discardIndex = 0)
        val tile = spawnFreeTile(world, sourcePlacement, MahjongTilePose.STANDING, assetKey)
        TileAnimationSteps.scheduleDiscardFlight(tile, finalPlacement)
        val endGameTime = world.time + MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS + PREVIEW_VIEWING_BUFFER_TICKS
        scheduleCleanup(world, endGameTime, listOf(tile))
        return COMMAND_SUCCESS
    }

    /**
     * `meld <chi|pon|kan>`：臨時生成幾張手牌位置的立牌（吃/碰 3 張，槓 4 張），重播鳴牌動畫（連續可見
     * 拋物線飛行）飛到模擬副露區位置，姿態轉為面朝上。
     */
    private fun previewMeld(source: ServerCommandSource, type: MeldType, tileArg: String?): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val assetKey = resolveAssetKey(tileArg)
        val tileCount = if (type == MeldType.OPEN_KAN) MELD_KAN_TILE_COUNT else MELD_TILE_COUNT
        val layout = virtualTableLayout(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())

        val sourcePlacements = (0 until tileCount).map { slot ->
            layout.handPlacement(handSize = DEFAULT_RULE_CONFIG.initialHandSize, tileIndex = slot)
        }
        val finalPlacements = layout.meldPlacements(type = type, tileCount = tileCount)
        val tiles = sourcePlacements.map { placement -> spawnFreeTile(world, placement, MahjongTilePose.STANDING, assetKey) }
        tiles.forEachIndexed { index, tile -> TileAnimationSteps.scheduleMeldClaim(tile, finalPlacements[index], MahjongTilePose.FACE_UP) }
        val endGameTime = world.time + MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS + PREVIEW_VIEWING_BUFFER_TICKS
        scheduleCleanup(world, endGameTime, tiles)
        return COMMAND_SUCCESS
    }

    /** 生成一張自由放置（不掛任何桌子/對局）的臨時牌 entity，理由見類別 KDoc 的牌面說明。 */
    private fun spawnFreeTile(
        world: ServerWorld,
        placement: MahjongTileWallPlacement,
        pose: MahjongTilePose,
        assetKey: String,
    ): MahjongTileEntity {
        val tile = MahjongTileEntity(world = world).apply {
            uuid = Uuid.random().toJavaUuid()
            refreshPositionAndAngles(placement.x, placement.y, placement.z, placement.yaw, 0.0f)
            tilePose = pose
            tileAssetKey = assetKey
        }
        world.spawnEntity(tile)
        return tile
    }

    /** 省略 `tile` 引數時隨機抽一個內建牌面（排除佔位用的 `unknown`），否則正規化呼叫者輸入的字串。 */
    private fun resolveAssetKey(tileArg: String?): String = tileArg?.normalizedTileAssetKey()
        ?: ALL_TILE_ASSET_KEYS.dropLast(1).random()

    /** 以玩家腳下方塊為基準，將虛擬 controller 沿玩家視線前推，讓座位 0 的正式布局落在玩家面前。 */
    private fun virtualTableLayout(
        playerBlockX: Int,
        playerBlockY: Int,
        playerBlockZ: Int,
        tableFacing: MahjongTableFacing,
    ): VirtualTableLayout {
        val controller = virtualControllerPos(playerBlockX, playerBlockY, playerBlockZ, tableFacing)
        return VirtualTableLayout(controller.x, controller.y, controller.z, tableFacing)
    }

    /** 算出 debug 虛擬桌 controller 方塊座標。 */
    private fun virtualControllerPos(
        playerBlockX: Int,
        playerBlockY: Int,
        playerBlockZ: Int,
        tableFacing: MahjongTableFacing,
    ): net.minecraft.util.math.BlockPos {
        val (offsetX, offsetZ) = when (tableFacing) {
            MahjongTableFacing.NORTH -> 0 to -VIRTUAL_CONTROLLER_FORWARD_BLOCKS
            MahjongTableFacing.EAST -> VIRTUAL_CONTROLLER_FORWARD_BLOCKS to 0
            MahjongTableFacing.SOUTH -> 0 to VIRTUAL_CONTROLLER_FORWARD_BLOCKS
            MahjongTableFacing.WEST -> -VIRTUAL_CONTROLLER_FORWARD_BLOCKS to 0
        }
        return net.minecraft.util.math.BlockPos(playerBlockX + offsetX, playerBlockY, playerBlockZ + offsetZ)
    }

    /** 虛擬桌的正式布局呼叫參數；不對應任何實際 controller block 或對局狀態。 */
    private data class VirtualTableLayout(
        val controllerX: Int,
        val controllerY: Int,
        val controllerZ: Int,
        val tableFacing: MahjongTableFacing,
    ) {
        /** 取得座位 0 的正式手牌格位。 */
        fun handPlacement(handSize: Int, tileIndex: Int): MahjongTileWallPlacement = handPlacement(DEBUG_SEAT_INDEX, handSize, tileIndex)

        /** 取得指定座位的正式手牌格位，供多家和 showcase 使用。 */
        fun handPlacement(seatIndex: Int, handSize: Int, tileIndex: Int): MahjongTileWallPlacement = MahjongTileTableLayout.handPlacement(
            controllerX = controllerX,
            controllerY = controllerY,
            controllerZ = controllerZ,
            tableFacing = tableFacing,
            seatIndex = seatIndex,
            handSize = handSize,
            tileIndex = tileIndex,
        )

        /** 取得與正式桌面完全一致的 showcase 世界中心。 */
        fun showcaseStagePlacement(): MahjongTileWallPlacement = MahjongTileTableLayout.showcaseStagePlacement(controllerX, controllerY, controllerZ)

        /** 取得座位 0 的正式摸牌格位。 */
        fun drawnTilePlacement(standingTileCount: Int): MahjongTileWallPlacement = MahjongTileTableLayout.drawnTilePlacement(
            controllerX = controllerX,
            controllerY = controllerY,
            controllerZ = controllerZ,
            tableFacing = tableFacing,
            seatIndex = DEBUG_SEAT_INDEX,
            standingTileCount = standingTileCount,
        )

        /** 取得標準 17 墩牌牆第一面的正式牌張格位。 */
        fun wallPlacement(tileIndex: Int): MahjongTileWallPlacement = MahjongTileTableLayout.wallPlacement(
            controllerX = controllerX,
            controllerY = controllerY,
            controllerZ = controllerZ,
            tableFacing = tableFacing,
            dealerSeatIndex = DEBUG_SEAT_INDEX,
            stacksPerSide = STANDARD_WALL_STACKS_PER_SIDE,
            position = TileWallPosition(
                side = 0,
                stack = tileIndex / WALL_LAYERS_PER_STACK,
                layer = tileIndex % WALL_LAYERS_PER_STACK,
            ),
        )

        /** 取得座位 0 第一張正式牌河格位。 */
        fun discardPlacement(discardIndex: Int): MahjongTileWallPlacement = MahjongTileTableLayout.discardPlacement(
            controllerX = controllerX,
            controllerY = controllerY,
            controllerZ = controllerZ,
            tableFacing = tableFacing,
            seatIndex = DEBUG_SEAT_INDEX,
            discardIndex = discardIndex,
            isSidewaysMarked = false,
            sidewaysMarkedDiscardIndex = null,
            wallRemaining = true,
        )

        /** 依正式副露游標、鳴牌來源與牌寬規則，取得單組吃、碰或明槓的格位。 */
        fun meldPlacements(type: MeldType, tileCount: Int): List<MahjongTileWallPlacement> {
            val sourceDirection = if (type == MeldType.CHI) RelativeDirection.Left else RelativeDirection.Across
            val sidewaysSlot = MahjongTileTableLayout.sidewaysSlotIndex(sourceDirection, tileCount)
            var cursorAlong = MahjongTileTableLayout.stickAreaWidth(stickCount = 0)
            return (tileCount - 1 downTo 0).map { slot ->
                val isSideways = slot == sidewaysSlot
                val halfWidth = if (isSideways) MahjongTileDimensions.TILE_HEIGHT / 2.0 else MahjongTileDimensions.TILE_WIDTH / 2.0
                cursorAlong += halfWidth
                val placement = MahjongTileTableLayout.meldPlacement(
                    controllerX = controllerX,
                    controllerY = controllerY,
                    controllerZ = controllerZ,
                    tableFacing = tableFacing,
                    seatIndex = DEBUG_SEAT_INDEX,
                    alongOffsetFromCorner = cursorAlong,
                    isSidewaysTile = isSideways,
                )
                cursorAlong += halfWidth + MahjongTileDimensions.TILE_SMALL_PADDING
                placement
            }.reversed()
        }
    }

    /**
     * 排定 [entities] 在 [endGameTime]（絕對 game time）到期時自動清除——每個子指令的動畫總時長各不
     * 相同，呼叫端各自算好自己那套動畫（含收尾緩衝）實際播完的時刻。純記憶體佇列，不寫進世界存檔：
     * 這些本來就是全新生成、不影響任何真實對局狀態的臨時 entity，伺服器重啟後這批清除排程單純消失，
     * 頂多留下沒清乾淨的臨時裝飾牌，不影響任何遊戲邏輯。
     */
    private fun scheduleCleanup(world: ServerWorld, endGameTime: Long, entities: List<Entity>) {
        cleanupTasks += CleanupTask(world, endGameTime, entities)
    }

    /** 見 [scheduleCleanup] KDoc。 */
    private data class CleanupTask(val world: ServerWorld, val endGameTime: Long, val entities: List<Entity>)

    private companion object {
        const val OP_PERMISSION_LEVEL: Int = 2
        const val DEBUG_SUBCOMMAND: String = "debug"
        const val TILE_ARGUMENT: String = "tile"
        const val WIN_SUBCOMMAND: String = "win"
        const val SHOWCASE_SUBCOMMAND: String = "showcase"
        const val MULTI_RON_ARGUMENT: String = "multi_ron"
        const val WINNER_COUNT_ARGUMENT: String = "winner_count"
        const val CUE_ARGUMENT: String = "cue"
        const val PHASE_ARGUMENT: String = "phase"
        const val PHASE_NAME_ARGUMENT: String = "phase_name"
        const val DEFAULT_SHOWCASE_CUE: String = "kokushi_musou"
        const val TSUMO_ARGUMENT: String = "tsumo"
        const val RON_ARGUMENT: String = "ron"
        const val DICE_SUBCOMMAND: String = "dice"
        const val DEAL_SUBCOMMAND: String = "deal"
        const val DRAW_SUBCOMMAND: String = "draw"
        const val DISCARD_SUBCOMMAND: String = "discard"
        const val MELD_SUBCOMMAND: String = "meld"
        const val CHI_ARGUMENT: String = "chi"
        const val PON_ARGUMENT: String = "pon"
        const val KAN_ARGUMENT: String = "kan"

        /** 虛擬 controller 與玩家腳下方塊的水平距離，使近側手牌落在玩家前方約一格處。 */
        const val VIRTUAL_CONTROLLER_FORWARD_BLOCKS: Int = 3

        /** 預覽固定使用座位 0，對應虛擬桌面向玩家的近側。 */
        const val DEBUG_SEAT_INDEX: Int = 0

        /** 標準四人日麻共 136 張牌，四面各 17 墩。 */
        const val STANDARD_WALL_STACKS_PER_SIDE: Int = 17

        /** 每墩牌牆固定上下兩層。 */
        const val WALL_LAYERS_PER_STACK: Int = 2

        /** 預覽牌數跟隨標準日麻規則的單一設定來源。 */
        val DEFAULT_RULE_CONFIG: RiichiRuleConfig = RiichiRuleConfig()

        /** `dice` 一次生成的骰子數量。 */
        const val DEBUG_DICE_COUNT: Int = 2

        /** 動畫播完到實際清除臨時 entity 之間的觀看緩衝，理由同正式對局的觀看緩衝設計。 */
        const val PREVIEW_VIEWING_BUFFER_TICKS: Int = 20

        /** `meld kan` 需要的牌數。 */
        const val MELD_KAN_TILE_COUNT: Int = 4

        /** `meld chi`／`meld pon` 需要的牌數。 */
        const val MELD_TILE_COUNT: Int = 3

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1

        /** Brigadier 失敗回傳值。 */
        const val COMMAND_FAILURE: Int = 0
    }
}

/**
 * 將 debug 指令提供的 cue 展開成指定贏家數量；單一 cue 套用至所有贏家，多個 cue 則依序對應，
 * 未提供的尾端位置使用 [defaultCue]。
 */
internal fun expandShowcaseCues(
    supplied: List<String>,
    winnerCount: Int,
    defaultCue: String,
): List<String> = when (supplied.size) {
    0 -> List(winnerCount) { defaultCue }
    1 -> List(winnerCount) { supplied.single() }
    else -> List(winnerCount) { index -> supplied.getOrNull(index) ?: defaultCue }
}

/**
 * 依目前輸入內容建立 showcase cue 候選；內建 namespace 預設縮成短名稱，輸入 namespace 時則保留完整
 * key，逗號清單只替換最後一段。
 */
internal fun buildShowcaseCueSuggestions(
    remaining: String,
    cueKeys: Set<String>,
    allowMultiple: Boolean,
): List<String> {
    val separatorIndex = if (allowMultiple) remaining.lastIndexOf(',') else -1
    val completedPrefix = remaining.takeIf { separatorIndex >= 0 }?.substring(0, separatorIndex + 1).orEmpty()
    val currentToken = remaining.substring(separatorIndex + 1)
    val useFullKeys = ':' in currentToken
    return (cueKeys + "mahjongcraft:generic")
        .asSequence()
        .map { key -> if (!useFullKeys && key.startsWith("mahjongcraft:")) key.removePrefix("mahjongcraft:") else key }
        .distinct()
        .filter { candidate -> candidate.startsWith(currentToken, ignoreCase = true) }
        .sorted()
        .map { candidate -> completedPrefix + candidate }
        .toList()
}
