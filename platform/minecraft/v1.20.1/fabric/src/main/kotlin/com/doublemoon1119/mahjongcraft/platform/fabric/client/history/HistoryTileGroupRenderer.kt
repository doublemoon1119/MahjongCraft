package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileFaceViewDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionTileOrientationDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.BuiltInMinecraftTileAppearances
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileSetAppearanceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TILE_BACK_PREVIEW_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.backAppearance
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * 歷史各頁共用牌面繪製，保持方向、材質與標籤一致。
 *
 * @property faces 共用牌面 renderer。
 * @property assets 牌種素材註冊表。
 * @property markerDisplays 牌河公開標記的提示文字來源。
 * @property displayNames 提示中牌名的查詢來源。
 */
internal class HistoryTileGroupRenderer(
    private val faces: MahjongTileFaceRenderer,
    private val assets: MinecraftTileAssetRegistry,
    private val markerDisplays: HistoryDiscardMarkerDisplayRegistry,
    private val displayNames: TileDisplayNameRegistry,
) {
    /**
     * 繪製已測量群組並取得指向牌張的索引提示。
     *
     * @param context 繪製上下文。
     * @param layout 共用幾何。
     * @param catalog 已驗證牌目錄。
     * @param x 群組左界。
     * @param y 群組上界。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param taken 已被取走的牌索引對應取走它的說明；保留原槽位但不再畫牌面。
     * @param markers 牌張公開標記，僅用於提示與強調，不重新判斷規則；有標記的牌在左上角加折角。
     * @param notes 牌張的額外說明，例如副露被鳴的牌來自哪位玩家；有說明的牌依說明種類在左上角加折角並列在提示中。
     * @param linkedTile 要一起強調的牌索引；只有被鳴走的空位與副露中被鳴的牌會蓋上強調色層。
     * @param appearance 該場公開規則指定的牌組外觀。
     * @param showCatalogIndex 是否顯示局內牌索引；獨立展示牌面必須設為 false。
     * @return 游標指向的牌與其提示，或 null。
     */
    fun render(
        context: DrawContext,
        layout: HistoryTileGroupLayout,
        catalog: List<TileFaceViewDto>,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int,
        taken: Map<Int, Text> = emptyMap(),
        markers: Map<Int, Set<String>> = emptyMap(),
        notes: Map<Int, List<HistoryTileNote>> = emptyMap(),
        linkedTile: Int? = null,
        appearance: MinecraftTileSetAppearanceDefinition = BuiltInMinecraftTileAppearances.DEFAULT,
        showCatalogIndex: Boolean = true,
    ): HistoryTileHit? {
        var hit: HistoryTileHit? = null
        layout.placements.forEach { placement ->
            val tile = catalog.getOrNull(placement.tile) ?: return@forEach
            val faceAsset = tile.toHistoryAssetKey(assets)
            val asset = if (placement.faceDown) TILE_BACK_PREVIEW_ASSET_KEY else faceAsset
            // 蓋著的牌依牌面樣式指定的牌身畫牌背；看不到牌面時就是規則外觀。
            val tileAppearance = if (placement.faceDown) assets.backAppearance(faceAsset, appearance) else appearance
            val rotated = placement.orientation != DecisionTileOrientationDto.UPRIGHT
            val nominalWidth = if (rotated) placement.height else placement.width
            val nominalHeight = if (rotated) placement.width else placement.height
            val left = (x + placement.x).toInt()
            val top = (y + placement.y).toInt()
            val right = (x + placement.x + placement.width).toInt()
            val bottom = (y + placement.y + placement.height).toInt()
            val isTaken = placement.tile in taken
            if (isTaken) {
                context.drawOutline(left, top, right, bottom, TAKEN_COLOR)
            } else {
                context.matrices.push()
                context.matrices.translate((x + placement.x).toDouble(), (y + placement.y).toDouble(), 0.0)
                context.matrices.scale(nominalWidth / BASE_WIDTH, nominalHeight / BASE_HEIGHT, 1f)
                faces.renderGui(context, asset, 0, 0, BASE_WIDTH, BASE_HEIGHT, placement.orientation, tileAppearance)
                context.matrices.pop()
            }
            // 色層只畫在這張牌自己的範圍內，不佔用牌與牌之間的間隔；折角畫在色層之上保持清楚。
            if (isLinkedClaimHighlight(placement.tile, linkedTile, isTaken, notes[placement.tile].orEmpty())) {
                context.fill(left, top, right, bottom, LINK_TINT_COLOR)
            }
            // 被鳴走的空位與副露中被鳴的牌是同一張牌的兩端，使用同色折角；空位跟著外框以半透明呈現。
            val foldColor = when {
                isTaken -> TAKEN_FOLD_COLOR
                else -> notes[placement.tile]?.firstOrNull()?.kind?.color ?: MARKER_COLOR.takeIf { !markers[placement.tile].isNullOrEmpty() }
            }
            if (foldColor != null) {
                val size = (minOf(placement.width, placement.height) * FOLD_RATIO).toInt().coerceAtLeast(MIN_FOLD_SIZE)
                repeat(size) { row -> context.fill(left, top + row, left + size - row, top + row + 1, foldColor) }
            }
            if (mouseX >= x + placement.x && mouseX < x + placement.x + placement.width && mouseY >= y + placement.y && mouseY < y + placement.y + placement.height) {
                hit = HistoryTileHit(
                    tile = placement.tile,
                    tooltip = historyTileTooltip(
                        name = tile.toHistoryNameText(displayNames),
                        catalogIndex = placement.tile.takeIf { showCatalogIndex },
                        takenBy = taken[placement.tile],
                        markerLabels = markers[placement.tile].orEmpty().sorted().map { marker ->
                            markerDisplays.find(marker)?.let { Text.translatable(it.labelTranslationKey) } ?: Text.literal(marker)
                        } + notes[placement.tile].orEmpty().map { it.text },
                    ),
                )
            }
        }
        return hit
    }

    /**
     * 畫出 1 像素寬的矩形外框。
     *
     * @param left 左界。
     * @param top 上界。
     * @param right 右界（不含）。
     * @param bottom 下界（不含）。
     * @param color ARGB 顏色。
     */
    private fun DrawContext.drawOutline(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        color: Int,
    ) {
        fill(left, top, right, top + 1, color)
        fill(left, bottom - 1, right, bottom, color)
        fill(left, top, left + 1, bottom, color)
        fill(right - 1, top, right, bottom, color)
    }

    /** 共用材質識別、牌面尺寸與配色。 */
    private companion object {
        /** 被取走牌張的空槽邊界。 */
        const val TAKEN_COLOR = 0x80666666.toInt()

        /** 被鳴走空位的折角：與副露中被鳴的牌同色，透明度與空位外框一致。 */
        const val TAKEN_FOLD_COLOR = 0x80FFB13B.toInt()

        /** 游標指向被鳴的牌或被鳴走的空位時，兩端一起蓋上的半透明色層；色調與被鳴的牌的折角相同。 */
        const val LINK_TINT_COLOR = 0x66FFB13B.toInt()

        /** 牌河公開標記的折角顏色。 */
        const val MARKER_COLOR = 0xffe07bff.toInt()

        /** 折角邊長相對於牌較短邊的比例。 */
        const val FOLD_RATIO = 0.4f

        /** 折角的最小邊長。 */
        const val MIN_FOLD_SIZE = 3

        /** 直立基準牌寬。 */
        const val BASE_WIDTH = 18

        /** 直立基準牌高。 */
        const val BASE_HEIGHT = 24
    }
}

/**
 * 牌譜中一張牌的滑鼠提示：牌名在前，其次是被誰鳴走、牌河公開標記與副露說明；設定開啟時最前面加上局內牌索引。
 *
 * @param name 本地化牌名。
 * @param catalogIndex 局內牌索引；不顯示時為 null。
 * @param takenBy 這張牌被鳴走的說明；沒有被鳴走時為 null。
 * @param markerLabels 牌河公開標記與副露說明的文字，依顯示順序排列。
 * @return 依序顯示的提示文字。
 */
internal fun historyTileTooltip(
    name: Text,
    catalogIndex: Int?,
    takenBy: Text?,
    markerLabels: List<Text>,
): List<Text> = buildList {
    catalogIndex?.let { add(Text.translatable(MinecraftHistoryScreenKeys.STATE_TILE_INDEX, it).formatted(Formatting.GRAY)) }
    add(name)
    takenBy?.let { add(it.copy().formatted(Formatting.GRAY)) }
    markerLabels.forEach { add(it.copy().formatted(Formatting.GRAY)) }
}

/**
 * 這張牌是否要蓋上鳴牌連動的強調色層：游標指向的牌是被鳴走的空位或副露中被鳴的牌時，同一張牌的兩端一起強調。
 *
 * @param tile 這個位置的局內牌索引。
 * @param linkedTile 游標指向的牌索引；沒有時為 null。
 * @param isTaken 這個位置是否為被鳴走的空位。
 * @param notes 這個位置的牌張說明。
 * @return 需要強調時為 true。
 */
internal fun isLinkedClaimHighlight(
    tile: Int,
    linkedTile: Int?,
    isTaken: Boolean,
    notes: List<HistoryTileNote>,
): Boolean = tile == linkedTile && (isTaken || notes.any { it.kind == HistoryTileNoteKind.CLAIMED_FROM })

/**
 * 游標指向的牌。
 *
 * @property tile 局內牌索引；獨立展示牌面時為列內位置。
 * @property tooltip 這張牌的滑鼠提示。
 */
internal data class HistoryTileHit(val tile: Int, val tooltip: List<Text>)

/**
 * 牌組中一張牌的額外說明，依種類在牌的左上角畫上不同顏色的折角。
 *
 * @property kind 說明種類。
 * @property text 滑鼠提示中的說明文字。
 */
internal data class HistoryTileNote(val kind: HistoryTileNoteKind, val text: Text)

/**
 * 牌張說明的種類與折角顏色。
 *
 * @property color 折角的 ARGB 顏色。
 */
internal enum class HistoryTileNoteKind(val color: Int) {
    /** 副露中從其他玩家打出的牌鳴來的那張。 */
    CLAIMED_FROM(0xFFFFB13B.toInt()),

    /** 加槓時從自己手牌補上的那張。 */
    ADDED_KAN(0xFF7FD6FF.toInt()),
}
