package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPresentation
import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicator
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerDisplayNameResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementDetailSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementRankingSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementWinnerSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.BuiltInWinSettlementTemplateKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExtensionPresentationField
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationAlignment
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationAnimationEffect
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationContainerStyle
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationFieldId
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayoutSolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayoutSolver.Companion.anchorOffset
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayoutSolver.Companion.arrange
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayoutSolver.Companion.crossAxisOffset
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayoutSolver.Companion.fittedScale
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayoutSolver.Companion.unweighted
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationNodeSize
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationTextMeasurer
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationTimelineAnchor
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ScoreRankingAnimation
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationFieldSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementTextKeys
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

/**
 * 逐位贏家詳情與最終共用排行的 client-only billboard renderer。
 *
 * @param context Minecraft renderer factory 提供的 client context。
 * @param templateRegistry 提供贏家詳情版面模板與欄位 provider。
 * @param portraitRenderer 繪製玩家頭像。
 * @param tileFaceRenderer 繪製版面中的牌張。
 * @param playerNames 依桌子與玩家 ID 解析顯示名稱。
 * @param configStore 提供胡牌結算面板可見性設定。
 * @param indicatorTextResolver 解析排名中的付款原因指示器文字與顏色。
 */
class WinSettlementPresentationEntityRenderer(
    context: EntityRendererFactory.Context,
    private val templateRegistry: WinSettlementPresentationTemplateRegistry,
    private val portraitRenderer: PlayerPortraitRenderer,
    private val tileFaceRenderer: MahjongTileFaceRenderer,
    private val playerNames: ClientPlayerDisplayNameResolver,
    private val configStore: MahjongClientConfigStore,
    private val indicatorTextResolver: PublicPlayerIndicatorTextResolver,
) : EntityRenderer<WinSettlementPresentationEntity>(context) {
    private val textRenderer = context.textRenderer

    /** 宣告式版面的量測與配置；文字寬度用本 renderer 的字型量測。 */
    private val layoutSolver = PresentationLayoutSolver(
        templateRegistry,
        object : PresentationTextMeasurer {
            override fun measure(text: PresentationValue.TextValue): Float = textRenderer.getWidth(Text.translatable(text.translationKey, *text.arguments.toTypedArray())).toFloat()

            override fun measurePlain(text: String): Float = textRenderer.getWidth(text).toFloat()
        },
    )

    /** 目前這次 [render] 呼叫所屬的桌子 ID，[playerName] 解析名稱時查詢用；每次 render 開頭重設。 */
    private var currentTableId: Uuid? = null

    override fun render(
        entity: WinSettlementPresentationEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        if (!configStore.current.presentationVisibility.winSettlementEnabled) return
        currentTableId = entity.managedTableId
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        val elapsed = entity.elapsedTicks(tickDelta)
        if (elapsed < 0.0 || elapsed > entity.endGameTime - entity.startGameTime) return
        matrices.push()
        matrices.translate(0.0, 0.55, 0.0)
        matrices.multiply(dispatcher.rotation)
        matrices.scale(-SCALE, -SCALE, SCALE)
        val rankingStart = entity.rankingStartTick().toDouble()
        if (elapsed < rankingStart) {
            renderWinnerPhase(entity, elapsed, matrices, vertexConsumers)
        } else {
            renderRankingPhase(entity, elapsed - rankingStart, matrices, vertexConsumers)
        }
        matrices.pop()
    }

    /**
     * 按揭曉時間選出目前贏家並繪製該位玩家的結算詳情。
     *
     * @param entity 提供贏家、模板及揭曉時間的結算 entity。
     * @param elapsed 此次結算演出已經過的 tick。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供面板與文字頂點緩衝。
     */
    private fun renderWinnerPhase(
        entity: WinSettlementPresentationEntity,
        elapsed: Double,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        var index = 0
        while (index + 1 < entity.winners.size && elapsed >= entity.winnerStartTick(index + 1)) index++
        val winner = entity.winners[index]
        val local = elapsed - entity.winnerStartTick(index)
        val duration = entity.winnerDurationTicks(winner).toDouble()
        val alpha = WorldPanelRenderer.phaseAlpha(local, 0.0, 12.0, duration - 12.0, duration)
        if (alpha <= MIN_VISIBLE_ALPHA) return
        val template = templateRegistry.findTemplate(entity.templateKey)
            ?: templateRegistry.findTemplate(BuiltInWinSettlementTemplateKeys.GENERIC)
        if (template != null) {
            renderDeclarativeTemplate(entity, winner, template.root, local, alpha, matrices, consumers)
        }
    }

    /**
     * 第三方模板使用受控文字／牌面／動畫原語，不接受任意 callback。
     *
     * @param entity 提供贏家欄位資料的結算 entity。
     * @param winner 目前要顯示的贏家資料。
     * @param root 模板版面的根節點。
     * @param local 此位贏家演出已經過的 tick。
     * @param alpha 面板透明度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供面板、文字與牌面頂點緩衝。
     */
    private fun renderDeclarativeTemplate(
        entity: WinSettlementPresentationEntity,
        winner: WinSettlementWinnerSnapshot,
        root: PresentationLayout,
        local: Double,
        alpha: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val snapshot = winner.toFieldSnapshot(entity)
        val size = layoutSolver.measure(root, snapshot)
        val scale = minOf(1f, PresentationLayoutSolver.MAX_WIDTH / size.width.coerceAtLeast(1f), PresentationLayoutSolver.MAX_HEIGHT / size.height.coerceAtLeast(1f))
        matrices.push()
        matrices.scale(scale, scale, 1f)
        renderLayout(root, snapshot, -size.width / 2f, -size.height / 2f, local, alpha, matrices, consumers)
        matrices.pop()
    }

    /**
     * 將贏家與結算 entity 的同步資料轉成模板解析用欄位快照。
     *
     * @param entity 提供結果類型、排名與揭曉時序的結算 entity。
     * @return 對應此贏家的模板欄位與擴充欄位快照。
     */
    private fun WinSettlementWinnerSnapshot.toFieldSnapshot(entity: WinSettlementPresentationEntity): WinSettlementPresentationFieldSnapshot {
        val fields = details.mapNotNull { detail ->
            val id = runCatching { PresentationFieldId(detail.id) }.getOrNull() ?: return@mapNotNull null
            val value = when (detail.type) {
                WinSettlementPresentationEntity.DETAIL_TEXT -> PresentationValue.TextValue(detail.values.firstOrNull().orEmpty(), detail.values.drop(1))
                WinSettlementPresentationEntity.DETAIL_TILES -> PresentationValue.TileListValue(detail.values)
                WinSettlementPresentationEntity.DETAIL_ENTRIES -> PresentationValue.EntryListValue(
                    detail.values.chunked(WinSettlementPresentationEntity.ENTRY_VALUE_COUNT)
                        .filter { it.size == WinSettlementPresentationEntity.ENTRY_VALUE_COUNT }
                        .map {
                            PresentationValue.EntryListValue.Entry(
                                translationKey = it[0],
                                trailingText = it[1],
                                trailingTranslationKey = it[2].ifBlank { null },
                                trailingTranslationArgument = it[3].ifBlank { null },
                                highlighted = it[4] == WinSettlementPresentationEntity.ENTRY_HIGHLIGHTED,
                            )
                        },
                )
                else -> return@mapNotNull null
            }
            ExtensionPresentationField(id, value)
        }
        return WinSettlementPresentationFieldSnapshot(
            outcomeId = entity.outcomeId,
            isTsumo = entity.isTsumo,
            winnerId = playerId,
            winnerDisplayName = playerName(playerId),
            winnerIsAi = isAi,
            responsiblePlayerId = responsiblePlayerId,
            responsiblePlayerDisplayName = responsiblePlayerId?.let(::playerName),
            responsiblePlayerIsAi = responsiblePlayerId?.let { id -> entity.rankings.firstOrNull { it.playerId == id }?.isAi ?: false },
            totalScore = totalScore,
            tileAssetKeys = handAssetKeys + melds.flatMap { it.assetKeys },
            tileAssetGroups = buildList {
                add(handAssetKeys)
                addAll(
                    melds.map { meld ->
                        meld.assetKeys.mapIndexed { index, asset -> if (index in meld.faceDownIndices) TILE_BACK_ASSET_KEY else asset }
                    },
                )
            }.filter(List<String>::isNotEmpty),
            winningTileAssetKey = winningTileAssetKey,
            extensionFields = fields,
            initialFadeTicks = entity.revealTiming.initialFadeTicks,
            entryStaggerTicks = entity.revealTiming.entryStaggerTicks,
            scoreRevealTicks = entity.revealTiming.scoreRevealTicks,
            scoreRevealDelayTicks = WinSettlementPresentationEntity.scoreRevealDelayTicks(
                WinSettlementPresentationEntity.layoutOf(entity.templateKey),
            ).toInt(),
        )
    }

    /**
     * 依版面節點型別繪製節點及子節點。
     *
     * @param layout 要繪製的版面節點。
     * @param snapshot 此贏家可供模板解析的欄位快照。
     * @param x 節點左上角 X 座標。
     * @param y 節點左上角 Y 座標。
     * @param local 此位贏家演出已經過的 tick。
     * @param alpha 節點透明度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供繪製頂點緩衝。
     * @param allocatedWidth 父容器分配的寬度；未提供時使用節點自然寬度。
     */
    private fun renderLayout(
        layout: PresentationLayout,
        snapshot: WinSettlementPresentationFieldSnapshot,
        x: Float,
        y: Float,
        local: Double,
        alpha: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        allocatedWidth: Float? = null,
    ) {
        val measured = layoutSolver.measure(layout, snapshot)
        val size = if (allocatedWidth != null) PresentationNodeSize(allocatedWidth, measured.height) else measured
        when (layout) {
            is PresentationLayout.Text -> (layoutSolver.resolve(layout, snapshot) as? PresentationValue.TextValue)?.let {
                val text = Text.translatable(it.translationKey, *it.arguments.toTypedArray())
                if (allocatedWidth == null) {
                    draw(text, x, y, Align.LEFT, color(layout.argb and 0xFFFFFF, alpha * ((layout.argb ushr 24) / 255f)), layout.scale, matrices, consumers)
                } else {
                    val align = when (layout.alignment) {
                        PresentationAlignment.START -> Align.LEFT
                        PresentationAlignment.CENTER -> Align.CENTER
                        PresentationAlignment.END -> Align.RIGHT
                    }
                    val anchorX = when (align) {
                        Align.LEFT -> x
                        Align.CENTER -> x + allocatedWidth / 2f
                        Align.RIGHT -> x + allocatedWidth
                    }
                    val naturalWidth = textRenderer.getWidth(text).toFloat().coerceAtLeast(1f)
                    draw(
                        text,
                        anchorX,
                        y,
                        align,
                        color(layout.argb and 0xFFFFFF, alpha * ((layout.argb ushr 24) / 255f)),
                        minOf(layout.scale, allocatedWidth.coerceAtLeast(1f) / naturalWidth),
                        matrices,
                        consumers,
                    )
                }
            }
            is PresentationLayout.PlayerIdentity -> (layoutSolver.resolve(layout, snapshot) as? PresentationValue.PlayerIdentityValue)?.let { identity ->
                var cursor = x
                if (layout.showFace) {
                    renderPlayerFace(identity.playerId, identity.isAi, cursor, y, alpha, matrices, consumers, PresentationLayoutSolver.FACE_SIZE * layout.scale)
                    cursor += PresentationLayoutSolver.FACE_SIZE * layout.scale + if (layout.showName) layout.spacing * layout.scale else 0f
                }
                if (layout.showName) {
                    draw(Text.literal(identity.displayName), cursor, y, Align.LEFT, color(layout.argb and 0xFFFFFF, alpha * ((layout.argb ushr 24) / 255f)), layout.scale, matrices, consumers)
                }
            }
            is PresentationLayout.Tile -> (layoutSolver.resolve(layout, snapshot) as? PresentationValue.TileValue)?.let {
                renderTile(it.assetKey, x + layout.width / 2f, y + layout.height / 2f, layout.width, layout.height, alpha, matrices, consumers)
            }
            is PresentationLayout.TileList -> (layoutSolver.resolve(layout, snapshot) as? PresentationValue.TileListValue)?.assetKeys.orEmpty().forEachIndexed { index, asset ->
                renderTile(asset, x + layout.tileWidth / 2f + index * (layout.tileWidth + layout.spacing), y + layout.tileHeight / 2f, layout.tileWidth, layout.tileHeight, alpha, matrices, consumers)
            }
            is PresentationLayout.TileGroups -> {
                var tileX = x + layout.tileWidth / 2f
                val groups = (layoutSolver.resolve(layout, snapshot) as? PresentationValue.TileGroupsValue)?.groups.orEmpty().filter(List<String>::isNotEmpty)
                groups.forEachIndexed { groupIndex, group ->
                    group.forEach { asset ->
                        renderTile(asset, tileX, y + layout.tileHeight / 2f, layout.tileWidth, layout.tileHeight, alpha, matrices, consumers)
                        tileX += layout.tileWidth + layout.tileSpacing
                    }
                    if (groupIndex != groups.lastIndex) tileX += layout.groupSpacing - layout.tileSpacing
                }
            }
            is PresentationLayout.RepeatEntries -> (layoutSolver.resolve(layout, snapshot) as? PresentationValue.EntryListValue)?.entries.orEmpty().forEachIndexed { index, entry ->
                val reveal = ((local - snapshot.initialFadeTicks - index * snapshot.entryStaggerTicks) / 6.0).coerceIn(0.0, 1.0).toFloat()
                if (reveal <= MIN_VISIBLE_ALPHA) return@forEachIndexed
                val entryScale = 0.85f
                val emphasisScale = if (entry.highlighted) lerp(1.08f, 1f, reveal) else 1f
                val count = (layoutSolver.resolve(layout, snapshot) as? PresentationValue.EntryListValue)?.entries?.size ?: 0
                val columns = ((count + layout.entriesPerColumn - 1) / layout.entriesPerColumn).coerceAtLeast(1)
                val columnWidth = layout.width / columns
                val column = index / layout.entriesPerColumn
                val row = index % layout.entriesPerColumn
                val rowX = x + column * columnWidth
                val rowY = y + row * layout.rowHeight
                val title = Text.translatable(entry.translationKey)
                val titleScale = fittedScale(textRenderer.getWidth(title).toFloat(), columnWidth - 44f, entryScale)
                val titleY = rowY + crossAxisOffset(layout.rowHeight, textRenderer.fontHeight * titleScale, layout.verticalAlignment)
                val trailing = entry.trailingTranslationKey?.let { key ->
                    entry.trailingTranslationArgument?.let { Text.translatable(key, it) } ?: Text.translatable(key)
                } ?: entry.trailingText.takeIf(String::isNotBlank)?.let(Text::literal)
                val trailingY = rowY + crossAxisOffset(layout.rowHeight, textRenderer.fontHeight * entryScale, layout.verticalAlignment)
                matrices.push()
                matrices.translate(rowX, rowY + layout.rowHeight / 2f, 0f)
                matrices.scale(emphasisScale, emphasisScale, 1f)
                matrices.translate(-rowX, -(rowY + layout.rowHeight / 2f), 0f)
                draw(title, rowX, titleY, Align.LEFT, color(if (entry.highlighted) 0xFFD45A else 0xFFFFFF, alpha * reveal), titleScale, matrices, consumers)
                if (trailing != null) draw(trailing, x + (column + 1) * columnWidth - 5f, trailingY, Align.RIGHT, color(if (entry.highlighted) 0xFF8C42 else 0xFFE08A, alpha * reveal), entryScale, matrices, consumers)
                matrices.pop()
            }
            is PresentationLayout.Row -> {
                renderContainer(layout.style, x, y, size, alpha, matrices, consumers)
                val contentWidth = size.width - layout.style.padding * 2f
                val slots = layoutSolver.allocateMainAxis(layout.children, contentWidth, layout.spacing, snapshot)
                val positions = arrange(slots.map { it.second }, contentWidth, layout.spacing, layout.arrangement)
                layout.children.forEachIndexed { index, child ->
                    val childHeight = layoutSolver.measure(child.unweighted(), snapshot).height
                    val childY = y + layout.style.padding + crossAxisOffset(size.height - layout.style.padding * 2f, childHeight, layout.alignment)
                    renderLayout(child.unweighted(), snapshot, x + layout.style.padding + positions[index], childY, local, alpha, matrices, consumers, slots[index].second)
                }
            }
            is PresentationLayout.Column -> {
                renderContainer(layout.style, x, y, size, alpha, matrices, consumers)
                val contentHeight = size.height - layout.style.padding * 2f
                val heights = layoutSolver.allocateVerticalMainAxis(layout.children, contentHeight, layout.spacing, snapshot)
                val positions = arrange(heights, contentHeight, layout.spacing, layout.arrangement)
                layout.children.forEachIndexed { index, child ->
                    val childLayout = child.unweighted()
                    val childWidth = layoutSolver.measure(childLayout, snapshot).width
                    val childX = x + layout.style.padding + crossAxisOffset(size.width - layout.style.padding * 2f, childWidth, layout.alignment)
                    renderLayout(childLayout, snapshot, childX, y + layout.style.padding + positions[index], local, alpha, matrices, consumers)
                }
            }
            is PresentationLayout.Weighted -> renderLayout(layout.child, snapshot, x, y, local, alpha, matrices, consumers)
            is PresentationLayout.Grid -> {
                renderContainer(layout.style, x, y, size, alpha, matrices, consumers)
                val childSizes = layout.children.map { layoutSolver.measure(it, snapshot) }
                val cellWidth = childSizes.maxOfOrNull(PresentationNodeSize::width) ?: 0f
                val cellHeight = childSizes.maxOfOrNull(PresentationNodeSize::height) ?: 0f
                layout.children.forEachIndexed { index, child ->
                    renderLayout(child, snapshot, x + layout.style.padding + (index % layout.columns) * (cellWidth + layout.horizontalSpacing), y + layout.style.padding + (index / layout.columns) * (cellHeight + layout.verticalSpacing), local, alpha, matrices, consumers)
                }
            }
            is PresentationLayout.Spacer -> Unit
            is PresentationLayout.SizeConstraint -> renderLayout(layout.child, snapshot, x, y, local, alpha, matrices, consumers)
            is PresentationLayout.Box -> {
                renderContainer(layout.style, x, y, size, alpha, matrices, consumers)
                layout.children.forEach { positioned ->
                    val childSize = layoutSolver.measure(positioned.child, snapshot)
                    val childX = x + layout.style.padding + positioned.x - anchorOffset(childSize.width, positioned.horizontalAnchor)
                    val childY = y + layout.style.padding + positioned.y - anchorOffset(childSize.height, positioned.verticalAnchor)
                    renderLayout(positioned.child, snapshot, childX, childY, local, alpha, matrices, consumers)
                }
            }
            is PresentationLayout.Positioned -> renderLayout(layout.child, snapshot, x, y, local, alpha, matrices, consumers)
            is PresentationLayout.IfPresent -> if (templateRegistry.findFieldProvider(layout.fieldId)?.provide(snapshot) != null) {
                renderLayout(layout.child, snapshot, x, y, local, alpha, matrices, consumers, allocatedWidth)
            }
            is PresentationLayout.Animated -> renderAnimatedLayout(layout, snapshot, x, y, local, alpha, matrices, consumers, allocatedWidth)
        }
    }

    /**
     * 根據時間軸套用動畫效果後繪製子版面。
     *
     * @param layout 動畫版面節點。
     * @param snapshot 此贏家可供模板解析的欄位快照。
     * @param x 節點左上角 X 座標。
     * @param y 節點左上角 Y 座標。
     * @param local 此位贏家演出已經過的 tick。
     * @param alpha 節點透明度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供繪製頂點緩衝。
     * @param allocatedWidth 父容器分配的寬度；未提供時使用子節點自然寬度。
     */
    private fun renderAnimatedLayout(
        layout: PresentationLayout.Animated,
        snapshot: WinSettlementPresentationFieldSnapshot,
        x: Float,
        y: Float,
        local: Double,
        alpha: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        allocatedWidth: Float?,
    ) {
        val start = animationAnchorTick(layout.timeline.anchor, snapshot) + layout.timeline.offsetTicks
        val progress = ((local - start) / layout.timeline.durationTicks).coerceIn(0.0, 1.0).toFloat()
        if (local < start) return
        val size = layoutSolver.measure(layout.child, snapshot).let { if (allocatedWidth == null) it else PresentationNodeSize(allocatedWidth, it.height) }
        var animatedAlpha = alpha
        var translateX = 0f
        var translateY = 0f
        var scaleX = 1f
        var scaleY = 1f
        layout.effects.forEach { effect ->
            when (effect) {
                is PresentationAnimationEffect.Fade -> animatedAlpha *= lerp(effect.fromAlpha, effect.toAlpha, progress)
                is PresentationAnimationEffect.Slide -> {
                    translateX += lerp(effect.fromX, 0f, progress)
                    translateY += lerp(effect.fromY, 0f, progress)
                }
                is PresentationAnimationEffect.ScaleKeyframes -> {
                    val scale = scaleAt(effect, progress)
                    scaleX *= scale
                    scaleY *= scale
                }
                is PresentationAnimationEffect.HorizontalReveal -> scaleX *= lerp(effect.fromScale, 1f, progress)
                is PresentationAnimationEffect.BackgroundPulse -> renderAnimatedOverlay(effect.argb, x, y, size, alpha * pulse(progress), matrices, consumers)
                is PresentationAnimationEffect.HighlightSweep -> renderHighlightSweep(effect, x, y, size, progress, alpha, matrices, consumers)
            }
        }
        if (animatedAlpha <= MIN_VISIBLE_ALPHA) return
        matrices.push()
        val originX = x + anchorOffset(size.width, layout.transformOriginX)
        val originY = y + anchorOffset(size.height, layout.transformOriginY)
        matrices.translate(originX + translateX, originY + translateY, 0f)
        matrices.scale(scaleX, scaleY, 1f)
        matrices.translate(-originX, -originY, 0f)
        renderLayout(layout.child, snapshot, x, y, local, animatedAlpha, matrices, consumers, allocatedWidth)
        matrices.pop()
    }

    /**
     * 依動畫時間錨點計算此效果在本贏家詳情中的起始 tick。
     *
     * @param anchor 動畫使用的時間錨點。
     * @param snapshot 提供進場、條目與分數時序的欄位快照。
     * @return 相對本贏家階段起點的整數 tick。
     */
    private fun animationAnchorTick(anchor: PresentationTimelineAnchor, snapshot: WinSettlementPresentationFieldSnapshot): Int {
        val entries = snapshot.extensionFields.mapNotNull { it.value as? PresentationValue.EntryListValue }.sumOf { it.entries.size }
        return when (anchor) {
            PresentationTimelineAnchor.PANEL_START -> 0
            PresentationTimelineAnchor.ENTRIES_START -> snapshot.initialFadeTicks
            PresentationTimelineAnchor.AFTER_ENTRIES -> snapshot.initialFadeTicks + entries * snapshot.entryStaggerTicks
            PresentationTimelineAnchor.SCORE_REVEAL ->
                snapshot.initialFadeTicks + entries * snapshot.entryStaggerTicks + snapshot.scoreRevealDelayTicks
        }
    }

    /**
     * 在相鄰縮放關鍵影格之間線性內插倍率。
     *
     * @param effect 含有有序縮放關鍵影格的動畫效果。
     * @param progress 動畫進度。
     * @return 此進度對應的縮放倍率。
     */
    private fun scaleAt(effect: PresentationAnimationEffect.ScaleKeyframes, progress: Float): Float {
        val rightIndex = effect.keyframes.indexOfFirst { it.progress >= progress }.coerceAtLeast(1)
        val left = effect.keyframes[rightIndex - 1]
        val right = effect.keyframes[rightIndex]
        val local = ((progress - left.progress) / (right.progress - left.progress)).coerceIn(0f, 1f)
        return lerp(left.scale, right.scale, local)
    }

    /**
     * 以容器繪製器輸出動畫背景色板。
     *
     * @param argb 背景 ARGB 顏色。
     * @param x 色板左上角 X 座標。
     * @param y 色板左上角 Y 座標。
     * @param size 色板寬高。
     * @param alpha 顯示透明度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供背景頂點緩衝。
     */
    private fun renderAnimatedOverlay(argb: Int, x: Float, y: Float, size: PresentationNodeSize, alpha: Float, matrices: MatrixStack, consumers: VertexConsumerProvider) {
        renderContainer(PresentationContainerStyle(backgroundArgb = argb), x, y, size, alpha, matrices, consumers)
    }

    /**
     * 繪製依動畫進度由左至右移動的高亮掃光。
     *
     * @param effect 掃光寬度及顏色設定。
     * @param x 節點左上角 X 座標。
     * @param y 節點左上角 Y 座標。
     * @param size 節點版面尺寸。
     * @param progress 掃光動畫進度。
     * @param alpha 節點透明度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供掃光頂點緩衝。
     */
    private fun renderHighlightSweep(
        effect: PresentationAnimationEffect.HighlightSweep,
        x: Float,
        y: Float,
        size: PresentationNodeSize,
        progress: Float,
        alpha: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val left = x - effect.width + (size.width + effect.width * 2f) * progress
        renderAnimatedOverlay(effect.argb, left, y, PresentationNodeSize(effect.width, size.height), alpha * pulse(progress), matrices, consumers)
    }

    /**
     * 取得由 0 上升至 1 再回到 0 的三角脈衝值。
     *
     * @param progress 動畫進度。
     * @return 介於 0 與 1 的脈衝值。
     */
    private fun pulse(progress: Float): Float = 1f - abs(progress * 2f - 1f)

    /**
     * 以指定進度在線性區間內插兩個浮點值。
     *
     * @param from 起點值。
     * @param to 終點值。
     * @param progress 內插進度。
     * @return 內插結果。
     */
    private fun lerp(from: Float, to: Float, progress: Float): Float = from + (to - from) * progress

    /**
     * 繪製排名列玩家的頭像。
     *
     * @param player 排名玩家快照。
     * @param x 頭像左側 X 座標。
     * @param y 頭像上側 Y 座標。
     * @param alpha 頭像透明度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供頭像頂點緩衝。
     */
    private fun renderRankingPortrait(
        player: WinSettlementRankingSnapshot,
        x: Float,
        y: Float,
        alpha: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) = renderPlayerFace(player.playerId, player.isAi, x, y, alpha, matrices, consumers)

    /**
     * 透過共用 renderer 繪製指定玩家的頭像。
     *
     * @param playerId 玩家 ID。
     * @param isAi 玩家是否為 AI。
     * @param x 頭像左側 X 座標。
     * @param y 頭像上側 Y 座標。
     * @param alpha 頭像透明度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供頭像頂點緩衝。
     * @param size 頭像邊長。
     */
    private fun renderPlayerFace(
        playerId: String,
        isAi: Boolean,
        x: Float,
        y: Float,
        alpha: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        size: Float = PresentationLayoutSolver.FACE_SIZE,
    ) = portraitRenderer.render(
        playerId = Uuid.parse(playerId),
        isAi = isAi,
        x = x,
        y = y,
        size = size,
        alpha = alpha,
        z = 0f,
        matrices = matrices,
        consumers = consumers,
    )

    /**
     * 繪製容器背景與邊框；兩者皆透明時不輸出頂點。
     *
     * @param style 背景、邊框顏色與邊框寬度。
     * @param x 容器左上角 X 座標。
     * @param y 容器左上角 Y 座標。
     * @param size 容器寬高。
     * @param alpha 動畫透明度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供背景及邊框頂點緩衝。
     */
    private fun renderContainer(style: PresentationContainerStyle, x: Float, y: Float, size: PresentationNodeSize, alpha: Float, matrices: MatrixStack, consumers: VertexConsumerProvider) {
        if ((style.backgroundArgb ushr 24) == 0 && style.borderWidth <= 0f) return
        val matrix = matrices.peek().positionMatrix
        val buffer = consumers.getBuffer(ExhaustiveDrawSettlementPanelRenderLayer.layer)
        fun quad(left: Float, top: Float, right: Float, bottom: Float, argb: Int) {
            val a = (((argb ushr 24) and 0xFF) * alpha).roundToInt()
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            buffer.vertex(matrix, left, top, 0.5f).color(r, g, b, a).next()
            buffer.vertex(matrix, right, top, 0.5f).color(r, g, b, a).next()
            buffer.vertex(matrix, right, bottom, 0.5f).color(r, g, b, a).next()
            buffer.vertex(matrix, left, bottom, 0.5f).color(r, g, b, a).next()
        }
        if ((style.backgroundArgb ushr 24) != 0) quad(x, y, x + size.width, y + size.height, style.backgroundArgb)
        if (style.borderWidth > 0f && (style.borderArgb ushr 24) != 0) {
            val w = style.borderWidth
            quad(x, y, x + size.width, y + w, style.borderArgb)
            quad(x, y + size.height - w, x + size.width, y + size.height, style.borderArgb)
            quad(x, y, x + w, y + size.height, style.borderArgb)
            quad(x + size.width - w, y, x + size.width, y + size.height, style.borderArgb)
        }
    }

    /**
     * 繪製最終排名、分數變化與付款原因欄位。
     *
     * @param entity 提供排名快照及時間軸的結算 entity。
     * @param local 排名階段已經過的 tick。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供面板、頭像與文字頂點緩衝。
     */
    private fun renderRankingPhase(entity: WinSettlementPresentationEntity, local: Double, matrices: MatrixStack, consumers: VertexConsumerProvider) {
        val alpha = WorldPanelRenderer.phaseAlpha(local, 0.0, 12.0, WinSettlementPresentationEntity.RANKING_TICKS.toDouble(), (WinSettlementPresentationEntity.RANKING_TICKS + WinSettlementPresentationEntity.FADE_OUT_TICKS).toDouble())
        if (alpha <= MIN_VISIBLE_ALPHA) return
        val layout = measureRankingLayout(entity.rankings)
        val rankRightX = layout[SettlementRankingColumnId.RANK].right
        val faceLeftX = layout[SettlementRankingColumnId.FACE].left
        val nameLeftX = layout[SettlementRankingColumnId.NAME].left
        val scoreRightX = layout[SettlementRankingColumnId.SCORE].right
        val deltaRightX = layout[SettlementRankingColumnId.DELTA].right
        val paymentReasonLeftX = layout.find(SettlementRankingColumnId.PAYMENT_REASON)?.left
        val panelBottom = -20f + entity.rankings.size * 16f + 8f
        renderPanel(layout.panelHalfWidth, -55f, panelBottom, alpha, matrices, consumers)
        draw(Text.translatable(WinSettlementTextKeys.SCORE_RANKING), 0f, -42f, Align.CENTER, color(0xFFD45A, alpha), 1.25f, matrices, consumers)
        val progress = ((local - 35.0) / 48.0).coerceIn(0.0, 1.0)
        val presentation = ScoreRankingPresentation(entity.rankings.map { it.toRankingPlayer() })
        val rows = ScoreRankingAnimation.rows(presentation, progress)
        val ranks = ScoreRankingAnimation.liveRanks(rows)
        rows.sortedBy { it.player.previousRank }.forEachIndexed { index, row ->
            val reveal = ((local - WinSettlementPresentationEntity.RANKING_ROW_START_TICKS - index * WinSettlementPresentationEntity.RANKING_ROW_STAGGER_TICKS) / 6.0).coerceIn(0.0, 1.0).toFloat()
            if (reveal <= 0f) return@forEachIndexed
            val y = -20f + (row.position - 1.0).toFloat() * 16f
            val rowAlpha = alpha * reveal
            val snapshot = entity.rankings.first { it.playerId == row.player.playerId.toString() }
            val settledEffect = SettlementRankingSettledEffect.resolve(
                local,
                WinSettlementPresentationEntity.RANKING_SETTLED_SOUND_TICKS.toDouble(),
                snapshot.previousRank != snapshot.currentRank,
            )
            renderRankingSettledHighlight(layout.panelHalfWidth, y, rowAlpha, settledEffect, matrices, consumers)
            matrices.push()
            matrices.translate(0f, y, 0f)
            matrices.scale(settledEffect.rowScale, settledEffect.rowScale, 1f)
            matrices.push()
            matrices.translate(rankRightX, 0f, 0f)
            matrices.scale(settledEffect.rankScale, settledEffect.rankScale, 1f)
            draw(
                Text.literal(ranks.getValue(row.player.playerId).toString()),
                0f,
                0f,
                Align.RIGHT,
                color(mixRgb(0xFFD45A, 0xFFFFFF, settledEffect.rankWhiteness), rowAlpha),
                1f,
                matrices,
                consumers,
            )
            matrices.pop()
            renderRankingPortrait(snapshot, faceLeftX, -1f, rowAlpha, matrices, consumers)
            draw(Text.literal(fitPlayerName(playerName(row.player.playerId.toString()))), nameLeftX, 0f, Align.LEFT, color(0xFFFFFF, rowAlpha), 1f, matrices, consumers)
            draw(Text.literal(row.score.toString()), scoreRightX, 0f, Align.RIGHT, color(0xFFF3C4, rowAlpha), 1f, matrices, consumers)
            draw(Text.literal(SettlementRankingLayout.formatDelta(row.delta)), deltaRightX, 0f, Align.RIGHT, color(if (row.delta >= 0) 0x80FF80 else 0xFF8080, rowAlpha), 1f, matrices, consumers)
            val paymentReason = snapshot.paymentReasonText()
            if (paymentReasonLeftX != null && paymentReason != null) {
                val (reasonText, reasonArgb) = paymentReason
                draw(reasonText, paymentReasonLeftX, 0f, Align.LEFT, color(reasonArgb and 0xFFFFFF, rowAlpha), 1f, matrices, consumers)
            }
            matrices.pop()
        }
    }

    /**
     * 繪製排名落定列的底色高亮與橫向掃光。
     *
     * @param panelHalfWidth 排名面板半寬。
     * @param y 排名列頂部 Y 座標。
     * @param alpha 排名列透明度。
     * @param effect 落定效果的縮放、透明度與掃光進度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供高亮頂點緩衝。
     */
    private fun renderRankingSettledHighlight(
        panelHalfWidth: Float,
        y: Float,
        alpha: Float,
        effect: SettlementRankingSettledEffect.State,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        if (!effect.active) return
        val left = -panelHalfWidth + 5f
        val right = panelHalfWidth - 5f
        renderHighlightQuad(left, right, y - 3f, y + 11f, alpha * effect.highlightAlpha, matrices, consumers)
        val sweepCenter = left + (right - left) * effect.sweepProgress
        renderHighlightQuad(
            maxOf(left, sweepCenter - RANKING_SWEEP_HALF_WIDTH),
            minOf(right, sweepCenter + RANKING_SWEEP_HALF_WIDTH),
            y - 3f,
            y + 11f,
            alpha * effect.highlightAlpha * 1.6f,
            matrices,
            consumers,
        )
    }

    /**
     * 以金色繪製單一半透明矩形高亮。
     *
     * @param left 矩形左緣。
     * @param right 矩形右緣。
     * @param top 矩形上緣。
     * @param bottom 矩形下緣。
     * @param alpha 矩形透明度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供高亮頂點緩衝。
     */
    private fun renderHighlightQuad(
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        alpha: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        if (right <= left || alpha <= 0f) return
        val matrix = matrices.peek().positionMatrix
        val buffer = consumers.getBuffer(ExhaustiveDrawSettlementPanelRenderLayer.layer)
        val a = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
        buffer.vertex(matrix, left, top, RANKING_HIGHLIGHT_Z).color(255, 205, 92, a).next()
        buffer.vertex(matrix, right, top, RANKING_HIGHLIGHT_Z).color(255, 238, 178, a).next()
        buffer.vertex(matrix, right, bottom, RANKING_HIGHLIGHT_Z).color(255, 238, 178, a).next()
        buffer.vertex(matrix, left, bottom, RANKING_HIGHLIGHT_Z).color(255, 205, 92, a).next()
    }

    /**
     * 逐通道混合兩個 RGB 顏色。
     *
     * @param from 混合起點 RGB 顏色。
     * @param to 混合終點 RGB 顏色。
     * @param progress 由起點往終點的混合進度。
     * @return 混合後的 RGB 顏色。
     */
    private fun mixRgb(from: Int, to: Int, progress: Float): Int {
        val amount = progress.coerceIn(0f, 1f)
        fun channel(shift: Int): Int = (((from shr shift) and 0xFF) + ((((to shr shift) and 0xFF) - ((from shr shift) and 0xFF)) * amount)).roundToInt()
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /**
     * 依排名文字的實際像素寬度量測欄位，包含分數、增減及付款原因；沒有付款原因時省略該欄。
     *
     * @param players 所有排名玩家快照。
     * @return 排名列共用的水平欄位版面。
     */
    private fun measureRankingLayout(players: List<WinSettlementRankingSnapshot>): SettlementRankingLayout {
        val scoreWidth = SettlementRankingLayout.contentColumnWidth(
            contentWidths = players.flatMap { listOf(it.previousScore, it.currentScore) }.map { textRenderer.getWidth(it.toString()).toFloat() },
            padding = SettlementRankingLayout.NUMERIC_COLUMN_PADDING,
            minWidth = SettlementRankingLayout.MIN_SCORE_COLUMN_WIDTH,
        )
        val deltaWidth = SettlementRankingLayout.contentColumnWidth(
            contentWidths = players.map { textRenderer.getWidth(SettlementRankingLayout.formatDelta(it.currentScore - it.previousScore)).toFloat() },
            padding = SettlementRankingLayout.NUMERIC_COLUMN_PADDING,
            minWidth = SettlementRankingLayout.MIN_DELTA_COLUMN_WIDTH,
        )
        val paymentReasonWidths = players.mapNotNull { it.paymentReasonText()?.first?.let(textRenderer::getWidth)?.toFloat() }
        return SettlementRankingLayout.arrange(
            columns = SettlementRankingLayout.standardColumns(
                faceSize = PresentationLayoutSolver.FACE_SIZE,
                scoreWidth = scoreWidth,
                deltaWidth = deltaWidth,
                paymentReasonWidth = paymentReasonWidths.maxOrNull(),
            ),
            panelPadding = PANEL_PADDING,
        )
    }

    /**
     * 將付款原因指示器解析為顯示文字與 ARGB 顏色；沒有有效原因時回傳 `null`。
     *
     * @return 有效付款原因的文字與 ARGB 顏色；沒有原因或 ID 格式不符時為 `null`。
     */
    private fun WinSettlementRankingSnapshot.paymentReasonText(): Pair<Text, Int>? = paymentReasonId
        ?.takeIf { ':' in it }
        ?.let { indicatorTextResolver.resolve(PublicPlayerIndicator(it)) }

    /**
     * 將玩家名稱裁切至排名欄位的固定最大寬度。
     *
     * @param name 原始玩家名稱。
     * @return 可放入名稱欄的文字。
     */
    private fun fitPlayerName(name: String): String = WorldPanelRenderer.fitText(textRenderer, name, SettlementRankingLayout.NAME_MAX_WIDTH)

    /**
     * 將詳情快照的第一個值當翻譯 key，其餘值作為翻譯參數。
     *
     * @return 依本機語系建立的詳情文字。
     */
    private fun WinSettlementDetailSnapshot.text(): Text = Text.translatable(values.firstOrNull().orEmpty(), *values.drop(1).toTypedArray())

    /**
     * 將同步排名快照轉成共用分數動畫模型。
     *
     * @return 含玩家、分數與名次變化的動畫資料。
     */
    private fun WinSettlementRankingSnapshot.toRankingPlayer() = ScoreRankingPlayer(Uuid.parse(playerId), seatIndex, isAi, previousScore, currentScore, previousRank, currentRank)

    /**
     * 依目前結算所屬桌子解析玩家顯示名稱。
     *
     * @param id 玩家 ID。
     * @return 共用名稱解析器提供的顯示名稱。
     */
    private fun playerName(id: String): String = playerNames.resolve(currentTableId, id)

    /**
     * 繪製排名階段使用的半透明黑色面板。
     *
     * @param halfWidth 面板半寬。
     * @param top 面板上緣 Y 座標。
     * @param bottom 面板下緣 Y 座標。
     * @param alpha 面板透明度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供面板頂點緩衝。
     */
    private fun renderPanel(halfWidth: Float, top: Float, bottom: Float, alpha: Float, matrices: MatrixStack, consumers: VertexConsumerProvider) {
        val matrix = matrices.peek().positionMatrix
        val buffer = consumers.getBuffer(ExhaustiveDrawSettlementPanelRenderLayer.layer)
        val a = (alpha * 0.78f * 255).roundToInt()
        buffer.vertex(matrix, -halfWidth, top, 0.5f).color(0, 0, 0, a).next()
        buffer.vertex(matrix, halfWidth, top, 0.5f).color(0, 0, 0, a).next()
        buffer.vertex(matrix, halfWidth, bottom, 0.5f).color(0, 0, 0, a).next()
        buffer.vertex(matrix, -halfWidth, bottom, 0.5f).color(0, 0, 0, a).next()
    }

    /**
     * 在模板版面指定中心繪製一張牌面。
     *
     * @param asset 牌面資產 key。
     * @param cx 牌面中心 X 座標。
     * @param cy 牌面中心 Y 座標。
     * @param width 牌面寬度。
     * @param height 牌面高度。
     * @param alpha 牌面透明度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供牌面頂點緩衝。
     */
    private fun renderTile(asset: String, cx: Float, cy: Float, width: Float, height: Float, alpha: Float, matrices: MatrixStack, consumers: VertexConsumerProvider) {
        tileFaceRenderer.renderWorldPanel(asset, cx, cy, width, height, alpha, 0f, matrices, consumers)
    }

    /**
     * 依指定水平對齊方式、顏色與縮放繪製文字。
     *
     * @param text 要繪製的文字。
     * @param x 對齊錨點 X 座標。
     * @param y 文字基準 Y 座標。
     * @param align 文字相對錨點的水平對齊方式。
     * @param color 文字 ARGB 顏色。
     * @param scale 文字縮放倍率。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供文字頂點緩衝。
     */
    private fun draw(text: Text, x: Float, y: Float, align: Align, color: Int, scale: Float, matrices: MatrixStack, consumers: VertexConsumerProvider) {
        matrices.push()
        matrices.translate(x, y, 0f)
        matrices.scale(scale, scale, 1f)
        val width = textRenderer.getWidth(text).toFloat()
        val localX = when (align) {
            Align.LEFT -> 0f
            Align.CENTER -> -width / 2f
            Align.RIGHT -> -width
        }
        textRenderer.draw(text, localX, 0f, color, false, matrices.peek().positionMatrix, consumers, TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE)
        matrices.pop()
    }

    /**
     * 將 RGB 顏色與動畫透明度合成 ARGB。
     *
     * @param rgb 不含 alpha 的 RGB 顏色。
     * @param alpha 動畫透明度。
     * @return 合成後的 ARGB 顏色。
     */
    private fun color(rgb: Int, alpha: Float) = WorldPanelRenderer.withAlpha(rgb, alpha)
    override fun getTexture(entity: WinSettlementPresentationEntity): Identifier? = null

    /** 文字相對水平錨點的對齊方式。 */
    private enum class Align {
        /** 錨點為文字左緣。 */
        LEFT,

        /** 錨點為文字中心。 */
        CENTER,

        /** 錨點為文字右緣。 */
        RIGHT,
    }

    private companion object {
        const val SCALE = 0.02f
        const val PANEL_PADDING = 12f
        const val TILE_BACK_ASSET_KEY = "back"
        const val MIN_VISIBLE_ALPHA = 0.02f
        const val RANKING_SWEEP_HALF_WIDTH = 18f
        const val RANKING_HIGHLIGHT_Z = 0.25f
    }
}
