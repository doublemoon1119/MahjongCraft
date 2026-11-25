package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

import com.doublemoon1119.mahjongcraft.platform.minecraft.character.BuiltInAiCharacters
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongBotArmAnimation
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongBotEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongStoolSeatGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientModels
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla.resourceId
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.BuiltInMinecraftTileAppearances
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.item.ItemDisplayContext
import kotlin.math.PI
import kotlin.math.sin

/**
 * 沿用標準牌體模型，搭配方塊短手腳呈現麻將機器人，不讀取任何牌局私有狀態。
 *
 * @param context 客戶端 renderer 建立情境。
 */
class MahjongBotEntityRenderer(
    context: EntityRendererProvider.Context,
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
    private val blockModelRenderer = context.blockRenderDispatcher.modelRenderer

    init {
        shadowRadius = SHADOW_RADIUS
    }

    /** 依乘坐關係切換腿部姿勢，依同步序號繪製短暫揮手。 */
    override fun render(
        entity: MahjongBotEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
    ) {
        renderPose(
            entity = entity,
            yaw = yaw,
            tickDelta = tickDelta,
            matrices = matrices,
            vertexConsumers = vertexConsumers,
            light = light,
            seated = entity.isPassenger,
        )
    }

    /**
     * 以指定姿勢繪製機器人。不在世界中的預覽實體不能乘坐，由呼叫端直接決定是否坐著。
     *
     * @param entity 要繪製的機器人。
     * @param yaw 水平朝向。
     * @param tickDelta 本幀 tick 進度。
     * @param matrices 目前模型矩陣，原點在腳底。
     * @param vertexConsumers 頂點緩衝。
     * @param light 光照。
     * @param seated 是否以坐姿繪製。
     * @param swingProgress 揮手進度，0 到 1。預設依實體收到的揮手通知計算。
     * @param alpha 0 到 1 之間的不透明度。小於 1 時以半透明圖層畫出淡化的機器人。
     */
    fun renderPose(
        entity: MahjongBotEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
        seated: Boolean,
        swingProgress: Float = entity.swingProgress(tickDelta),
        alpha: Float = 1f,
    ) {
        if (entity.isInvisible) return
        val hipY = if (seated) MahjongStoolSeatGeometry.SEATED_BOTTOM_HEIGHT.toFloat() else LEG_LENGTH
        val swing = MahjongBotArmAnimation.swingDegrees(swingProgress)
        val phase = (entity.uuid.hashCode().toLong() and IDLE_PHASE_MASK).toDouble() / IDLE_PHASE_MASK * (2.0 * PI)
        val idleTime = (entity.tickCount + tickDelta).toDouble() * IDLE_SPEED + phase
        val idleLeft = sin(idleTime).toFloat() * IDLE_SWING_ANGLE
        val idleRight = sin(idleTime + PI).toFloat() * IDLE_SWING_ANGLE
        matrices.pushPose()
        matrices.mulPose(Axis.YP.rotationDegrees(180f - yaw))
        renderLeg(-LEG_OFFSET, hipY, seated, SEATED_LEG_SPREAD, matrices, vertexConsumers, light, alpha)
        renderLeg(LEG_OFFSET, hipY, seated, -SEATED_LEG_SPREAD, matrices, vertexConsumers, light, alpha)
        matrices.pushPose()
        matrices.translate(0f, hipY, 0f)
        renderLimb(arm, -ARM_OFFSET, SHOULDER_HEIGHT, idleLeft, matrices, vertexConsumers, light, alpha = alpha)
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
            alpha = alpha,
        )
        matrices.translate(0f, BODY_HEIGHT / 2f, 0f)
        matrices.scale(BODY_SCALE, BODY_SCALE, BODY_SCALE)
        MahjongTileItemRenderer.renderWithAppearance(
            appearanceId = BuiltInMinecraftTileAppearances.DEFAULT.id,
            assetKey = entity.faceAssetKey,
            mode = ItemDisplayContext.HEAD,
            matrices = matrices,
            vertexConsumers = vertexConsumers,
            light = light,
            overlay = OverlayTexture.NO_OVERLAY,
            alpha = alpha,
        )
        matrices.popPose()
        matrices.popPose()
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
    }

    /**
     * 坐姿時先將腿部向外展，再往前伸。站姿維持直立。
     *
     * @param x 臀部水平位置。
     * @param y 臀部高度。
     * @param seated 是否正在乘坐。
     * @param spread 坐姿外展角度。
     * @param matrices 目前模型矩陣。
     * @param consumers 頂點緩衝。
     * @param light 世界光照。
     * @param alpha 0 到 1 之間的不透明度。
     */
    private fun renderLeg(
        x: Float,
        y: Float,
        seated: Boolean,
        spread: Float,
        matrices: PoseStack,
        consumers: MultiBufferSource,
        light: Int,
        alpha: Float,
    ) {
        matrices.pushPose()
        matrices.translate(x, y, 0f)
        if (seated) matrices.mulPose(Axis.YP.rotationDegrees(spread))
        renderLimb(leg, 0f, 0f, if (seated) SEATED_LEG_ANGLE else 0f, matrices, consumers, light, alpha = alpha)
        matrices.popPose()
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
     * @param alpha 0 到 1 之間的不透明度。小於 1 時改用半透明圖層。
     */
    private fun renderLimb(
        dimensions: LimbDimensions,
        x: Float,
        y: Float,
        angle: Float,
        matrices: PoseStack,
        consumers: MultiBufferSource,
        light: Int,
        yaw: Float = 0f,
        roll: Float = 0f,
        alpha: Float = 1f,
    ) {
        matrices.pushPose()
        matrices.translate(x, y, 0f)
        matrices.mulPose(Axis.ZP.rotationDegrees(roll))
        matrices.mulPose(Axis.YP.rotationDegrees(yaw))
        matrices.mulPose(Axis.XP.rotationDegrees(angle))
        matrices.scale(dimensions.width, dimensions.length, dimensions.depth)
        matrices.translate(-0.5, -1.0, -0.5)
        val model = ClientModels.find(LIMB_MODEL_ID) ?: modelManager.missingModel
        blockModelRenderer.renderModel(
            matrices.last(),
            if (alpha < 1f) {
                AlphaVertexConsumer(consumers.getBuffer(RenderType.itemEntityTranslucentCull(InventoryMenu.BLOCK_ATLAS)), alpha)
            } else {
                consumers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS))
            },
            null,
            model,
            1f,
            1f,
            1f,
            light,
            OverlayTexture.NO_OVERLAY,
        )
        matrices.popPose()
    }

    /** 牌體模型從方塊材質圖集選取自己的公開材質。 */
    override fun getTextureLocation(entity: MahjongBotEntity): ResourceLocation = InventoryMenu.BLOCK_ATLAS

    /**
     * 從關節向下延伸的方柱尺寸。各面材質由共用模型指定。
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
        val LIMB_MODEL_ID: ResourceLocation = resourceId(BuiltInAiCharacters.TILE_MASCOT_LIMB_MODEL_ID)

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
