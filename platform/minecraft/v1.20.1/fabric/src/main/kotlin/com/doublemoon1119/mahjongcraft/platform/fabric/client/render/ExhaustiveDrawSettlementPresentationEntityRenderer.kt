package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPlayer
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ScoreRankingPresentation
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerDisplayNameResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ExhaustiveDrawSettlementPlayerSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ExhaustiveDrawSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ScoreRankingAnimation
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
import kotlin.math.roundToInt
import kotlin.uuid.Uuid as KotlinUuid

/**
 * 統一流局結算的 client-only billboard 排行榜。
 *
 * @param context Minecraft entity renderer 共用資源。
 * @param reasonDisplayNames 流局原因的本地化顯示名稱。
 * @param portraitRenderer 共用真人／AI 頭像 renderer。
 * @param tileFaceRenderer 共用牌面 renderer。
 * @param playerNames 共用玩家顯示名稱解析器。
 * @param configStore 控制流局結算呈現可見性的客戶端設定。
 */
class ExhaustiveDrawSettlementPresentationEntityRenderer(
    context: EntityRendererFactory.Context,
    private val reasonDisplayNames: ExhaustiveDrawReasonDisplayNameRegistry,
    private val portraitRenderer: PlayerPortraitRenderer,
    private val tileFaceRenderer: MahjongTileFaceRenderer,
    private val playerNames: ClientPlayerDisplayNameResolver,
    private val configStore: MahjongClientConfigStore,
) : EntityRenderer<ExhaustiveDrawSettlementPresentationEntity>(context) {
    private val logger = mahjongCraftLogger(ExhaustiveDrawSettlementPresentationEntityRenderer::class)

    private val textRenderer = context.textRenderer
    private val warnedUnknownReasonIds = mutableSetOf<String>()

    /** 目前這次 [render] 呼叫所屬的桌子 ID，[resolvePlayerName] 解析名稱時查詢用；每次 render 開頭重設。 */
    private var currentTableId: KotlinUuid? = null

    override fun render(
        entity: ExhaustiveDrawSettlementPresentationEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        if (!configStore.current.presentationVisibility.drawSettlementEnabled) return
        currentTableId = entity.managedTableId
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        val elapsed = entity.elapsedTicks(tickDelta)
        val duration = (entity.endGameTime - entity.startGameTime).toDouble()
        if (elapsed !in 0.0..duration) return
        val hasInformationPhase = entity.hasInformationPhase
        val informationAlpha = if (hasInformationPhase) WorldPanelRenderer.phaseAlpha(elapsed, 30.0, 40.0, 92.0, 100.0) else 0f
        val rankingAlpha = if (hasInformationPhase) {
            WorldPanelRenderer.phaseAlpha(elapsed, 100.0, 110.0, 220.0, 240.0)
        } else {
            WorldPanelRenderer.phaseAlpha(elapsed, 40.0, 50.0, 160.0, 180.0)
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

    /**
     * 繪製排行榜與公開資訊共用的半透明背景。
     *
     * @param panelHalfWidth 面板半寬。
     * @param panelBottom 面板下界。
     * @param alpha 背景透明度基準。
     * @param matrices 目前繪製矩陣。
     * @param vertexConsumers 頂點消費者。
     */
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

    /**
     * 繪製流局原因標題。
     *
     * @param reasonId 流局原因識別碼。
     * @param alpha 文字透明度。
     * @param matrices 目前繪製矩陣。
     * @param vertexConsumers 頂點消費者。
     */
    private fun renderTitle(reasonId: String, alpha: Float, matrices: MatrixStack, vertexConsumers: VertexConsumerProvider) {
        matrices.push()
        matrices.translate(0f, TITLE_Y, 0f)
        matrices.scale(TITLE_SCALE, TITLE_SCALE, 1f)
        drawText(reasonText(reasonId).copy().formatted(Formatting.GOLD), 0f, 0f, Alignment.CENTER, withAlpha(0xFFD37A, alpha), matrices, vertexConsumers)
        matrices.pop()
    }

    /**
     * 繪製單一排名列及結算動態效果。
     *
     * @param player 保存的玩家結算資料。
     * @param displayedRank 動畫期間顯示的即時名次。
     * @param layout 排名欄位幾何。
     * @param score 動畫期間顯示的分數。
     * @param delta 動畫期間顯示的分數變化。
     * @param y 此列目前的垂直位置。
     * @param alpha 此列透明度。
     * @param settledEffect 名次結算動畫狀態。
     * @param matrices 目前繪製矩陣。
     * @param vertexConsumers 頂點消費者。
     */
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
        val deltaText = SettlementRankingLayout.formatDelta(delta)
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

    /**
     * 在已變動名次列後方繪製掃過的強調底光。
     *
     * @param panelHalfWidth 面板半寬。
     * @param y 名次列垂直位置。
     * @param alpha 排名階段透明度。
     * @param effect 名次穩定效果狀態。
     * @param matrices 目前繪製矩陣。
     * @param vertexConsumers 頂點消費者。
     */
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

    /**
     * 繪製指定矩形的漸層強調 quad。
     *
     * @param left 矩形左界。
     * @param right 矩形右界。
     * @param top 矩形上界。
     * @param bottom 矩形下界。
     * @param alpha 透明度。
     * @param matrices 目前繪製矩陣。
     * @param vertexConsumers 頂點消費者。
     */
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

    /**
     * 依進度逐色頻道混合兩個 RGB 顏色。
     *
     * @param from 起始 RGB 顏色。
     * @param to 目標 RGB 顏色。
     * @param progress 混色進度，會限制在 0 到 1。
     * @return 混合後的 RGB 顏色。
     */
    private fun mixRgb(from: Int, to: Int, progress: Float): Int {
        val amount = progress.coerceIn(0f, 1f)

        /**
         * 混合 RGB 顏色中指定位移的單一色彩頻道。
         *
         * @param shift 色彩頻道的位元位移。
         * @return 混合後的頻道值。
         */
        fun channel(shift: Int): Int = (((from shr shift) and 0xFF) + ((((to shr shift) and 0xFF) - ((from shr shift) and 0xFF)) * amount)).roundToInt()
        return (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    /**
     * 繪製第一階段公開資訊列：玩家身分、狀態與全部等待牌使用固定欄位對齊。
     *
     * @param player 玩家結算資料。
     * @param layout 公開資訊列欄位幾何。
     * @param y 此列垂直位置。
     * @param alpha 此列透明度。
     * @param matrices 目前繪製矩陣。
     * @param vertexConsumers 頂點消費者。
     */
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

    /**
     * 交由共用 renderer 解析第三方來源與真人／AI fallback。
     *
     * @param player 玩家結算資料。
     * @param x 頭像左界。
     * @param y 頭像上界。
     * @param alpha 頭像透明度。
     * @param matrices 目前繪製矩陣。
     * @param vertexConsumers 頂點消費者。
     */
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
     *
     * @param assetKey 牌面素材識別鍵。
     * @param centerX 牌面中心水平位置。
     * @param centerY 牌面中心垂直位置。
     * @param width 牌面寬度。
     * @param height 牌面高度。
     * @param alpha 牌面透明度。
     * @param matrices 目前繪製矩陣。
     * @param vertexConsumers 頂點消費者。
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

    /**
     * 依指定錨點與對齊方式繪製文字。
     *
     * @param text 要繪製的文字。
     * @param anchorX 對齊錨點的水平位置。
     * @param y 文字上界。
     * @param alignment 文字相對錨點的對齊方式。
     * @param color 含透明度的文字顏色。
     * @param matrices 目前繪製矩陣。
     * @param vertexConsumers 頂點消費者。
     */
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

    /**
     * 保證原版最多 16 字元的玩家名稱完整顯示；非標準長名稱以 `...` 收尾。
     *
     * @param name 玩家顯示名稱。
     * @return 符合名稱欄寬度的文字。
     */
    private fun fitPlayerName(name: String): String = WorldPanelRenderer.fitText(textRenderer, name, NAME_MAX_WIDTH)

    /**
     * 依目前桌子與保存身分解析玩家名稱。
     *
     * @param player 玩家結算資料。
     * @return 玩家顯示名稱。
     */
    private fun resolvePlayerName(player: ExhaustiveDrawSettlementPlayerSnapshot): String = playerNames.resolve(currentTableId, player.playerId, player.isAi)

    /**
     * 將流局原因 ID 解析為名稱，未知 ID 使用中立預設並只警告一次。
     *
     * @param reasonId 流局原因 ID。
     * @return 原因本地化文字。
     */
    private fun reasonText(reasonId: String): Text = reasonDisplayNames.find(reasonId)?.let(Text::translatable) ?: run {
        if (warnedUnknownReasonIds.add(reasonId)) logger.warn("Unknown exhaustive-draw reason display name: {}", reasonId)
        Text.translatable(MinecraftMessageKeys.EXHAUSTIVE_DRAW_REASON_NORMAL)
    }

    /**
     * 將已知流局狀態 ID 尾段映射至翻譯鍵。
     *
     * @param statusId 可為 null 的狀態 ID。
     * @return 已知狀態的翻譯鍵；未知或空值時為 null。
     */
    private fun statusTranslationKey(statusId: String?): String? = when (statusId?.substringAfter(':')) {
        "tenpai" -> MinecraftMessageKeys.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_TENPAI
        "noten" -> MinecraftMessageKeys.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_NOTEN
        "draw_declaration" -> MinecraftMessageKeys.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_DRAW_DECLARATION
        else -> null
    }

    /**
     * 中央排行榜只顯示語意狀態；等待牌由手牌上方的獨立牌面面板承擔。
     *
     * @param player 玩家結算資料。
     * @return 已知狀態的翻譯文字；沒有對應狀態時為 null。
     */
    private fun settlementStatusText(player: ExhaustiveDrawSettlementPlayerSnapshot): Text? = statusTranslationKey(player.statusId)?.let(Text::translatable)

    /**
     * 計算排行榜單列的揭示進度。
     *
     * @param elapsed 動畫開始後經過的遊戲 tick。
     * @param rowIndex 排名列索引。
     * @param hasInformationPhase 是否先播放公開資訊階段。
     * @return 限制在 0 到 1 的列揭示進度。
     */
    private fun rowRevealProgress(elapsed: Double, rowIndex: Int, hasInformationPhase: Boolean): Float {
        val start = ExhaustiveDrawSettlementPresentationEntity.rowRevealTick(rowIndex, hasInformationPhase).toDouble()
        return ((elapsed - start) / ROW_REVEAL_DURATION).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * 手牌完成翻開後逐列顯示公開資訊。
     *
     * @param elapsed 動畫開始後經過的遊戲 tick。
     * @param rowIndex 公開資訊列索引。
     * @return 限制在 0 到 1 的列揭示進度。
     */
    private fun informationRowRevealProgress(elapsed: Double, rowIndex: Int): Float {
        val start = ExhaustiveDrawSettlementPresentationEntity.informationRowRevealTick(rowIndex).toDouble()
        return ((elapsed - start) / INFORMATION_ROW_REVEAL_DURATION).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * 取得第一階段值得列出的玩家；未聽者已由桌上的蓋牌清楚表達，不重複占用面板。
     *
     * @param players 本局所有玩家的保存結算資料。
     * @return 有等待牌需要公開資訊列的玩家。
     */
    private fun informationPlayers(players: List<ExhaustiveDrawSettlementPlayerSnapshot>): List<ExhaustiveDrawSettlementPlayerSnapshot> = players.filter { player ->
        player.waitingTileAssetKeys.isNotEmpty()
    }

    /**
     * 建立集中式公開資訊欄位，最多十三張等待牌仍維持單列且不壓縮玩家名稱。
     *
     * @param players 要呈現公開資訊的玩家。
     * @return 公開資訊面板尺寸及欄位位置。
     */
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

    /**
     * 依所有起訖數值及本地化狀態的實際像素寬度建立穩定欄位；動畫中途不會改變面板尺寸。
     *
     * @param players 本局所有玩家的保存結算資料。
     * @return 排名面板欄位及尺寸。
     */
    private fun measureLayout(players: List<ExhaustiveDrawSettlementPlayerSnapshot>): TableLayout {
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
        val statusWidth = SettlementRankingLayout.contentColumnWidth(
            contentWidths = players.map { player -> settlementStatusText(player)?.let(textRenderer::getWidth)?.toFloat() ?: 0f },
            padding = STATUS_COLUMN_PADDING,
            minWidth = MIN_STATUS_COLUMN_WIDTH,
        )
        val columns = SettlementRankingLayout.arrange(
            columns = SettlementRankingLayout.standardColumns(
                faceSize = FACE_SIZE,
                scoreWidth = scoreWidth,
                deltaWidth = deltaWidth,
                statusWidth = statusWidth,
            ),
            panelPadding = PANEL_PADDING,
        )
        val panelBottom = FIRST_ROW_Y + players.size.coerceAtLeast(1) * ROW_HEIGHT + PANEL_BOTTOM_PADDING
        return TableLayout(
            panelHalfWidth = columns.panelHalfWidth,
            panelBottom = panelBottom,
            rankRightX = columns[SettlementRankingColumnId.RANK].right,
            faceLeftX = columns[SettlementRankingColumnId.FACE].left,
            nameLeftX = columns[SettlementRankingColumnId.NAME].left,
            statusCenterX = columns[SettlementRankingColumnId.STATUS].centerX,
            scoreRightX = columns[SettlementRankingColumnId.SCORE].right,
            deltaRightX = columns[SettlementRankingColumnId.DELTA].right,
        )
    }

    /**
     * 將可持久化 Fabric snapshot 投影為 Flow 層共用的規則中立排行關鍵影格。
     *
     * @param player 可持久化的流局結算玩家快照。
     * @return 共用排行動畫使用的玩家資料。
     */
    private fun ExhaustiveDrawSettlementPlayerSnapshot.toScoreRankingPlayer(): ScoreRankingPlayer = ScoreRankingPlayer(
        playerId = KotlinUuid.parse(playerId),
        seatIndex = seatIndex,
        isAi = isAi,
        previousScore = previousScore,
        currentScore = currentScore,
        previousRank = previousRank,
        currentRank = currentRank,
    )

    /**
     * 將透明度套用至 RGB 顏色。
     *
     * @param rgb 不含 alpha 的 RGB 顏色。
     * @param alpha 透明度比例。
     * @return 合成後的 ARGB 顏色。
     */
    private fun withAlpha(rgb: Int, alpha: Float): Int = WorldPanelRenderer.withAlpha(rgb, alpha)
    override fun getTexture(entity: ExhaustiveDrawSettlementPresentationEntity): Identifier? = null

    /** 文字相對水平錨點的對齊方式。 */
    private enum class Alignment {
        /** 從錨點向右繪製。 */
        LEFT,

        /** 以錨點置中。 */
        CENTER,

        /** 從錨點向左繪製。 */
        RIGHT,
    }

    /**
     * 排名面板固定欄位位置與整體尺寸。
     *
     * @property panelHalfWidth 排名面板半寬。
     * @property panelBottom 面板下界。
     * @property rankRightX 名次欄右界。
     * @property faceLeftX 頭像欄左界。
     * @property nameLeftX 名稱欄左界。
     * @property statusCenterX 狀態欄中心。
     * @property scoreRightX 分數欄右界。
     * @property deltaRightX 分數變化欄右界。
     */
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

    /**
     * 單列排名動畫的已解析內容。
     *
     * @property player 對應的保存玩家資料。
     * @property revealIndex 揭示順序索引。
     * @property score 動畫中的分數。
     * @property delta 動畫中的分數差額。
     * @property position 動畫中的排名位置。
     */
    private data class AnimatedRow(
        val player: ExhaustiveDrawSettlementPlayerSnapshot,
        val revealIndex: Int,
        val score: Int,
        val delta: Int,
        val position: Double,
    )

    /**
     * 第一階段公開資訊面板的尺寸與欄位位置。
     *
     * @property panelHalfWidth 面板半寬。
     * @property panelBottom 面板下界。
     * @property faceLeftX 頭像欄左界。
     * @property nameLeftX 名稱欄左界。
     * @property statusLeftX 狀態欄左界。
     * @property waitingTilesLeftX 等待牌列左界。
     */
    private data class InformationLayout(
        val panelHalfWidth: Float,
        val panelBottom: Float,
        val faceLeftX: Float,
        val nameLeftX: Float,
        val statusLeftX: Float,
        val waitingTilesLeftX: Float,
    )

    private companion object {
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
        const val COLUMN_GAP = 7f
        const val SECTION_GAP = 9f
        const val MIN_STATUS_COLUMN_WIDTH = 48f
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
