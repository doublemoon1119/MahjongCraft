package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerDisplayNameResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MatchSettlementPlayerSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MatchSettlementPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplate
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
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
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.uuid.Uuid

/**
 * 與回合結算共用視覺語言，但以冠軍為主角的 client-only 終局頒獎面板。
 *
 * @param context Minecraft renderer factory 提供的 client context。
 * @param templates 提供內建與擴充頒獎面板模板。
 * @param portraitRenderer 繪製玩家頭像。
 * @param playerNames 依桌子及玩家 ID 解析顯示名稱。
 * @param configStore 提供終局頒獎面板的可見性設定。
 */
class MatchSettlementPresentationEntityRenderer(
    context: EntityRendererFactory.Context,
    private val templates: MatchSettlementPresentationTemplateRegistry,
    private val portraitRenderer: PlayerPortraitRenderer,
    private val playerNames: ClientPlayerDisplayNameResolver,
    private val configStore: MahjongClientConfigStore,
) : EntityRenderer<MatchSettlementPresentationEntity>(context) {
    private val logger = mahjongCraftLogger(MatchSettlementPresentationEntityRenderer::class)

    private val textRenderer = context.textRenderer
    private val warnedUnknownTemplateKeys = mutableSetOf<String>()

    /** 目前這次 [render] 呼叫所屬的桌子 ID，[resolvePlayerName] 解析名稱時查詢用；每次 render 開頭重設。 */
    private var currentTableId: Uuid? = null

    override fun render(
        entity: MatchSettlementPresentationEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        if (!configStore.current.presentationVisibility.matchSettlementEnabled) return
        currentTableId = entity.managedTableId
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

    /**
     * 在固定頒獎位置繪製玩家；第一名居中並使用較大的 FACE 與冠軍資訊框。
     *
     * @param player 要繪製的最終排名玩家。
     * @param layout 依玩家數量量測的頒獎版面。
     * @param template 控制文字與色彩的模板。
     * @param alpha 此列透明度。
     * @param revealProgress 此列揭曉進度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供頭像、框線與文字頂點緩衝。
     */
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

    /**
     * 第一名揭曉期間繪製向內收束的金色角標與短暫像素星點。
     *
     * @param displayName 已裁切的顯示名稱。
     * @param rankText 名次文字。
     * @param y 冠軍資訊卡頂部位置。
     * @param alpha 顯示透明度。
     * @param progress 揭曉動畫進度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供像素幾何頂點緩衝。
     */
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

        val starAlpha = (alpha * sin(progress * Math.PI).toFloat() * 220f).roundToInt().coerceIn(0, 255)
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

    /**
     * 繪製一個指向完整冠軍資訊卡的 L 形像素角標。
     *
     * @param buffer 接收角標頂點的緩衝。
     * @param matrix 目前的頂點位置矩陣。
     * @param x 角標的轉角 X 座標。
     * @param y 角標的轉角 Y 座標。
     * @param xDirection 水平延伸方向，正負值分別代表右、左。
     * @param yDirection 垂直延伸方向，正負值分別代表下、上。
     * @param alpha 頂點透明度。
     */
    private fun renderCorner(buffer: VertexConsumer, matrix: Matrix4f, x: Float, y: Float, xDirection: Float, yDirection: Float, alpha: Int) {
        renderQuad(buffer, matrix, x, y, x + CORNER_LENGTH * xDirection, y + CORNER_THICKNESS * yDirection, alpha)
        renderQuad(buffer, matrix, x, y, x + CORNER_THICKNESS * xDirection, y + CORNER_LENGTH * yDirection, alpha)
    }

    /**
     * 以金色繪製一個固定深度的面板矩形。
     *
     * @param buffer 接收矩形頂點的緩衝。
     * @param matrix 目前的頂點位置矩陣。
     * @param x1 第一個角的 X 座標。
     * @param y1 第一個角的 Y 座標。
     * @param x2 對角的 X 座標。
     * @param y2 對角的 Y 座標。
     * @param alpha 頂點透明度。
     */
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

    /**
     * 繪製完整半透明背景板並正常寫入深度。
     *
     * @param layout 面板尺寸與玩家位置。
     * @param template 提供背景顏色的模板。
     * @param alpha 顯示透明度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供背景頂點緩衝。
     */
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

    /**
     * 交由共用 renderer 解析第三方來源與真人／AI fallback。
     *
     * @param player 頭像所屬玩家快照。
     * @param x 頭像左側 X 座標。
     * @param y 頭像上側 Y 座標。
     * @param size 頭像邊長。
     * @param alpha 頭像透明度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供頭像頂點緩衝。
     */
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

    /**
     * 依玩家數建立冠軍居中、其餘名次分層的自適應頒獎位置。
     *
     * @param playerCount 頒獎名單中的玩家數量。
     * @return 含面板範圍與各最終名次位置的版面。
     */
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

    /**
     * 解析模板；未知 key 去重警告後使用內建 fallback。
     *
     * @param key entity 同步的模板 key。
     * @return 找到的模板或內建 fallback；fallback 也不存在時為 `null`。
     */
    private fun resolveTemplate(key: String): MatchSettlementPresentationTemplate? = templates.find(key) ?: run {
        if (warnedUnknownTemplateKeys.add(key)) logger.warn("Unknown match settlement template on client: {}", key)
        templates.find(BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY)
    }

    /**
     * 保證標準玩家名稱完整顯示，超寬名稱以省略號收尾。
     *
     * @param name 原始玩家名稱。
     * @param maxWidth 名稱可用像素寬度。
     * @return 寬度符合限制的名稱文字。
     */
    private fun fitPlayerName(name: String, maxWidth: Int): String = WorldPanelRenderer.fitText(textRenderer, name, maxWidth)

    /**
     * 從 client player list 解析名稱，AI 與離線玩家使用穩定 fallback。
     *
     * @param player 要解析名稱的玩家快照。
     * @return 玩家顯示名稱。
     */
    private fun resolvePlayerName(player: MatchSettlementPlayerSnapshot): String = playerNames.resolve(currentTableId, player.playerId, player.isAi)

    /**
     * 計算面板淡入、閱讀與淡出透明度。
     *
     * @param elapsed 演出已經過的 tick。
     * @param duration 整段演出長度。
     * @return 當前面板透明度。
     */
    private fun panelAlpha(elapsed: Double, duration: Double): Float = WorldPanelRenderer.phaseAlpha(
        elapsed,
        MatchSettlementPresentationEntity.PANEL_FADE_IN_START_TICK.toDouble(),
        MatchSettlementPresentationEntity.FIRST_ROW_REVEAL_TICK.toDouble(),
        duration - MatchSettlementPresentationEntity.FADE_OUT_TICKS,
        duration,
    )

    /**
     * 計算指定揭曉序列 index 的淡入進度。
     *
     * @param elapsed 演出已經過的 tick。
     * @param index 玩家在揭曉序列中的索引。
     * @param interval 相鄰玩家揭曉間隔。
     * @return 此列介於 0 到 1 的顯示進度。
     */
    private fun rowRevealProgress(elapsed: Double, index: Int, interval: Int): Float {
        val start = MatchSettlementPresentationEntity.rowRevealTick(index, interval).toDouble()
        return ((elapsed - start) / MatchSettlementPresentationEntity.ROW_REVEAL_DURATION_TICKS).toFloat().coerceIn(0f, 1f)
    }

    /**
     * 第一名使用略微超越後回到一倍大小的回彈曲線。
     *
     * @param progress 冠軍列揭曉進度。
     * @return 此時的縮放倍率。
     */
    private fun championScale(progress: Float): Float = when {
        progress < 0.7f -> 0.92f + progress / 0.7f * 0.14f
        else -> 1.06f - (progress - 0.7f) / 0.3f * 0.06f
    }

    /**
     * 繪製指定對齊方式的全亮文字。
     *
     * @param text 要繪製的文字。
     * @param anchorX 文字對齊錨點的 X 座標。
     * @param y 文字頂部 Y 座標。
     * @param alignment 水平對齊方式。
     * @param color 文字 ARGB 顏色。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供文字頂點緩衝。
     * @param scale 文字縮放倍率。
     */
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

    /**
     * 將 RGB 與動畫透明度合成 ARGB。
     *
     * @param rgb 不含 alpha 的 RGB 顏色。
     * @param alpha 動畫透明度。
     * @return 合成後的 ARGB 顏色。
     */
    private fun withAlpha(rgb: Int, alpha: Float): Int = WorldPanelRenderer.withAlpha(rgb, alpha)

    override fun getTexture(entity: MatchSettlementPresentationEntity): Identifier? = null

    private enum class Alignment { LEFT, CENTER, RIGHT }

    /**
     * 頒獎面板尺寸及各名次的定位資料。
     *
     * @property panelHalfWidth 面板半寬。
     * @property panelBottom 面板底部 Y 座標。
     * @property placements 以最終名次索引的玩家版位。
     */
    private data class Layout(
        val panelHalfWidth: Float,
        val panelBottom: Float,
        val placements: Map<Int, Placement>,
    )

    /**
     * 單一名次在頒獎面板上的中心位置與名稱寬度。
     *
     * @property x 水平中心位置。
     * @property y 垂直位置。
     * @property nameWidth 玩家名稱可用寬度。
     */
    private data class Placement(val x: Float, val y: Float, val nameWidth: Int)

    private companion object {
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
