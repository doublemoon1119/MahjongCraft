package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingAnimation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPresentation
import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerDisplayNameResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ExhaustiveDrawSettlementPlayerSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ExhaustiveDrawSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import net.minecraft.util.Identifier
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt
import kotlin.uuid.Uuid as KotlinUuid

/** 統一流局結算的 client-only billboard 排行榜。 */
class ExhaustiveDrawSettlementPresentationEntityRenderer(
    context: EntityRendererFactory.Context,
    private val reasonDisplayNames: ExhaustiveDrawReasonDisplayNameRegistry,
    private val portraitRenderer: PlayerPortraitRenderer,
    private val tileFaceRenderer: MahjongTileFaceRenderer,
    private val playerNames: ClientPlayerDisplayNameResolver,
) : EntityRenderer<ExhaustiveDrawSettlementPresentationEntity>(context) {
    private val textRenderer = context.textRenderer
    private val warnedUnknownReasonIds = mutableSetOf<String>()

    override fun render(
        entity: ExhaustiveDrawSettlementPresentationEntity,
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
        val rankingPresentation = ScoreRankingPresentation(entity.players.map { player -> player.toScoreRankingPlayer() })
        val scoreRows = ScoreRankingAnimation.rows(rankingPresentation, scoreProgress)
        val liveRanks = ScoreRankingAnimation.liveRanks(scoreRows)
        val snapshotsById = entity.players.associateBy { KotlinUuid.parse(it.playerId) }
        val animatedRows = scoreRows.sortedBy { it.player.previousRank }.mapIndexed { rowIndex, row ->
            AnimatedRow(
                player = snapshotsById.getValue(row.player.playerId),
                revealIndex = rowIndex,
                score = row.score,
                delta = row.delta,
                position = row.position,
            )
        }
        animatedRows.forEach { row ->
            val player = row.player
            val rowProgress = rowRevealProgress(elapsed, row.revealIndex, hasInformationPhase)
            if (rowProgress <= 0f) return@forEach
            val targetY = FIRST_ROW_Y + (row.position - 1.0).toFloat() * ROW_HEIGHT
            val settledEffect = SettlementRankingSettledEffect.resolve(
                elapsed,
                ExhaustiveDrawSettlementPresentationEntity.rankingSettledSoundTick(hasInformationPhase).toDouble(),
                player.previousRank != player.currentRank,
            )
            drawRow(
                player,
                liveRanks.getValue(KotlinUuid.parse(player.playerId)),
                rankingLayout,
                row.score,
                row.delta,
                targetY - (1f - rowProgress) * ROW_REVEAL_OFFSET,
                rankingAlpha * rowProgress,
                settledEffect,
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
        val buffer = vertexConsumers.getBuffer(ExhaustiveDrawSettlementPanelRenderLayer.layer)
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
        player: ExhaustiveDrawSettlementPlayerSnapshot,
        displayedRank: Int,
        layout: TableLayout,
        score: Int,
        delta: Int,
        y: Float,
        alpha: Float,
        settledEffect: SettlementRankingSettledEffect.State,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
    ) {
        val deltaText = formatDelta(delta)
        val deltaColor = when {
            delta > 0 -> 0x80FF80
            delta < 0 -> 0xFF8080
            else -> 0xC8C8C8
        }

        renderRankingSettledHighlight(layout.panelHalfWidth, y, alpha, settledEffect, matrices, vertexConsumers)
        matrices.push()
        matrices.translate(0f, y, 0f)
        matrices.scale(settledEffect.rowScale, settledEffect.rowScale, 1f)
        matrices.push()
        matrices.translate(layout.rankRightX, 0f, 0f)
        matrices.scale(settledEffect.rankScale, settledEffect.rankScale, 1f)
        drawText(
            Text.literal(displayedRank.toString()),
            0f,
            0f,
            Alignment.RIGHT,
            withAlpha(mixRgb(0xFFD86A, 0xFFFFFF, settledEffect.rankWhiteness), alpha),
            matrices,
            vertexConsumers,
        )
        matrices.pop()
        renderPortrait(player, layout.faceLeftX, -FACE_TEXT_CENTER_OFFSET, alpha, matrices, vertexConsumers)
        drawText(Text.literal(fitPlayerName(resolvePlayerName(player))), layout.nameLeftX, 0f, Alignment.LEFT, withAlpha(0xFFFFFF, alpha), matrices, vertexConsumers)
        settlementStatusText(player)?.let { statusText ->
            drawText(statusText, layout.statusCenterX, 0f, Alignment.CENTER, withAlpha(0xFFE08A, alpha), matrices, vertexConsumers)
        }
        drawText(Text.literal(score.toString()), layout.scoreRightX, 0f, Alignment.RIGHT, withAlpha(0xFFF3C4, alpha), matrices, vertexConsumers)
        drawText(Text.literal(deltaText), layout.deltaRightX, 0f, Alignment.RIGHT, withAlpha(deltaColor, alpha), matrices, vertexConsumers)
        matrices.pop()
    }

    private fun renderRankingSettledHighlight(
        panelHalfWidth: Float,
        y: Float,
        alpha: Float,
        effect: SettlementRankingSettledEffect.State,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
    ) {
        if (!effect.active) return
        val left = -panelHalfWidth + 5f
        val right = panelHalfWidth - 5f
        renderHighlightQuad(left, right, y - 3f, y + 11f, alpha * effect.highlightAlpha, matrices, vertexConsumers)
        val sweepCenter = left + (right - left) * effect.sweepProgress
        renderHighlightQuad(
            maxOf(left, sweepCenter - RANKING_SWEEP_HALF_WIDTH),
            minOf(right, sweepCenter + RANKING_SWEEP_HALF_WIDTH),
            y - 3f,
            y + 11f,
            alpha * effect.highlightAlpha * 1.6f,
            matrices,
            vertexConsumers,
        )
    }

    private fun renderHighlightQuad(
        left: Float,
        right: Float,
        top: Float,
        bottom: Float,
        alpha: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
    ) {
        if (right <= left || alpha <= 0f) return
        val matrix = matrices.peek().positionMatrix
        val buffer = vertexConsumers.getBuffer(ExhaustiveDrawSettlementPanelRenderLayer.layer)
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

    /** 第一階段的公開資訊列：玩家身分、狀態與全部等待牌使用固定欄位對齊。 */
    private fun drawInformationRow(
        player: ExhaustiveDrawSettlementPlayerSnapshot,
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

    /** 交由共用 renderer 解析第三方來源與真人／AI fallback。 */
    private fun renderPortrait(
        player: ExhaustiveDrawSettlementPlayerSnapshot,
        x: Float,
        y: Float,
        alpha: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
    ) = portraitRenderer.render(
        playerId = KotlinUuid.parse(player.playerId),
        isAi = player.isAi,
        x = x,
        y = y,
        size = FACE_SIZE,
        alpha = alpha,
        z = FACE_Z,
        matrices = matrices,
        consumers = vertexConsumers,
    )

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
        tileFaceRenderer.renderWorldPanel(assetKey, centerX, centerY, width, height, alpha, TILE_FACE_Z, matrices, vertexConsumers)
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

    private fun resolvePlayerName(player: ExhaustiveDrawSettlementPlayerSnapshot): String = playerNames.resolve(player.playerId, player.isAi)

    private fun reasonText(reasonId: String): Text = reasonDisplayNames.find(reasonId)?.let(Text::translatable) ?: run {
        if (warnedUnknownReasonIds.add(reasonId)) logger.warn("Unknown exhaustive-draw reason display name: {}", reasonId)
        Text.translatable(MinecraftMessageKeys.EXHAUSTIVE_DRAW_REASON_NORMAL)
    }

    private fun statusTranslationKey(statusId: String?): String? = when (statusId?.substringAfter(':')) {
        "tenpai" -> MinecraftMessageKeys.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_TENPAI
        "noten" -> MinecraftMessageKeys.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_NOTEN
        "draw_declaration" -> MinecraftMessageKeys.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_DRAW_DECLARATION
        else -> null
    }

    /** 中央排行榜只顯示語意狀態；等待牌由手牌上方的獨立牌面面板承擔。 */
    private fun settlementStatusText(player: ExhaustiveDrawSettlementPlayerSnapshot): Text? = statusTranslationKey(player.statusId)?.let(Text::translatable)

    private fun rowRevealProgress(elapsed: Double, rowIndex: Int, hasInformationPhase: Boolean): Float {
        val start = ExhaustiveDrawSettlementPresentationEntity.rowRevealTick(rowIndex, hasInformationPhase).toDouble()
        return ((elapsed - start) / ROW_REVEAL_DURATION).coerceIn(0.0, 1.0).toFloat()
    }

    /** 手牌完成翻開後逐列顯示公開資訊。 */
    private fun informationRowRevealProgress(elapsed: Double, rowIndex: Int): Float {
        val start = ExhaustiveDrawSettlementPresentationEntity.informationRowRevealTick(rowIndex).toDouble()
        return ((elapsed - start) / INFORMATION_ROW_REVEAL_DURATION).coerceIn(0.0, 1.0).toFloat()
    }

    /** 取得第一階段值得列出的玩家；未聽者已由桌上的蓋牌清楚表達，不重複占用面板。 */
    private fun informationPlayers(players: List<ExhaustiveDrawSettlementPlayerSnapshot>): List<ExhaustiveDrawSettlementPlayerSnapshot> = players.filter { player ->
        player.waitingTileAssetKeys.isNotEmpty()
    }

    /** 建立集中式公開資訊欄位，最多十三張等待牌仍維持單列且不壓縮玩家名稱。 */
    private fun measureInformationLayout(players: List<ExhaustiveDrawSettlementPlayerSnapshot>): InformationLayout {
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
    private fun measureLayout(players: List<ExhaustiveDrawSettlementPlayerSnapshot>): TableLayout {
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

    /** 將可持久化 Fabric snapshot 投影為 Flow 層共用的規則中立排行關鍵影格。 */
    private fun ExhaustiveDrawSettlementPlayerSnapshot.toScoreRankingPlayer(): ScoreRankingPlayer = ScoreRankingPlayer(
        playerId = KotlinUuid.parse(playerId),
        seatIndex = seatIndex,
        isAi = isAi,
        previousScore = previousScore,
        currentScore = currentScore,
        previousRank = previousRank,
        currentRank = currentRank,
    )

    private fun phaseAlpha(elapsed: Double, fadeInStart: Double, fadeInEnd: Double, fadeOutStart: Double, fadeOutEnd: Double): Float = when {
        elapsed < fadeInStart || elapsed >= fadeOutEnd -> 0f
        elapsed < fadeInEnd -> ((elapsed - fadeInStart) / (fadeInEnd - fadeInStart)).toFloat()
        elapsed > fadeOutStart -> ((fadeOutEnd - elapsed) / (fadeOutEnd - fadeOutStart)).toFloat()
        else -> 1f
    }

    private fun withAlpha(rgb: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (rgb and 0xFFFFFF)
    override fun getTexture(entity: ExhaustiveDrawSettlementPresentationEntity): Identifier? = null

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
        val player: ExhaustiveDrawSettlementPlayerSnapshot,
        val revealIndex: Int,
        val score: Int,
        val delta: Int,
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
        val logger = LoggerFactory.getLogger(ExhaustiveDrawSettlementPresentationEntityRenderer::class.java)
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
        const val RANKING_SWEEP_HALF_WIDTH = 18f
        const val RANKING_HIGHLIGHT_Z = 0.25f
    }
}
