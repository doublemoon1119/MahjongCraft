package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.animation

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionTileOrientationDto
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.DiceRollPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDiceEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDicePoint
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MeldActionPopupTile
import com.doublemoon1119.mahjongcraft.platform.fabric.server.dice.toMahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPreviewDefaults
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugPreviewEntityLifecycle
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugTilePreviewSupport
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugVirtualTableLayout
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.support.DebugVirtualTableLayoutFactory
import com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.TileAnimationSteps
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceRollAnimationSpec
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongDiceTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import org.koin.core.annotation.Single
import kotlin.random.Random
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 建立 `/mahjongcraft debug` 底下的自由 entity 動畫預覽子指令：`dice`、`deal`、`draw`、`discard` 與
 * `meld`。
 *
 * 每個子指令都完全自成一體：在呼叫者面前臨時生成幾個全新的 [MahjongTileEntity]／[MahjongDiceEntity]
 * （不呼叫 `assignToTable`、不掛在任何桌子／對局底下），直接對這些臨時 entity 重播對應的動畫排程邏輯
 * （[TileAnimationSteps]，跟正式桌子綁定的 presenter 共用同一份），動畫播完後由
 * [DebugPreviewEntityLifecycle] 清除這些臨時 entity。完全不觸碰 `GameRepository`／
 * `PlayerMembershipRepository`／任何房間或對局 use case——呼叫者不需要站在任何桌子附近，也不需要加入
 * 房間或身處進行中的對局，站在隨便一個已載入的世界座標就能直接測試演出效果，且每次呼叫都是全新一批
 * entity，可以無限重複呼叫。
 *
 * 牌面由每個子指令下方可選的 `tile` 引數指定，解析與預設值見 [DebugTilePreviewSupport]。
 *
 * @property tilePreviewSupport 解析牌面、生成臨時牌 entity 並提供 `tile` 引數節點。
 * @property layoutFactory 依呼叫者座標建立虛擬桌布局。
 * @property entityLifecycle 排定臨時 entity 的到期清除。
 */
@Single
class FabricDebugAnimationCommand(
    private val tilePreviewSupport: DebugTilePreviewSupport,
    private val layoutFactory: DebugVirtualTableLayoutFactory,
    private val entityLifecycle: DebugPreviewEntityLifecycle,
) {
    /** 建立 `dice` 子指令；省略參數時使用 [DEFAULT_DEBUG_DICE_COUNT]。 */
    fun buildDiceCommand(): LiteralArgumentBuilder<ServerCommandSource> = literal(DICE_SUBCOMMAND)
        .executes { ctx -> previewDice(ctx.source, DEFAULT_DEBUG_DICE_COUNT) }
        .then(literal(TWO_DICE_ARGUMENT).executes { ctx -> previewDice(ctx.source, 2) })
        .then(literal(THREE_DICE_ARGUMENT).executes { ctx -> previewDice(ctx.source, 3) })

    /** 建立 `deal [tile]` 子指令。 */
    fun buildDealCommand(): LiteralArgumentBuilder<ServerCommandSource> = tilePreviewSupport.withOptionalTileArgument(literal(DEAL_SUBCOMMAND), ::previewDeal)

    /** 建立 `draw [tile]` 子指令。 */
    fun buildDrawCommand(): LiteralArgumentBuilder<ServerCommandSource> = tilePreviewSupport.withOptionalTileArgument(literal(DRAW_SUBCOMMAND), ::previewDraw)

    /** 建立 `discard [tile]` 子指令。 */
    fun buildDiscardCommand(): LiteralArgumentBuilder<ServerCommandSource> = tilePreviewSupport.withOptionalTileArgument(literal(DISCARD_SUBCOMMAND), ::previewDiscard)

    /** 建立 `meld <chi|pon|kan|added_kan> [tile]` 子指令。 */
    fun buildMeldCommand(): LiteralArgumentBuilder<ServerCommandSource> = literal(MELD_SUBCOMMAND)
        .then(tilePreviewSupport.withOptionalTileArgument(literal(CHI_ARGUMENT)) { source, tileArg -> previewMeld(source, MeldType.CHI, tileArg) })
        .then(tilePreviewSupport.withOptionalTileArgument(literal(PON_ARGUMENT)) { source, tileArg -> previewMeld(source, MeldType.PON, tileArg) })
        .then(tilePreviewSupport.withOptionalTileArgument(literal(KAN_ARGUMENT)) { source, tileArg -> previewMeld(source, MeldType.OPEN_KAN, tileArg) })
        .then(tilePreviewSupport.withOptionalTileArgument(literal(ADDED_KAN_ARGUMENT)) { source, tileArg -> previewAddedKanMeld(source, tileArg) })

    /** `dice [2|3]`：臨時生成完整桌面骰子與聚合結果面板，結束後自行清除。 */
    private fun previewDice(source: ServerCommandSource, diceCount: Int): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val layout = layoutFactory.create(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())
        val tableId = Uuid.random()
        val placements = MahjongDiceTableLayout.placements(
            controllerX = layout.controllerX,
            controllerY = layout.controllerY,
            controllerZ = layout.controllerZ,
            tableId = tableId,
            tableFacing = layout.tableFacing,
            throwSide = MahjongTableSide.SOUTH,
            rollSequence = 0,
            diceCount = diceCount,
        )
        val points = List(diceCount) { index -> MahjongDicePoint.entries[(index + DEBUG_DICE_POINT_OFFSET) % MahjongDicePoint.entries.size] }
        val revealGameTime = world.time + MahjongDiceTableLayout.maxStartDelayTicks(diceCount) + DiceRollAnimationSpec.DEFAULT_DURATION_TICKS
        val endGameTime = revealGameTime + DiceRollAnimationSpec.EXTRA_VIEWING_TICKS
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
        dice.zip(placements).zip(points).forEach { (entityAndPlacement, point) ->
            val (entity, placement) = entityAndPlacement
            entity.startRoll(
                finalPoint = point,
                seed = Random.nextLong(),
                startDelayTicks = placement.startDelayTicks,
                startOffset = placement.startOffset,
                sharedViewingEndGameTime = endGameTime,
            )
        }
        val stage = DiceRollPresentationEntity(world = world).apply {
            refreshPositionAndAngles(
                layout.controllerX + DEBUG_BLOCK_CENTER,
                layout.controllerY + DEBUG_TABLETOP_HEIGHT + DiceRollPresentationEntity.HEIGHT_ABOVE_TABLETOP,
                layout.controllerZ + DEBUG_BLOCK_CENTER,
                0.0f,
                0.0f,
            )
            configure(tableId, points.map(MahjongDicePoint::value), revealGameTime, endGameTime)
        }
        world.spawnEntity(stage)
        entityLifecycle.schedule(world, endGameTime, dice + stage)
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
        val assetKey = tilePreviewSupport.resolveAssetKey(tileArg)
        val layout = layoutFactory.create(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())
        val handSize = DebugPreviewDefaults.RULE_CONFIG.initialHandSize

        val finalPlacements = (0 until handSize).map { slot ->
            layout.handPlacement(handSize = handSize, tileIndex = slot)
        }
        val sourcePlacements = (0 until handSize).map { slot ->
            layout.wallPlacement(tileIndex = slot)
        }
        val tiles = sourcePlacements.map { placement -> tilePreviewSupport.spawnFreeTile(world, placement, MahjongTilePose.FACE_DOWN, assetKey) }

        val liftAbsoluteGameTime = world.time
        val flipAbsoluteGameTime = liftAbsoluteGameTime +
            MahjongTileTableLayout.DEAL_LIFT_DURATION_TICKS +
            MahjongTileTableLayout.DEAL_SNAP_GAP_TICKS +
            MahjongTileTableLayout.DEAL_DROP_DURATION_TICKS +
            MahjongTileTableLayout.DEAL_FLIP_GAP_TICKS
        tiles.forEachIndexed { index, tile ->
            TileAnimationSteps.scheduleDealBatch(
                tile,
                finalPlacements[index],
                finalPlacements[index],
                liftAbsoluteGameTime,
                flipAbsoluteGameTime,
                playDealSound = index == 0,
                playFlipSound = index == 0,
            )
        }
        val endGameTime = flipAbsoluteGameTime + MahjongTileTableLayout.DEAL_FLIP_DURATION_TICKS + PREVIEW_VIEWING_BUFFER_TICKS
        entityLifecycle.schedule(world, endGameTime, tiles)
        return COMMAND_SUCCESS
    }

    /**
     * `draw`：臨時生成一張牌在模擬牌牆位置（蓋牌），重播摸牌動畫（起飛→隱形傳送→翻面→落下）飛到
     * 摸牌位。
     */
    private fun previewDraw(source: ServerCommandSource, tileArg: String?): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val assetKey = tilePreviewSupport.resolveAssetKey(tileArg)
        val layout = layoutFactory.create(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())

        val sourcePlacement = layout.wallPlacement(tileIndex = 0)
        val finalPlacement = layout.drawnTilePlacement(standingTileCount = DebugPreviewDefaults.RULE_CONFIG.initialHandSize)
        val tile = tilePreviewSupport.spawnFreeTile(world, sourcePlacement, MahjongTilePose.FACE_DOWN, assetKey)
        TileAnimationSteps.scheduleDrawnTile(tile, finalPlacement)
        val endGameTime = world.time +
            MahjongTileTableLayout.DRAW_LIFT_DURATION_TICKS +
            MahjongTileTableLayout.DRAW_SNAP_GAP_TICKS +
            MahjongTileTableLayout.DRAW_DROP_DURATION_TICKS +
            PREVIEW_VIEWING_BUFFER_TICKS
        entityLifecycle.schedule(world, endGameTime, listOf(tile))
        return COMMAND_SUCCESS
    }

    /** `discard`：臨時生成一張手牌位置的立牌，重播捨牌動畫（連續可見拋物線飛行）飛到模擬牌河位置。 */
    private fun previewDiscard(source: ServerCommandSource, tileArg: String?): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val assetKey = tilePreviewSupport.resolveAssetKey(tileArg)
        val layout = layoutFactory.create(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())

        val sourcePlacement = layout.handPlacement(handSize = DebugPreviewDefaults.RULE_CONFIG.initialHandSize, tileIndex = 0)
        val finalPlacement = layout.discardPlacement(discardIndex = 0)
        val tile = tilePreviewSupport.spawnFreeTile(world, sourcePlacement, MahjongTilePose.STANDING, assetKey)
        TileAnimationSteps.scheduleDiscardFlight(tile, finalPlacement)
        val endGameTime = world.time + MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS + PREVIEW_VIEWING_BUFFER_TICKS
        entityLifecycle.schedule(world, endGameTime, listOf(tile))
        return COMMAND_SUCCESS
    }

    /**
     * `meld <chi|pon|kan>`：臨時生成幾張手牌位置的立牌（吃/碰 3 張，槓 4 張），重播鳴牌動畫（連續可見
     * 拋物線飛行）飛到模擬副露區位置，姿態轉為面朝上。
     */
    private fun previewMeld(source: ServerCommandSource, type: MeldType, tileArg: String?): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val assetKey = tilePreviewSupport.resolveAssetKey(tileArg)
        val tileCount = if (type == MeldType.OPEN_KAN) MELD_KAN_TILE_COUNT else DebugPreviewDefaults.MELD_TILE_COUNT
        val layout = layoutFactory.create(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())
        val sourceDirection = if (type == MeldType.CHI) RelativeDirection.Left else RelativeDirection.Across
        val sidewaysSlot = MahjongTileTableLayout.sidewaysSlotIndex(sourceDirection, tileCount)
        val claimedOrientation = when (sourceDirection) {
            RelativeDirection.Left -> DecisionTileOrientationDto.ROTATED_LEFT
            RelativeDirection.Across, RelativeDirection.Right -> DecisionTileOrientationDto.ROTATED_RIGHT
            RelativeDirection.Self -> DecisionTileOrientationDto.UPRIGHT
        }

        val sourcePlacements = (0 until tileCount).map { slot ->
            layout.handPlacement(handSize = DebugPreviewDefaults.RULE_CONFIG.initialHandSize, tileIndex = slot)
        }
        val finalPlacements = layout.meldPlacements(type = type, tileCount = tileCount)
        val tiles = sourcePlacements.map { placement -> tilePreviewSupport.spawnFreeTile(world, placement, MahjongTilePose.STANDING, assetKey) }
        tiles.forEachIndexed { index, tile ->
            TileAnimationSteps.scheduleMeldClaim(
                tile,
                finalPlacements[index],
                MahjongTilePose.FACE_UP,
                playLandingSound = index == 0,
            )
        }
        val popupTiles = tiles.mapIndexed { slot, tile ->
            MeldActionPopupTile(
                tileId = tile.uuid.toKotlinUuid(),
                orientation = if (slot == sidewaysSlot) claimedOrientation else DecisionTileOrientationDto.UPRIGHT,
                stacked = false,
            )
        }
        val landingTime = world.time + MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS
        tiles.first().showMeldActionPopup(
            popupTiles,
            landingTime,
            landingTime + TileAnimationSteps.ACTION_POPUP_DURATION_TICKS,
        )
        val endGameTime = world.time + MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS + PREVIEW_VIEWING_BUFFER_TICKS
        entityLifecycle.schedule(world, endGameTime, tiles)
        return COMMAND_SUCCESS
    }

    /**
     * `meld added_kan`：臨時生成一組碰（3 張）加上第 4 張加槓牌，重播鳴牌動畫飛到模擬副露區位置，並在
     * 其中一張牌上掛一次鳴牌牌面提示（[MahjongTileEntity.showMeldActionPopup]）——加槓的疊牌排列只在
     * 真正對局中湊出一次加槓才會觸發，加這個子指令讓它可以獨立重播測試。
     */
    private fun previewAddedKanMeld(source: ServerCommandSource, tileArg: String?): Int {
        val player = source.player ?: return COMMAND_FAILURE
        val world = player.serverWorld
        val assetKey = tilePreviewSupport.resolveAssetKey(tileArg)
        val layout = layoutFactory.create(player.blockPos.x, player.blockPos.y, player.blockPos.z, player.horizontalFacing.toMahjongTableFacing())
        val sourceDirection = RelativeDirection.Across
        val claimedOrientation = DecisionTileOrientationDto.ROTATED_RIGHT
        val sidewaysSlot = MahjongTileTableLayout.sidewaysSlotIndex(sourceDirection, DebugPreviewDefaults.MELD_TILE_COUNT)

        val sourcePlacements = (0 until MELD_KAN_TILE_COUNT).map { slot ->
            layout.handPlacement(handSize = DebugPreviewDefaults.RULE_CONFIG.initialHandSize, tileIndex = slot)
        }
        var cursorAlong = MahjongTileTableLayout.stickAreaWidth(stickCount = 0)
        var sidewaysAlongOffset = 0.0
        val basePlacements = (DebugPreviewDefaults.MELD_TILE_COUNT - 1 downTo 0).map { slot ->
            val isSideways = slot == sidewaysSlot
            val halfWidth = if (isSideways) MahjongTileDimensions.TILE_HEIGHT / 2.0 else MahjongTileDimensions.TILE_WIDTH / 2.0
            cursorAlong += halfWidth
            if (isSideways) sidewaysAlongOffset = cursorAlong
            val placement = MahjongTileTableLayout.meldPlacement(
                controllerX = layout.controllerX,
                controllerY = layout.controllerY,
                controllerZ = layout.controllerZ,
                tableFacing = layout.tableFacing,
                seatIndex = DebugVirtualTableLayout.DEBUG_SEAT_INDEX,
                alongOffsetFromCorner = cursorAlong,
                isSidewaysTile = isSideways,
            )
            cursorAlong += halfWidth + MahjongTileDimensions.TILE_SMALL_PADDING
            placement
        }.reversed()
        val addedPlacement = MahjongTileTableLayout.meldPlacement(
            controllerX = layout.controllerX,
            controllerY = layout.controllerY,
            controllerZ = layout.controllerZ,
            tableFacing = layout.tableFacing,
            seatIndex = DebugVirtualTableLayout.DEBUG_SEAT_INDEX,
            alongOffsetFromCorner = sidewaysAlongOffset,
            isSidewaysTile = true,
            depthOffsetFromEdge = MahjongTileTableLayout.ADDED_KAN_DEPTH_OFFSET,
        )
        val finalPlacements = basePlacements + addedPlacement

        val tiles = sourcePlacements.map { placement -> tilePreviewSupport.spawnFreeTile(world, placement, MahjongTilePose.STANDING, assetKey) }
        tiles.forEachIndexed { index, tile ->
            TileAnimationSteps.scheduleMeldClaim(
                tile,
                finalPlacements[index],
                MahjongTilePose.FACE_UP,
                playLandingSound = index == 0,
            )
        }
        val popupTiles = tiles.take(DebugPreviewDefaults.MELD_TILE_COUNT).mapIndexed { slot, tile ->
            MeldActionPopupTile(
                tileId = tile.uuid.toKotlinUuid(),
                orientation = if (slot == sidewaysSlot) claimedOrientation else DecisionTileOrientationDto.UPRIGHT,
                stacked = false,
            )
        } + MeldActionPopupTile(tiles[DebugPreviewDefaults.MELD_TILE_COUNT].uuid.toKotlinUuid(), claimedOrientation, stacked = true)
        val landingTime = world.time + MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS
        tiles.first().showMeldActionPopup(
            popupTiles,
            landingTime,
            landingTime + TileAnimationSteps.ACTION_POPUP_DURATION_TICKS,
        )
        val endGameTime = world.time + MahjongTileTableLayout.DISCARD_FLIGHT_DURATION_TICKS + PREVIEW_VIEWING_BUFFER_TICKS
        entityLifecycle.schedule(world, endGameTime, tiles)
        return COMMAND_SUCCESS
    }

    companion object {
        /** `dice` 子指令 literal。 */
        const val DICE_SUBCOMMAND: String = "dice"

        /** `dice` 的兩顆骰子 literal。 */
        const val TWO_DICE_ARGUMENT: String = "2"

        /** `dice` 的三顆骰子 literal。 */
        const val THREE_DICE_ARGUMENT: String = "3"

        /** `deal` 子指令 literal。 */
        const val DEAL_SUBCOMMAND: String = "deal"

        /** `draw` 子指令 literal。 */
        const val DRAW_SUBCOMMAND: String = "draw"

        /** `discard` 子指令 literal。 */
        const val DISCARD_SUBCOMMAND: String = "discard"

        /** `meld` 子指令 literal。 */
        const val MELD_SUBCOMMAND: String = "meld"

        /** `meld chi` literal。 */
        const val CHI_ARGUMENT: String = "chi"

        /** `meld pon` literal。 */
        const val PON_ARGUMENT: String = "pon"

        /** `meld kan` literal。 */
        const val KAN_ARGUMENT: String = "kan"

        /** `meld added_kan` literal。 */
        const val ADDED_KAN_ARGUMENT: String = "added_kan"

        /** 未指定參數時使用的擲骰數量。 */
        const val DEFAULT_DEBUG_DICE_COUNT: Int = 2

        /** 固定結果點數從三點開始排列所需的 enum offset。 */
        const val DEBUG_DICE_POINT_OFFSET: Int = 2

        /** 虛擬桌 controller 的水平中心偏移。 */
        const val DEBUG_BLOCK_CENTER: Double = 0.5

        /** 虛擬桌桌面相對 controller 的高度。 */
        const val DEBUG_TABLETOP_HEIGHT: Double = 1.0

        /** 動畫播完到實際清除臨時 entity 之間的觀看緩衝，理由同正式對局的觀看緩衝設計。 */
        const val PREVIEW_VIEWING_BUFFER_TICKS: Int = 20

        /** `meld kan` 需要的牌數。 */
        const val MELD_KAN_TILE_COUNT: Int = 4

        /** Brigadier 成功回傳值。 */
        const val COMMAND_SUCCESS: Int = 1

        /** Brigadier 失敗回傳值。 */
        const val COMMAND_FAILURE: Int = 0
    }
}
