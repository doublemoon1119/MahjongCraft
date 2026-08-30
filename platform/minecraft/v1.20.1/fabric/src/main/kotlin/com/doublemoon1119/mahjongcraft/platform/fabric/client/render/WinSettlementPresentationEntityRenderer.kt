package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingAnimation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementTranslationKeys
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementDetailSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementRankingSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinSettlementWinnerSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExtensionPresentationField
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationAlignment
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationAnimationEffect
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationArrangement
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationContainerStyle
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationFieldId
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationTimelineAnchor
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationFieldSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileTextureAssetPath
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

/** 逐位贏家詳情與最終共用排行的 client-only billboard renderer。 */
class WinSettlementPresentationEntityRenderer(
    context: EntityRendererFactory.Context,
    private val templateRegistry: WinSettlementPresentationTemplateRegistry,
    private val portraitRenderer: PlayerPortraitRenderer,
) : EntityRenderer<WinSettlementPresentationEntity>(context) {
    private val textRenderer = context.textRenderer
    private val tileTextures = mutableMapOf<String, Identifier>()

    override fun render(
        entity: WinSettlementPresentationEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
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
        val alpha = phaseAlpha(local, 0.0, 12.0, duration - 12.0, duration)
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
        val size = measure(root, snapshot)
        val scale = minOf(1f, DECLARATIVE_MAX_WIDTH / size.width.coerceAtLeast(1f), DECLARATIVE_MAX_HEIGHT / size.height.coerceAtLeast(1f))
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

    private fun resolve(layout: PresentationLayout, snapshot: WinSettlementPresentationFieldSnapshot): PresentationValue? {
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

    private fun measure(layout: PresentationLayout, snapshot: WinSettlementPresentationFieldSnapshot): NodeSize = when (layout) {
        is PresentationLayout.Text -> (resolve(layout, snapshot) as? PresentationValue.TextValue)?.let {
            NodeSize(textRenderer.getWidth(Text.translatable(it.translationKey, *it.arguments.toTypedArray())) * layout.scale, 10f * layout.scale)
        } ?: NodeSize.ZERO
        is PresentationLayout.PlayerIdentity -> (resolve(layout, snapshot) as? PresentationValue.PlayerIdentityValue)?.let {
            val faceWidth = if (layout.showFace) FACE_SIZE * layout.scale else 0f
            val nameWidth = if (layout.showName) textRenderer.getWidth(it.displayName) * layout.scale else 0f
            val gap = if (layout.showFace && layout.showName) layout.spacing * layout.scale else 0f
            NodeSize(faceWidth + gap + nameWidth, maxOf(faceWidth, 10f * layout.scale))
        } ?: NodeSize.ZERO
        is PresentationLayout.Tile -> if (resolve(layout, snapshot) is PresentationValue.TileValue) NodeSize(layout.width, layout.height) else NodeSize.ZERO
        is PresentationLayout.TileList -> {
            val count = (resolve(layout, snapshot) as? PresentationValue.TileListValue)?.assetKeys?.size ?: 0
            NodeSize(tileSequenceWidth(count, layout.tileWidth, layout.spacing), if (count > 0) layout.tileHeight else 0f)
        }
        is PresentationLayout.TileGroups -> {
            val groups = (resolve(layout, snapshot) as? PresentationValue.TileGroupsValue)?.groups.orEmpty().filter(List<String>::isNotEmpty)
            val count = groups.sumOf(List<String>::size)
            NodeSize(
                tileSequenceWidth(count, layout.tileWidth, layout.tileSpacing) +
                    (groups.size - 1).coerceAtLeast(0) * (layout.groupSpacing - layout.tileSpacing),
                if (count > 0) layout.tileHeight else 0f,
            )
        }
        is PresentationLayout.RepeatEntries -> {
            val count = (resolve(layout, snapshot) as? PresentationValue.EntryListValue)?.entries?.size ?: 0
            val columns = (count + layout.entriesPerColumn - 1) / layout.entriesPerColumn
            NodeSize(if (count > 0) layout.width else 0f, minOf(count, layout.entriesPerColumn) * layout.rowHeight)
        }
        is PresentationLayout.Row -> {
            val sizes = layout.children.map { measure(it, snapshot) }
            val intrinsicWidth = sizes.sumOf { it.width.toDouble() }.toFloat() + (sizes.size - 1).coerceAtLeast(0) * layout.spacing
            styledSize(if (layout.fillMaxWidth) DECLARATIVE_MAX_WIDTH - layout.style.padding * 2f else intrinsicWidth, sizes.maxOfOrNull(NodeSize::height) ?: 0f, layout.style)
        }
        is PresentationLayout.Column -> {
            val sizes = layout.children.map { measure(it, snapshot) }
            val intrinsicHeight = sizes.sumOf { it.height.toDouble() }.toFloat() + (sizes.size - 1).coerceAtLeast(0) * layout.spacing
            styledSize(
                sizes.maxOfOrNull(NodeSize::width) ?: 0f,
                if (layout.fillMaxHeight) DECLARATIVE_MAX_HEIGHT - layout.style.padding * 2f else intrinsicHeight,
                layout.style,
            )
        }
        is PresentationLayout.Weighted -> measure(layout.child, snapshot)
        is PresentationLayout.Grid -> {
            val sizes = layout.children.map { measure(it, snapshot) }
            val rows = (sizes.size + layout.columns - 1) / layout.columns
            val cellWidth = sizes.maxOfOrNull(NodeSize::width) ?: 0f
            val cellHeight = sizes.maxOfOrNull(NodeSize::height) ?: 0f
            styledSize(layout.columns * cellWidth + (layout.columns - 1) * layout.horizontalSpacing, rows * cellHeight + (rows - 1).coerceAtLeast(0) * layout.verticalSpacing, layout.style)
        }
        is PresentationLayout.Spacer -> NodeSize(layout.width, layout.height)
        is PresentationLayout.SizeConstraint -> measure(layout.child, snapshot).let { NodeSize(minOf(it.width, layout.maxWidth ?: it.width), minOf(it.height, layout.maxHeight ?: it.height)) }
        is PresentationLayout.Box -> styledSize(layout.width, layout.height, layout.style)
        is PresentationLayout.Positioned -> measure(layout.child, snapshot)
        is PresentationLayout.IfPresent -> if (templateRegistry.findFieldProvider(layout.fieldId)?.provide(snapshot) != null) measure(layout.child, snapshot) else NodeSize.ZERO
        is PresentationLayout.Animated -> measure(layout.child, snapshot)
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
        val measured = measure(layout, snapshot)
        val size = if (allocatedWidth != null) NodeSize(allocatedWidth, measured.height) else measured
        when (layout) {
            is PresentationLayout.Text -> (resolve(layout, snapshot) as? PresentationValue.TextValue)?.let {
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
            is PresentationLayout.PlayerIdentity -> (resolve(layout, snapshot) as? PresentationValue.PlayerIdentityValue)?.let { identity ->
                var cursor = x
                if (layout.showFace) {
                    renderPlayerFace(identity.playerId, identity.isAi, cursor, y, alpha, matrices, consumers, FACE_SIZE * layout.scale)
                    cursor += FACE_SIZE * layout.scale + if (layout.showName) layout.spacing * layout.scale else 0f
                }
                if (layout.showName) {
                    draw(Text.literal(identity.displayName), cursor, y, Align.LEFT, color(layout.argb and 0xFFFFFF, alpha * ((layout.argb ushr 24) / 255f)), layout.scale, matrices, consumers)
                }
            }
            is PresentationLayout.Tile -> (resolve(layout, snapshot) as? PresentationValue.TileValue)?.let {
                renderTile(it.assetKey, x + layout.width / 2f, y + layout.height / 2f, layout.width, layout.height, alpha, matrices, consumers)
            }
            is PresentationLayout.TileList -> (resolve(layout, snapshot) as? PresentationValue.TileListValue)?.assetKeys.orEmpty().forEachIndexed { index, asset ->
                renderTile(asset, x + layout.tileWidth / 2f + index * (layout.tileWidth + layout.spacing), y + layout.tileHeight / 2f, layout.tileWidth, layout.tileHeight, alpha, matrices, consumers)
            }
            is PresentationLayout.TileGroups -> {
                var tileX = x + layout.tileWidth / 2f
                val groups = (resolve(layout, snapshot) as? PresentationValue.TileGroupsValue)?.groups.orEmpty().filter(List<String>::isNotEmpty)
                groups.forEachIndexed { groupIndex, group ->
                    group.forEach { asset ->
                        renderTile(asset, tileX, y + layout.tileHeight / 2f, layout.tileWidth, layout.tileHeight, alpha, matrices, consumers)
                        tileX += layout.tileWidth + layout.tileSpacing
                    }
                    if (groupIndex != groups.lastIndex) tileX += layout.groupSpacing - layout.tileSpacing
                }
            }
            is PresentationLayout.RepeatEntries -> (resolve(layout, snapshot) as? PresentationValue.EntryListValue)?.entries.orEmpty().forEachIndexed { index, entry ->
                val reveal = ((local - snapshot.initialFadeTicks - index * snapshot.entryStaggerTicks) / 6.0).coerceIn(0.0, 1.0).toFloat()
                if (reveal <= MIN_VISIBLE_ALPHA) return@forEachIndexed
                val entryScale = 0.85f
                val emphasisScale = if (entry.trailingTranslationKey == null) 1f else lerp(1.08f, 1f, reveal)
                val count = (resolve(layout, snapshot) as? PresentationValue.EntryListValue)?.entries?.size ?: 0
                val columns = ((count + layout.entriesPerColumn - 1) / layout.entriesPerColumn).coerceAtLeast(1)
                val columnWidth = layout.width / columns
                val column = index / layout.entriesPerColumn
                val row = index % layout.entriesPerColumn
                val rowX = x + column * columnWidth
                val rowY = y + row * layout.rowHeight
                val title = Text.translatable(entry.translationKey)
                val titleScale = fittedTextScale(title, columnWidth - 44f, entryScale)
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
                val slots = allocateMainAxis(layout.children, contentWidth, layout.spacing, snapshot)
                val positions = arrange(slots.map { it.second }, contentWidth, layout.spacing, layout.arrangement)
                layout.children.forEachIndexed { index, child ->
                    val childHeight = measure(child.unweighted(), snapshot).height
                    val childY = y + layout.style.padding + crossAxisOffset(size.height - layout.style.padding * 2f, childHeight, layout.alignment)
                    renderLayout(child.unweighted(), snapshot, x + layout.style.padding + positions[index], childY, local, alpha, matrices, consumers, slots[index].second)
                }
            }
            is PresentationLayout.Column -> {
                renderContainer(layout.style, x, y, size, alpha, matrices, consumers)
                val contentHeight = size.height - layout.style.padding * 2f
                val heights = allocateVerticalMainAxis(layout.children, contentHeight, layout.spacing, snapshot)
                val positions = arrange(heights, contentHeight, layout.spacing, layout.arrangement)
                layout.children.forEachIndexed { index, child ->
                    val childLayout = child.unweighted()
                    val childWidth = measure(childLayout, snapshot).width
                    val childX = x + layout.style.padding + crossAxisOffset(size.width - layout.style.padding * 2f, childWidth, layout.alignment)
                    renderLayout(childLayout, snapshot, childX, y + layout.style.padding + positions[index], local, alpha, matrices, consumers)
                }
            }
            is PresentationLayout.Weighted -> renderLayout(layout.child, snapshot, x, y, local, alpha, matrices, consumers)
            is PresentationLayout.Grid -> {
                renderContainer(layout.style, x, y, size, alpha, matrices, consumers)
                val childSizes = layout.children.map { measure(it, snapshot) }
                val cellWidth = childSizes.maxOfOrNull(NodeSize::width) ?: 0f
                val cellHeight = childSizes.maxOfOrNull(NodeSize::height) ?: 0f
                layout.children.forEachIndexed { index, child ->
                    renderLayout(child, snapshot, x + layout.style.padding + (index % layout.columns) * (cellWidth + layout.horizontalSpacing), y + layout.style.padding + (index / layout.columns) * (cellHeight + layout.verticalSpacing), local, alpha, matrices, consumers)
                }
            }
            is PresentationLayout.Spacer -> Unit
            is PresentationLayout.SizeConstraint -> renderLayout(layout.child, snapshot, x, y, local, alpha, matrices, consumers)
            is PresentationLayout.Box -> {
                renderContainer(layout.style, x, y, size, alpha, matrices, consumers)
                layout.children.forEach { positioned ->
                    val childSize = measure(positioned.child, snapshot)
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
        val size = measure(layout.child, snapshot).let { if (allocatedWidth == null) it else NodeSize(allocatedWidth, it.height) }
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

    private fun renderAnimatedOverlay(argb: Int, x: Float, y: Float, size: NodeSize, alpha: Float, matrices: MatrixStack, consumers: VertexConsumerProvider) {
        renderContainer(PresentationContainerStyle(backgroundArgb = argb), x, y, size, alpha, matrices, consumers)
    }

    private fun renderHighlightSweep(
        effect: PresentationAnimationEffect.HighlightSweep,
        x: Float,
        y: Float,
        size: NodeSize,
        progress: Float,
        alpha: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val left = x - effect.width + (size.width + effect.width * 2f) * progress
        renderAnimatedOverlay(effect.argb, left, y, NodeSize(effect.width, size.height), alpha * pulse(progress), matrices, consumers)
    }

    private fun pulse(progress: Float): Float = 1f - kotlin.math.abs(progress * 2f - 1f)

    private fun lerp(from: Float, to: Float, progress: Float): Float = from + (to - from) * progress

    private fun styledSize(width: Float, height: Float, style: PresentationContainerStyle) = NodeSize(width + style.padding * 2f, height + style.padding * 2f)

    private fun tileSequenceWidth(count: Int, tileWidth: Float, gap: Float): Float = count * tileWidth + (count - 1).coerceAtLeast(0) * gap

    private fun anchorOffset(size: Float, alignment: PresentationAlignment): Float = when (alignment) {
        PresentationAlignment.START -> 0f
        PresentationAlignment.CENTER -> size / 2f
        PresentationAlignment.END -> size
    }

    private fun crossAxisOffset(available: Float, childSize: Float, alignment: PresentationAlignment): Float = when (alignment) {
        PresentationAlignment.START -> 0f
        PresentationAlignment.CENTER -> (available - childSize).coerceAtLeast(0f) / 2f
        PresentationAlignment.END -> (available - childSize).coerceAtLeast(0f)
    }

    private fun PresentationLayout.unweighted(): PresentationLayout = (this as? PresentationLayout.Weighted)?.child ?: this

    private fun allocateMainAxis(
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

    private fun arrange(widths: List<Float>, available: Float, spacing: Float, arrangement: PresentationArrangement): List<Float> {
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

    private fun allocateVerticalMainAxis(
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

    private fun renderWinnerSummary(
        entity: WinSettlementPresentationEntity,
        winner: WinSettlementWinnerSnapshot,
        singlePlayer: Boolean,
        y: Float,
        alpha: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val winnerName = Text.literal(playerName(winner.playerId))
        val winnerWidth = FACE_SIZE + FACE_GAP + textRenderer.getWidth(winnerName)
        val responsibleId = winner.responsiblePlayerId.takeUnless { singlePlayer }
        val responsibleName = responsibleId?.let { Text.literal(playerName(it)) }
        val arrow = Text.literal("←")
        val relationshipWidth = responsibleName?.let {
            SUMMARY_RELATION_GAP + textRenderer.getWidth(arrow) + SUMMARY_RELATION_GAP +
                FACE_SIZE + FACE_GAP + textRenderer.getWidth(it)
        } ?: 0f
        var left = -(winnerWidth + relationshipWidth) / 2f
        renderPlayerFace(winner.playerId, winner.isAi, left, y - 1f, alpha, matrices, consumers)
        left += FACE_SIZE + FACE_GAP
        draw(winnerName, left, y, Align.LEFT, color(0xFFFFFF, alpha), 1f, matrices, consumers)
        left += textRenderer.getWidth(winnerName)
        if (responsibleId != null && responsibleName != null) {
            left += SUMMARY_RELATION_GAP
            draw(arrow, left, y, Align.LEFT, color(0xE5C16A, alpha), 1f, matrices, consumers)
            left += textRenderer.getWidth(arrow) + SUMMARY_RELATION_GAP
            val responsibleIsAi = entity.rankings.firstOrNull { it.playerId == responsibleId }?.isAi ?: false
            renderPlayerFace(responsibleId, responsibleIsAi, left, y - 1f, alpha, matrices, consumers)
            left += FACE_SIZE + FACE_GAP
            draw(responsibleName, left, y, Align.LEFT, color(0xFFFFFF, alpha), 1f, matrices, consumers)
        }
    }

    /** 以目前語言的實際字寬決定面板寬度；超長翻譯擴張背景，不讓文字覆蓋牌面。 */
    private fun measureWinnerPanelHalfWidth(
        entity: WinSettlementPresentationEntity,
        winner: WinSettlementWinnerSnapshot,
        titleKey: String,
        nagashi: Boolean,
    ): Float {
        val titleWidth = textRenderer.getWidth(Text.translatable(titleKey)) * 1.35f
        val winnerNameWidth = textRenderer.getWidth(playerName(winner.playerId))
        val summaryWidth = if (entity.isTsumo || nagashi || winner.responsiblePlayerId == null) {
            FACE_SIZE + FACE_GAP + winnerNameWidth
        } else {
            FACE_SIZE + FACE_GAP + winnerNameWidth + SUMMARY_RELATION_GAP * 2f + textRenderer.getWidth("←") +
                FACE_SIZE + FACE_GAP + textRenderer.getWidth(playerName(winner.responsiblePlayerId))
        }
        val indicatorWidth = if (nagashi) {
            0f
        } else {
            val tileSlotsWidth = INDICATOR_SLOT_COUNT * INDICATOR_TILE_WIDTH +
                (INDICATOR_SLOT_COUNT - 1) * INDICATOR_TILE_GAP
            val labelWidths = listOf(WinSettlementTranslationKeys.DORA, WinSettlementTranslationKeys.URA_DORA)
                .sumOf { textRenderer.getWidth(Text.translatable(it)).toDouble() }.toFloat() * 0.82f
            labelWidths + tileSlotsWidth * 2f + INDICATOR_LABEL_GAP * 2f + INDICATOR_MINIMUM_SPACE * 3f
        }
        val contentWidth = maxOf(titleWidth, summaryWidth, indicatorWidth, ENTRY_AREA_WIDTH)
        return maxOf(PANEL_HALF_WIDTH, contentWidth / 2f + PANEL_PADDING)
    }

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
        size: Float = FACE_SIZE,
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

    private fun renderContainer(style: PresentationContainerStyle, x: Float, y: Float, size: NodeSize, alpha: Float, matrices: MatrixStack, consumers: VertexConsumerProvider) {
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

    private fun renderHand(winner: WinSettlementWinnerSnapshot, y: Float, alpha: Float, matrices: MatrixStack, consumers: VertexConsumerProvider) {
        val groups = buildList {
            add(winner.handAssetKeys.map { TileRenderSnapshot(it, false) })
            addAll(winner.melds.map { meld -> meld.assetKeys.mapIndexed { index, asset -> TileRenderSnapshot(asset, index in meld.faceDownIndices) } })
            add(listOf(TileRenderSnapshot(winner.winningTileAssetKey, false)))
        }.filter { it.isNotEmpty() }
        val tileCount = groups.sumOf(List<TileRenderSnapshot>::size)
        val gaps = (tileCount - 1).coerceAtLeast(0) * TILE_GAP + (groups.size - 1).coerceAtLeast(0) * GROUP_GAP
        val total = tileCount * TILE_WIDTH + gaps
        var x = -total / 2f + TILE_WIDTH / 2f
        groups.forEachIndexed { groupIndex, group ->
            group.forEach { tile ->
                renderTile(if (tile.faceDown) TILE_BACK_ASSET_KEY else tile.assetKey, x, y, TILE_WIDTH, TILE_HEIGHT, alpha, matrices, consumers)
                x += TILE_WIDTH + TILE_GAP
            }
            if (groupIndex != groups.lastIndex) x += GROUP_GAP
        }
    }

    private fun renderIndicatorRow(
        label: Text,
        detail: WinSettlementDetailSnapshot?,
        x: Float,
        y: Float,
        alpha: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val labelWidth = textRenderer.getWidth(label) * 0.82f
        draw(label, x, y, Align.LEFT, color(0xE5C16A, alpha), 0.82f, matrices, consumers)
        var tileX = x + labelWidth + INDICATOR_LABEL_GAP + INDICATOR_TILE_WIDTH / 2f
        val assets = detail?.values.orEmpty().take(INDICATOR_SLOT_COUNT) + List((INDICATOR_SLOT_COUNT - detail?.values.orEmpty().size).coerceAtLeast(0)) { TILE_BACK_ASSET_KEY }
        assets.forEach { asset ->
            renderTile(asset, tileX, y + 2f, INDICATOR_TILE_WIDTH, 11f, alpha, matrices, consumers)
            tileX += INDICATOR_TILE_WIDTH + INDICATOR_TILE_GAP
        }
    }

    private fun indicatorContentWidth(label: Text): Float {
        val labelWidth = textRenderer.getWidth(label) * 0.82f
        val tilesWidth = INDICATOR_SLOT_COUNT * INDICATOR_TILE_WIDTH + (INDICATOR_SLOT_COUNT - 1) * INDICATOR_TILE_GAP
        return labelWidth + INDICATOR_LABEL_GAP + tilesWidth
    }

    private fun renderEntries(detail: WinSettlementDetailSnapshot?, local: Double, panelAlpha: Float, matrices: MatrixStack, consumers: VertexConsumerProvider, startY: Float) {
        val pairs = detail?.values.orEmpty().chunked(WinSettlementPresentationEntity.ENTRY_VALUE_COUNT)
            .filter { it.size == WinSettlementPresentationEntity.ENTRY_VALUE_COUNT }
        val columnCount = ((pairs.size + 3) / 4).coerceAtLeast(1)
        val columnWidth = ENTRY_AREA_WIDTH / columnCount
        pairs.forEachIndexed { index, pair ->
            val reveal = ((local - WinSettlementPresentationEntity.INITIAL_FADE_TICKS - index * WinSettlementPresentationEntity.ENTRY_STAGGER_TICKS) / 6.0).coerceIn(0.0, 1.0).toFloat()
            if (reveal <= 0f) return@forEachIndexed
            val column = index / 4
            val row = index % 4
            val x = -ENTRY_AREA_WIDTH / 2f + column * columnWidth
            val y = startY + row * 11f
            val trailing = pair[2].takeIf(String::isNotBlank)?.let { key ->
                pair[3].takeIf(String::isNotBlank)?.let { Text.translatable(key, it) } ?: Text.translatable(key)
            } ?: pair[1].takeIf(String::isNotBlank)?.let { Text.translatable(WinSettlementTranslationKeys.HAN, it) }
            val trailingWidth = trailing?.let(textRenderer::getWidth)?.times(0.85f) ?: 0f
            drawFitted(
                Text.translatable(pair[0]),
                x,
                y,
                columnWidth - trailingWidth - ENTRY_TEXT_GAP - ENTRY_COLUMN_PADDING,
                color(0xFFFFFF, panelAlpha * reveal),
                0.85f,
                matrices,
                consumers,
            )
            if (trailing != null) {
                draw(requireNotNull(trailing), x + columnWidth - ENTRY_COLUMN_PADDING, y, Align.RIGHT, color(0xFFE08A, panelAlpha * reveal), 0.85f, matrices, consumers)
            }
        }
    }

    private fun renderRankingPhase(entity: WinSettlementPresentationEntity, local: Double, matrices: MatrixStack, consumers: VertexConsumerProvider) {
        val alpha = phaseAlpha(local, 0.0, 12.0, WinSettlementPresentationEntity.RANKING_TICKS.toDouble(), (WinSettlementPresentationEntity.RANKING_TICKS + WinSettlementPresentationEntity.FADE_OUT_TICKS).toDouble())
        if (alpha <= MIN_VISIBLE_ALPHA) return
        val layout = measureRankingLayout(entity.rankings)
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
            matrices.translate(layout.rankRightX, 0f, 0f)
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
            renderRankingPortrait(snapshot, layout.faceLeftX, -1f, rowAlpha, matrices, consumers)
            draw(Text.literal(fitPlayerName(playerName(row.player.playerId.toString()))), layout.nameLeftX, 0f, Align.LEFT, color(0xFFFFFF, rowAlpha), 1f, matrices, consumers)
            draw(Text.literal(row.score.toString()), layout.scoreRightX, 0f, Align.RIGHT, color(0xFFF3C4, rowAlpha), 1f, matrices, consumers)
            draw(Text.literal(formatDelta(row.delta)), layout.deltaRightX, 0f, Align.RIGHT, color(if (row.delta >= 0) 0x80FF80 else 0xFF8080, rowAlpha), 1f, matrices, consumers)
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

    private fun measureRankingLayout(players: List<WinSettlementRankingSnapshot>): RankingLayout {
        val scoreWidth = maxOf(
            MIN_SCORE_COLUMN_WIDTH,
            players.maxOfOrNull { maxOf(textRenderer.getWidth(it.previousScore.toString()), textRenderer.getWidth(it.currentScore.toString())) }
                ?.plus(NUMERIC_COLUMN_PADDING * 2) ?: 0,
        ).toFloat()
        val deltaWidth = maxOf(
            MIN_DELTA_COLUMN_WIDTH,
            players.maxOfOrNull { textRenderer.getWidth(formatDelta(it.currentScore - it.previousScore)) }
                ?.plus(NUMERIC_COLUMN_PADDING * 2) ?: 0,
        ).toFloat()
        val totalWidth = PANEL_PADDING * 2 + RANK_COLUMN_WIDTH + COLUMN_GAP + FACE_SIZE + COLUMN_GAP +
            NAME_MAX_WIDTH + SECTION_GAP + scoreWidth + SECTION_GAP + deltaWidth
        var cursor = -totalWidth / 2f + PANEL_PADDING
        val rankRightX = cursor + RANK_COLUMN_WIDTH
        cursor = rankRightX + COLUMN_GAP
        val faceLeftX = cursor
        cursor += FACE_SIZE + COLUMN_GAP
        val nameLeftX = cursor
        cursor += NAME_MAX_WIDTH + SECTION_GAP
        val scoreRightX = cursor + scoreWidth
        cursor = scoreRightX + SECTION_GAP
        return RankingLayout(totalWidth / 2f, rankRightX, faceLeftX, nameLeftX, scoreRightX, cursor + deltaWidth)
    }

    private fun fitPlayerName(name: String): String {
        if (textRenderer.getWidth(name) <= NAME_MAX_WIDTH) return name
        val suffix = "..."
        return textRenderer.trimToWidth(name, NAME_MAX_WIDTH - textRenderer.getWidth(suffix)) + suffix
    }

    private fun WinSettlementDetailSnapshot.text(): Text = Text.translatable(values.firstOrNull().orEmpty(), *values.drop(1).toTypedArray())
    private fun WinSettlementRankingSnapshot.toRankingPlayer() = ScoreRankingPlayer(Uuid.parse(playerId), seatIndex, isAi, previousScore, currentScore, previousRank, currentRank)
    private fun playerName(id: String): String = runCatching { java.util.UUID.fromString(id) }.getOrNull()?.let { uuid ->
        MinecraftClient.getInstance().networkHandler?.getPlayerListEntry(uuid)?.profile?.name
    } ?: id.take(8)

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
        val texture = tileTextures.getOrPut(asset) {
            val requested = Identifier(MinecraftModMetadata.MOD_ID, tileTextureAssetPath(asset))
            if (MinecraftClient.getInstance().resourceManager.getResource(requested).isPresent) {
                requested
            } else {
                Identifier(MinecraftModMetadata.MOD_ID, tileTextureAssetPath(UNKNOWN_TILE_ASSET_KEY))
            }
        }
        val buffer = consumers.getBuffer(ExhaustiveDrawSettlementTileFaceRenderLayer.get(texture))
        val matrix = matrices.peek().positionMatrix
        val a = (alpha * 255).roundToInt()
        fun vertex(x: Float, y: Float, u: Float, v: Float) = buffer.vertex(matrix, x, y, 0f).color(255, 255, 255, a).texture(u, v).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).next()
        vertex(cx - width / 2f, cy - height / 2f, 0f, 0f)
        vertex(cx + width / 2f, cy - height / 2f, 1f, 0f)
        vertex(cx + width / 2f, cy + height / 2f, 1f, 1f)
        vertex(cx - width / 2f, cy + height / 2f, 0f, 1f)
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

    private fun drawFitted(
        text: Text,
        x: Float,
        y: Float,
        maxWidth: Float,
        color: Int,
        preferredScale: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        draw(text, x, y, Align.LEFT, color, fittedTextScale(text, maxWidth, preferredScale), matrices, consumers)
    }

    private fun fittedTextScale(text: Text, maxWidth: Float, preferredScale: Float): Float {
        val naturalWidth = textRenderer.getWidth(text).toFloat().coerceAtLeast(1f)
        return minOf(preferredScale, maxWidth.coerceAtLeast(1f) / naturalWidth)
    }

    private fun phaseAlpha(value: Double, inStart: Double, inEnd: Double, outStart: Double, outEnd: Double): Float = when {
        value < inStart || value >= outEnd -> 0f
        value < inEnd -> ((value - inStart) / (inEnd - inStart)).toFloat()
        value > outStart -> ((outEnd - value) / (outEnd - outStart)).toFloat()
        else -> 1f
    }
    private fun color(rgb: Int, alpha: Float) = ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (rgb and 0xFFFFFF)
    private fun formatDelta(delta: Int) = if (delta > 0) {
        "+$delta"
    } else if (delta < 0) {
        delta.toString()
    } else {
        "±0"
    }
    override fun getTexture(entity: WinSettlementPresentationEntity): Identifier? = null

    private enum class Align { LEFT, CENTER, RIGHT }
    private data class NodeSize(val width: Float, val height: Float) {
        companion object {
            val ZERO = NodeSize(0f, 0f)
        }
    }

    private data class TileRenderSnapshot(val assetKey: String, val faceDown: Boolean)

    private data class RankingLayout(
        val panelHalfWidth: Float,
        val rankRightX: Float,
        val faceLeftX: Float,
        val nameLeftX: Float,
        val scoreRightX: Float,
        val deltaRightX: Float,
    )

    private companion object {
        const val SCALE = 0.02f
        const val PANEL_HALF_WIDTH = 160f
        const val PANEL_TOP = -78f
        const val PANEL_BOTTOM = 78f
        const val TILE_WIDTH = 11f
        const val TILE_HEIGHT = 15f
        const val TILE_GAP = 1.2f
        const val GROUP_GAP = 5f
        const val DECLARATIVE_TILE_WIDTH = 11f
        const val DECLARATIVE_TILE_HEIGHT = 15f
        const val DECLARATIVE_GROUP_GAP = 5f
        const val DECLARATIVE_MAX_WIDTH = 320f
        const val DECLARATIVE_MAX_HEIGHT = 156f
        const val ENTRY_COLUMN_WIDTH = 118f
        const val ENTRY_ROW_HEIGHT = 11f
        const val ENTRY_AREA_WIDTH = 232f
        const val ENTRY_COLUMN_PADDING = 6f
        const val ENTRY_TEXT_GAP = 5f
        const val FACE_SIZE = 10f
        const val FACE_GAP = 5f
        const val INDICATOR_SLOT_COUNT = 5
        const val INDICATOR_TILE_WIDTH = 8f
        const val INDICATOR_TILE_GAP = 2f
        const val INDICATOR_LABEL_GAP = 7f
        const val INDICATOR_MINIMUM_SPACE = 8f
        const val SUMMARY_RELATION_GAP = 8f
        const val PANEL_PADDING = 12f
        const val RANK_COLUMN_WIDTH = 12f
        const val COLUMN_GAP = 7f
        const val SECTION_GAP = 9f
        const val NAME_MAX_WIDTH = 96
        const val MIN_SCORE_COLUMN_WIDTH = 48
        const val MIN_DELTA_COLUMN_WIDTH = 56
        const val NUMERIC_COLUMN_PADDING = 6
        const val TILE_BACK_ASSET_KEY = "back"
        const val MIN_VISIBLE_ALPHA = 0.02f
        const val RANKING_SWEEP_HALF_WIDTH = 18f
        const val RANKING_HIGHLIGHT_Z = 0.25f
    }
}
