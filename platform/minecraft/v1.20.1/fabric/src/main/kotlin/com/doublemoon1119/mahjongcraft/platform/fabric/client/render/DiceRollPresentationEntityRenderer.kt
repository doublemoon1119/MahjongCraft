package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.DiceRollPresentationEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongDicePoint
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis
import kotlin.math.PI
import kotlin.math.sin

/** 在桌面上方以 billboard 呈現整組放大 3D 骰子與合計點數。 */
class DiceRollPresentationEntityRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<DiceRollPresentationEntity>(context) {
    /** 共用原版 item renderer，讓結果模型與桌面骰子使用相同模型及材質。 */
    private val itemRenderer = context.itemRenderer

    /** 共用原版文字 renderer。 */
    private val textRenderer = context.textRenderer

    /** 依絕對遊戲時間繪製結果面板；結果尚未揭示或已結束時不渲染。 */
    override fun render(
        entity: DiceRollPresentationEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        val elapsed = entity.elapsedResultTicks(tickDelta)
        val duration = (entity.endGameTime - entity.revealGameTime).toDouble()
        if (elapsed < 0.0 || elapsed >= duration) return
        val points = entity.points
        if (points.size !in SUPPORTED_DICE_COUNTS) return
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)

        val alpha = panelAlpha(elapsed, duration)
        val entranceScale = entranceScale(elapsed)
        val exitScale = if (elapsed > duration - DiceRollPresentationEntity.FADE_OUT_TICKS) alpha else 1f
        val visualScale = entranceScale * exitScale
        val panelWidth = panelWidth(points.size)

        matrices.push()
        matrices.multiply(dispatcher.rotation)
        matrices.scale(-PIXEL_SCALE, -PIXEL_SCALE, PIXEL_SCALE)
        WorldPanelRenderer.drawBackground(
            -panelWidth / 2f,
            -PANEL_HEIGHT / 2f,
            panelWidth / 2f,
            PANEL_HEIGHT / 2f,
            WorldPanelRenderer.withAlpha(PANEL_RGB, alpha * PANEL_BASE_ALPHA),
            0f,
            matrices,
            vertexConsumers,
        )
        renderDice(points, visualScale, matrices, vertexConsumers, light)
        renderTotal(points.sum(), alpha, matrices, vertexConsumers, light)
        matrices.pop()
    }

    /** 依點數數量等距排列結果骰子。 */
    private fun renderDice(
        points: List<Int>,
        scale: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
    ) {
        val groupWidth = (points.size - 1) * DICE_CENTER_GAP
        points.forEachIndexed { index, pointValue ->
            val centerX = -groupWidth / 2f + index * DICE_CENTER_GAP
            renderDie(MahjongDicePoint.fromValueOrDefault(pointValue), centerX, DICE_CENTER_Y, scale, matrices, consumers, light)
        }
    }

    /** 重用骰子 item model，將指定點數由朝上姿態轉成朝向目前觀看者。 */
    private fun renderDie(
        point: MahjongDicePoint,
        centerX: Float,
        centerY: Float,
        scale: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
    ) {
        matrices.push()
        matrices.translate(centerX.toDouble(), centerY.toDouble(), DICE_Z.toDouble())
        matrices.scale(DICE_PIXEL_SIZE * scale, DICE_PIXEL_SIZE * scale, DICE_PIXEL_SIZE * scale)
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(RESULT_FACE_ROTATION_DEGREES))
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(point.xRotationDegrees))
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(point.yRotationDegrees))
        matrices.translate(0.0, DICE_MODEL_CENTER_OFFSET_Y, 0.0)
        itemRenderer.renderItem(
            DICE_STACK,
            ModelTransformationMode.HEAD,
            light,
            OverlayTexture.DEFAULT_UV,
            matrices,
            consumers,
            null,
            point.value,
        )
        matrices.pop()
    }

    /** 在骰子下方置中繪製本地化合計。 */
    private fun renderTotal(
        total: Int,
        alpha: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
    ) {
        val text = Text.translatable(MinecraftMessageKeys.DICE_ROLL_TOTAL, total)
        val x = -textRenderer.getWidth(text) / 2f
        WorldPanelRenderer.drawText(
            textRenderer,
            text,
            x,
            TOTAL_TEXT_Y,
            WorldPanelRenderer.withAlpha(TOTAL_TEXT_RGB, alpha),
            TEXT_Z,
            light,
            matrices,
            consumers,
        )
    }

    /** 依淡入、閱讀與淡出階段取得面板透明度。 */
    private fun panelAlpha(elapsed: Double, duration: Double): Float = when {
        elapsed < DiceRollPresentationEntity.FADE_IN_TICKS ->
            (elapsed / DiceRollPresentationEntity.FADE_IN_TICKS).toFloat().coerceIn(0f, 1f)
        elapsed > duration - DiceRollPresentationEntity.FADE_OUT_TICKS ->
            ((duration - elapsed) / DiceRollPresentationEntity.FADE_OUT_TICKS).toFloat().coerceIn(0f, 1f)
        else -> 1f
    }

    /** 淡入時加入輕微放大回彈，提升結果落定感。 */
    private fun entranceScale(elapsed: Double): Float {
        val progress = (elapsed / ENTRANCE_TICKS).coerceIn(0.0, 1.0)
        return (ENTRANCE_START_SCALE + (1.0 - ENTRANCE_START_SCALE) * progress + sin(progress * PI) * ENTRANCE_OVERSHOOT).toFloat()
    }

    /** 依骰子數量計算對稱背景寬度。 */
    private fun panelWidth(diceCount: Int): Float = HORIZONTAL_PADDING * 2f + DICE_PIXEL_SIZE + (diceCount - 1) * DICE_CENTER_GAP

    /** ItemRenderer 自行解析材質，因此 entity renderer 不提供單一 texture。 */
    override fun getTexture(entity: DiceRollPresentationEntity): Identifier? = null

    companion object {
        /** 支援的骰子數量。 */
        private val SUPPORTED_DICE_COUNTS = 2..3

        /** 將 pixel layout 轉成世界尺寸的 billboard 縮放。 */
        private const val PIXEL_SCALE = 0.021f

        /** 單顆結果骰子的畫面尺寸，約為桌面骰子的四倍。 */
        private const val DICE_PIXEL_SIZE = 22f

        /** 相鄰骰子中心距離。 */
        private const val DICE_CENTER_GAP = 31f

        /** 背景左右 padding。 */
        private const val HORIZONTAL_PADDING = 14f

        /** 面板固定高度。 */
        private const val PANEL_HEIGHT = 64f

        /** 骰子中心的垂直位置。 */
        private const val DICE_CENTER_Y = -8f

        /** 合計文字的垂直位置。 */
        private const val TOTAL_TEXT_Y = 15f

        /** 骰子相對背景向觀看者移動的距離。 */
        private const val DICE_Z = -0.04f

        /** 文字相對背景向觀看者移動的距離。 */
        private const val TEXT_Z = -0.03f

        /** 把原本朝上的結果面轉到 billboard 正面的額外旋轉。 */
        private const val RESULT_FACE_ROTATION_DEGREES = -90f

        /** 將骰子模型的實際幾何中心對齊 item renderer 旋轉原點。 */
        private const val DICE_MODEL_CENTER_OFFSET_Y = 3.0 / 16.0

        /** 入場回彈時長。 */
        private const val ENTRANCE_TICKS = 8.0

        /** 入場起始縮放。 */
        private const val ENTRANCE_START_SCALE = 0.82

        /** 入場回彈額外振幅。 */
        private const val ENTRANCE_OVERSHOOT = 0.07

        /** 面板不透明部分的 RGB。 */
        private const val PANEL_RGB = 0x1A2232

        /** 與 Round Info／Player Info `0xB0` 背景 alpha 一致的比例。 */
        private const val PANEL_BASE_ALPHA = 176f / 255f

        /** 合計文字不透明部分的 RGB。 */
        private const val TOTAL_TEXT_RGB = 0xFFD966

        /** 共用骰子物品模型。 */
        private val DICE_STACK = ItemStack(ModItems.MAHJONG_DICE)
    }
}
