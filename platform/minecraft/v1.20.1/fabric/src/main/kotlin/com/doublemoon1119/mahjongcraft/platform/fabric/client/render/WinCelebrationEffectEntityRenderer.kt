package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongVisualEffectKeys
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationEffectEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.render.entity.model.TridentEntityModel
import net.minecraft.client.render.item.ItemRenderer
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis
import org.joml.Matrix4f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * 以小型帶狀閃電擊中胡牌張，接續短暫環繞電弧與錯時擴張的波紋；所有幾何皆保持牌面中央淨空。
 */
class WinCelebrationEffectEntityRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<WinCelebrationEffectEntity>(context) {
    /** 原版三叉戟實體模型；直接使用其明確座標，讓戟尖能穩定對準胡牌張。 */
    private val tridentModel: TridentEntityModel = TridentEntityModel(context.getPart(EntityModelLayers.TRIDENT))

    /** 依同步的絕對時間提交引雷三叉戟、閃電、充能電弧與波紋幾何。 */
    override fun render(
        entity: WinCelebrationEffectEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        if (entity.effectKey != MahjongVisualEffectKeys.WIN_CELEBRATION || !entity.isActive(tickDelta)) return
        val elapsedTicks = entity.world.time.toDouble() + tickDelta - entity.startGameTime
        renderTrident(entity, elapsedTicks, matrices, vertexConsumers, light)

        val lightningElapsed = elapsedTicks - LIGHTNING_START_TICK
        if (lightningElapsed < 0.0) return
        val progress = (lightningElapsed / LIGHTNING_DURATION_TICKS).coerceIn(0.0, 1.0)
        val envelope = sin(progress * PI).coerceAtLeast(0.0)
        if (envelope <= 0.0) return

        val rotation = seedRotation(entity.animationSeed) + progress * FULL_ROTATION_RADIANS
        val entry = matrices.peek()
        val lightningBuffer = vertexConsumers.getBuffer(RenderLayer.getLightning())
        lightningStrike(
            buffer = lightningBuffer,
            positionMatrix = entry.positionMatrix,
            effectProgress = progress,
            seed = entity.animationSeed,
        )
        groundDischarge(
            buffer = lightningBuffer,
            positionMatrix = entry.positionMatrix,
            effectProgress = progress,
            seed = entity.animationSeed,
            rotation = seedRotation(entity.animationSeed),
        )
        repeat(RIPPLE_COUNT) { index ->
            ripple(
                buffer = lightningBuffer,
                positionMatrix = entry.positionMatrix,
                rotation = rotation,
                effectProgress = progress,
                envelope = envelope,
                rippleIndex = index,
            )
        }
    }

    /** 先讓縮小的原版附魔三叉戟落下，再以快速衰減的阻尼擺動表現插入後的顫動。 */
    private fun renderTrident(
        entity: WinCelebrationEffectEntity,
        elapsedTicks: Double,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
    ) {
        if (elapsedTicks !in 0.0..<TRIDENT_SHRINK_END_TICK.toDouble()) return
        val fallProgress = (elapsedTicks / TRIDENT_FALL_DURATION_TICKS).coerceIn(0.0, 1.0)
        val easedFall = 1.0 - (1.0 - fallProgress) * (1.0 - fallProgress) * (1.0 - fallProgress)
        val settleProgress = ((elapsedTicks - TRIDENT_FALL_DURATION_TICKS) / TRIDENT_SETTLE_DURATION_TICKS).coerceIn(0.0, 1.0)
        val rawShrinkProgress =
            ((elapsedTicks - TRIDENT_SHRINK_START_TICK) / TRIDENT_SHRINK_DURATION_TICKS).coerceIn(0.0, 1.0)
        val shrinkProgress = rawShrinkProgress * rawShrinkProgress * (3.0 - 2.0 * rawShrinkProgress)
        val tridentScale = TRIDENT_SCALE * (1.0 - shrinkProgress).toFloat()
        val wobble = if (elapsedTicks < TRIDENT_FALL_DURATION_TICKS) {
            0.0
        } else {
            sin(settleProgress * TRIDENT_WOBBLE_CYCLES * PI * 2.0) *
                exp(-settleProgress * TRIDENT_WOBBLE_DAMPING) * TRIDENT_WOBBLE_DEGREES
        }
        val seededYaw = Math.floorMod(entity.animationSeed, TRIDENT_YAW_STEPS).toFloat() /
            TRIDENT_YAW_STEPS * FULL_ROTATION_DEGREES
        val seededTilt =
            TRIDENT_MIN_INSERT_ANGLE_DEGREES +
                (
                    (seededOffset(entity.animationSeed, TRIDENT_TILT_SALT) + 1.0) / 2.0 *
                        (TRIDENT_MAX_INSERT_ANGLE_DEGREES - TRIDENT_MIN_INSERT_ANGLE_DEGREES)
                    ).toFloat()
        val fallingTilt = seededTilt * (1.0 - easedFall).toFloat()

        matrices.push()
        matrices.translate(0.0, TRIDENT_START_HEIGHT + (TRIDENT_END_HEIGHT - TRIDENT_START_HEIGHT) * easedFall, 0.0)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(seededYaw))
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(fallingTilt + wobble.toFloat()))
        matrices.scale(tridentScale, tridentScale, tridentScale)
        matrices.translate(0.0, TRIDENT_TIP_TO_MODEL_ORIGIN, 0.0)
        val buffer = ItemRenderer.getDirectItemGlintConsumer(
            consumers,
            tridentModel.getLayer(TridentEntityModel.TEXTURE),
            false,
            true,
        )
        tridentModel.render(
            matrices,
            buffer,
            light,
            OverlayTexture.DEFAULT_UV,
            1.0f,
            1.0f,
            1.0f,
            1.0f,
        )
        matrices.pop()
    }

    /** 以三層半透明帶狀幾何疊出縮小版原版閃電，並在前段切換數次形狀與明暗。 */
    private fun lightningStrike(
        buffer: VertexConsumer,
        positionMatrix: Matrix4f,
        effectProgress: Double,
        seed: Long,
    ) {
        val strikeProgress = effectProgress / LIGHTNING_DURATION_FRACTION
        if (strikeProgress !in 0.0..<1.0) return

        val flash = kotlin.math.abs(sin(strikeProgress * LIGHTNING_FLASH_COUNT * PI))
        if (flash < LIGHTNING_VISIBLE_THRESHOLD) return
        val shapeIndex = (strikeProgress * LIGHTNING_SHAPE_COUNT).toInt()
        val points = lightningPoints(seed, shapeIndex)
        for (index in 0 until points.lastIndex) {
            val start = points[index]
            val end = points[index + 1]
            lightningSegment(
                buffer, positionMatrix, start, end, LIGHTNING_OUTER_WIDTH, LIGHTNING_OUTER_ALPHA,
                LIGHTNING_OUTER_RED, LIGHTNING_OUTER_GREEN, LIGHTNING_OUTER_BLUE, flash,
            )
            lightningSegment(
                buffer, positionMatrix, start, end, LIGHTNING_MIDDLE_WIDTH, LIGHTNING_MIDDLE_ALPHA,
                LIGHTNING_MIDDLE_RED, LIGHTNING_MIDDLE_GREEN, LIGHTNING_MIDDLE_BLUE, flash,
            )
            lightningSegment(
                buffer, positionMatrix, start, end, LIGHTNING_CORE_WIDTH, LIGHTNING_CORE_ALPHA,
                LIGHTNING_CORE_RED, LIGHTNING_CORE_GREEN, LIGHTNING_CORE_BLUE, flash,
            )
        }
    }

    /** 由持久化 seed 與離散形狀編號產生相連的閃電節點；底端固定命中胡牌張中央。 */
    private fun lightningPoints(
        seed: Long,
        shapeIndex: Int,
    ): List<LightningPoint> = List(LIGHTNING_SEGMENTS + 1) { index ->
        val verticalProgress = index.toDouble() / LIGHTNING_SEGMENTS
        val displacementEnvelope = sin(verticalProgress * PI)
        val salt = shapeIndex * LIGHTNING_SEGMENTS + index
        LightningPoint(
            x = seededOffset(seed, salt * 2) * LIGHTNING_MAX_OFFSET * displacementEnvelope,
            y = LIGHTNING_BOTTOM_HEIGHT + LIGHTNING_HEIGHT * verticalProgress,
            z = seededOffset(seed, salt * 2 + 1) * LIGHTNING_MAX_OFFSET * displacementEnvelope,
        )
    }.reversed()

    /** 每層各畫兩片互相垂直的帶狀面，從各視角都能看到近似原版的柱狀閃電。 */
    private fun lightningSegment(
        buffer: VertexConsumer,
        positionMatrix: Matrix4f,
        start: LightningPoint,
        end: LightningPoint,
        width: Double,
        maxAlpha: Int,
        red: Int,
        green: Int,
        blue: Int,
        flash: Double,
    ) {
        val alpha = (maxAlpha * flash).toInt().coerceIn(0, 255)
        lightningQuad(buffer, positionMatrix, start, end, width, 0.0, alpha, red, green, blue)
        lightningQuad(buffer, positionMatrix, start, end, 0.0, width, alpha, red, green, blue)
        val diagonalWidth = width * INVERSE_SQRT_TWO
        lightningQuad(buffer, positionMatrix, start, end, diagonalWidth, diagonalWidth, alpha, red, green, blue)
        lightningQuad(buffer, positionMatrix, start, end, diagonalWidth, -diagonalWidth, alpha, red, green, blue)
    }

    /** 寫入單片不使用材質的半透明閃電帶。 */
    private fun lightningQuad(
        buffer: VertexConsumer,
        positionMatrix: Matrix4f,
        start: LightningPoint,
        end: LightningPoint,
        xWidth: Double,
        zWidth: Double,
        alpha: Int,
        red: Int,
        green: Int,
        blue: Int,
    ) {
        coloredQuad(
            buffer,
            positionMatrix,
            LightningPoint(start.x - xWidth, start.y, start.z - zWidth),
            LightningPoint(end.x - xWidth, end.y, end.z - zWidth),
            LightningPoint(end.x + xWidth, end.y, end.z + zWidth),
            LightningPoint(start.x + xWidth, start.y, start.z + zWidth),
            alpha,
            red,
            green,
            blue,
        )
    }

    /** 寫入正反兩面，避免帶狀幾何因視角或背面剔除而消失。 */
    private fun coloredQuad(
        buffer: VertexConsumer,
        positionMatrix: Matrix4f,
        first: LightningPoint,
        second: LightningPoint,
        third: LightningPoint,
        fourth: LightningPoint,
        alpha: Int,
        red: Int,
        green: Int,
        blue: Int,
    ) {
        listOf(first, second, third, fourth, fourth, third, second, first).forEach { point ->
            lightningVertex(buffer, positionMatrix, point.x, point.y, point.z, alpha, red, green, blue)
        }
    }

    /** 閃電 render layer 使用 position-color 頂點格式。 */
    private fun lightningVertex(
        buffer: VertexConsumer,
        positionMatrix: Matrix4f,
        x: Double,
        y: Double,
        z: Double,
        alpha: Int,
        red: Int,
        green: Int,
        blue: Int,
    ) {
        buffer.vertex(positionMatrix, x.toFloat(), y.toFloat(), z.toFloat())
            .color(red, green, blue, alpha)
            .next()
    }

    /** 將 seed 穩定映射至 -1 到 1，讓每次重載仍得到相同的閃電形狀。 */
    private fun seededOffset(
        seed: Long,
        salt: Int,
    ): Double {
        var value = seed + salt * LIGHTNING_HASH_MULTIPLIER
        value = (value xor (value ushr 30)) * LIGHTNING_HASH_MIX_A
        value = (value xor (value ushr 27)) * LIGHTNING_HASH_MIX_B
        value = value xor (value ushr 31)
        return (value ushr 11) / LIGHTNING_HASH_DENOMINATOR * 2.0 - 1.0
    }

    /** 閃電折線節點。 */
    private data class LightningPoint(
        val x: Double,
        val y: Double,
        val z: Double,
    )

    /** 閃電命中後讓鋸齒狀電流貼近牌桌向外放射，並以短分岔強化接地放電感。 */
    private fun groundDischarge(
        buffer: VertexConsumer,
        positionMatrix: Matrix4f,
        effectProgress: Double,
        seed: Long,
        rotation: Double,
    ) {
        val dischargeProgress = (effectProgress - DISCHARGE_START_FRACTION) / DISCHARGE_DURATION_FRACTION
        if (dischargeProgress !in 0.0..<1.0) return

        val envelope = sin(dischargeProgress * PI).coerceAtLeast(0.0)
        val flicker = DISCHARGE_FLICKER_MIN + (1.0 - DISCHARGE_FLICKER_MIN) *
            kotlin.math.abs(sin(dischargeProgress * DISCHARGE_FLICKER_COUNT * PI))
        val intensity = envelope * flicker
        val shapeIndex = (dischargeProgress * DISCHARGE_SHAPE_COUNT).toInt()
        repeat(DISCHARGE_RAY_COUNT) { rayIndex ->
            val baseAngle =
                rotation + rayIndex * FULL_ROTATION_RADIANS / DISCHARGE_RAY_COUNT +
                    seededOffset(seed, shapeIndex * 101 + rayIndex) * DISCHARGE_ANGLE_JITTER
            val lengthScale = DISCHARGE_MIN_LENGTH_SCALE +
                (1.0 - DISCHARGE_MIN_LENGTH_SCALE) * ((rayIndex % 3) / 2.0)
            val points = dischargePoints(seed, shapeIndex, rayIndex, baseAngle, lengthScale)
            drawDischargePath(buffer, positionMatrix, points, intensity)

            if (rayIndex % 3 == 0) {
                val branchStart = points[DISCHARGE_BRANCH_START_INDEX]
                val branchAngle = baseAngle + seededOffset(seed, shapeIndex * 313 + rayIndex) * DISCHARGE_BRANCH_ANGLE
                val branchEnd = LightningPoint(
                    branchStart.x + cos(branchAngle) * DISCHARGE_BRANCH_LENGTH,
                    branchStart.y + DISCHARGE_BRANCH_HEIGHT_OFFSET,
                    branchStart.z + sin(branchAngle) * DISCHARGE_BRANCH_LENGTH,
                )
                drawDischargePath(buffer, positionMatrix, listOf(branchStart, branchEnd), intensity * DISCHARGE_BRANCH_ALPHA)
            }
        }
    }

    /** 由中心向外建立一條具側向偏移的接地放電路徑。 */
    private fun dischargePoints(
        seed: Long,
        shapeIndex: Int,
        rayIndex: Int,
        baseAngle: Double,
        lengthScale: Double,
    ): List<LightningPoint> = List(DISCHARGE_SEGMENTS + 1) { index ->
        val pathProgress = index.toDouble() / DISCHARGE_SEGMENTS
        val salt = shapeIndex * 401 + rayIndex * 37 + index
        val radius = DISCHARGE_START_RADIUS + DISCHARGE_LENGTH * lengthScale * pathProgress
        val angle = baseAngle + seededOffset(seed, salt) * DISCHARGE_PATH_JITTER * sin(pathProgress * PI)
        LightningPoint(
            x = cos(angle) * radius,
            y = DISCHARGE_HEIGHT + seededOffset(seed, salt + 701) * DISCHARGE_HEIGHT_JITTER,
            z = sin(angle) * radius,
        )
    }

    /** 以淡藍外光與近白核心疊出一條水平放電路徑。 */
    private fun drawDischargePath(
        buffer: VertexConsumer,
        positionMatrix: Matrix4f,
        points: List<LightningPoint>,
        intensity: Double,
    ) {
        for (index in 0 until points.lastIndex) {
            lightningSegment(
                buffer, positionMatrix, points[index], points[index + 1],
                DISCHARGE_OUTER_WIDTH, DISCHARGE_OUTER_ALPHA,
                LIGHTNING_OUTER_RED, LIGHTNING_OUTER_GREEN, LIGHTNING_OUTER_BLUE, intensity,
            )
            lightningSegment(
                buffer, positionMatrix, points[index], points[index + 1],
                DISCHARGE_CORE_WIDTH, DISCHARGE_CORE_ALPHA,
                LIGHTNING_CORE_RED, LIGHTNING_CORE_GREEN, LIGHTNING_CORE_BLUE, intensity,
            )
        }
    }

    /** 依序展開一圈波紋；每個後續圓環稍晚出現、略暗且高度錯開，模擬水滴擴散。 */
    private fun ripple(
        buffer: VertexConsumer,
        positionMatrix: Matrix4f,
        rotation: Double,
        effectProgress: Double,
        envelope: Double,
        rippleIndex: Int,
    ) {
        val startProgress = RIPPLE_START_FRACTION + rippleIndex * RIPPLE_DELAY_FRACTION
        val rippleProgress = ((effectProgress - startProgress) / RIPPLE_DURATION_FRACTION).coerceIn(0.0, 1.0)
        if (effectProgress < startProgress || rippleProgress >= 1.0) return

        val fadeIn = (rippleProgress / RIPPLE_FADE_IN_FRACTION).coerceIn(0.0, 1.0)
        val brightness = 1.0 - rippleIndex * RIPPLE_BRIGHTNESS_STEP
        val alpha = (MAX_RING_ALPHA * envelope * fadeIn * (1.0 - rippleProgress) * brightness).toInt()
        if (alpha <= 0) return

        val radius = RING_START_RADIUS + (RING_END_RADIUS - RING_START_RADIUS) * rippleProgress
        val height = RING_HEIGHT + rippleIndex * RIPPLE_HEIGHT_OFFSET
        val red = if (rippleIndex == 0) LIGHTNING_MIDDLE_RED else GLOW_RED
        val green = if (rippleIndex == 0) LIGHTNING_MIDDLE_GREEN else GLOW_GREEN
        val blue = if (rippleIndex == 0) LIGHTNING_MIDDLE_BLUE else GLOW_BLUE
        repeat(RING_SEGMENTS) { segmentIndex ->
            val startAngle = rotation + segmentIndex * FULL_ROTATION_RADIANS / RING_SEGMENTS
            val endAngle = rotation + (segmentIndex + 1) * FULL_ROTATION_RADIANS / RING_SEGMENTS
            rippleSegment(buffer, positionMatrix, startAngle, endAngle, radius, height, RIPPLE_OUTER_WIDTH, alpha, red, green, blue)
            rippleSegment(
                buffer, positionMatrix, startAngle, endAngle, radius, height,
                RIPPLE_CORE_WIDTH, (alpha * RIPPLE_CORE_ALPHA_MULTIPLIER).toInt(),
                if (rippleIndex == 0) LIGHTNING_CORE_RED else RIPPLE_CORE_RED,
                if (rippleIndex == 0) LIGHTNING_CORE_GREEN else RIPPLE_CORE_GREEN,
                if (rippleIndex == 0) LIGHTNING_CORE_BLUE else RIPPLE_CORE_BLUE,
            )
        }
    }

    /** 同時畫水平環帶與薄垂直側壁，讓水波紋從俯視與低角度都清楚可見。 */
    private fun rippleSegment(
        buffer: VertexConsumer,
        positionMatrix: Matrix4f,
        startAngle: Double,
        endAngle: Double,
        radius: Double,
        height: Double,
        width: Double,
        alpha: Int,
        red: Int,
        green: Int,
        blue: Int,
    ) {
        val innerRadius = radius - width
        val outerRadius = radius + width
        val innerStart = LightningPoint(cos(startAngle) * innerRadius, height, sin(startAngle) * innerRadius)
        val innerEnd = LightningPoint(cos(endAngle) * innerRadius, height, sin(endAngle) * innerRadius)
        val outerEnd = LightningPoint(cos(endAngle) * outerRadius, height, sin(endAngle) * outerRadius)
        val outerStart = LightningPoint(cos(startAngle) * outerRadius, height, sin(startAngle) * outerRadius)
        coloredQuad(buffer, positionMatrix, innerStart, innerEnd, outerEnd, outerStart, alpha, red, green, blue)

        val lowerStart = LightningPoint(outerStart.x, height - RIPPLE_VERTICAL_HALF_HEIGHT, outerStart.z)
        val lowerEnd = LightningPoint(outerEnd.x, height - RIPPLE_VERTICAL_HALF_HEIGHT, outerEnd.z)
        val upperEnd = LightningPoint(outerEnd.x, height + RIPPLE_VERTICAL_HALF_HEIGHT, outerEnd.z)
        val upperStart = LightningPoint(outerStart.x, height + RIPPLE_VERTICAL_HALF_HEIGHT, outerStart.z)
        coloredQuad(buffer, positionMatrix, lowerStart, lowerEnd, upperEnd, upperStart, alpha, red, green, blue)
    }

    /** 將持久化 seed 穩定映射到一圈內的起始角度，避免世界重載後圖樣旋轉相位改變。 */
    private fun seedRotation(seed: Long): Double = Math.floorMod(seed, SEED_ROTATION_STEPS) /
        SEED_ROTATION_STEPS.toDouble() * FULL_ROTATION_RADIANS

    /** 純色線段不使用材質。 */
    override fun getTexture(entity: WinCelebrationEffectEntity): Identifier? = null

    companion object {
        /** 三叉戟落下、插入停頓與後續閃電的時間分段。 */
        private const val TRIDENT_FALL_DURATION_TICKS: Int = MahjongTileTableLayout.WIN_TRIDENT_FALL_DURATION_TICKS
        private const val TRIDENT_SETTLE_DURATION_TICKS: Int = MahjongTileTableLayout.WIN_TRIDENT_SETTLE_DURATION_TICKS
        private const val LIGHTNING_START_TICK: Int = MahjongTileTableLayout.WIN_LIGHTNING_START_TICK
        private const val LIGHTNING_DURATION_TICKS: Int = MahjongTileTableLayout.WIN_EFFECT_DURATION_TICKS - LIGHTNING_START_TICK
        private const val TRIDENT_SHRINK_DELAY_TICKS: Int = 2
        private const val TRIDENT_SHRINK_DURATION_TICKS: Int = 8
        private const val TRIDENT_SHRINK_START_TICK: Int = LIGHTNING_START_TICK + TRIDENT_SHRINK_DELAY_TICKS
        private const val TRIDENT_SHRINK_END_TICK: Int = TRIDENT_SHRINK_START_TICK + TRIDENT_SHRINK_DURATION_TICKS

        /** 三叉戟的世界高度、尺寸與 seed 控制的插入角度範圍。 */
        private const val TRIDENT_START_HEIGHT: Double = 1.8
        private const val TRIDENT_END_HEIGHT: Double = 0.075
        private const val TRIDENT_SCALE: Float = 0.2f
        private const val TRIDENT_TIP_TO_MODEL_ORIGIN: Double = 3.0 / 16.0
        private const val TRIDENT_MIN_INSERT_ANGLE_DEGREES: Float = 8.0f
        private const val TRIDENT_MAX_INSERT_ANGLE_DEGREES: Float = 20.0f

        /** 插入後的阻尼顫動參數。 */
        private const val TRIDENT_WOBBLE_CYCLES: Double = 2.5
        private const val TRIDENT_WOBBLE_DAMPING: Double = 3.8
        private const val TRIDENT_WOBBLE_DEGREES: Double = 7.0

        /** 持久化 seed 映射至三叉戟水平朝向時使用的離散精度。 */
        private const val TRIDENT_YAW_STEPS: Long = 360L

        /** 由持久化 seed 取得三叉戟傾角時使用的獨立雜湊 salt。 */
        private const val TRIDENT_TILT_SALT: Int = 8731

        /** 完整一圈的角度。 */
        private const val FULL_ROTATION_DEGREES: Float = 360.0f

        /** 圓環線段數，兼顧圓滑度與頂點數。 */
        private const val RING_SEGMENTS: Int = 32

        /** 依序展開的水波紋數量。 */
        private const val RIPPLE_COUNT: Int = 3

        /** 小型閃電主幹的分段數。 */
        private const val LIGHTNING_SEGMENTS: Int = 7

        /** 命中點向外四散的接地放電數量與每道分段數。 */
        private const val DISCHARGE_RAY_COUNT: Int = 5
        private const val DISCHARGE_SEGMENTS: Int = 5
        private const val DISCHARGE_BRANCH_START_INDEX: Int = 2

        /** seed 映射至初始旋轉角時的一圈離散精度。 */
        private const val SEED_ROTATION_STEPS: Long = 3600L

        /** 整段演出繞牌旋轉一圈的弧度。 */
        private const val FULL_ROTATION_RADIANS: Double = PI * 2.0

        /** 脈衝環起始半徑。 */
        private const val RING_START_RADIUS: Double = 0.075

        /** 脈衝環結束半徑。 */
        private const val RING_END_RADIUS: Double = 0.38

        /** 脈衝環略高於牌面，避免深度衝突。 */
        private const val RING_HEIGHT: Double = 0.04

        /** 單道波紋占整段演出的時間比例。 */
        private const val RIPPLE_DURATION_FRACTION: Double = 0.42

        /** 第一環延後至閃電擊中後才開始。 */
        private const val RIPPLE_START_FRACTION: Double = 0.11

        /** 相鄰波紋起始時間的間隔。 */
        private const val RIPPLE_DELAY_FRACTION: Double = 0.13

        /** 波紋開始時用於快速淡入的自身進度比例。 */
        private const val RIPPLE_FADE_IN_FRACTION: Double = 0.12

        /** 每道後續波紋降低的亮度比例。 */
        private const val RIPPLE_BRIGHTNESS_STEP: Double = 0.18

        /** 相鄰波紋的垂直錯位，避免共面閃爍。 */
        private const val RIPPLE_HEIGHT_OFFSET: Double = 0.0015

        /** 接地放電的播放區間、閃爍節奏與離散形狀數。 */
        private const val DISCHARGE_START_FRACTION: Double = 0.08
        private const val DISCHARGE_DURATION_FRACTION: Double = 0.38
        private const val DISCHARGE_SHAPE_COUNT: Double = 4.0
        private const val DISCHARGE_FLICKER_COUNT: Double = 5.0
        private const val DISCHARGE_FLICKER_MIN: Double = 0.45

        /** 接地放電由擊中點向外爬行的長度、角度與高度參數。 */
        private const val DISCHARGE_START_RADIUS: Double = 0.055
        private const val DISCHARGE_LENGTH: Double = 0.29
        private const val DISCHARGE_MIN_LENGTH_SCALE: Double = 0.62
        private const val DISCHARGE_ANGLE_JITTER: Double = 0.2
        private const val DISCHARGE_PATH_JITTER: Double = 0.42
        private const val DISCHARGE_HEIGHT: Double = 0.052
        private const val DISCHARGE_HEIGHT_JITTER: Double = 0.006

        /** 偶數主電弧在中段長出的短分岔。 */
        private const val DISCHARGE_BRANCH_ANGLE: Double = PI * 0.72
        private const val DISCHARGE_BRANCH_LENGTH: Double = 0.065
        private const val DISCHARGE_BRANCH_HEIGHT_OFFSET: Double = 0.003
        private const val DISCHARGE_BRANCH_ALPHA: Double = 0.72

        /** 接地放電的淡藍外光與近白核心尺寸。 */
        private const val DISCHARGE_OUTER_WIDTH: Double = 0.014
        private const val DISCHARGE_CORE_WIDTH: Double = 0.0045
        private const val DISCHARGE_OUTER_ALPHA: Int = 120
        private const val DISCHARGE_CORE_ALPHA: Int = 235

        /** 波紋外光、核心環帶寬度與低角度可見的側壁半高。 */
        private const val RIPPLE_OUTER_WIDTH: Double = 0.018
        private const val RIPPLE_CORE_WIDTH: Double = 0.006
        private const val RIPPLE_VERTICAL_HALF_HEIGHT: Double = 0.012
        private const val RIPPLE_CORE_ALPHA_MULTIPLIER: Double = 1.08

        /** 金色波紋的近白核心。 */
        private const val RIPPLE_CORE_RED: Int = 255
        private const val RIPPLE_CORE_GREEN: Int = 239
        private const val RIPPLE_CORE_BLUE: Int = 172

        /** 閃電播放區間、閃爍次數與離散形狀數。 */
        private const val LIGHTNING_DURATION_FRACTION: Double = 0.3
        private const val LIGHTNING_FLASH_COUNT: Double = 3.0
        private const val LIGHTNING_SHAPE_COUNT: Double = 3.0
        private const val LIGHTNING_VISIBLE_THRESHOLD: Double = 0.08

        /** 閃電從牌面上方劈下的尺寸與最大水平偏移。 */
        private const val LIGHTNING_BOTTOM_HEIGHT: Double = 0.045
        private const val LIGHTNING_HEIGHT: Double = 0.78
        private const val LIGHTNING_MAX_OFFSET: Double = 0.085

        /** 外光、中層與核心的半寬。 */
        private const val LIGHTNING_OUTER_WIDTH: Double = 0.052
        private const val LIGHTNING_MIDDLE_WIDTH: Double = 0.029
        private const val LIGHTNING_CORE_WIDTH: Double = 0.009

        /** 三層帶狀幾何的透明度。 */
        private const val LIGHTNING_OUTER_ALPHA: Int = 58
        private const val LIGHTNING_MIDDLE_ALPHA: Int = 125
        private const val LIGHTNING_CORE_ALPHA: Int = 245

        /** 45 度交叉面的寬度正規化係數。 */
        private const val INVERSE_SQRT_TWO: Double = 0.7071067811865476

        /** 淡藍外光、藍白中層與近白核心。 */
        private const val LIGHTNING_OUTER_RED: Int = 128
        private const val LIGHTNING_OUTER_GREEN: Int = 158
        private const val LIGHTNING_OUTER_BLUE: Int = 255
        private const val LIGHTNING_MIDDLE_RED: Int = 190
        private const val LIGHTNING_MIDDLE_GREEN: Int = 210
        private const val LIGHTNING_MIDDLE_BLUE: Int = 255
        private const val LIGHTNING_CORE_RED: Int = 248
        private const val LIGHTNING_CORE_GREEN: Int = 251
        private const val LIGHTNING_CORE_BLUE: Int = 255

        /** SplitMix64 雜湊常數，將持久化 seed 轉為穩定的節點偏移。 */
        private const val LIGHTNING_HASH_MULTIPLIER: Long = -7046029254386353131L
        private const val LIGHTNING_HASH_MIX_A: Long = -4658895280553007687L
        private const val LIGHTNING_HASH_MIX_B: Long = -7723592293110705685L
        private const val LIGHTNING_HASH_DENOMINATOR: Double = 9007199254740992.0

        /** 脈衝環最高 alpha。 */
        private const val MAX_RING_ALPHA: Int = 230

        /** 暖金色外層光暈與脈衝環。 */
        private const val GLOW_RED: Int = 255
        private const val GLOW_GREEN: Int = 190
        private const val GLOW_BLUE: Int = 62
    }
}
