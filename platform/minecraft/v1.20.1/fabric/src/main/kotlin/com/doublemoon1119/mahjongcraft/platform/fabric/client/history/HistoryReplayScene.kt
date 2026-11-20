package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene.GuideScene
import com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene.GuideSceneBox
import com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene.GuideSceneTile
import com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene.GuideSceneView
import com.doublemoon1119.mahjongcraft.platform.fabric.client.guide.scene.GuideScenes
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTilePose
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryReplayArrangement
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.ReplayTableNotes
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TableTileLayoutProfile

/** 把牌譜保存的一份桌況擺成可以轉動的牌桌場景，牌的位置與對局中相同。 */
internal object HistoryReplayScene {
    /**
     * 建立場景。
     *
     * @param state 牌譜保存的桌況。
     * @param notes 牌譜保存的擺放附註。
     * @param module 這場對局的規則模組。
     * @param tileLayout 規則的桌面牌位配置。
     * @param sidewaysMarker 牌河標記是否代表橫放。
     * @param openHands 是否把每一家的手牌面朝上攤開。
     * @param assetKey 局內牌索引對應的牌面素材；看不到牌面時為 null。
     * @return 牌桌場景。
     */
    fun build(
        state: HistoryRoundStateDto,
        notes: ReplayTableNotes,
        module: MahjongRuleModule<*>,
        tileLayout: TableTileLayoutProfile,
        sidewaysMarker: (String) -> Boolean,
        openHands: Boolean,
        assetKey: (Int) -> String?,
    ): GuideScene {
        val arranged = HistoryReplayArrangement.arrange(
            state = state,
            notes = notes,
            module = module,
            tileLayout = tileLayout,
            openHands = openHands,
            sidewaysMarker = sidewaysMarker,
        )
        val tiles = arranged.map { (index, tile) ->
            GuideSceneTile(
                assetKey = assetKey(index),
                x = tile.placement.x,
                y = tile.placement.y,
                z = tile.placement.z,
                yaw = tile.placement.yaw,
                pose = when (tile.pose) {
                    TilePose.UPRIGHT -> MahjongTilePose.STANDING
                    TilePose.FACE_UP -> MahjongTilePose.FACE_UP
                    TilePose.FACE_DOWN -> MahjongTilePose.FACE_DOWN
                },
            )
        }
        val seats = MahjongSeatingTableLayout.seatPlacements(
            layout = MahjongTableSeatLayout.forPlayerCount(state.players.size),
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
