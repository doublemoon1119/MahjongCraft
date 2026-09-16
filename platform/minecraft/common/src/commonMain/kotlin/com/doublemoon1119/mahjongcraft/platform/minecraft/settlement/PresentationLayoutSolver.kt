package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 版面節點量測出來的尺寸。
 *
 * @property width 寬度。
 * @property height 高度。
 */
data class PresentationNodeSize(val width: Float, val height: Float) {
    companion object {
        /** 不佔空間的尺寸，供解析不到內容的節點使用。 */
        val ZERO = PresentationNodeSize(0f, 0f)
    }
}

/**
 * 版面計算取得文字寬度的邊界。
 *
 * 文字寬度只有實際的字型量測得出來，因此由平台實作；解算本身不認識任何字型或繪製型別。
 */
interface PresentationTextMeasurer {
    /** 本地化文字的寬度。 */
    fun measure(text: PresentationValue.TextValue): Float

    /** 已經是最終字串（例如玩家顯示名稱）的寬度。 */
    fun measurePlain(text: String): Float
}

/**
 * 宣告式結算版面的量測與配置。
 *
 * 負責把 [PresentationLayout] 樹換算成尺寸與位置：節點量測、主軸空間分配（含權重）、依 arrangement 決定
 * 每個子節點的起點，以及對齊偏移。實際繪製不在這裡——只回傳數字，呼叫端拿著數字去畫。
 *
 * 除了文字寬度（由 [textMeasurer] 提供）與欄位內容（由 [templateRegistry] 提供）之外，全部是純算術，
 * 不依賴任何繪製或 entity 型別。
 *
 * @property templateRegistry 解析版面節點引用的欄位內容。
 * @property textMeasurer 量測文字寬度。
 */
class PresentationLayoutSolver(
    private val templateRegistry: WinSettlementPresentationTemplateRegistry,
    private val textMeasurer: PresentationTextMeasurer,
) {
    /** 解析節點引用的欄位內容；不引用欄位的節點為 `null`。 */
    fun resolve(layout: PresentationLayout, snapshot: WinSettlementPresentationFieldSnapshot): PresentationValue? {
        val id = when (layout) {
            is PresentationLayout.Text -> layout.fieldId
            is PresentationLayout.PlayerIdentity -> layout.fieldId
            is PresentationLayout.Tile -> layout.fieldId
            is PresentationLayout.TileList -> layout.fieldId
            is PresentationLayout.TileGroups -> layout.fieldId
            is PresentationLayout.RepeatEntries -> layout.fieldId
            else -> return null
        }
        return templateRegistry.findFieldProvider(id)?.provide(snapshot)
    }

    /**
     * 量測單一節點的尺寸。
     *
     * 引用欄位但解析不到內容的節點為 [PresentationNodeSize.ZERO]——不佔空間、也不留間距，這樣同一份
     * 模板可以直接套用在缺少該欄位的結算上，不需要為每種組合各寫一份模板。
     */
    fun measure(layout: PresentationLayout, snapshot: WinSettlementPresentationFieldSnapshot): PresentationNodeSize = when (layout) {
        is PresentationLayout.Text -> (resolve(layout, snapshot) as? PresentationValue.TextValue)?.let {
            PresentationNodeSize(textMeasurer.measure(it) * layout.scale, TEXT_LINE_HEIGHT * layout.scale)
        } ?: PresentationNodeSize.ZERO
        is PresentationLayout.PlayerIdentity -> (resolve(layout, snapshot) as? PresentationValue.PlayerIdentityValue)?.let {
            val faceWidth = if (layout.showFace) FACE_SIZE * layout.scale else 0f
            val nameWidth = if (layout.showName) textMeasurer.measurePlain(it.displayName) * layout.scale else 0f
            val gap = if (layout.showFace && layout.showName) layout.spacing * layout.scale else 0f
            PresentationNodeSize(faceWidth + gap + nameWidth, maxOf(faceWidth, TEXT_LINE_HEIGHT * layout.scale))
        } ?: PresentationNodeSize.ZERO
        is PresentationLayout.Tile ->
            if (resolve(layout, snapshot) is PresentationValue.TileValue) {
                PresentationNodeSize(layout.width, layout.height)
            } else {
                PresentationNodeSize.ZERO
            }
        is PresentationLayout.TileList -> {
            val count = (resolve(layout, snapshot) as? PresentationValue.TileListValue)?.assetKeys?.size ?: 0
            PresentationNodeSize(tileSequenceWidth(count, layout.tileWidth, layout.spacing), if (count > 0) layout.tileHeight else 0f)
        }
        is PresentationLayout.TileGroups -> {
            val groups = (resolve(layout, snapshot) as? PresentationValue.TileGroupsValue)?.groups.orEmpty().filter(List<String>::isNotEmpty)
            val count = groups.sumOf(List<String>::size)
            PresentationNodeSize(
                tileSequenceWidth(count, layout.tileWidth, layout.tileSpacing) +
                    (groups.size - 1).coerceAtLeast(0) * (layout.groupSpacing - layout.tileSpacing),
                if (count > 0) layout.tileHeight else 0f,
            )
        }
        is PresentationLayout.RepeatEntries -> {
            val count = (resolve(layout, snapshot) as? PresentationValue.EntryListValue)?.entries?.size ?: 0
            PresentationNodeSize(if (count > 0) layout.width else 0f, minOf(count, layout.entriesPerColumn) * layout.rowHeight)
        }
        is PresentationLayout.Row -> {
            val sizes = layout.children.map { measure(it, snapshot) }
            val intrinsicWidth = sizes.sumOf { it.width.toDouble() }.toFloat() + (sizes.size - 1).coerceAtLeast(0) * layout.spacing
            styledSize(
                if (layout.fillMaxWidth) MAX_WIDTH - layout.style.padding * 2f else intrinsicWidth,
                sizes.maxOfOrNull(PresentationNodeSize::height) ?: 0f,
                layout.style,
            )
        }
        is PresentationLayout.Column -> {
            val sizes = layout.children.map { measure(it, snapshot) }
            val intrinsicHeight = sizes.sumOf { it.height.toDouble() }.toFloat() + (sizes.size - 1).coerceAtLeast(0) * layout.spacing
            styledSize(
                sizes.maxOfOrNull(PresentationNodeSize::width) ?: 0f,
                if (layout.fillMaxHeight) MAX_HEIGHT - layout.style.padding * 2f else intrinsicHeight,
                layout.style,
            )
        }
        is PresentationLayout.Weighted -> measure(layout.child, snapshot)
        is PresentationLayout.Grid -> {
            val sizes = layout.children.map { measure(it, snapshot) }
            val rows = (sizes.size + layout.columns - 1) / layout.columns
            val cellWidth = sizes.maxOfOrNull(PresentationNodeSize::width) ?: 0f
            val cellHeight = sizes.maxOfOrNull(PresentationNodeSize::height) ?: 0f
            styledSize(
                layout.columns * cellWidth + (layout.columns - 1) * layout.horizontalSpacing,
                rows * cellHeight + (rows - 1).coerceAtLeast(0) * layout.verticalSpacing,
                layout.style,
            )
        }
        is PresentationLayout.Spacer -> PresentationNodeSize(layout.width, layout.height)
        is PresentationLayout.SizeConstraint -> measure(layout.child, snapshot).let {
            PresentationNodeSize(minOf(it.width, layout.maxWidth ?: it.width), minOf(it.height, layout.maxHeight ?: it.height))
        }
        is PresentationLayout.Box -> styledSize(layout.width, layout.height, layout.style)
        is PresentationLayout.Positioned -> measure(layout.child, snapshot)
        is PresentationLayout.IfPresent ->
            if (templateRegistry.findFieldProvider(layout.fieldId)?.provide(snapshot) != null) {
                measure(layout.child, snapshot)
            } else {
                PresentationNodeSize.ZERO
            }
        is PresentationLayout.Animated -> measure(layout.child, snapshot)
    }

    /**
     * 分配水平主軸空間。
     *
     * 非權重子節點取自己的量測寬度；剩下的空間依權重比例分給權重子節點，`fill` 為 `false` 的權重子節點
     * 最多只拿到自己的量測寬度，不會被撐大。
     */
    fun allocateMainAxis(
        children: List<PresentationLayout>,
        available: Float,
        spacing: Float,
        snapshot: WinSettlementPresentationFieldSnapshot,
    ): List<Pair<PresentationLayout, Float>> {
        val spacingWidth = (children.size - 1).coerceAtLeast(0) * spacing
        val fixed = children.filterNot { it is PresentationLayout.Weighted }.sumOf { measure(it, snapshot).width.toDouble() }.toFloat()
        val weighted = children.filterIsInstance<PresentationLayout.Weighted>()
        val remaining = (available - fixed - spacingWidth).coerceAtLeast(0f)
        val totalWeight = weighted.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)
        return children.map { child ->
            val width = if (child is PresentationLayout.Weighted) {
                val allocated = remaining * child.weight / totalWeight
                if (child.fill) allocated else minOf(allocated, measure(child.child, snapshot).width)
            } else {
                measure(child, snapshot).width
            }
            child to width
        }
    }

    /** 分配垂直主軸空間；規則與 [allocateMainAxis] 相同，只是換成高度。 */
    fun allocateVerticalMainAxis(
        children: List<PresentationLayout>,
        available: Float,
        spacing: Float,
        snapshot: WinSettlementPresentationFieldSnapshot,
    ): List<Float> {
        val spacingHeight = (children.size - 1).coerceAtLeast(0) * spacing
        val fixed = children.filterNot { it is PresentationLayout.Weighted }.sumOf { measure(it, snapshot).height.toDouble() }.toFloat()
        val weighted = children.filterIsInstance<PresentationLayout.Weighted>()
        val remaining = (available - fixed - spacingHeight).coerceAtLeast(0f)
        val totalWeight = weighted.sumOf { it.weight.toDouble() }.toFloat().coerceAtLeast(1f)
        return children.map { child ->
            if (child is PresentationLayout.Weighted) {
                val allocated = remaining * child.weight / totalWeight
                if (child.fill) allocated else minOf(allocated, measure(child.child, snapshot).height)
            } else {
                measure(child, snapshot).height
            }
        }
    }

    companion object {
        /**
         * 依 arrangement 算出每個子節點沿主軸的起點。
         *
         * 內容超出 [available] 時沒有多餘空間可分配，六種 arrangement 都退化成從 0 依序排開。
         */
        fun arrange(widths: List<Float>, available: Float, spacing: Float, arrangement: PresentationArrangement): List<Float> {
            if (widths.isEmpty()) return emptyList()
            val content = widths.sum() + spacing * (widths.size - 1).coerceAtLeast(0)
            val free = (available - content).coerceAtLeast(0f)
            val (start, extraGap) = when (arrangement) {
                PresentationArrangement.START -> 0f to 0f
                PresentationArrangement.CENTER -> free / 2f to 0f
                PresentationArrangement.END -> free to 0f
                PresentationArrangement.SPACE_BETWEEN -> 0f to if (widths.size > 1) free / (widths.size - 1) else 0f
                PresentationArrangement.SPACE_AROUND -> free / widths.size / 2f to free / widths.size
                PresentationArrangement.SPACE_EVENLY -> free / (widths.size + 1) to free / (widths.size + 1)
            }
            var cursor = start
            return widths.map { width -> cursor.also { cursor += width + spacing + extraGap } }
        }

        /** 對齊點相對於自身尺寸的偏移。 */
        fun anchorOffset(size: Float, alignment: PresentationAlignment): Float = when (alignment) {
            PresentationAlignment.START -> 0f
            PresentationAlignment.CENTER -> size / 2f
            PresentationAlignment.END -> size
        }

        /** 子節點在交叉軸上的偏移；子節點比容器大時不產生負偏移。 */
        fun crossAxisOffset(available: Float, childSize: Float, alignment: PresentationAlignment): Float = when (alignment) {
            PresentationAlignment.START -> 0f
            PresentationAlignment.CENTER -> (available - childSize).coerceAtLeast(0f) / 2f
            PresentationAlignment.END -> (available - childSize).coerceAtLeast(0f)
        }

        /** 連續排列的牌張總寬度；沒有牌時為 0，不會算進負的間距。 */
        fun tileSequenceWidth(count: Int, tileWidth: Float, gap: Float): Float = count * tileWidth + (count - 1).coerceAtLeast(0) * gap

        /** 加上容器留白之後的尺寸。 */
        fun styledSize(width: Float, height: Float, style: PresentationContainerStyle): PresentationNodeSize = PresentationNodeSize(width + style.padding * 2f, height + style.padding * 2f)

        /** 取出權重節點包住的實際節點；非權重節點回傳自己。 */
        fun PresentationLayout.unweighted(): PresentationLayout = (this as? PresentationLayout.Weighted)?.child ?: this

        /** 宣告式版面可用的最大寬度。 */
        const val MAX_WIDTH = 320f

        /** 宣告式版面可用的最大高度。 */
        const val MAX_HEIGHT = 156f

        /** 玩家頭像的邊長。 */
        const val FACE_SIZE = 10f

        /** 單行文字的高度。 */
        const val TEXT_LINE_HEIGHT = 10f
    }
}
