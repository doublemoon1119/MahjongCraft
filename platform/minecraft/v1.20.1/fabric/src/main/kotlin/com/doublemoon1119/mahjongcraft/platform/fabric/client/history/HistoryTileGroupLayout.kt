package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryReplayMeldDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionTileOrientationDto

/**
 * 單張歷史牌面在一組牌面中的幾何位置與顯示方向。
 *
 * @property tile 牌面目錄中的牌索引。
 * @property x 牌面左界的水平位置。
 * @property y 牌面上界的垂直位置。
 * @property width 牌面在目前縮放下的寬度。
 * @property height 牌面在目前縮放下的高度。
 * @property orientation 牌面是否依副露來源旋轉。
 * @property faceDown 是否顯示牌背。
 */
internal data class HistoryTilePlacement(
    val tile: Int,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val orientation: DecisionTileOrientationDto = DecisionTileOrientationDto.UPRIGHT,
    val faceDown: Boolean = false,
)

/**
 * 一組歷史牌面的完整尺寸與各牌位置。
 *
 * @property placements 依顯示順序排列的牌面位置。
 * @property width 這組牌面所需的總寬度。
 * @property height 這組牌面所需的總高度。
 */
internal data class HistoryTileGroupLayout(
    val placements: List<HistoryTilePlacement>,
    val width: Float,
    val height: Float,
)

/** 計算歷史手牌與副露的純幾何配置，不負責載入牌面或繪製。 */
internal object HistoryTileGroupLayoutCalculator {
    /**
     * 計算可換行的手牌配置，和牌張與立牌之間保留額外間距。
     *
     * @param tiles 依歷史資料保存順序排列的立牌牌索引。
     * @param winningTile 和牌張牌索引；若沒有則為 null。
     * @param maxWidth 單列允許的最大寬度。
     * @param tileWidth 直立牌的寬度。
     * @param tileHeight 直立牌的高度。
     * @param gap 相鄰牌面之間的間距。
     * @param sidewaysTiles 應橫置顯示的牌索引；預設沒有橫置牌。
     * @return 已換行且包含和牌張的配置。
     */
    fun hand(
        tiles: List<Int>,
        winningTile: Int? = null,
        maxWidth: Float,
        tileWidth: Float = DEFAULT_TILE_WIDTH,
        tileHeight: Float = DEFAULT_TILE_HEIGHT,
        gap: Float = DEFAULT_GAP,
        sidewaysTiles: Set<Int> = emptySet(),
    ): HistoryTileGroupLayout {
        val standingTiles = tiles.toMutableList().apply {
            if (winningTile != null) remove(winningTile)
        }
        val placements = mutableListOf<HistoryTilePlacement>()
        var x = 0f
        var y = 0f
        var lineHeight = 0f
        var lineStart = 0
        var maxRight = 0f

        /** 將目前列封存並開始下一列。 */
        fun beginLine() {
            maxRight = maxOf(maxRight, x)
            x = 0f
            y += lineHeight + gap
            lineHeight = 0f
            lineStart = placements.size
        }

        /**
         * 將一張牌加入目前列，必要時先換列並維持牌底線對齊。
         *
         * @param tile 牌目錄索引。
         * @param extraGap 相對前一張牌額外保留的水平間距。
         */
        fun append(tile: Int, extraGap: Float = 0f) {
            val sideways = tile in sidewaysTiles
            val width = if (sideways) tileHeight else tileWidth
            val height = if (sideways) tileWidth else tileHeight
            val required = if (x == 0f) width else gap + extraGap + width
            if (x > 0f && x + required > maxWidth.coerceAtLeast(tileWidth)) beginLine()
            val leadingGap = if (x == 0f) 0f else gap + extraGap
            if (height > lineHeight) {
                lineHeight = height
                for (index in lineStart until placements.size) {
                    val previous = placements[index]
                    placements[index] = previous.copy(y = y + lineHeight - previous.height)
                }
            }
            x += leadingGap
            placements += HistoryTilePlacement(
                tile = tile,
                x = x,
                y = y + lineHeight - height,
                width = width,
                height = height,
                orientation = if (sideways) DecisionTileOrientationDto.ROTATED_RIGHT else DecisionTileOrientationDto.UPRIGHT,
            )
            x += width
            maxRight = maxOf(maxRight, x)
        }

        standingTiles.forEach(::append)
        winningTile?.let { append(it, WINNING_TILE_EXTRA_GAP) }
        val rawHeight = if (placements.isEmpty()) 0f else y + lineHeight
        val scale = if (maxRight > maxWidth && maxWidth > 0f) maxWidth / maxRight else 1f
        return HistoryTileGroupLayout(
            placements = placements.map { placement ->
                placement.copy(
                    x = placement.x * scale,
                    y = placement.y * scale,
                    width = placement.width * scale,
                    height = placement.height * scale,
                )
            },
            width = maxRight * scale,
            height = rawHeight * scale,
        )
    }

    /**
     * 計算單一副露配置：所有牌直立、依保存順序排成一列，加槓的四張也排成一列。
     *
     * 牌譜沒有桌面座位方向，因此不以橫放表示鳴牌來源；來源由呼叫端另外標記。
     *
     * @param meld 歷史資料中的副露。
     * @param maxWidth 單組副露允許的最大寬度；超出時等比例縮小。
     * @param tileWidth 直立牌的寬度。
     * @param tileHeight 直立牌的高度。
     * @param gap 相鄰牌面之間的間距。
     * @return 不拆開副露且符合寬度上限的配置。
     */
    fun meld(
        meld: HistoryReplayMeldDto,
        maxWidth: Float,
        tileWidth: Float = DEFAULT_TILE_WIDTH,
        tileHeight: Float = DEFAULT_TILE_HEIGHT,
        gap: Float = DEFAULT_GAP,
    ): HistoryTileGroupLayout {
        val placements = meld.tiles.mapIndexed { index, tile ->
            HistoryTilePlacement(
                tile = tile,
                x = index * (tileWidth + gap),
                y = 0f,
                width = tileWidth,
                height = tileHeight,
                faceDown = tile in meld.faceDownTileIds,
            )
        }
        val rawWidth = placements.maxOfOrNull { it.x + it.width } ?: 0f
        val rawHeight = if (placements.isEmpty()) 0f else tileHeight
        val scale = if (rawWidth > maxWidth && maxWidth > 0f) maxWidth / rawWidth else 1f
        return HistoryTileGroupLayout(
            placements = placements.map { placement ->
                placement.copy(
                    x = placement.x * scale,
                    width = placement.width * scale,
                    height = placement.height * scale,
                )
            },
            width = rawWidth * scale,
            height = rawHeight * scale,
        )
    }

    /** 預設直立牌寬度。 */
    private const val DEFAULT_TILE_WIDTH = 18f

    /** 預設直立牌高度。 */
    private const val DEFAULT_TILE_HEIGHT = 24f

    /** 預設相鄰牌面間距。 */
    private const val DEFAULT_GAP = 2f

    /** 立牌與和牌張之間的額外間距。 */
    private const val WINNING_TILE_EXTRA_GAP = 6f
}
