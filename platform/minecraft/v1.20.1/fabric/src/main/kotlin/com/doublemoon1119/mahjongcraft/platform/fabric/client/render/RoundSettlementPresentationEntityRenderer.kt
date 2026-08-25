package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.RoundSettlementPlayerSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.RoundSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileTextureAssetPath
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.DefaultSkinHelper
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import org.joml.Matrix4f
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.roundToInt

/** 統一流局結算的 client-only billboard 排行榜。 */
class RoundSettlementPresentationEntityRenderer(
    context: EntityRendererFactory.Context,
    private val reasonDisplayNames: ExhaustiveDrawReasonDisplayNameRegistry,
) : EntityRenderer<RoundSettlementPresentationEntity>(context) {
    private val textRenderer = context.textRenderer
    private val tileTextures = mutableMapOf<String, Identifier>()
    private val warnedUnknownReasonIds = mutableSetOf<String>()

    override fun render(
        entity: RoundSettlementPresentationEntity,
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
        val hasInformationPhase = entity.hasInformationPhase
        val informationAlpha = if (hasInformationPhase) phaseAlpha(elapsed, 30.0, 40.0, 92.0, 100.0) else 0f
        val rankingAlpha = if (hasInformationPhase) {
            phaseAlpha(elapsed, 100.0, 110.0, 220.0, 240.0)
        } else {
            phaseAlpha(elapsed, 40.0, 50.0, 160.0, 180.0)
        }
        val panelAlpha = maxOf(informationAlpha, rankingAlpha)
        if (panelAlpha <= 0f) return
        val scoreAnimationStart = if (hasInformationPhase) 130.0 else 70.0
        val scoreProgress = ((elapsed - scoreAnimationStart) / 40.0).coerceIn(0.0, 1.0)
        val eased = 1.0 - (1.0 - scoreProgress) * (1.0 - scoreProgress) * (1.0 - scoreProgress)
        val rankingLayout = measureLayout(entity.players)
        val informationPlayers = informationPlayers(entity.players)
        val informationLayout = measureInformationLayout(informationPlayers)
        val sharedPanelHalfWidth = maxOf(rankingLayout.panelHalfWidth, informationLayout.panelHalfWidth)
        val sharedPanelBottom = maxOf(rankingLayout.panelBottom, informationLayout.panelBottom)

        matrices.push()
        matrices.translate(0.0, 0.55, 0.0)
        matrices.multiply(dispatcher.rotation)
        matrices.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE)
        renderPanel(sharedPanelHalfWidth, sharedPanelBottom, panelAlpha, matrices, vertexConsumers)
        renderTitle(entity.reasonId, panelAlpha, matrices, vertexConsumers)
        informationPlayers.forEachIndexed { index, player ->
            val rowProgress = informationRowRevealProgress(elapsed, index)
            val rowAlpha = informationAlpha * rowProgress
            if (rowAlpha > 0f) {
                drawInformationRow(
                    player = player,
                    layout = informationLayout,
                    y = FIRST_ROW_Y + index * INFORMATION_ROW_HEIGHT - (1f - rowProgress) * INFORMATION_ROW_REVEAL_OFFSET,
                    alpha = rowAlpha,
                    matrices = matrices,
                    vertexConsumers = vertexConsumers,
                )
            }
        }
        val animatedRows = entity.players.sortedBy { it.previousRank }.mapIndexed { rowIndex, player ->
            AnimatedRow(
                player = player,
                revealIndex = rowIndex,
                position = lerp(player.previousRank.toDouble(), player.currentRank.toDouble(), eased),
            )
        }
        val liveRanks = animatedRows
            .sortedWith(compareBy<AnimatedRow> { it.position }.thenBy { it.player.previousRank })
            .mapIndexed { index, row -> row.player.playerId to index + 1 }
            .toMap()
        animatedRows.forEach { row ->
            val player = row.player
            val rowProgress = rowRevealProgress(elapsed, row.revealIndex, hasInformationPhase)
            if (rowProgress <= 0f) return@forEach
            val targetY = FIRST_ROW_Y + (row.position - 1.0).toFloat() * ROW_HEIGHT
            drawRow(
                player,
                liveRanks.getValue(player.playerId),
                rankingLayout,
                eased,
                targetY - (1f - rowProgress) * ROW_REVEAL_OFFSET,
                rankingAlpha * rowProgress,
                matrices,
                vertexConsumers,
            )
        }
        matrices.pop()
    }

    private fun renderPanel(
        panelHalfWidth: Float,
        panelBottom: Float,
        alpha: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
    ) {
        val matrix = matrices.peek().positionMatrix
        val buffer = vertexConsumers.getBuffer(RoundSettlementPanelRenderLayer.layer)
        val panelAlpha = (alpha * PANEL_ALPHA * 255).roundToInt()
        buffer.vertex(matrix, -panelHalfWidth, PANEL_TOP, PANEL_Z).color(0, 0, 0, panelAlpha).next()
        buffer.vertex(matrix, panelHalfWidth, PANEL_TOP, PANEL_Z).color(0, 0, 0, panelAlpha).next()
        buffer.vertex(matrix, panelHalfWidth, panelBottom, PANEL_Z).color(0, 0, 0, panelAlpha).next()
        buffer.vertex(matrix, -panelHalfWidth, panelBottom, PANEL_Z).color(0, 0, 0, panelAlpha).next()
    }

    private fun renderTitle(reasonId: String, alpha: Float, matrices: MatrixStack, vertexConsumers: VertexConsumerProvider) {
        matrices.push()
        matrices.translate(0f, TITLE_Y, 0f)
        matrices.scale(TITLE_SCALE, TITLE_SCALE, 1f)
        drawText(reasonText(reasonId).copy().formatted(Formatting.GOLD), 0f, 0f, Alignment.CENTER, withAlpha(0xFFD37A, alpha), matrices, vertexConsumers)
        matrices.pop()
    }

    private fun drawRow(
        player: RoundSettlementPlayerSnapshot,
        displayedRank: Int,
        layout: TableLayout,
        progress: Double,
        y: Float,
        alpha: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
    ) {
        val score = lerp(player.previousScore.toDouble(), player.currentScore.toDouble(), progress).roundToInt()
        val delta = lerp(0.0, (player.currentScore - player.previousScore).toDouble(), progress).roundToInt()
        val deltaText = formatDelta(delta)
        val deltaColor = when {
            delta > 0 -> 0x80FF80
            delta < 0 -> 0xFF8080
            else -> 0xC8C8C8
        }

        drawText(Text.literal(displayedRank.toString()), layout.rankRightX, y, Alignment.RIGHT, withAlpha(0xFFD86A, alpha), matrices, vertexConsumers)
        renderPortrait(player, layout.faceLeftX, y - FACE_TEXT_CENTER_OFFSET, alpha, matrices, vertexConsumers)
        drawText(Text.literal(fitPlayerName(resolvePlayerName(player))), layout.nameLeftX, y, Alignment.LEFT, withAlpha(0xFFFFFF, alpha), matrices, vertexConsumers)
        settlementStatusText(player)?.let { statusText ->
            drawText(statusText, layout.statusCenterX, y, Alignment.CENTER, withAlpha(0xFFE08A, alpha), matrices, vertexConsumers)
        }
        drawText(Text.literal(score.toString()), layout.scoreRightX, y, Alignment.RIGHT, withAlpha(0xFFF3C4, alpha), matrices, vertexConsumers)
        drawText(Text.literal(deltaText), layout.deltaRightX, y, Alignment.RIGHT, withAlpha(deltaColor, alpha), matrices, vertexConsumers)
    }

    /** 第一階段的公開資訊列：玩家身分、狀態與全部等待牌使用固定欄位對齊。 */
    private fun drawInformationRow(
        player: RoundSettlementPlayerSnapshot,
        layout: InformationLayout,
        y: Float,
        alpha: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
    ) {
        renderPortrait(player, layout.faceLeftX, y - FACE_TEXT_CENTER_OFFSET, alpha, matrices, vertexConsumers)
        drawText(
            Text.literal(fitPlayerName(resolvePlayerName(player))),
            layout.nameLeftX,
            y,
            Alignment.LEFT,
            withAlpha(0xFFFFFF, alpha),
            matrices,
            vertexConsumers,
        )
        settlementStatusText(player)?.let { status ->
            drawText(status, layout.statusLeftX, y, Alignment.LEFT, withAlpha(0xFFE08A, alpha), matrices, vertexConsumers)
        }
        var tileCenterX = layout.waitingTilesLeftX + WAIT_TILE_WIDTH / 2f
        player.waitingTileAssetKeys.forEach { assetKey ->
            renderTileFace(
                assetKey = assetKey,
                centerX = tileCenterX,
                centerY = y + FACE_SIZE / 2f - FACE_TEXT_CENTER_OFFSET,
                width = WAIT_TILE_WIDTH,
                height = WAIT_TILE_HEIGHT,
                alpha = alpha,
                matrices = matrices,
                vertexConsumers = vertexConsumers,
            )
            tileCenterX += WAIT_TILE_WIDTH + WAIT_TILE_GAP
        }
    }

    /** 真人只繪製正方形 skin 正面臉部；AI 暫時統一使用 unknown 牌面，等待日後 Seat Actor 提供正式外觀。 */
    private fun renderPortrait(
        player: RoundSettlementPlayerSnapshot,
        x: Float,
        y: Float,
        alpha: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
    ) {
        if (player.isAi) {
            renderTileFace(
                assetKey = UNKNOWN_TILE_ASSET_KEY,
                centerX = x + FACE_SIZE / 2f,
                centerY = y + FACE_SIZE / 2f,
                width = AI_PORTRAIT_TILE_WIDTH,
                height = AI_PORTRAIT_TILE_HEIGHT,
                alpha = alpha,
                matrices = matrices,
                vertexConsumers = vertexConsumers,
            )
            return
        }
        val uuid = runCatching { UUID.fromString(player.playerId) }.getOrNull()
        val texture = uuid?.let { MinecraftClient.getInstance().networkHandler?.getPlayerListEntry(it)?.skinTexture }
            ?: uuid?.let(DefaultSkinHelper::getTexture)
            ?: DefaultSkinHelper.getTexture(UUID(0L, 0L))
        val buffer = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(texture))
        val matrix = matrices.peek().positionMatrix
        drawFaceLayer(buffer, matrix, x, y, FACE_SIZE, 8f, 8f, 16f, 16f, alpha)
    }

    /**
     * 直接將既有麻將牌正面材質繪製成指定尺寸的 quad，避免 ItemRenderer 的 HEAD transform 再次縮小
     * 結算 UI。找不到第三方材質時安全退回 unknown 牌面。
     */
    private fun renderTileFace(
        assetKey: String,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        alpha: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
    ) {
        val texture = resolveTileTexture(assetKey)
        val buffer = vertexConsumers.getBuffer(RoundSettlementTileFaceRenderLayer.get(texture))
        val matrix = matrices.peek().positionMatrix
        val left = centerX - width / 2f
        val right = centerX + width / 2f
        val top = centerY - height / 2f
        val bottom = centerY + height / 2f
        val a = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
        fun vertex(x: Float, y: Float, u: Float, v: Float) {
            buffer.vertex(matrix, x, y, TILE_FACE_Z)
                .color(255, 255, 255, a)
                .texture(u, v)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .next()
        }
        vertex(left, top, 0f, 0f)
        vertex(right, top, 1f, 0f)
        vertex(right, bottom, 1f, 1f)
        vertex(left, bottom, 0f, 1f)
    }

    /** 解析牌面材質；資源不存在時統一退回內建 unknown。 */
    private fun resolveTileTexture(assetKey: String): Identifier = tileTextures.getOrPut(assetKey) {
        val requested = Identifier(MinecraftModMetadata.MOD_ID, tileTextureAssetPath(assetKey))
        if (MinecraftClient.getInstance().resourceManager.getResource(requested).isPresent) {
            requested
        } else {
            Identifier(MinecraftModMetadata.MOD_ID, tileTextureAssetPath(UNKNOWN_TILE_ASSET_KEY))
        }
    }

    private fun drawFaceLayer(
        buffer: VertexConsumer,
        matrix: Matrix4f,
        x: Float,
        y: Float,
        size: Float,
        u0: Float,
        v0: Float,
        u1: Float,
        v1: Float,
        alpha: Float,
    ) {
        val a = (alpha * 255).roundToInt()
        fun vertex(px: Float, py: Float, u: Float, v: Float) {
            buffer.vertex(matrix, px, py, FACE_Z).color(255, 255, 255, a).texture(u / 64f, v / 64f)
                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(0f, 0f, 1f).next()
        }
        vertex(x, y, u0, v0)
        vertex(x + size, y, u1, v0)
        vertex(x + size, y + size, u1, v1)
        vertex(x, y + size, u0, v1)
    }

    private fun drawText(
        text: Text,
        anchorX: Float,
        y: Float,
        alignment: Alignment,
        color: Int,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
    ) {
        val width = textRenderer.getWidth(text).toFloat()
        val x = when (alignment) {
            Alignment.LEFT -> anchorX
            Alignment.CENTER -> anchorX - width / 2f
            Alignment.RIGHT -> anchorX - width
        }
        textRenderer.draw(text, x, y, color, false, matrices.peek().positionMatrix, vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE)
    }

    /** 保證原版最多 16 字元的玩家名稱完整顯示；非標準長名稱以 `...` 收尾。 */
    private fun fitPlayerName(name: String): String {
        if (textRenderer.getWidth(name) <= NAME_MAX_WIDTH) return name
        val suffix = "..."
        return textRenderer.trimToWidth(name, NAME_MAX_WIDTH - textRenderer.getWidth(suffix)) + suffix
    }

    private fun resolvePlayerName(player: RoundSettlementPlayerSnapshot): String {
        if (player.isAi) return "AI-${player.playerId.take(6)}"
        val uuid = runCatching { UUID.fromString(player.playerId) }.getOrNull()
        return uuid?.let { MinecraftClient.getInstance().networkHandler?.getPlayerListEntry(it)?.profile?.name } ?: player.playerId.take(8)
    }

    private fun reasonText(reasonId: String): Text = reasonDisplayNames.find(reasonId)?.let(Text::translatable) ?: run {
        if (warnedUnknownReasonIds.add(reasonId)) logger.warn("Unknown exhaustive-draw reason display name: {}", reasonId)
        Text.translatable(MinecraftMessageKeys.EXHAUSTIVE_DRAW_REASON_NORMAL)
    }

    private fun statusTranslationKey(statusId: String?): String? = when (statusId?.substringAfter(':')) {
        "tenpai" -> MinecraftMessageKeys.ROUND_SETTLEMENT_STATUS_TENPAI
        "noten" -> MinecraftMessageKeys.ROUND_SETTLEMENT_STATUS_NOTEN
        "draw_declaration" -> MinecraftMessageKeys.ROUND_SETTLEMENT_STATUS_DRAW_DECLARATION
        else -> null
    }

    /** 中央排行榜只顯示語意狀態；等待牌由手牌上方的獨立牌面面板承擔。 */
    private fun settlementStatusText(player: RoundSettlementPlayerSnapshot): Text? = statusTranslationKey(player.statusId)?.let(Text::translatable)

    private fun rowRevealProgress(elapsed: Double, rowIndex: Int, hasInformationPhase: Boolean): Float {
        val start = RoundSettlementPresentationEntity.rowRevealTick(rowIndex, hasInformationPhase).toDouble()
        return ((elapsed - start) / ROW_REVEAL_DURATION).coerceIn(0.0, 1.0).toFloat()
    }

    /** 手牌完成翻開後逐列顯示公開資訊。 */
    private fun informationRowRevealProgress(elapsed: Double, rowIndex: Int): Float {
        val start = RoundSettlementPresentationEntity.informationRowRevealTick(rowIndex).toDouble()
        return ((elapsed - start) / INFORMATION_ROW_REVEAL_DURATION).coerceIn(0.0, 1.0).toFloat()
    }

    /** 取得第一階段值得列出的玩家；未聽者已由桌上的蓋牌清楚表達，不重複占用面板。 */
    private fun informationPlayers(players: List<RoundSettlementPlayerSnapshot>): List<RoundSettlementPlayerSnapshot> = players.filter { player ->
        player.waitingTileAssetKeys.isNotEmpty()
    }

    /** 建立集中式公開資訊欄位，最多十三張等待牌仍維持單列且不壓縮玩家名稱。 */
    private fun measureInformationLayout(players: List<RoundSettlementPlayerSnapshot>): InformationLayout {
        val maximumWaitingTileCount = players.maxOfOrNull { it.waitingTileAssetKeys.size } ?: 0
        val waitingTilesWidth = maximumWaitingTileCount * WAIT_TILE_WIDTH +
            (maximumWaitingTileCount - 1).coerceAtLeast(0) * WAIT_TILE_GAP
        val totalWidth = PANEL_PADDING * 2 + FACE_SIZE + COLUMN_GAP + NAME_MAX_WIDTH + SECTION_GAP +
            INFORMATION_STATUS_WIDTH + SECTION_GAP + waitingTilesWidth
        var cursor = -totalWidth / 2f + PANEL_PADDING
        val faceLeftX = cursor
        cursor += FACE_SIZE + COLUMN_GAP
        val nameLeftX = cursor
        cursor += NAME_MAX_WIDTH + SECTION_GAP
        val statusLeftX = cursor
        cursor += INFORMATION_STATUS_WIDTH + SECTION_GAP
        val waitingTilesLeftX = cursor
        val rowCount = players.size.coerceAtLeast(1)
        val panelBottom = FIRST_ROW_Y + rowCount * INFORMATION_ROW_HEIGHT + PANEL_BOTTOM_PADDING
        return InformationLayout(totalWidth / 2f, panelBottom, faceLeftX, nameLeftX, statusLeftX, waitingTilesLeftX)
    }

    /** 依所有起訖數值及本地化狀態的實際像素寬度建立穩定欄位；動畫中途不會改變面板尺寸。 */
    private fun measureLayout(players: List<RoundSettlementPlayerSnapshot>): TableLayout {
        val scoreContentWidth = players.maxOfOrNull { player ->
            maxOf(textRenderer.getWidth(player.previousScore.toString()), textRenderer.getWidth(player.currentScore.toString()))
        } ?: 0
        val deltaContentWidth = players.maxOfOrNull { player -> textRenderer.getWidth(formatDelta(player.currentScore - player.previousScore)) } ?: 0
        val statusContentWidth = players.maxOfOrNull { player ->
            settlementStatusText(player)?.let(textRenderer::getWidth) ?: 0
        } ?: 0
        val scoreWidth = maxOf(MIN_SCORE_COLUMN_WIDTH, scoreContentWidth + NUMERIC_COLUMN_PADDING * 2)
        val deltaWidth = maxOf(MIN_DELTA_COLUMN_WIDTH, deltaContentWidth + NUMERIC_COLUMN_PADDING * 2)
        val statusWidth = maxOf(MIN_STATUS_COLUMN_WIDTH, statusContentWidth + STATUS_COLUMN_PADDING * 2)
        val totalWidth = PANEL_PADDING * 2 + RANK_COLUMN_WIDTH + COLUMN_GAP + FACE_SIZE + COLUMN_GAP + NAME_MAX_WIDTH + SECTION_GAP + statusWidth + SECTION_GAP + scoreWidth + SECTION_GAP + deltaWidth
        var cursor = -totalWidth / 2f + PANEL_PADDING
        val rankRightX = cursor + RANK_COLUMN_WIDTH
        cursor = rankRightX + COLUMN_GAP
        val faceLeftX = cursor
        cursor += FACE_SIZE + COLUMN_GAP
        val nameLeftX = cursor
        cursor += NAME_MAX_WIDTH + SECTION_GAP
        val statusCenterX = cursor + statusWidth / 2f
        cursor += statusWidth + SECTION_GAP
        val scoreRightX = cursor + scoreWidth
        cursor = scoreRightX + SECTION_GAP
        val deltaRightX = cursor + deltaWidth
        val panelBottom = FIRST_ROW_Y + players.size.coerceAtLeast(1) * ROW_HEIGHT + PANEL_BOTTOM_PADDING
        return TableLayout(totalWidth / 2f, panelBottom, rankRightX, faceLeftX, nameLeftX, statusCenterX, scoreRightX, deltaRightX)
    }

    private fun formatDelta(delta: Int): String = when {
        delta > 0 -> "+$delta"
        delta < 0 -> delta.toString()
        else -> "±0"
    }

    private fun phaseAlpha(elapsed: Double, fadeInStart: Double, fadeInEnd: Double, fadeOutStart: Double, fadeOutEnd: Double): Float = when {
        elapsed < fadeInStart || elapsed >= fadeOutEnd -> 0f
        elapsed < fadeInEnd -> ((elapsed - fadeInStart) / (fadeInEnd - fadeInStart)).toFloat()
        elapsed > fadeOutStart -> ((fadeOutEnd - elapsed) / (fadeOutEnd - fadeOutStart)).toFloat()
        else -> 1f
    }

    private fun lerp(start: Double, end: Double, progress: Double): Double = start + (end - start) * progress
    private fun withAlpha(rgb: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (rgb and 0xFFFFFF)
    override fun getTexture(entity: RoundSettlementPresentationEntity): Identifier? = null

    private enum class Alignment { LEFT, CENTER, RIGHT }

    private data class TableLayout(
        val panelHalfWidth: Float,
        val panelBottom: Float,
        val rankRightX: Float,
        val faceLeftX: Float,
        val nameLeftX: Float,
        val statusCenterX: Float,
        val scoreRightX: Float,
        val deltaRightX: Float,
    )

    private data class AnimatedRow(
        val player: RoundSettlementPlayerSnapshot,
        val revealIndex: Int,
        val position: Double,
    )

    private data class InformationLayout(
        val panelHalfWidth: Float,
        val panelBottom: Float,
        val faceLeftX: Float,
        val nameLeftX: Float,
        val statusLeftX: Float,
        val waitingTilesLeftX: Float,
    )

    private companion object {
        val logger = LoggerFactory.getLogger(RoundSettlementPresentationEntityRenderer::class.java)
        const val TEXT_SCALE = 0.02f
        const val TITLE_SCALE = 1.3f
        const val TITLE_Y = -49f
        const val FIRST_ROW_Y = -24f
        const val ROW_HEIGHT = 16f
        const val PANEL_TOP = -61f
        const val PANEL_BOTTOM_PADDING = 8f
        const val PANEL_ALPHA = 0.72f
        const val PANEL_Z = 0.5f
        const val FACE_Z = 0f
        const val FACE_SIZE = 10f
        const val AI_PORTRAIT_TILE_WIDTH = 7.5f
        const val AI_PORTRAIT_TILE_HEIGHT = 10f
        const val FACE_TEXT_CENTER_OFFSET = 0.5f
        const val NAME_MAX_WIDTH = 96
        const val PANEL_PADDING = 8f
        const val RANK_COLUMN_WIDTH = 12f
        const val COLUMN_GAP = 7f
        const val SECTION_GAP = 9f
        const val MIN_STATUS_COLUMN_WIDTH = 48f
        const val MIN_SCORE_COLUMN_WIDTH = 48f
        const val MIN_DELTA_COLUMN_WIDTH = 56f
        const val NUMERIC_COLUMN_PADDING = 6f
        const val STATUS_COLUMN_PADDING = 4f
        const val ROW_REVEAL_DURATION = 6.0
        const val ROW_REVEAL_OFFSET = 6f
        const val INFORMATION_STATUS_WIDTH = 48f
        const val INFORMATION_ROW_REVEAL_DURATION = 8.0
        const val INFORMATION_ROW_REVEAL_OFFSET = 5f
        const val INFORMATION_ROW_HEIGHT = 22f
        const val WAIT_TILE_WIDTH = 12f
        const val WAIT_TILE_HEIGHT = 16f
        const val WAIT_TILE_GAP = 2f
        const val TILE_FACE_Z = 0f
    }
}
