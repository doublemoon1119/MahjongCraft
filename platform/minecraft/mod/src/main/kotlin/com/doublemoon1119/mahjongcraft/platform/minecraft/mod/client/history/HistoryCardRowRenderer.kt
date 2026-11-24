package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileFaceViewDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component

/**
 * 畫出牌譜卡片中的文字、牌列與和牌手牌；交易標題與讀取按鈕這類帶按鈕的列由各畫面自己畫。
 *
 * @property session 頭像、牌面外觀與牌名的來源。
 * @property textRenderer 文字繪製器。
 * @property tileFaceRenderer 共用 GUI 牌面 renderer。
 * @property tileAssetRegistry 牌種素材映射來源。
 * @property tileGroups 完整手牌群組的繪製。
 */
internal class HistoryCardRowRenderer(
    private val session: HistoryBrowseSession,
    private val textRenderer: Font,
    private val tileFaceRenderer: MahjongTileFaceRenderer,
    private val tileAssetRegistry: MinecraftTileAssetRegistry,
    private val tileGroups: HistoryTileGroupRenderer,
) {
    /**
     * 畫出一列。
     *
     * @param context 繪製上下文。
     * @param row 要畫的列。
     * @param left 卡片左界。
     * @param y 列的上界。
     * @param hovered 游標是否在卡片上。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 游標指著這一列時的提示；沒有時為 null。
     */
    fun render(
        context: GuiGraphics,
        row: HistoryCardRow,
        left: Int,
        y: Int,
        hovered: Boolean,
        mouseX: Int,
        mouseY: Int,
    ): List<Component>? {
        var tooltip: List<Component>? = null
        when (row) {
            is HistoryCardRow.TextLine -> {
                row.participant?.let { session.participants.render(it, context, left + row.portraitIndent, y - 1, PORTRAIT_SIZE, matchId = matchId()) }
                context.drawString(textRenderer, row.text, left + row.indent, y, row.color)
                if (hovered && mouseY >= y && mouseY < y + row.height) tooltip = row.tooltip
            }
            is HistoryCardRow.Tiles -> row.tiles.forEachIndexed { index, tile ->
                val x = left + row.indent + index * HistoryCardStyle.TILE_STEP
                tile(context, tile, x, y, hovered, mouseX, mouseY)?.let { tooltip = it }
            }
            is HistoryCardRow.LabeledTiles -> {
                val textY = y + (HistoryCardStyle.TILE_HEIGHT - HistoryCardStyle.LINE_HEIGHT) / 2 + 1
                context.drawString(textRenderer, HistoryCardStyle.SEAT_LINE_BULLET, left + row.bulletIndent, textY, HistoryCardStyle.TEXT_COLOR)
                row.participant?.let { session.participants.render(it, context, left + row.portraitIndent, textY - 1, PORTRAIT_SIZE, matchId = matchId()) }
                context.drawString(textRenderer, row.label, left + row.labelIndent, textY, HistoryCardStyle.TEXT_COLOR)
                row.tiles.forEachIndexed { index, tile ->
                    val x = left + row.tileIndent + index * HistoryCardStyle.TILE_STEP
                    tile(context, tile, x, y, hovered, mouseX, mouseY)?.let { tooltip = it }
                }
            }
            is HistoryCardRow.Hand -> {
                val hit = tileGroups.render(context, row.layout, row.catalog, left + HistoryCardStyle.HAND_INDENT, y, mouseX, mouseY, notes = row.notes, appearance = session.tileAppearance())
                if (hovered) hit?.let { tooltip = it.tooltip }
            }
            is HistoryCardRow.Space, is HistoryCardRow.Heading, is HistoryCardRow.StateLink -> Unit
        }
        return tooltip
    }

    /**
     * 畫出一張牌。
     *
     * @param context 繪製上下文。
     * @param tile 牌面。
     * @param x 左界。
     * @param y 上界。
     * @param hovered 游標是否在卡片上。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @return 游標指著這張牌時的提示；沒有時為 null。
     */
    private fun tile(context: GuiGraphics, tile: TileFaceViewDto, x: Int, y: Int, hovered: Boolean, mouseX: Int, mouseY: Int): List<Component>? {
        val asset = tile.toHistoryAssetKey(registry = tileAssetRegistry, registries = session.networkRegistries)
        tileFaceRenderer.renderGui(context, asset, x, y, HistoryCardStyle.TILE_WIDTH, HistoryCardStyle.TILE_HEIGHT, appearance = session.tileAppearance())
        val over = hovered && mouseX >= x && mouseX < x + HistoryCardStyle.TILE_WIDTH && mouseY >= y && mouseY < y + HistoryCardStyle.TILE_HEIGHT
        return if (over) historyTileTooltip(tile.toHistoryNameText(session.tileDisplayNames), catalogIndex = null, takenBy = null, markerLabels = emptyList()) else null
    }

    /**
     * 頭像查詢所用的對局。
     *
     * @return 對局識別碼；沒有摘要時為 null。
     */
    private fun matchId(): String? = session.controller.state.value.summary?.detail?.summary?.matchId

    /** 卡片中的頭像尺寸。 */
    private companion object {
        /** 頭像邊長。 */
        const val PORTRAIT_SIZE = 10
    }
}
