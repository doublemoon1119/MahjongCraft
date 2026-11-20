package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene.GuideScene
import com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene.GuideSceneBox
import com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene.GuideSceneTile
import com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene.GuideSceneView
import com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene.GuideScenes
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryReplayArrangement
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ArrangedTile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileAnimationSample
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLean

/** 把牌譜保存的一份桌況擺成可以轉動的牌桌場景，牌的位置與對局中相同。 */
internal object HistoryReplayScene {
    /**
     * 建立場景。
     *
     * @param playerCount 這一局的人數，決定凳子的位置。
     * @param arranged 依局內牌索引排列的牌桌擺放，例如 [HistoryReplayArrangement.arrange] 的結果。
     * @param samples 正在播放動畫的牌在這一刻的樣子，取代擺放中的位置；隱形的牌不畫。
     * @param assetKey 局內牌索引對應的牌面素材；看不到牌面時為 null。
     * @return 牌桌場景。
     */
    fun build(
        playerCount: Int,
        arranged: Map<Int, ArrangedTile>,
        samples: Map<Int, TileAnimationSample>,
        assetKey: (Int) -> String?,
    ): GuideScene {
        val tiles = arranged.mapNotNull { (index, tile) ->
            val sample = samples[index]
            if (sample?.invisible == true) return@mapNotNull null
            val placement = sample?.placement ?: tile.placement
            GuideSceneTile(
                assetKey = assetKey(index),
                x = placement.x,
                y = placement.y,
                z = placement.z,
                yaw = placement.yaw,
                pose = when (tile.pose) {
                    TilePose.UPRIGHT -> MahjongTilePose.STANDING
                    TilePose.FACE_UP -> MahjongTilePose.FACE_UP
                    TilePose.FACE_DOWN -> MahjongTilePose.FACE_DOWN
                },
                poseDegrees = sample?.poseDegrees,
                lean = sample?.lean ?: TileLean.NONE,
            )
        }
        val seats = MahjongSeatingTableLayout.seatPlacements(
            layout = MahjongTableSeatLayout.forPlayerCount(playerCount),
            controllerX = 0,
            controllerY = 0,
            controllerZ = 0,
            tableFacing = MahjongTableFacing.NORTH,
        )
        val furniture = GuideScenes.tableBlocks() + GuideScenes.stools(seats)
        return GuideScene(
            blocks = GuideScenes.floor() + furniture,
            figures = emptyList(),
            tiles = tiles,
            view = GuideSceneView(yawDegrees = VIEW_YAW, pitchDegrees = VIEW_PITCH),
            frame = GuideSceneBox(
                minX = furniture.minOf { it.position.x }.toDouble(),
                minY = furniture.minOf { it.position.y }.toDouble(),
                minZ = furniture.minOf { it.position.z }.toDouble(),
                maxX = furniture.maxOf { it.position.x } + 1.0,
                maxY = furniture.maxOf { it.position.y } + 1.0 + TILE_HEADROOM,
                maxZ = furniture.maxOf { it.position.z } + 1.0,
            ),
            rotatable = true,
        )
    }

    /** 框住桌椅時在桌面上方多留的高度，容得下立著的牌。 */
    private const val TILE_HEADROOM: Double = 0.25

    /** 場景一開始的水平角度。 */
    private const val VIEW_YAW: Float = 20f

    /** 場景的俯視角度，看得清楚桌面上的牌。 */
    private const val VIEW_PITCH: Float = 50f
}
