package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiHistoryDiscardMarkerIds
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionTileOrientationDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * 歷史各頁共用牌面繪製，保持方向、材質與標籤一致。
 * @property faces 共用牌面 renderer。
 * @property assets 牌種素材註冊表。
 */
internal class HistoryTileGroupRenderer(
    private val faces: MahjongTileFaceRenderer,
    private val assets: MinecraftTileAssetRegistry,
) {
    /**
     * 繪製已測量群組並取得指向牌張的索引提示。
     * @param context 繪製上下文。
     * @param layout 共用幾何。
     * @param catalog 已驗證牌目錄。
     * @param x 群組左界。
     * @param y 群組上界。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param taken 已被取走的牌索引；保留原槽位但不再畫牌面。
     * @param markers 牌張公開標記，僅用於提示與強調，不重新判斷規則。
     * @return 指向牌面時的提示，或 null。
     */
    fun render(context: DrawContext, layout: HistoryTileGroupLayout, catalog: List<TileDto>, x: Int, y: Int, mouseX: Int, mouseY: Int, taken: Set<Int> = emptySet(), markers: Map<Int, Set<String>> = emptyMap()): List<Text>? {
        var tooltip: List<Text>? = null
        layout.placements.forEach { placement ->
            val tile = catalog.getOrNull(placement.tile) ?: return@forEach
            val asset = if (placement.faceDown) TILE_BACK_ASSET_KEY else tile.toDomain().toAssetKey(assets)
            val rotated = placement.orientation != DecisionTileOrientationDto.UPRIGHT
            val nominalWidth = if (rotated) placement.height else placement.width
            val nominalHeight = if (rotated) placement.width else placement.height
            if (placement.tile in taken) {
                val left = (x + placement.x).toInt()
                val top = (y + placement.y).toInt()
                context.fill(left, top, (left + placement.width).toInt(), top + 1, TAKEN_COLOR)
                context.fill(left, (top + placement.height - 1).toInt(), (left + placement.width).toInt(), (top + placement.height).toInt(), TAKEN_COLOR)
                context.fill(left, top, left + 1, (top + placement.height).toInt(), TAKEN_COLOR)
                context.fill((left + placement.width - 1).toInt(), top, (left + placement.width).toInt(), (top + placement.height).toInt(), TAKEN_COLOR)
            } else {
                context.matrices.push()
                context.matrices.translate((x + placement.x).toDouble(), (y + placement.y).toDouble(), 0.0)
                context.matrices.scale(nominalWidth / BASE_WIDTH, nominalHeight / BASE_HEIGHT, 1f)
                faces.renderGui(context, asset, 0, 0, BASE_WIDTH, BASE_HEIGHT, placement.orientation)
                context.matrices.pop()
            }
            if (!markers[placement.tile].isNullOrEmpty()) {
                context.fill((x + placement.x).toInt(), (y + placement.y + placement.height).toInt(), (x + placement.x + placement.width).toInt(), (y + placement.y + placement.height + 1).toInt(), MARKER_COLOR)
            }
            if (mouseX >= x + placement.x && mouseX < x + placement.x + placement.width && mouseY >= y + placement.y && mouseY < y + placement.y + placement.height) {
                tooltip = buildList {
                    add(Text.translatable(MinecraftHistoryScreenKeys.STATE_TILE_INDEX, placement.tile).formatted(Formatting.GRAY))
                    if (placement.tile in taken) add(Text.translatable(MinecraftHistoryScreenKeys.STATE_TAKEN)) else add(Text.literal(asset))
                    markers[placement.tile].orEmpty().sorted().forEach { marker ->
                        val label = if (marker == RiichiHistoryDiscardMarkerIds.RIICHI_DECLARED) {
                            Text.translatable(MinecraftMessageKeys.PLAYER_INDICATOR_RIICHI)
                        } else {
                            Text.literal(marker)
                        }
                        add(label.formatted(Formatting.GRAY))
                    }
                }
            }
        }
        return tooltip
    }

    /** 共用材質識別、牌面尺寸與配色。 */
    private companion object {
        /** 既有牌背素材識別碼。 */
        const val TILE_BACK_ASSET_KEY = "back"

        /** 被取走牌張的空槽邊界。 */
        val TAKEN_COLOR = 0x80666666.toInt()

        /** 公開標記的底線。 */
        val MARKER_COLOR = 0xff8ed5df.toInt()

        /** 直立基準牌寬。 */
        const val BASE_WIDTH = 18

        /** 直立基準牌高。 */
        const val BASE_HEIGHT = 24
    }
}
