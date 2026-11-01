package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongBotArmAnimation
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongBotEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongStoolSeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.BuiltInAiCharacters
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.BuiltInMinecraftTileAppearances
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.screen.PlayerScreenHandler
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis
import kotlin.math.PI
import kotlin.math.sin

/**
 * 沿用標準牌體模型，搭配方塊短手腳呈現麻將機器人，不讀取任何牌局私有狀態。
 *
 * @param context 客戶端 renderer 建立情境。
 */
class MahjongBotEntityRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<MahjongBotEntity>(context) {
    /** 一對手臂共用的小方柱模型。 */
    private val arm = LimbDimensions(
        width = ARM_WIDTH,
        length = ARM_LENGTH,
        depth = ARM_WIDTH,
    )

    /** 一對短腿共用的小方柱模型。 */
    private val leg = LimbDimensions(
        width = LEG_WIDTH,
        length = LEG_LENGTH,
        depth = LEG_DEPTH,
    )

    /** 資源重載後也從同一個管理器取得本輪模型，不快取舊烘焙結果。 */
    private val modelManager = context.modelManager

    /** 以標準烘焙模型及圖集畫出四肢。 */
    private val blockModelRenderer = context.blockRenderManager.modelRenderer

    init {
        shadowRadius = SHADOW_RADIUS
    }

    /** 依乘坐關係切換腿部姿勢，依同步序號繪製短暫揮手。 */
    override fun render(
        entity: MahjongBotEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        if (entity.isInvisible) return
        val seated = entity.hasVehicle()
        val hipY = if (seated) MahjongStoolSeatGeometry.SEATED_BOTTOM_HEIGHT.toFloat() else LEG_LENGTH
        val swingProgress = entity.swingProgress(tickDelta)
        val swing = MahjongBotArmAnimation.swingDegrees(swingProgress)
        val phase = (entity.uuid.hashCode().toLong() and IDLE_PHASE_MASK).toDouble() / IDLE_PHASE_MASK * (2.0 * PI)
        val idleTime = (entity.age + tickDelta).toDouble() * IDLE_SPEED + phase
        val idleLeft = sin(idleTime).toFloat() * IDLE_SWING_ANGLE
        val idleRight = sin(idleTime + PI).toFloat() * IDLE_SWING_ANGLE
        matrices.push()
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f - yaw))
        renderLeg(-LEG_OFFSET, hipY, seated, SEATED_LEG_SPREAD, matrices, vertexConsumers, light)
        renderLeg(LEG_OFFSET, hipY, seated, -SEATED_LEG_SPREAD, matrices, vertexConsumers, light)
        matrices.push()
        matrices.translate(0f, hipY, 0f)
        renderLimb(arm, -ARM_OFFSET, SHOULDER_HEIGHT, idleLeft, matrices, vertexConsumers, light)
        renderLimb(
            dimensions = arm,
            x = ARM_OFFSET,
            y = SHOULDER_HEIGHT,
            angle = swing + idleRight,
            matrices = matrices,
            consumers = vertexConsumers,
            light = light,
            yaw = MahjongBotArmAnimation.yawDegrees(swingProgress),
            roll = MahjongBotArmAnimation.rollDegrees(swingProgress),
        )
        matrices.translate(0f, BODY_HEIGHT / 2f, 0f)
        matrices.scale(BODY_SCALE, BODY_SCALE, BODY_SCALE)
        MahjongTileItemRenderer.renderWithAppearance(
            appearanceId = BuiltInMinecraftTileAppearances.DEFAULT.id,
            assetKey = entity.faceAssetKey,
            mode = ModelTransformationMode.HEAD,
            matrices = matrices,
            vertexConsumers = vertexConsumers,
            light = light,
            overlay = OverlayTexture.DEFAULT_UV,
        )
        matrices.pop()
        matrices.pop()
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
    }

    /**
     * 坐姿時先將腿部向外展，再往前伸；站姿維持直立。
     *
     * @param x 臀部水平位置。
     * @param y 臀部高度。
     * @param seated 是否正在乘坐。
     * @param spread 坐姿外展角度。
     * @param matrices 目前模型矩陣。
     * @param consumers 頂點緩衝。
     * @param light 世界光照。
     */
    private fun renderLeg(
        x: Float,
        y: Float,
        seated: Boolean,
        spread: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
    ) {
        matrices.push()
        matrices.translate(x, y, 0f)
        if (seated) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(spread))
        renderLimb(leg, 0f, 0f, if (seated) SEATED_LEG_ANGLE else 0f, matrices, consumers, light)
        matrices.pop()
    }

    /**
     * 在局部位置繪製一條手臂或腿。
     *
     * @param dimensions 方柱尺寸。
     * @param x 肩膀或臀部的水平位置。
     * @param y 肩膀或臀部的高度。
     * @param angle 相對直立方向的前擺角度。
     * @param matrices 目前模型矩陣。
     * @param consumers 頂點緩衝。
     * @param light 世界光照。
     * @param yaw 手臂的水平旋轉角度。
     * @param roll 手臂向身體內側的旋轉角度。
     */
    private fun renderLimb(
        dimensions: LimbDimensions,
        x: Float,
        y: Float,
        angle: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
        yaw: Float = 0f,
        roll: Float = 0f,
    ) {
        matrices.push()
        matrices.translate(x, y, 0f)
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(roll))
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw))
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle))
        matrices.scale(dimensions.width, dimensions.length, dimensions.depth)
        matrices.translate(-0.5, -1.0, -0.5)
        val model = (modelManager as FabricBakedModelManager).getModel(LIMB_MODEL_ID) ?: modelManager.missingModel
        blockModelRenderer.render(
            matrices.peek(),
            consumers.getBuffer(RenderLayer.getEntityCutout(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE)),
            null,
            model,
            1f,
            1f,
            1f,
            light,
            OverlayTexture.DEFAULT_UV,
        )
        matrices.pop()
    }

    /** 牌體模型從方塊材質圖集選取自己的公開材質。 */
    override fun getTexture(entity: MahjongBotEntity): Identifier = PlayerScreenHandler.BLOCK_ATLAS_TEXTURE

    /**
     * 從關節向下延伸的方柱尺寸；各面材質由共用模型指定。
     *
     * @property width 世界寬度。
     * @property length 世界長度。
     * @property depth 世界厚度。
     */
    private class LimbDimensions(
        val width: Float,
        val length: Float,
        val depth: Float,
    )

    /** 固定角色比例，不改變任何正式麻將牌模型尺寸。 */
    private companion object {
        /** 四肢模型繼承牌體圖集，六面都使用白色區域。 */
        val LIMB_MODEL_ID: Identifier = Identifier(BuiltInAiCharacters.TILE_MASCOT_LIMB_MODEL_ID)

        /** 牌體世界高度。 */
        const val BODY_HEIGHT: Float = 0.8f

        /** 標準牌體 HEAD 變換後的高度為 0.15 格。 */
        const val BODY_SCALE: Float = BODY_HEIGHT / 0.15f

        /** 肩膀在牌體上方三分之一附近，不與牌頂齊平。 */
        const val SHOULDER_HEIGHT: Float = BODY_HEIGHT * 0.65f

        /** 坐姿左右各向外偏開的角度。 */
        const val SEATED_LEG_SPREAD: Float = 12f

        /** 待機擺臂每 tick 的相位增量。 */
        const val IDLE_SPEED: Double = 0.045

        /** 小幅待機擺動角度。 */
        const val IDLE_SWING_ANGLE: Float = 2.5f

        /** 身分只用於各角色待機相位，不參與遊戲亂數。 */
        const val IDLE_PHASE_MASK: Long = 0xffffffffL

        /** 手臂方柱寬度。 */
        const val ARM_WIDTH: Float = 0.20f

        /** 手臂長度。 */
        const val ARM_LENGTH: Float = 0.40f

        /** 肩膀與牌體中心的水平距離。 */
        const val ARM_OFFSET: Float = 0.40f

        /** 腿部方柱寬度。 */
        const val LEG_WIDTH: Float = 0.28f

        /** 加深的短腿厚度。 */
        const val LEG_DEPTH: Float = 0.32f

        /** 腿長。 */
        const val LEG_LENGTH: Float = 0.38f

        /** 腿部左右距離。 */
        const val LEG_OFFSET: Float = 0.16f

        /** 坐姿往前伸腿的角度。 */
        const val SEATED_LEG_ANGLE: Float = 90f

        /** 地面陰影半徑。 */
        const val SHADOW_RADIUS: Float = 0.3f
    }
}
