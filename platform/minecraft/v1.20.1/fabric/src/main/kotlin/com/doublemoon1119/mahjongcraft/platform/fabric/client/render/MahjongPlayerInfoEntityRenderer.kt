package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.resolvedPlayerNameText
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongPlayerInfoEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.seatIndexToTableSide
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis
import kotlin.math.max
import kotlin.uuid.Uuid

/**
 * 從單一桌級 entity 在各固定座位頭頂後方繪製雙面公開資訊。
 *
 * @param context Minecraft renderer factory 提供的 client context。
 * @param portraits 繪製各玩家頭像的共用 renderer。
 * @param indicatorTextResolver 將公開指示器轉成文字與顏色。
 * @param configStore 提供玩家資訊面板可見性設定。
 */
class MahjongPlayerInfoEntityRenderer(
    context: EntityRendererFactory.Context,
    private val portraits: PlayerPortraitRenderer,
    private val indicatorTextResolver: PublicPlayerIndicatorTextResolver,
    private val configStore: MahjongClientConfigStore,
) : EntityRenderer<MahjongPlayerInfoEntity>(context) {
    private val textRenderer = context.textRenderer

    override fun render(
        entity: MahjongPlayerInfoEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        if (!configStore.current.presentationVisibility.playerInfoEnabled) return
        if (entity.isInvisible) return
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        val players = entity.players.sortedBy { it.seatIndex }
        if (players.isEmpty()) return
        val width = measureSharedWidth(players, entity.dealerPlayerId)
        players.forEach { player ->
            val side = rotateSide(seatIndexToTableSide(player.seatIndex), entity.tableFacing.ordinal)
            matrices.push()
            val offset = sideOffset(side)
            matrices.translate(offset.first, PANEL_HEIGHT, offset.second)
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(sideYaw(side)))
            matrices.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE)
            renderFace(player, entity.dealerPlayerId == player.playerId, width, light, matrices, vertexConsumers)
            matrices.push()
            matrices.translate(0.0, 0.0, BACK_FACE_OFFSET.toDouble())
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f))
            renderFace(player, entity.dealerPlayerId == player.playerId, width, light, matrices, vertexConsumers)
            matrices.pop()
            matrices.pop()
        }
    }

    /**
     * 繪製單一玩家的正面資訊列與其餘公開指示器。
     *
     * @param player 要顯示的座位資訊。
     * @param isDealer 該玩家是否為莊家。
     * @param width 所有玩家共用的面板寬度。
     * @param light 世界光照值。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供面板與文字頂點緩衝。
     */
    private fun renderFace(
        player: MahjongPlayerInfoEntry,
        isDealer: Boolean,
        width: Float,
        light: Int,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val indicators = player.indicators.map(indicatorTextResolver::resolve)
        val height = PANEL_PADDING * 2 + FIRST_ROW_HEIGHT + SCORE_ROW_HEIGHT + indicators.size * INDICATOR_ROW_HEIGHT
        val left = -width / 2f
        WorldPanelRenderer.drawBackground(left, -height / 2f, left + width, height / 2f, BACKGROUND, 0f, matrices, consumers)
        val top = -height / 2f + PANEL_PADDING
        portraits.render(player.playerId, player.isAi, left + PANEL_PADDING, top, PORTRAIT_SIZE, 1f, TEXT_Z, matrices, consumers)
        val nameX = left + PANEL_PADDING + PORTRAIT_SIZE + PORTRAIT_GAP
        val suffixWidth = textRenderer.getWidth(windText(player.seatWind)).toFloat() +
            if (isDealer) textRenderer.getWidth(DEALER_MARK) + COLUMN_GAP else 0f
        val displayName = resolvedPlayerNameText(player.playerName).string
        val fitted = WorldPanelRenderer.fitText(textRenderer, displayName, (left + width - PANEL_PADDING - nameX - suffixWidth).toInt())
        WorldPanelRenderer.drawText(textRenderer, Text.literal(fitted), nameX, top + 1f, NAME_COLOR, TEXT_Z, light, matrices, consumers)
        var right = left + width - PANEL_PADDING
        if (isDealer) {
            WorldPanelRenderer.drawText(textRenderer, Text.literal(DEALER_MARK), right - textRenderer.getWidth(DEALER_MARK), top + 1f, DEALER_COLOR, TEXT_Z, light, matrices, consumers)
            right -= textRenderer.getWidth(DEALER_MARK) + COLUMN_GAP
        }
        val wind = windText(player.seatWind)
        WorldPanelRenderer.drawText(textRenderer, wind, right - textRenderer.getWidth(wind), top + 1f, WIND_COLOR, TEXT_Z, light, matrices, consumers)
        val scoreY = top + FIRST_ROW_HEIGHT
        val score = Text.literal(player.score.toString())
        WorldPanelRenderer.drawText(textRenderer, score, left + (width - textRenderer.getWidth(score)) / 2f, scoreY, SCORE_COLOR, TEXT_Z, light, matrices, consumers)
        indicators.forEachIndexed { index, (text, color) ->
            WorldPanelRenderer.drawText(
                textRenderer, text, left + (width - textRenderer.getWidth(text)) / 2f,
                scoreY + SCORE_ROW_HEIGHT + index * INDICATOR_ROW_HEIGHT, color, TEXT_Z, light, matrices, consumers,
            )
        }
    }

    /**
     * 依名稱、分數與指示器文字量測所有玩家共用的面板寬度。
     *
     * @param players 本桌公開的玩家資訊。
     * @param dealerPlayerId 莊家玩家 ID；若與某位玩家相符，量測時預留莊家標記空間。
     * @return 介於最小與最大寬度之間的共用面板寬度。
     */
    private fun measureSharedWidth(
        players: List<MahjongPlayerInfoEntry>,
        dealerPlayerId: Uuid?,
    ): Float {
        var width = MIN_PANEL_WIDTH
        players.forEach { player ->
            val displayName = resolvedPlayerNameText(player.playerName).string
            val header = PANEL_PADDING * 2 + PORTRAIT_SIZE + PORTRAIT_GAP +
                textRenderer.getWidth(displayName.take(MAX_NAME_CHARACTERS)) + COLUMN_GAP + textRenderer.getWidth(windText(player.seatWind)) +
                if (player.playerId == dealerPlayerId) textRenderer.getWidth(DEALER_MARK) + COLUMN_GAP else 0f
            val score = PANEL_PADDING * 2 + textRenderer.getWidth(player.score.toString())
            val indicator = player.indicators.maxOfOrNull {
                PANEL_PADDING * 2 + textRenderer.getWidth(indicatorTextResolver.resolve(it).first)
            } ?: 0f
            width = max(width, max(header, max(score, indicator)))
        }
        return width.coerceAtMost(MAX_PANEL_WIDTH)
    }

    /**
     * 將座風轉成對應的本地化文字。
     *
     * @param wind 要顯示的座風。
     * @return 對應風牌翻譯 key 的文字。
     */
    private fun windText(wind: Wind): Text = Text.translatable(
        when (wind) {
            Wind.EAST -> MinecraftMessageKeys.TILE_HONOR_EAST
            Wind.SOUTH -> MinecraftMessageKeys.TILE_HONOR_SOUTH
            Wind.WEST -> MinecraftMessageKeys.TILE_HONOR_WEST
            Wind.NORTH -> MinecraftMessageKeys.TILE_HONOR_NORTH
        },
    )

    /**
     * 依桌面朝向將座位側別旋轉指定的四方位步數。
     *
     * @param side 未旋轉的桌面側別。
     * @param steps 順時針索引位移；會依四個方位取餘數。
     * @return 旋轉後的桌面側別。
     */
    private fun rotateSide(side: MahjongTableSide, steps: Int): MahjongTableSide {
        val order = listOf(MahjongTableSide.NORTH, MahjongTableSide.EAST, MahjongTableSide.SOUTH, MahjongTableSide.WEST)
        return order[(order.indexOf(side) + steps).mod(order.size)]
    }

    /**
     * 取得指定側別面板相對桌面中心的水平偏移。
     *
     * @param side 面板所屬桌面側別。
     * @return X、Z 軸偏移量。
     */
    private fun sideOffset(side: MahjongTableSide): Pair<Double, Double> = when (side) {
        MahjongTableSide.NORTH -> 0.0 to -PANEL_DISTANCE
        MahjongTableSide.EAST -> PANEL_DISTANCE to 0.0
        MahjongTableSide.SOUTH -> 0.0 to PANEL_DISTANCE
        MahjongTableSide.WEST -> -PANEL_DISTANCE to 0.0
    }

    /**
     * 取得指定側別面板朝向觀看者的 yaw 角度。
     *
     * @param side 面板所屬桌面側別。
     * @return 面板旋轉角度（度）。
     */
    private fun sideYaw(side: MahjongTableSide): Float = when (side) {
        MahjongTableSide.NORTH -> 180f
        MahjongTableSide.EAST -> -90f
        MahjongTableSide.SOUTH -> 0f
        MahjongTableSide.WEST -> 90f
    }

    override fun getTexture(entity: MahjongPlayerInfoEntity): Identifier? = null

    private companion object {
        const val TEXT_SCALE = 0.014f
        const val PANEL_HEIGHT = 2.7
        const val PANEL_DISTANCE = 3.1
        const val BACK_FACE_OFFSET = 0.02f
        const val TEXT_Z = -0.02f
        const val PANEL_PADDING = 7f
        const val PORTRAIT_SIZE = 12f
        const val PORTRAIT_GAP = 5f
        const val COLUMN_GAP = 5f
        const val FIRST_ROW_HEIGHT = 15f
        const val SCORE_ROW_HEIGHT = 12f
        const val INDICATOR_ROW_HEIGHT = 11f
        const val MIN_PANEL_WIDTH = 104f
        const val MAX_PANEL_WIDTH = 176f
        const val MAX_NAME_CHARACTERS = 16
        const val BACKGROUND = 0xB01A2232.toInt()
        const val NAME_COLOR = 0xFFFFFFFF.toInt()
        const val WIND_COLOR = 0xFFFFD45A.toInt()
        const val DEALER_COLOR = 0xFFFF9D45.toInt()
        const val SCORE_COLOR = 0xFFF3F3F3.toInt()
        const val DEALER_MARK = "●"
    }
}
