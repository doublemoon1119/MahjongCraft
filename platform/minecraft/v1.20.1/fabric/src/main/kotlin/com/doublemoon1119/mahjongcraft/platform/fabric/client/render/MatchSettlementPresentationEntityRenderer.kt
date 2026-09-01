package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY
import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerDisplayNameResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MatchSettlementPlayerSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MatchSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplate
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileTextureAssetPath
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import org.joml.Matrix4f
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt
import kotlin.uuid.Uuid

/** 與回合結算共用視覺語言，但以冠軍為主角的 client-only 終局頒獎面板。 */
class MatchSettlementPresentationEntityRenderer(
    context: EntityRendererFactory.Context,
    private val templates: MatchSettlementPresentationTemplateRegistry,
    private val portraitRenderer: PlayerPortraitRenderer,
    private val playerNames: ClientPlayerDisplayNameResolver,
) : EntityRenderer<MatchSettlementPresentationEntity>(context) {
    private val textRenderer = context.textRenderer
    private val warnedUnknownTemplateKeys = mutableSetOf<String>()
    private val unknownTileTexture = Identifier(MinecraftModMetadata.MOD_ID, tileTextureAssetPath(UNKNOWN_TILE_ASSET_KEY))

    override fun render(
        entity: MatchSettlementPresentationEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        val elapsed = entity.elapsedTicks(tickDelta)
        val duration = (entity.endGameTime - entity.startGameTime).toDouble()
        if (elapsed !in 0.0..duration) return
        val template = resolveTemplate(entity.templateKey) ?: return
        val alpha = panelAlpha(elapsed, duration)
        if (alpha <= 0f) return
        val layout = measureLayout(entity.players.size)
        val revealIndexByPlayer = entity.revealSequence().mapIndexed { index, player -> player.playerId to index }.toMap()

        matrices.push()
        matrices.translate(0.0, 0.55, 0.0)
        matrices.multiply(dispatcher.rotation)
        matrices.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE)
        renderPanel(layout, template, alpha, matrices, vertexConsumers)
        drawText(
            Text.translatable(template.titleTranslationKey),
            0f,
            TITLE_Y,
            Alignment.CENTER,
            withAlpha(template.titleRgb, alpha),
            matrices,
            vertexConsumers,
            TITLE_SCALE,
        )
        entity.players.sortedBy(MatchSettlementPlayerSnapshot::finalRank).forEach { player ->
            val revealIndex = revealIndexByPlayer.getValue(player.playerId)
            val progress = rowRevealProgress(elapsed, revealIndex, entity.rowRevealIntervalTicks)
            if (progress <= 0f) return@forEach
            renderPlacement(player, layout, template, alpha * progress, progress, matrices, vertexConsumers)
        }
        matrices.pop()
    }

    /** 在固定頒獎位置繪製玩家；第一名居中並使用較大的 FACE 與冠軍資訊框。 */
    private fun renderPlacement(
        player: MatchSettlementPlayerSnapshot,
        layout: Layout,
        template: MatchSettlementPresentationTemplate,
        alpha: Float,
        revealProgress: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val champion = player.finalRank == 1
        val placement = layout.placements.getValue(player.finalRank)
        val displayName = fitPlayerName(resolvePlayerName(player), placement.nameWidth)
        val rankText = Text.translatable(MinecraftMessageKeys.MATCH_SETTLEMENT_RANK, player.finalRank)
        if (champion) renderChampionHighlight(displayName, rankText, placement.y, alpha, revealProgress, matrices, consumers)
        val bounce = if (champion) championScale(revealProgress) else 1f
        matrices.push()
        matrices.translate(placement.x, placement.y - (1f - revealProgress) * PLACEMENT_REVEAL_OFFSET, 0f)
        matrices.scale(bounce, bounce, 1f)
        val faceSize = if (champion) CHAMPION_FACE_SIZE else PLACEMENT_FACE_SIZE
        val nameY = faceSize + 4f
        val rankY = nameY + 11f
        renderPortrait(player, -faceSize / 2f, 0f, faceSize, alpha, matrices, consumers)
        drawText(
            Text.literal(displayName),
            0f,
            nameY,
            Alignment.CENTER,
            withAlpha(if (champion) template.championRgb else NAME_RGB, alpha),
            matrices,
            consumers,
        )
        drawText(
            rankText,
            0f,
            rankY,
            Alignment.CENTER,
            withAlpha(if (champion) template.championRgb else template.rankRgb, alpha),
            matrices,
            consumers,
        )
        matrices.pop()
    }

    /** 第一名揭曉期間繪製向內收束的金色角標與短暫像素星點。 */
    private fun renderChampionHighlight(
        displayName: String,
        rankText: Text,
        y: Float,
        alpha: Float,
        progress: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val matrix = matrices.peek().positionMatrix
        val buffer = consumers.getBuffer(ExhaustiveDrawSettlementPanelRenderLayer.layer)
        val settle = 1f - (1f - progress) * (1f - progress)
        val offset = (1f - settle) * CORNER_ENTRY_OFFSET
        val contentWidth = maxOf(CHAMPION_FACE_SIZE, textRenderer.getWidth(displayName).toFloat(), textRenderer.getWidth(rankText).toFloat())
        val halfWidth = contentWidth / 2f + CHAMPION_FRAME_HORIZONTAL_PADDING
        val left = -halfWidth - offset
        val right = halfWidth + offset
        val top = y - CHAMPION_FRAME_VERTICAL_PADDING - offset
        val bottom = y + CHAMPION_CARD_HEIGHT + CHAMPION_FRAME_VERTICAL_PADDING + offset
        val a = (alpha * 210f).roundToInt().coerceIn(0, 255)
        renderCorner(buffer, matrix, left, top, 1f, 1f, a)
        renderCorner(buffer, matrix, right, top, -1f, 1f, a)
        renderCorner(buffer, matrix, left, bottom, 1f, -1f, a)
        renderCorner(buffer, matrix, right, bottom, -1f, -1f, a)

        val starAlpha = (alpha * kotlin.math.sin(progress * Math.PI).toFloat() * 220f).roundToInt().coerceIn(0, 255)
        if (starAlpha > 0) {
            val starOffsets = listOf(
                -halfWidth - STAR_DISTANCE to 7f,
                halfWidth + STAR_DISTANCE to 11f,
                -halfWidth - STAR_DISTANCE * 0.65f to 31f,
                halfWidth + STAR_DISTANCE * 0.65f to 35f,
            )
            starOffsets.forEach { (starX, starY) ->
                renderQuad(buffer, matrix, starX - 1f, y + starY - 1f, starX + 1f, y + starY + 1f, starAlpha)
            }
        }
    }

    /** 繪製一個指向完整冠軍資訊卡的 L 形像素角標。 */
    private fun renderCorner(buffer: VertexConsumer, matrix: Matrix4f, x: Float, y: Float, xDirection: Float, yDirection: Float, alpha: Int) {
        renderQuad(buffer, matrix, x, y, x + CORNER_LENGTH * xDirection, y + CORNER_THICKNESS * yDirection, alpha)
        renderQuad(buffer, matrix, x, y, x + CORNER_THICKNESS * xDirection, y + CORNER_LENGTH * yDirection, alpha)
    }

    /** 以金色繪製一個固定深度的面板矩形。 */
    private fun renderQuad(buffer: VertexConsumer, matrix: Matrix4f, x1: Float, y1: Float, x2: Float, y2: Float, alpha: Int) {
        val left = minOf(x1, x2)
        val right = maxOf(x1, x2)
        val top = minOf(y1, y2)
        val bottom = maxOf(y1, y2)
        buffer.vertex(matrix, left, top, HIGHLIGHT_Z).color(255, 211, 82, alpha).next()
        buffer.vertex(matrix, right, top, HIGHLIGHT_Z).color(255, 241, 170, alpha).next()
        buffer.vertex(matrix, right, bottom, HIGHLIGHT_Z).color(255, 241, 170, alpha).next()
        buffer.vertex(matrix, left, bottom, HIGHLIGHT_Z).color(255, 211, 82, alpha).next()
    }

    /** 繪製完整半透明背景板並正常寫入深度。 */
    private fun renderPanel(
        layout: Layout,
        template: MatchSettlementPresentationTemplate,
        alpha: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val matrix = matrices.peek().positionMatrix
        val buffer = consumers.getBuffer(ExhaustiveDrawSettlementPanelRenderLayer.layer)
        val color = template.backgroundArgb
        val a = ((((color ushr 24) and 0xFF) * alpha).roundToInt()).coerceIn(0, 255)
        val red = color ushr 16 and 0xFF
        val green = color ushr 8 and 0xFF
        val blue = color and 0xFF
        buffer.vertex(matrix, -layout.panelHalfWidth, PANEL_TOP, PANEL_Z).color(red, green, blue, a).next()
        buffer.vertex(matrix, layout.panelHalfWidth, PANEL_TOP, PANEL_Z).color(red, green, blue, a).next()
        buffer.vertex(matrix, layout.panelHalfWidth, layout.panelBottom, PANEL_Z).color(red, green, blue, a).next()
        buffer.vertex(matrix, -layout.panelHalfWidth, layout.panelBottom, PANEL_Z).color(red, green, blue, a).next()
    }

    /** 交由共用 renderer 解析第三方來源與真人／AI fallback。 */
    private fun renderPortrait(
        player: MatchSettlementPlayerSnapshot,
        x: Float,
        y: Float,
        size: Float,
        alpha: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) = portraitRenderer.render(
        playerId = Uuid.parse(player.playerId),
        isAi = player.isAi,
        x = x,
        y = y,
        size = size,
        alpha = alpha,
        z = FACE_Z,
        matrices = matrices,
        consumers = consumers,
    )

    /** 依玩家數建立冠軍居中、其餘名次分層的自適應頒獎位置。 */
    private fun measureLayout(playerCount: Int): Layout {
        val otherCount = (playerCount - 1).coerceAtLeast(0)
        val columnCount = otherCount.coerceIn(1, MAX_PLACEMENTS_PER_ROW)
        val rowCount = if (otherCount == 0) 0 else (otherCount + MAX_PLACEMENTS_PER_ROW - 1) / MAX_PLACEMENTS_PER_ROW
        val panelWidth = maxOf(MIN_PANEL_WIDTH, columnCount * PLACEMENT_CARD_WIDTH + PANEL_PADDING * 2)
        val placements = mutableMapOf(1 to Placement(0f, CHAMPION_Y, CHAMPION_NAME_WIDTH))
        for (rank in 2..playerCount) {
            val index = rank - 2
            val row = index / MAX_PLACEMENTS_PER_ROW
            val entriesInRow = minOf(MAX_PLACEMENTS_PER_ROW, otherCount - row * MAX_PLACEMENTS_PER_ROW)
            val column = index % MAX_PLACEMENTS_PER_ROW
            val rowWidth = entriesInRow * PLACEMENT_CARD_WIDTH
            val x = -rowWidth / 2f + PLACEMENT_CARD_WIDTH * (column + 0.5f)
            placements[rank] = Placement(x, PLACEMENT_FIRST_ROW_Y + row * PLACEMENT_ROW_HEIGHT, PLACEMENT_NAME_WIDTH)
        }
        val panelBottom = if (rowCount == 0) {
            CHAMPION_Y + CHAMPION_CARD_HEIGHT + PANEL_BOTTOM_PADDING
        } else {
            PLACEMENT_FIRST_ROW_Y + (rowCount - 1) * PLACEMENT_ROW_HEIGHT + PLACEMENT_CARD_HEIGHT + PANEL_BOTTOM_PADDING
        }
        return Layout(panelWidth / 2f, panelBottom, placements)
    }

    /** 解析模板；未知 key 去重警告後使用內建 fallback。 */
    private fun resolveTemplate(key: String): MatchSettlementPresentationTemplate? = templates.find(key) ?: run {
        if (warnedUnknownTemplateKeys.add(key)) logger.warn("Unknown match settlement template on client: {}", key)
        templates.find(BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY)
    }

    /** 保證標準玩家名稱完整顯示，超寬名稱以省略號收尾。 */
    private fun fitPlayerName(name: String, maxWidth: Int): String {
        if (textRenderer.getWidth(name) <= maxWidth) return name
        val suffix = "..."
        return textRenderer.trimToWidth(name, maxWidth - textRenderer.getWidth(suffix)) + suffix
    }

    /** 從 client player list 解析名稱，AI 與離線玩家使用穩定 fallback。 */
    private fun resolvePlayerName(player: MatchSettlementPlayerSnapshot): String = playerNames.resolve(player.playerId, player.isAi)

    /** 計算面板淡入、閱讀與淡出透明度。 */
    private fun panelAlpha(elapsed: Double, duration: Double): Float = when {
        elapsed < MatchSettlementPresentationEntity.PANEL_FADE_IN_START_TICK -> 0f
        elapsed < MatchSettlementPresentationEntity.FIRST_ROW_REVEAL_TICK ->
            (
                (elapsed - MatchSettlementPresentationEntity.PANEL_FADE_IN_START_TICK) /
                    (MatchSettlementPresentationEntity.FIRST_ROW_REVEAL_TICK - MatchSettlementPresentationEntity.PANEL_FADE_IN_START_TICK)
                ).toFloat()
        elapsed > duration - MatchSettlementPresentationEntity.FADE_OUT_TICKS ->
            ((duration - elapsed) / MatchSettlementPresentationEntity.FADE_OUT_TICKS).toFloat().coerceIn(0f, 1f)
        else -> 1f
    }

    /** 計算指定揭曉序列 index 的淡入進度。 */
    private fun rowRevealProgress(elapsed: Double, index: Int, interval: Int): Float {
        val start = MatchSettlementPresentationEntity.rowRevealTick(index, interval).toDouble()
        return ((elapsed - start) / MatchSettlementPresentationEntity.ROW_REVEAL_DURATION_TICKS).toFloat().coerceIn(0f, 1f)
    }

    /** 第一名使用略微超越後回到一倍大小的回彈曲線。 */
    private fun championScale(progress: Float): Float = when {
        progress < 0.7f -> 0.92f + progress / 0.7f * 0.14f
        else -> 1.06f - (progress - 0.7f) / 0.3f * 0.06f
    }

    /** 繪製指定對齊方式的全亮文字。 */
    private fun drawText(
        text: Text,
        anchorX: Float,
        y: Float,
        alignment: Alignment,
        color: Int,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        scale: Float = 1f,
    ) {
        matrices.push()
        matrices.translate(anchorX, y, 0f)
        matrices.scale(scale, scale, 1f)
        val width = textRenderer.getWidth(text).toFloat()
        val x = when (alignment) {
            Alignment.LEFT -> 0f
            Alignment.CENTER -> -width / 2f
            Alignment.RIGHT -> -width
        }
        textRenderer.draw(text, x, 0f, color, false, matrices.peek().positionMatrix, consumers, TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE)
        matrices.pop()
    }

    /** 將 RGB 與動畫透明度合成 ARGB。 */
    private fun withAlpha(rgb: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (rgb and 0xFFFFFF)

    override fun getTexture(entity: MatchSettlementPresentationEntity): Identifier? = null

    private enum class Alignment { LEFT, CENTER, RIGHT }

    private data class Layout(
        val panelHalfWidth: Float,
        val panelBottom: Float,
        val placements: Map<Int, Placement>,
    )

    /** 單一名次在頒獎面板上的中心位置與名稱寬度。 */
    private data class Placement(val x: Float, val y: Float, val nameWidth: Int)

    private companion object {
        val logger = LoggerFactory.getLogger(MatchSettlementPresentationEntityRenderer::class.java)
        const val TEXT_SCALE = 0.02f
        const val TITLE_SCALE = 1.3f
        const val TITLE_Y = -52f
        const val PANEL_TOP = -61f
        const val PANEL_BOTTOM_PADDING = 8f
        const val PANEL_Z = 0.5f
        const val HIGHLIGHT_Z = 0.25f
        const val CHAMPION_CARD_HEIGHT = 45f
        const val FACE_Z = 0f
        const val CHAMPION_FACE_SIZE = 20f
        const val PLACEMENT_FACE_SIZE = 12f
        const val CHAMPION_Y = -30f
        const val PLACEMENT_FIRST_ROW_Y = 24f
        const val PLACEMENT_ROW_HEIGHT = 43f
        const val PLACEMENT_CARD_HEIGHT = 38f
        const val PLACEMENT_REVEAL_OFFSET = 6f
        const val CHAMPION_NAME_WIDTH = 96
        const val PLACEMENT_NAME_WIDTH = 64
        const val NAME_RGB = 0xFFFFFF
        const val PANEL_PADDING = 8f
        const val MIN_PANEL_WIDTH = 150f
        const val PLACEMENT_CARD_WIDTH = 76f
        const val MAX_PLACEMENTS_PER_ROW = 4
        const val CHAMPION_FRAME_HORIZONTAL_PADDING = 7f
        const val CHAMPION_FRAME_VERTICAL_PADDING = 5f
        const val CORNER_ENTRY_OFFSET = 8f
        const val CORNER_LENGTH = 7f
        const val CORNER_THICKNESS = 1.5f
        const val STAR_DISTANCE = 7f
    }
}
