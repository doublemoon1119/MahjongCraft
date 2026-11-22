package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPlacement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallPosition
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideArrangement
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallSideReference
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.MahjongStoolBlock
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.MahjongTableBlock
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.MahjongTableStructure
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongStoolSeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry.ModBlocks
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.BuiltInRoundInfoLineKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoArgument
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLine
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileWallSideLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfile
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import kotlin.math.floor
import kotlin.uuid.Uuid

/** 教學畫面使用的固定示意場景。 */
internal object GuideScenes {
    /**
     * 準備桌椅的場景：朝北的木製麻將桌、四邊各一張木製麻將凳，三位麻將機器人坐在凳子上，鏡頭這一側的凳子留空。
     * 桌椅放在兩種淺色相間的地板上，玩家可以用地板的格子估出桌子的大小。
     *
     * 凳子與角色的位置沿用開局入座的標準座位，因此與遊戲中坐下的位置一致。
     *
     * @return 準備桌椅的場景。
     */
    fun tableSetup(): GuideScene {
        val tableBlocks = tableBlocks()
        val stool = ModBlocks.woodenMahjongStool as MahjongStoolBlock
        val feetY = stool.design.seatHeight - MahjongStoolSeatGeometry.SEATED_BOTTOM_HEIGHT
        val seats = seats()
        val viewerSeat = seats[viewerSeatIndex(seats)]
        val stools = stools(seats)
        val figures = seats.filter { it !== viewerSeat }.mapIndexed { index, seat ->
            GuideSceneFigure(
                faceAssetKey = FIGURE_FACES[index % FIGURE_FACES.size],
                x = seat.x,
                y = feetY,
                z = seat.z,
                yaw = seat.yaw,
                seated = true,
            )
        }
        return GuideScene(
            blocks = floor() + tableBlocks + stools,
            figures = figures,
            tiles = emptyList(),
            view = GuideSceneView(yawDegrees = TABLE_SETUP_YAW, pitchDegrees = TABLE_SETUP_PITCH),
            rotatable = true,
        )
    }

    /**
     * 從座位往前看自己手牌的場景：桌上立著一手 13 張牌，畫法與位置和對局中的手牌相同。
     *
     * @param faces 由左到右（以坐在座位上的玩家來看）的牌面資源鍵。
     * @param glows 依同一順序索引、要發光的牌與發光種類。
     * @return 手牌場景。
     */
    fun hand(faces: List<String>, glows: Map<Int, GuideSceneTileGlow> = emptyMap()): GuideScene {
        val seats = seats()
        val seatIndex = viewerSeatIndex(seats)
        val tiles = faces.mapIndexed { displayIndex, face ->
            // 手牌索引 0 在玩家右手邊，畫面由左到右的順序要反過來。
            val placement = MahjongTileTableLayout.handPlacement(
                controllerX = 0,
                controllerY = 0,
                controllerZ = 0,
                tableFacing = MahjongTableFacing.NORTH,
                seatLayout = MahjongTableSeatLayout.FOUR_PLAYER,
                seatIndex = seatIndex,
                handSize = faces.size,
                tileIndex = faces.lastIndex - displayIndex,
            )
            GuideSceneTile(
                assetKey = face,
                x = placement.x,
                y = placement.y,
                z = placement.z,
                yaw = placement.yaw,
                glow = glows[displayIndex],
            )
        }
        val margin = MahjongTileDimensions.TILE_WIDTH
        return GuideScene(
            blocks = tableBlocks(),
            figures = emptyList(),
            tiles = tiles,
            view = GuideSceneView(yawDegrees = 0f, pitchDegrees = HAND_PITCH),
            focus = GuideSceneBox(
                minX = tiles.minOf { it.x } - margin,
                minY = tiles.minOf { it.y },
                minZ = tiles.minOf { it.z } - HAND_FOCUS_DEPTH,
                maxX = tiles.maxOf { it.x } + margin,
                maxY = tiles.maxOf { it.y } + MahjongTileDimensions.TILE_HEIGHT,
                maxZ = tiles.maxOf { it.z } + margin,
            ),
        )
    }

    /**
     * 認識牌桌的場景：與準備桌椅相同的斜上方視角，可以拖曳轉動。桌上擺著牌牆、四家手牌、牌河與一組副露，
     * 桌子中央上方浮著局況，四個座位後上方浮著玩家資訊；各區塊都標示了滑鼠指著時要說明的名稱。
     *
     * 牌的位置沿用遊戲的牌桌布局與預設的標準桌面配置，只擺各規則共通的區塊。
     *
     * @param handFaces 由左到右（以坐在座位上的玩家來看）的自己手牌。
     * @param viewer 坐在鏡頭這一側座位的玩家，玩家資訊顯示他的名稱與頭像。
     * @return 認識牌桌的場景。
     */
    fun tableOverview(handFaces: List<String>, viewer: GuideViewer): GuideScene {
        val seats = seats()
        val viewerSeat = viewerSeatIndex(seats)
        val otherSeats = seats.indices.filter { it != viewerSeat }
        val meldSeat = otherSeats.first()
        val stool = ModBlocks.woodenMahjongStool as MahjongStoolBlock
        val feetY = stool.design.seatHeight - MahjongStoolSeatGeometry.SEATED_BOTTOM_HEIGHT
        val figures = otherSeats.mapIndexed { order, seatIndex ->
            val seat = seats[seatIndex]
            GuideSceneFigure(
                faceAssetKey = FIGURE_FACES[order % FIGURE_FACES.size],
                x = seat.x,
                y = feetY,
                z = seat.z,
                yaw = seat.yaw,
                seated = true,
                region = GuideSceneRegion.AI_PLAYER,
            )
        }
        val hands = seats.indices.flatMap { seatIndex ->
            val faces: List<String?> = when (seatIndex) {
                viewerSeat -> handFaces
                meldSeat -> List(handFaces.size - MELD_FACES.size) { null }
                else -> List(handFaces.size) { null }
            }
            val region = if (seatIndex == viewerSeat) GuideSceneRegion.OWN_HAND else GuideSceneRegion.OTHER_HAND
            faces.mapIndexed { displayIndex, face ->
                val placement = MahjongTileTableLayout.handPlacement(
                    controllerX = 0,
                    controllerY = 0,
                    controllerZ = 0,
                    tableFacing = MahjongTableFacing.NORTH,
                    seatLayout = MahjongTableSeatLayout.FOUR_PLAYER,
                    seatIndex = seatIndex,
                    handSize = faces.size,
                    tileIndex = faces.lastIndex - displayIndex,
                )
                GuideSceneTile(assetKey = face, x = placement.x, y = placement.y, z = placement.z, yaw = placement.yaw, region = region)
            }
        }
        val rivers = seats.indices.flatMap { seatIndex ->
            RIVER_FACES[seatIndex].mapIndexed { discardIndex, face ->
                val placement = MahjongTileTableLayout.discardPlacement(
                    controllerX = 0,
                    controllerY = 0,
                    controllerZ = 0,
                    tableFacing = MahjongTableFacing.NORTH,
                    seatLayout = MahjongTableSeatLayout.FOUR_PLAYER,
                    seatIndex = seatIndex,
                    discardIndex = discardIndex,
                    isSidewaysMarked = false,
                    sidewaysMarkedDiscardIndex = null,
                    wallRemaining = true,
                )
                GuideSceneTile(
                    assetKey = face,
                    x = placement.x,
                    y = placement.y,
                    z = placement.z,
                    yaw = placement.yaw,
                    pose = MahjongTilePose.FACE_UP,
                    region = GuideSceneRegion.RIVER,
                )
            }
        }
        val meld = MELD_FACES.mapIndexed { index, face ->
            val step = MahjongTileDimensions.TILE_WIDTH + MahjongTileDimensions.TILE_SMALL_PADDING
            val placement = MahjongTileTableLayout.meldPlacement(
                controllerX = 0,
                controllerY = 0,
                controllerZ = 0,
                tableFacing = MahjongTableFacing.NORTH,
                seatLayout = MahjongTableSeatLayout.FOUR_PLAYER,
                seatIndex = meldSeat,
                alongOffsetFromCorner = MahjongTileDimensions.TILE_WIDTH / 2.0 + index * step,
                isSidewaysTile = false,
            )
            GuideSceneTile(
                assetKey = face,
                x = placement.x,
                y = placement.y,
                z = placement.z,
                yaw = placement.yaw,
                pose = MahjongTilePose.FACE_UP,
                region = GuideSceneRegion.MELD,
            )
        }
        val roundInfoPlacement = MahjongTileTableLayout.roundInfoDisplayPlacement(0, 0, 0)
        val players = seats.indices.map { seatIndex ->
            MahjongPlayerInfoEntry(
                playerId = if (seatIndex == viewerSeat) viewer.id else Uuid.fromLongs(0L, seatIndex.toLong() + 1L),
                playerName = if (seatIndex == viewerSeat) viewer.name else "AI ${otherSeats.indexOf(seatIndex) + 1}",
                isAi = seatIndex != viewerSeat,
                seatIndex = seatIndex,
                seatWind = Wind.entries[seatIndex],
                score = STARTING_SCORE,
                indicators = emptyList(),
            )
        }
        return GuideScene(
            blocks = floor() + tableBlocks() + stools(seats),
            figures = figures,
            tiles = wall() + hands + rivers + meld,
            view = GuideSceneView(yawDegrees = TABLE_SETUP_YAW, pitchDegrees = TABLE_OVERVIEW_PITCH),
            rotatable = true,
            roundInfo = GuideSceneRoundInfo(
                lines = listOf(
                    RoundInfoLine(BuiltInRoundInfoLineKeys.ROUND_NUMBER, listOf(RoundInfoArgument.Number(EXAMPLE_ROUND_NUMBER))),
                    RoundInfoLine(BuiltInRoundInfoLineKeys.WALL_REMAINING, listOf(RoundInfoArgument.Number(EXAMPLE_WALL_REMAINING))),
                ),
                x = roundInfoPlacement.x,
                y = roundInfoPlacement.y,
                z = roundInfoPlacement.z,
            ),
            playerInfo = GuideScenePlayerInfo(
                players = players,
                dealerPlayerId = players[viewerSeat].playerId,
                shownSeats = seats.indices.toSet(),
                x = TABLE_CENTER,
                y = 0.0,
                z = TABLE_CENTER,
            ),
        )
    }

    /**
     * 四面各 [WALL_STACKS_PER_SIDE] 墩、上下兩層的牌牆，牌面朝下；第一面開頭幾墩已被摸走。
     *
     * @return 牌牆的牌。
     */
    private fun wall(): List<GuideSceneTile> {
        val sideLayout = MahjongTileWallSideLayout.resolve(
            arrangement = TileWallSideArrangement(reference = TileWallSideReference.TABLE, quarterTurns = (0 until WALL_SIDES).toList()),
            dealerSide = MahjongTableSide.SOUTH,
        )
        return (0 until WALL_SIDES).flatMap { side ->
            (0 until WALL_STACKS_PER_SIDE).flatMap { stack ->
                (0 until WALL_LAYERS).mapNotNull { layer ->
                    if (side == 0 && stack < DRAWN_STACKS) return@mapNotNull null
                    val placement = MahjongTileTableLayout.wallPlacement(
                        controllerX = 0,
                        controllerY = 0,
                        controllerZ = 0,
                        tableFacing = MahjongTableFacing.NORTH,
                        sideLayout = sideLayout,
                        tileLayout = TableTileLayoutProfile.STANDARD,
                        stacksPerSide = WALL_STACKS_PER_SIDE,
                        placement = TileWallPlacement(TileWallPosition(side = side, stack = stack, layer = layer)),
                    )
                    GuideSceneTile(
                        assetKey = null,
                        x = placement.x,
                        y = placement.y,
                        z = placement.z,
                        yaw = placement.yaw,
                        pose = MahjongTilePose.FACE_DOWN,
                        region = GuideSceneRegion.WALL,
                    )
                }
            }
        }
    }

    /**
     * 四邊座位上的木製麻將凳，正面朝向桌子。
     *
     * @param seats 標準座位。
     * @return 凳子方塊。
     */
    internal fun stools(seats: List<SeatGeometry>): List<GuideSceneBlock> {
        val stool = ModBlocks.woodenMahjongStool as MahjongStoolBlock
        return seats.map { seat ->
            val towardTable = Direction.getNearest(TABLE_CENTER - seat.x, 0.0, TABLE_CENTER - seat.z)
            GuideSceneBlock(
                state = stool.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, towardTable),
                position = BlockPos(floor(seat.x).toInt(), 0, floor(seat.z).toInt()),
            )
        }
    }

    /** 朝北木製麻將桌的 18 個方塊，controller 在原點。 */
    internal fun tableBlocks(): List<GuideSceneBlock> {
        val table = ModBlocks.woodenMahjongTable
        return MahjongTableStructure.placements(BlockPos.ZERO, Direction.NORTH).map { (part, position) ->
            GuideSceneBlock(
                state = table.defaultBlockState().setValue(MahjongTableBlock.PART, part).setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH),
                position = position,
            )
        }
    }

    /** 桌椅下方的地板：涵蓋桌子與四張凳子，兩種淺色方塊交錯，一格一個方塊。 */
    internal fun floor(): List<GuideSceneBlock> = (-FLOOR_RADIUS..FLOOR_RADIUS).flatMap { x ->
        (-FLOOR_RADIUS..FLOOR_RADIUS).map { z ->
            val block = if ((x + z) % 2 == 0) Blocks.WHITE_CONCRETE else Blocks.SNOW_BLOCK
            GuideSceneBlock(state = block.defaultBlockState(), position = BlockPos(x, -1, z))
        }
    }

    /** 四人桌的標準座位，依座位 index 排列。 */
    private fun seats(): List<SeatGeometry> = MahjongSeatingTableLayout.seatPlacements(
        layout = MahjongTableSeatLayout.FOUR_PLAYER,
        controllerX = 0,
        controllerY = 0,
        controllerZ = 0,
        tableFacing = MahjongTableFacing.NORTH,
    )

    /**
     * 鏡頭這一側（南側）的座位 index；場景以這個座位當作「你的座位」。
     *
     * @param seats 依座位 index 排列的標準座位。
     * @return 南側座位的 index。
     */
    private fun viewerSeatIndex(seats: List<SeatGeometry>): Int = seats.indices.maxBy { seats[it].z }

    /** 手牌場景的俯視角度，接近坐在座位上往前看的角度。 */
    private const val HAND_PITCH: Float = 22f

    /** 手牌場景往桌子中央多帶入的深度，讓牌後面看得到一段桌面。 */
    private const val HAND_FOCUS_DEPTH: Double = 0.2

    /** 認識牌桌場景的俯視角度，比準備桌椅高一些，看得清楚桌面上的牌。 */
    private const val TABLE_OVERVIEW_PITCH: Float = 45f

    /** 牌牆的面數。 */
    private const val WALL_SIDES: Int = 4

    /** 牌牆每面的墩數。 */
    private const val WALL_STACKS_PER_SIDE: Int = 17

    /** 牌牆每墩的層數。 */
    private const val WALL_LAYERS: Int = 2

    /** 示意已被摸走的墩數。 */
    private const val DRAWN_STACKS: Int = 6

    /** 示意副露：碰出的三張發。 */
    private val MELD_FACES: List<String> = listOf("green_dragon", "green_dragon", "green_dragon")

    /** 示意的起始分數。 */
    private const val STARTING_SCORE: Int = 25000

    /** 示意的牌牆剩餘張數。 */
    private const val EXAMPLE_WALL_REMAINING: Int = 70

    /** 示意的局數。 */
    private const val EXAMPLE_ROUND_NUMBER: Int = 1

    /** 各座位牌河中的牌，依座位 index 排列。 */
    private val RIVER_FACES: List<List<String>> = listOf(
        listOf("north", "m9", "p1", "s8", "west", "green_dragon"),
        listOf("s1", "south", "m1", "p9", "white_dragon"),
        listOf("p9", "east", "m8", "s2", "north", "m1"),
        listOf("m9", "s9", "west", "p2", "south"),
    )

    /** 地板從桌子中心往外延伸的格數，涵蓋四邊的凳子。 */
    private const val FLOOR_RADIUS: Int = 2

    /** 桌子中心的水平座標；controller 位於原點方塊。 */
    private const val TABLE_CENTER: Double = 0.5

    /** 準備桌椅場景的水平旋轉，讓鏡頭這一側的凳子落在畫面下方。 */
    private const val TABLE_SETUP_YAW: Float = 20f

    /** 準備桌椅場景的俯視角度。 */
    private const val TABLE_SETUP_PITCH: Float = 32f

    /** 三位機器人的牌面，固定使用標準牌。 */
    private val FIGURE_FACES: List<String> = listOf("east", "red_dragon", "p5")
}
