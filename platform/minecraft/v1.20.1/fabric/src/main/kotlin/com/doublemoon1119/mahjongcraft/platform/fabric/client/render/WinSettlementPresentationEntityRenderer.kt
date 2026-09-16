package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingAnimation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementTranslationKeys
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerDisplayNameResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementDetailSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementRankingSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementWinnerSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
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
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationFieldSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

/** 逐位贏家詳情與最終共用排行的 client-only billboard renderer。 */
class WinSettlementPresentationEntityRenderer(
    context: EntityRendererFactory.Context,
    private val templateRegistry: WinSettlementPresentationTemplateRegistry,
    private val portraitRenderer: PlayerPortraitRenderer,
    private val tileFaceRenderer: MahjongTileFaceRenderer,
    private val playerNames: ClientPlayerDisplayNameResolver,
    private val configStore: MahjongClientConfigStore,
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
            ?: templateRegistry.findTemplate("${MinecraftModMetadata.MOD_ID}:generic")
        if (template != null) {
            renderDeclarativeTemplate(entity, winner, template.root, local, alpha, matrices, consumers)
        }
    }

    /** 第三方模板使用受控文字／牌面／動畫原語，不接受任意 callback。 */
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
        )
    }

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
                val emphasisScale = if (entry.trailingTranslationKey == null) 1f else lerp(1.08f, 1f, reveal)
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
                } ?: entry.trailingText.takeIf(String::isNotBlank)?.let { Text.translatable(WinSettlementTranslationKeys.HAN, it) }
                val trailingY = rowY + crossAxisOffset(layout.rowHeight, textRenderer.fontHeight * entryScale, layout.verticalAlignment)
                matrices.push()
                matrices.translate(rowX, rowY + layout.rowHeight / 2f, 0f)
                matrices.scale(emphasisScale, emphasisScale, 1f)
                matrices.translate(-rowX, -(rowY + layout.rowHeight / 2f), 0f)
                draw(title, rowX, titleY, Align.LEFT, color(if (entry.trailingTranslationKey == null) 0xFFFFFF else 0xFFD45A, alpha * reveal), titleScale, matrices, consumers)
                if (trailing != null) draw(trailing, x + (column + 1) * columnWidth - 5f, trailingY, Align.RIGHT, color(if (entry.trailingTranslationKey == null) 0xFFE08A else 0xFF8C42, alpha * reveal), entryScale, matrices, consumers)
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

    private fun animationAnchorTick(anchor: PresentationTimelineAnchor, snapshot: WinSettlementPresentationFieldSnapshot): Int {
        val entries = snapshot.extensionFields.mapNotNull { it.value as? PresentationValue.EntryListValue }.sumOf { it.entries.size }
        return when (anchor) {
            PresentationTimelineAnchor.PANEL_START -> 0
            PresentationTimelineAnchor.ENTRIES_START -> snapshot.initialFadeTicks
            PresentationTimelineAnchor.AFTER_ENTRIES -> snapshot.initialFadeTicks + entries * snapshot.entryStaggerTicks
            PresentationTimelineAnchor.SCORE_REVEAL -> {
                snapshot.initialFadeTicks + entries * snapshot.entryStaggerTicks +
                    if (snapshot.extensionFields.any { it.id.value.endsWith(":riichi_han_fu") || it.id.value.endsWith(":riichi_yakuman_total") }) {
                        WinSettlementPresentationEntity.HAN_FU_REVEAL_TICKS.toInt()
                    } else {
                        0
                    }
            }
        }
    }

    private fun scaleAt(effect: PresentationAnimationEffect.ScaleKeyframes, progress: Float): Float {
        val rightIndex = effect.keyframes.indexOfFirst { it.progress >= progress }.coerceAtLeast(1)
        val left = effect.keyframes[rightIndex - 1]
        val right = effect.keyframes[rightIndex]
        val local = ((progress - left.progress) / (right.progress - left.progress)).coerceIn(0f, 1f)
        return lerp(left.scale, right.scale, local)
    }

    private fun renderAnimatedOverlay(argb: Int, x: Float, y: Float, size: PresentationNodeSize, alpha: Float, matrices: MatrixStack, consumers: VertexConsumerProvider) {
        renderContainer(PresentationContainerStyle(backgroundArgb = argb), x, y, size, alpha, matrices, consumers)
    }

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

    private fun pulse(progress: Float): Float = 1f - kotlin.math.abs(progress * 2f - 1f)

    private fun lerp(from: Float, to: Float, progress: Float): Float = from + (to - from) * progress

    private fun renderRankingPortrait(
        player: WinSettlementRankingSnapshot,
        x: Float,
        y: Float,
        alpha: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) = renderPlayerFace(player.playerId, player.isAi, x, y, alpha, matrices, consumers)

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

    private fun renderRankingPhase(entity: WinSettlementPresentationEntity, local: Double, matrices: MatrixStack, consumers: VertexConsumerProvider) {
        val alpha = WorldPanelRenderer.phaseAlpha(local, 0.0, 12.0, WinSettlementPresentationEntity.RANKING_TICKS.toDouble(), (WinSettlementPresentationEntity.RANKING_TICKS + WinSettlementPresentationEntity.FADE_OUT_TICKS).toDouble())
        if (alpha <= MIN_VISIBLE_ALPHA) return
        val layout = measureRankingLayout(entity.rankings)
        val rankRightX = layout[SettlementRankingColumnId.RANK].right
        val faceLeftX = layout[SettlementRankingColumnId.FACE].left
        val nameLeftX = layout[SettlementRankingColumnId.NAME].left
        val scoreRightX = layout[SettlementRankingColumnId.SCORE].right
        val deltaRightX = layout[SettlementRankingColumnId.DELTA].right
        val panelBottom = -20f + entity.rankings.size * 16f + 8f
        renderPanel(layout.panelHalfWidth, -55f, panelBottom, alpha, matrices, consumers)
        draw(Text.translatable(WinSettlementTranslationKeys.SCORE_RANKING), 0f, -42f, Align.CENTER, color(0xFFD45A, alpha), 1.25f, matrices, consumers)
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
            matrices.pop()
        }
    }

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

    private fun mixRgb(from: Int, to: Int, progress: Float): Int {
        val amount = progress.coerceIn(0f, 1f)
        fun channel(shift: Int): Int = (((from shr shift) and 0xFF) + ((((to shr shift) and 0xFF) - ((from shr shift) and 0xFF)) * amount)).roundToInt()
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /** 依所有起訖分數與增減的實際像素寬度建立排名欄位。 */
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
        return SettlementRankingLayout.arrange(
            columns = SettlementRankingLayout.standardColumns(
                faceSize = PresentationLayoutSolver.FACE_SIZE,
                scoreWidth = scoreWidth,
                deltaWidth = deltaWidth,
            ),
            panelPadding = PANEL_PADDING,
        )
    }

    private fun fitPlayerName(name: String): String = WorldPanelRenderer.fitText(textRenderer, name, SettlementRankingLayout.NAME_MAX_WIDTH)

    private fun WinSettlementDetailSnapshot.text(): Text = Text.translatable(values.firstOrNull().orEmpty(), *values.drop(1).toTypedArray())
    private fun WinSettlementRankingSnapshot.toRankingPlayer() = ScoreRankingPlayer(Uuid.parse(playerId), seatIndex, isAi, previousScore, currentScore, previousRank, currentRank)
    private fun playerName(id: String): String = playerNames.resolve(currentTableId, id)

    private fun renderPanel(halfWidth: Float, top: Float, bottom: Float, alpha: Float, matrices: MatrixStack, consumers: VertexConsumerProvider) {
        val matrix = matrices.peek().positionMatrix
        val buffer = consumers.getBuffer(ExhaustiveDrawSettlementPanelRenderLayer.layer)
        val a = (alpha * 0.78f * 255).roundToInt()
        buffer.vertex(matrix, -halfWidth, top, 0.5f).color(0, 0, 0, a).next()
        buffer.vertex(matrix, halfWidth, top, 0.5f).color(0, 0, 0, a).next()
        buffer.vertex(matrix, halfWidth, bottom, 0.5f).color(0, 0, 0, a).next()
        buffer.vertex(matrix, -halfWidth, bottom, 0.5f).color(0, 0, 0, a).next()
    }

    private fun renderTile(asset: String, cx: Float, cy: Float, width: Float, height: Float, alpha: Float, matrices: MatrixStack, consumers: VertexConsumerProvider) {
        tileFaceRenderer.renderWorldPanel(asset, cx, cy, width, height, alpha, 0f, matrices, consumers)
    }

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

    private fun color(rgb: Int, alpha: Float) = WorldPanelRenderer.withAlpha(rgb, alpha)
    override fun getTexture(entity: WinSettlementPresentationEntity): Identifier? = null

    private enum class Align { LEFT, CENTER, RIGHT }

    private companion object {
        const val SCALE = 0.02f
        const val PANEL_PADDING = 12f
        const val TILE_BACK_ASSET_KEY = "back"
        const val MIN_VISIBLE_ALPHA = 0.02f
        const val RANKING_SWEEP_HALF_WIDTH = 18f
        const val RANKING_HIGHLIGHT_Z = 0.25f
    }
}
