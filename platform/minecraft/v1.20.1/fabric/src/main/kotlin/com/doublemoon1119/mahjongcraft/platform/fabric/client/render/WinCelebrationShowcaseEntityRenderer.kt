package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ShowcaseCardSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationShowcaseEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.ShowcasePalette
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.ShowcaseVisualLayer
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.model.ModelPart
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.render.entity.model.EntityModelLayers
import net.minecraft.client.render.model.json.ModelTransformationMode
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 繪製持久化役種 showcase：逐牌收束波紋、微幅抖動、鞘翅與懸浮煙火起飛、弧形展示翼及徽記光場。
 */
class WinCelebrationShowcaseEntityRenderer(
    context: EntityRendererFactory.Context,
    private val showcaseRegistry: WinCelebrationShowcaseRegistry,
) : EntityRenderer<WinCelebrationShowcaseEntity>(context) {
    private val itemRenderer = context.itemRenderer
    private val textRenderer = context.textRenderer
    private val elytraRoot: ModelPart = context.getPart(EntityModelLayers.ELYTRA)
    private val leftWing: ModelPart = elytraRoot.getChild("left_wing")
    private val rightWing: ModelPart = elytraRoot.getChild("right_wing")
    private val tileStacks = mutableMapOf<String, ItemStack>()
    private val fireworkStack = ItemStack(Items.FIREWORK_ROCKET)

    override fun render(
        entity: WinCelebrationShowcaseEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        val elapsed = entity.elapsedTicks(tickDelta)
        val duration = (entity.endGameTime - entity.startGameTime).toDouble()
        if (duration <= 0.0 || elapsed !in 0.0..<duration) return
        val fadeStart = duration - WinCelebrationShowcaseEntity.FADE_OUT_TICKS
        val billboardRotation = Quaternionf(dispatcher.rotation)
        entity.wings.forEachIndexed { wingIndex, wing ->
            wing.cards.forEach { card ->
                renderCard(entity, card, wingIndex, elapsed, fadeStart, billboardRotation, matrices, vertexConsumers, light)
            }
        }
        renderWinningTile(entity, elapsed, fadeStart, billboardRotation, matrices, vertexConsumers, light)
        if (elapsed >= SHOWCASE_START_TICK) {
            renderShowcaseCenter(entity, elapsed, fadeStart, duration, billboardRotation, matrices, vertexConsumers, light)
        }
    }

    /** 在共享舞台中央只繪製一張權威胡牌張的視覺代理。 */
    private fun renderWinningTile(
        entity: WinCelebrationShowcaseEntity,
        elapsed: Double,
        fadeStart: Double,
        billboardRotation: Quaternionf,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
    ) {
        entity.winningTileSnapshot?.let { winningTile ->
            renderCard(
                entity = entity,
                card = ShowcaseCardSnapshot(
                    wingIndex = WINNING_TILE_WING_INDEX,
                    order = WINNING_TILE_ORDER,
                    assetKey = winningTile.assetKey,
                    startOffsetX = winningTile.startOffsetX,
                    startOffsetY = winningTile.startOffsetY,
                    startOffsetZ = winningTile.startOffsetZ,
                    startYaw = winningTile.startYaw,
                ),
                wingIndex = 0,
                elapsed = elapsed,
                fadeStart = fadeStart,
                billboardRotation = billboardRotation,
                matrices = matrices,
                vertexConsumers = consumers,
                light = light,
                targetXOverride = winningTileX(entity),
                winningTile = true,
            )
            return
        }
        if (elapsed < FLIGHT_END_TICK) return
        val fadeScale = if (elapsed < fadeStart) WINNING_TILE_SCALE else WINNING_TILE_SCALE * (1.0 - smoothStep((elapsed - fadeStart) / WinCelebrationShowcaseEntity.FADE_OUT_TICKS)).coerceAtLeast(0.0)
        matrices.push()
        val localX = winningTileX(entity)
        val offset = Vector3f(localX.toFloat(), 0.0f, 0.0f).rotate(billboardRotation)
        val fade = fadeScaleFactor(elapsed, fadeStart)
        val entranceProgress = ((elapsed - FLIGHT_END_TICK) / CARD_ENTRANCE_TICKS).coerceIn(0.0, 1.0)
        val entranceEase = easeOut(entranceProgress)
        val bob = if (entranceProgress >= 1.0) winningTileBobOffset(elapsed, fade) else 0.0
        if (entranceProgress >= 1.0) {
            renderWinningTileContactRipples(elapsed, fade, offset, matrices, consumers)
        }
        matrices.translate(
            offset.x.toDouble(),
            SHOWCASE_HEIGHT + WINNING_TILE_HEIGHT_OFFSET - (1.0 - entranceEase) * CARD_ENTRANCE_DROP + bob,
            offset.z.toDouble(),
        )
        matrices.multiply(billboardRotation)
        matrices.translate(0.0, 0.0, WINNING_TILE_FORWARD_OFFSET)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((360.0 * easeOut(entranceEase)).toFloat()))
        if (entranceProgress >= 1.0) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((sin((elapsed - FLIGHT_END_TICK - CARD_ENTRANCE_TICKS) * WINNING_TILE_PITCH_SPEED) * WINNING_TILE_PITCH_DEGREES).toFloat()))
        }
        val entranceBounce = 1.0 + sin(entranceProgress * PI) * CARD_ENTRANCE_BOUNCE
        val scale = (fadeScale * entranceBounce).toFloat()
        matrices.scale(scale, scale, scale)
        itemRenderer.renderItem(tileStack(entity.winningTileAssetKey), ModelTransformationMode.HEAD, light, OverlayTexture.DEFAULT_UV, matrices, consumers, entity.world, 9000)
        matrices.pop()
    }

    /** 依固定時間軸定位並繪製單張視覺牌。 */
    private fun renderCard(
        entity: WinCelebrationShowcaseEntity,
        card: ShowcaseCardSnapshot,
        wingIndex: Int,
        elapsed: Double,
        fadeStart: Double,
        billboardRotation: Quaternionf,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
        targetXOverride: Double? = null,
        winningTile: Boolean = false,
    ) {
        val count = entity.wings[wingIndex].cards.size.coerceAtLeast(1)
        val startX = card.startOffsetX
        val startY = card.startOffsetY
        val startZ = card.startOffsetZ
        val localTargetX = targetXOverride ?: formationCardX(entity, card.order, count, wingIndex)
        val seededPhase = seededUnit(entity.animationSeed, wingIndex * 97 + card.order * 13) * PI * 2.0
        val entranceStart = if (winningTile) FLIGHT_END_TICK else FLIGHT_END_TICK + card.order * CARD_ENTRANCE_STAGGER_TICKS
        val entranceProgress = ((elapsed - entranceStart) / CARD_ENTRANCE_TICKS).coerceIn(0.0, 1.0)
        val entranceEase = easeOut(entranceProgress)

        val lift = easeOut((elapsed / LIFT_END_TICK).coerceIn(0.0, 1.0)) * LIFT_HEIGHT
        var x = startX
        var y = startY + lift
        var z = startZ
        if (elapsed >= FLIGHT_START_TICK) {
            val pose = flightPose(entity, card, wingIndex, startX, startY, startZ, localTargetX, elapsed, billboardRotation)
            x = pose.position.x.toDouble()
            y = pose.position.y.toDouble()
            z = pose.position.z.toDouble()
            if (elapsed < FLIGHT_END_TICK) renderFlightTrail(entity, card, wingIndex, startX, startY, startZ, localTargetX, elapsed, billboardRotation, matrices, vertexConsumers, light, winningTile)
        } else if (elapsed >= SHOWCASE_START_TICK) {
            val target = Vector3f(localTargetX.toFloat(), 0.0f, 0.0f).rotate(billboardRotation)
            x = target.x.toDouble()
            z = target.z.toDouble()
        }
        val fade = fadeScaleFactor(elapsed, fadeStart)
        if (elapsed >= FLIGHT_END_TICK) {
            val bob = if (entranceProgress >= 1.0) {
                if (winningTile) winningTileBobOffset(elapsed, fade) else sin((elapsed - SHOWCASE_START_TICK) * BOB_SPEED + seededPhase) * BOB_HEIGHT * fade
            } else {
                0.0
            }
            y = SHOWCASE_HEIGHT + if (winningTile) WINNING_TILE_HEIGHT_OFFSET else 0.0
            y += -(1.0 - entranceEase) * CARD_ENTRANCE_DROP + bob
        }

        if (winningTile && entranceProgress >= 1.0) {
            val offset = Vector3f(localTargetX.toFloat(), 0.0f, 0.0f).rotate(billboardRotation)
            renderWinningTileContactRipples(elapsed, fade, offset, matrices, vertexConsumers)
        }

        if (elapsed in RIPPLE_START_TICK..<RIPPLE_END_TICK) {
            matrices.push()
            matrices.translate(x, TABLE_RIPPLE_HEIGHT, z)
            renderExpandingRipples(elapsed, matrices, vertexConsumers)
            matrices.pop()
        }
        matrices.push()
        matrices.translate(x, y, z)
        val poseRotation = when {
            elapsed >= FLIGHT_END_TICK -> {
                val pose = flightPose(entity, card, wingIndex, startX, startY, startZ, localTargetX, FLIGHT_END_TICK - 0.01, billboardRotation)
                flightRotation(pose.velocity).slerp(
                    Quaternionf(billboardRotation),
                    smoothStep(((elapsed - FLIGHT_END_TICK) / ARRIVAL_TRANSITION_TICKS).coerceIn(0.0, 1.0)).toFloat(),
                )
            }
            elapsed < STAND_START_TICK -> faceUpRotation(card.startYaw)
            elapsed < STAND_END_TICK -> {
                val progress = smoothStep((elapsed - STAND_START_TICK) / (STAND_END_TICK - STAND_START_TICK)).toFloat()
                faceUpRotation(card.startYaw).slerp(standingRotation(card.startYaw), progress)
            }
            elapsed < FLIGHT_START_TICK -> standingRotation(card.startYaw)
            else -> {
                val flightElapsed = elapsed.coerceIn(FLIGHT_START_TICK, FLIGHT_END_TICK - 0.01)
                val pose = flightPose(entity, card, wingIndex, startX, startY, startZ, localTargetX, flightElapsed, billboardRotation)
                val pathRotation = flightRotation(pose.velocity)
                standingRotation(card.startYaw).slerp(
                    pathRotation,
                    smoothStep(((elapsed - FLIGHT_START_TICK) / LAUNCH_TRANSITION_TICKS).coerceIn(0.0, 1.0)).toFloat(),
                )
            }
        }
        matrices.multiply(poseRotation)
        if (elapsed >= entranceStart) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((360.0 * easeOut(entranceEase)).toFloat()))
        }
        if (winningTile && entranceProgress >= 1.0) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((sin((elapsed - FLIGHT_END_TICK - CARD_ENTRANCE_TICKS) * WINNING_TILE_PITCH_SPEED) * WINNING_TILE_PITCH_DEGREES).toFloat()))
            matrices.translate(0.0, 0.0, WINNING_TILE_FORWARD_OFFSET)
        }
        if (elapsed >= FLIGHT_END_TICK) {
            val arrival = smoothStep(((elapsed - FLIGHT_END_TICK) / ARRIVAL_TRANSITION_TICKS).coerceIn(0.0, 1.0))
            val entranceBounce = 1.0 + sin(entranceProgress * PI) * CARD_ENTRANCE_BOUNCE
            val targetScale = if (winningTile) WINNING_TILE_SCALE else DISPLAY_CARD_SCALE
            val displayScale = (lerp(1.0, targetScale, arrival) * entranceBounce * fade).toFloat()
            matrices.scale(displayScale, displayScale, displayScale)
        }
        val recoil = if (elapsed in FIREWORK_USE_START_TICK..<FLIGHT_START_TICK) {
            sin((elapsed - FIREWORK_USE_START_TICK) / (FLIGHT_START_TICK - FIREWORK_USE_START_TICK) * PI) * RECOIL_DISTANCE
        } else {
            0.0
        }
        matrices.translate(0.0, 0.0, recoil)
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((-recoil * 80.0).toFloat()))
        if (elapsed in SHAKE_START_TICK..<SHAKE_END_TICK) {
            val envelope = sin((elapsed - SHAKE_START_TICK) / (SHAKE_END_TICK - SHAKE_START_TICK) * PI)
            val phase = (elapsed - SHAKE_START_TICK) * SHAKE_OSCILLATIONS * PI * 2.0 / (SHAKE_END_TICK - SHAKE_START_TICK) + seededPhase
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((sin(phase) * SHAKE_PITCH_DEGREES * envelope).toFloat()))
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((sin(phase * SHAKE_SECONDARY_FREQUENCY_RATIO + PI / 3.0) * SHAKE_SECONDARY_TILT_DEGREES * envelope).toFloat()))
        }
        itemRenderer.renderItem(tileStack(card.assetKey), ModelTransformationMode.HEAD, light, OverlayTexture.DEFAULT_UV, matrices, vertexConsumers, entity.world, card.order)
        if (elapsed in ELYTRA_APPEAR_TICK..<ELYTRA_FADE_END_TICK) {
            renderElytra(elapsed, matrices, vertexConsumers, light)
        }
        if (elapsed in FIREWORK_APPEAR_TICK..<EQUIPMENT_FADE_END_TICK) {
            renderHeldFirework(elapsed, card.order, matrices, vertexConsumers, light, entity)
        }
        if (elapsed in FIREWORK_USE_START_TICK..<FIREWORK_FLAME_END_TICK) {
            renderIgnition(elapsed, matrices, vertexConsumers)
        }
        matrices.pop()
    }

    /** 繪製貼在牌背的原版鞘翅；抵達並完成 billboard 後才收攏、縮小並淡出。 */
    private fun renderElytra(elapsed: Double, matrices: MatrixStack, consumers: VertexConsumerProvider, light: Int) {
        val open = when {
            elapsed < ELYTRA_OPEN_TICK -> 0.0
            elapsed < FIREWORK_USE_START_TICK -> smoothStep((elapsed - ELYTRA_OPEN_TICK) / (FIREWORK_USE_START_TICK - ELYTRA_OPEN_TICK))
            elapsed < ELYTRA_FADE_START_TICK -> 1.0
            else -> 1.0 - smoothStep((elapsed - ELYTRA_FADE_START_TICK) / (ELYTRA_FADE_END_TICK - ELYTRA_FADE_START_TICK))
        }
        leftWing.yaw = (0.08 * (1.0 - open)).toFloat()
        rightWing.yaw = -leftWing.yaw
        leftWing.roll = (-0.10 - open * 0.58).toFloat()
        rightWing.roll = -leftWing.roll
        leftWing.pitch = (0.52 * (1.0 - open) + ELYTRA_FLIGHT_PITCH * open).toFloat()
        rightWing.pitch = leftWing.pitch
        val visibility = equipmentVisibility(elapsed)
        val appearance = equipmentAppearance(elapsed)
        val scale = visibility * lerp(EQUIPMENT_APPEAR_START_SCALE, 1.0, appearance)
        matrices.push()
        matrices.translate(0.0, ELYTRA_ROOT_Y, ELYTRA_BACK_Z)
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0f))
        matrices.scale((ELYTRA_SCALE * scale).toFloat(), (ELYTRA_SCALE * scale).toFloat(), (ELYTRA_SCALE * scale).toFloat())
        val buffer = consumers.getBuffer(RenderLayer.getEntityTranslucent(ELYTRA_TEXTURE))
        elytraRoot.render(matrices, buffer, light, OverlayTexture.DEFAULT_UV, 1.0f, 1.0f, 1.0f, (appearance * visibility).toFloat())
        matrices.pop()
    }

    /** 煙火右鍵推出後回到貼近牌身的握持位置，飛行全程保留並與鞘翅一起淡出。 */
    private fun renderHeldFirework(
        elapsed: Double,
        order: Int,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
        entity: WinCelebrationShowcaseEntity,
    ) {
        val heightOffset = seededUnit(entity.animationSeed, order * 31) * 0.025
        val push = when {
            elapsed < FIREWORK_USE_START_TICK -> 0.0
            elapsed < FLIGHT_START_TICK -> easeOut((elapsed - FIREWORK_USE_START_TICK) / (FLIGHT_START_TICK - FIREWORK_USE_START_TICK))
            elapsed < FIREWORK_SETTLE_END_TICK -> 1.0 - smoothStep((elapsed - FLIGHT_START_TICK) / (FIREWORK_SETTLE_END_TICK - FLIGHT_START_TICK))
            else -> 0.0
        }
        val visibility = equipmentVisibility(elapsed)
        val appearance = equipmentAppearance(elapsed)
        val scale = visibility * lerp(EQUIPMENT_APPEAR_START_SCALE, 1.0, appearance)
        matrices.push()
        matrices.translate(RIGHT_HAND_X, RIGHT_HAND_Y + heightOffset + push * 0.075, RIGHT_HAND_Z - push * 0.24)
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((-35.0 - push * 18.0).toFloat()))
        matrices.scale((FIREWORK_ITEM_SCALE * scale).toFloat(), (FIREWORK_ITEM_SCALE * scale).toFloat(), (FIREWORK_ITEM_SCALE * scale).toFloat())
        itemRenderer.renderItem(fireworkStack, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV, matrices, consumers, entity.world, order + 4000)
        matrices.pop()
    }

    /** 在水平面逐環向外擴散；最後一環會在牌完成轉正時淡出。 */
    private fun renderExpandingRipples(elapsed: Double, matrices: MatrixStack, consumers: VertexConsumerProvider) {
        repeat(RIPPLE_COUNT) { ring ->
            val age = elapsed - RIPPLE_START_TICK - ring * RIPPLE_DELAY_TICKS
            val local = (age / RIPPLE_DURATION_TICKS).coerceIn(0.0, 1.0)
            if (local <= 0.0 || local >= 1.0) return@repeat
            val radius = lerp(RIPPLE_START_RADIUS, RIPPLE_END_RADIUS, smoothStep(local))
            val alpha = (sin(local * PI) * 150.0).toInt()
            val buffer = consumers.getBuffer(RenderLayer.getLightning())
            val matrix = matrices.peek().positionMatrix
            repeat(RIPPLE_SEGMENTS) { index ->
                val a = index * PI * 2.0 / RIPPLE_SEGMENTS
                val b = (index + 1) * PI * 2.0 / RIPPLE_SEGMENTS
                thinQuad(
                    buffer,
                    matrix,
                    cos(a) * radius,
                    RIPPLE_Y_OFFSET,
                    sin(a) * radius,
                    cos(b) * radius,
                    RIPPLE_Y_OFFSET,
                    sin(b) * radius,
                    0.003,
                    alpha,
                )
            }
        }
    }

    /** 由絕對時間與 seed 重建飛行位置，讓重載後的亂序飛行與拖尾仍保持一致。 */
    private fun flightPose(
        entity: WinCelebrationShowcaseEntity,
        card: ShowcaseCardSnapshot,
        wingIndex: Int,
        startX: Double,
        startY: Double,
        startZ: Double,
        localTargetX: Double,
        elapsed: Double,
        billboardRotation: Quaternionf,
    ): FlightPose {
        val delay = seededUnit(entity.animationSeed, wingIndex * 149 + card.order * 43) * MAX_FLIGHT_DELAY_TICKS
        val progress = ((elapsed - FLIGHT_START_TICK - delay) / (FLIGHT_END_TICK - FLIGHT_START_TICK - delay)).coerceIn(0.0, 1.0)
        val now = flightPosition(entity, card, wingIndex, startX, startY, startZ, localTargetX, progress, billboardRotation)
        val next = flightPosition(entity, card, wingIndex, startX, startY, startZ, localTargetX, (progress + 0.008).coerceAtMost(1.0), billboardRotation)
        val velocity = Vector3f(next).sub(now)
        if (velocity.lengthSquared() < 0.000001f) velocity.set(0.0f, 0.0f, 1.0f)
        return FlightPose(now, velocity.normalize())
    }

    private fun flightPosition(
        entity: WinCelebrationShowcaseEntity,
        card: ShowcaseCardSnapshot,
        wingIndex: Int,
        startX: Double,
        startY: Double,
        startZ: Double,
        localTargetX: Double,
        progress: Double,
        billboardRotation: Quaternionf,
    ): Vector3f {
        val eased = smoothStep(progress)
        val target = Vector3f(localTargetX.toFloat(), 0.0f, 0.0f).rotate(billboardRotation)
        val seedA = seededUnit(entity.animationSeed, wingIndex * 211 + card.order * 71)
        val seedB = seededUnit(entity.animationSeed, wingIndex * 263 + card.order * 89)
        val direction = if (seedA < 0.5) -1.0 else 1.0
        val loops = 1.0 + (card.order % 3) * 0.5
        val envelope = sin(progress * PI)
        val lateral = direction * envelope * (0.22 + seedA * 0.55) + sin(progress * PI * 2.0 * loops + seedB * PI) * envelope * 0.22
        val depth = cos(progress * PI * (2.0 + seedB)) * envelope * (0.12 + seedB * 0.34)
        val right = Vector3f(1.0f, 0.0f, 0.0f).rotate(billboardRotation)
        val forward = Vector3f(0.0f, 0.0f, 1.0f).rotate(billboardRotation)
        return Vector3f(
            lerp(startX, target.x.toDouble(), eased).toFloat(),
            (lerp(startY + LIFT_HEIGHT, SHOWCASE_HEIGHT - CARD_ENTRANCE_DROP, eased) + envelope * (FLIGHT_ARC_HEIGHT + seedB * 0.45) + sin(progress * PI * 3.0 + seedA * PI) * envelope * 0.12).toFloat(),
            lerp(startZ, target.z.toDouble(), eased).toFloat(),
        ).add(right.mul(lateral.toFloat())).add(forward.mul(depth.toFloat()))
    }

    /** 讓牌的長軸沿路徑切線，使牌面平面與飛行方向平行。 */
    private fun flightRotation(velocity: Vector3f): Quaternionf = Quaternionf().rotationTo(Vector3f(0.0f, 1.0f, 0.0f), Vector3f(velocity).normalize())

    private fun faceUpRotation(yaw: Float): Quaternionf = standingRotation(yaw).rotateX(Math.toRadians(90.0).toFloat())

    private fun standingRotation(yaw: Float): Quaternionf = Quaternionf().rotateY(Math.toRadians((-yaw + 180.0f).toDouble()).toFloat())

    /** 取數個過去位置構成彎曲的短尾跡；核心靠近牌最亮，尾端逐步淡出。 */
    private fun renderFlightTrail(
        entity: WinCelebrationShowcaseEntity,
        card: ShowcaseCardSnapshot,
        wingIndex: Int,
        startX: Double,
        startY: Double,
        startZ: Double,
        localTargetX: Double,
        elapsed: Double,
        billboardRotation: Quaternionf,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
        winningTile: Boolean,
    ) {
        val buffer = consumers.getBuffer(RenderLayer.getEntityTranslucent(GLOW_TEXTURE))
        val entry = matrices.peek()
        var previous = flightPose(entity, card, wingIndex, startX, startY, startZ, localTargetX, elapsed, billboardRotation).position
        repeat(TRAIL_SEGMENTS) { index ->
            val sampleElapsed = elapsed - (index + 1) * TRAIL_SAMPLE_TICKS
            if (sampleElapsed < FLIGHT_START_TICK) return@repeat
            val point = flightPose(entity, card, wingIndex, startX, startY, startZ, localTargetX, sampleElapsed, billboardRotation).position
            val strength = 1.0 - index.toDouble() / TRAIL_SEGMENTS
            val color = if (winningTile) WINNING_TILE_TRAIL_COLOR else CARD_TRAIL_COLOR
            texturedThinQuad(buffer, entry.positionMatrix, entry.normalMatrix, previous, point, 0.004 + strength * 0.009, (strength * 175).toInt(), light, color)
            previous = point
        }
    }

    private fun texturedThinQuad(buffer: VertexConsumer, matrix: Matrix4f, normal: Matrix3f, start: Vector3f, end: Vector3f, width: Double, alpha: Int, light: Int, color: Int) {
        val dx = end.x - start.x
        val dz = end.z - start.z
        val length = sqrt((dx * dx + dz * dz).toDouble()).coerceAtLeast(0.0001)
        val ox = (-dz / length * width).toFloat()
        val oz = (dx / length * width).toFloat()
        fun vertex(point: Vector3f, sideX: Float, sideZ: Float, u: Float, v: Float) {
            buffer.vertex(matrix, point.x + sideX, point.y, point.z + sideZ)
                .color(color shr 16 and 0xFF, color shr 8 and 0xFF, color and 0xFF, alpha.coerceIn(0, 255))
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(normal, 0.0f, 1.0f, 0.0f)
                .next()
        }
        vertex(start, -ox, -oz, 0.0f, 1.0f)
        vertex(start, ox, oz, 1.0f, 1.0f)
        vertex(end, ox, oz, 1.0f, 0.0f)
        vertex(end, -ox, -oz, 0.0f, 0.0f)
    }

    /** 繪製煙火推出後的短促白黃點火十字。 */
    private fun renderIgnition(elapsed: Double, matrices: MatrixStack, consumers: VertexConsumerProvider) {
        val progress = ((elapsed - FIREWORK_USE_START_TICK) / (FIREWORK_FLAME_END_TICK - FIREWORK_USE_START_TICK)).coerceIn(0.0, 1.0)
        val alpha = ((1.0 - progress) * 220.0).toInt()
        val length = 0.035 + progress * 0.11
        val matrix = matrices.peek().positionMatrix
        val buffer = consumers.getBuffer(RenderLayer.getLightning())
        thinQuad(buffer, matrix, RIGHT_HAND_X, RIGHT_HAND_Y, RIGHT_HAND_Z, RIGHT_HAND_X, RIGHT_HAND_Y - length, RIGHT_HAND_Z, 0.012, alpha)
        thinQuad(buffer, matrix, RIGHT_HAND_X - length / 2.0, RIGHT_HAND_Y - 0.03, RIGHT_HAND_Z, RIGHT_HAND_X + length / 2.0, RIGHT_HAND_Y - 0.03, RIGHT_HAND_Z, 0.008, alpha)
    }

    /** 繪製徽記 billboard、役名以及金紅色抵達光場。 */
    private fun renderShowcaseCenter(
        entity: WinCelebrationShowcaseEntity,
        elapsed: Double,
        fadeStart: Double,
        duration: Double,
        billboardRotation: Quaternionf,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
    ) {
        val cue = entity.wings.firstOrNull()?.cueKey.orEmpty()
        val definition = showcaseRegistry.find(cue) ?: fallbackDefinition(cue)
        val fade = when {
            elapsed < fadeStart -> 1.0
            else -> 1.0 - ((elapsed - fadeStart) / (duration - fadeStart)).coerceIn(0.0, 1.0)
        }
        val buffer = consumers.getBuffer(RenderLayer.getLightning())
        val matrix = matrices.peek().positionMatrix
        // 內建舞台不繪製中央 Halo；registry 仍保留該宣告值，讓 extension API 維持相容。
        if (ShowcaseVisualLayer.SparkField in definition.layers) {
            repeat(18) { index ->
                val phase = elapsed * 0.035 + seededUnit(entity.animationSeed, index * 47) * PI * 2.0
                val radius = 0.45 + seededUnit(entity.animationSeed, index * 83) * 0.65
                val x = cos(phase + index) * radius
                val z = sin(phase + index) * radius
                val y = SHOWCASE_HEIGHT + ((elapsed * 0.025 + index * 0.173) % 0.75)
                thinQuad(buffer, matrix, x, y, z, x, y + 0.035, z, 0.006, (fade * 150).toInt(), definition.palette.accent)
            }
        }
        entity.wings.forEachIndexed { index, wing ->
            val wingDefinition = showcaseRegistry.find(wing.cueKey) ?: fallbackDefinition(wing.cueKey)
            renderTitleImage(
                wingDefinition,
                fade,
                formationWingCenterX(entity, index),
                billboardRotation,
                matrices,
                consumers,
                light,
            )
            renderTitleText(
                wingDefinition,
                fade,
                formationWingCenterX(entity, index),
                billboardRotation,
                matrices,
                consumers,
                light,
            )
        }
    }

    /** 在完整牌組正上方繪製透明橫版書法役名。 */
    private fun renderTitleImage(
        definition: WinCelebrationShowcaseDefinition,
        fade: Double,
        localCenterX: Double,
        billboardRotation: Quaternionf,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
    ) {
        val texture = Identifier.tryParse(definition.titleImageResourceId) ?: FALLBACK_TITLE_IMAGE
        matrices.push()
        val offset = Vector3f(localCenterX.toFloat(), 0.0f, 0.0f).rotate(billboardRotation)
        matrices.translate(offset.x.toDouble(), SHOWCASE_HEIGHT + TITLE_IMAGE_HEIGHT + offset.y, offset.z.toDouble())
        matrices.multiply(billboardRotation)
        matrices.scale(TITLE_IMAGE_SCALE, TITLE_IMAGE_SCALE, TITLE_IMAGE_SCALE)
        val entry = matrices.peek()
        val buffer = consumers.getBuffer(RenderLayer.getEntityTranslucent(texture))
        texturedVertex(buffer, entry.positionMatrix, entry.normalMatrix, -1f, -0.5f, 0f, 1f, 1f, light, fade)
        texturedVertex(buffer, entry.positionMatrix, entry.normalMatrix, 1f, -0.5f, 0f, 0f, 1f, light, fade)
        texturedVertex(buffer, entry.positionMatrix, entry.normalMatrix, 1f, 0.5f, 0f, 0f, 0f, light, fade)
        texturedVertex(buffer, entry.positionMatrix, entry.normalMatrix, -1f, 0.5f, 0f, 1f, 0f, light, fade)
        matrices.pop()
    }

    /** 在書法圖下方補上清楚、可本地化的小役名。 */
    private fun renderTitleText(
        definition: WinCelebrationShowcaseDefinition,
        fade: Double,
        localCenterX: Double,
        billboardRotation: Quaternionf,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
    ) {
        val label = Text.translatable(definition.titleTranslationKey)
        matrices.push()
        val offset = Vector3f(localCenterX.toFloat(), 0.0f, 0.0f).rotate(billboardRotation)
        matrices.translate(offset.x.toDouble(), SHOWCASE_HEIGHT - TITLE_TEXT_DROP + offset.y, offset.z.toDouble())
        matrices.multiply(billboardRotation)
        matrices.translate(0.0, 0.0, -0.006)
        matrices.scale(-TITLE_TEXT_SCALE, -TITLE_TEXT_SCALE, TITLE_TEXT_SCALE)
        textRenderer.draw(
            label,
            -textRenderer.getWidth(label) / 2.0f,
            0.0f,
            ((fade * 255.0).toInt().coerceIn(0, 255) shl 24) or TITLE_TEXT_RGB,
            false,
            matrices.peek().positionMatrix,
            consumers,
            TextRenderer.TextLayerType.NORMAL,
            0,
            light,
        )
        matrices.pop()
    }

    private fun tileStack(assetKey: String): ItemStack = tileStacks.getOrPut(assetKey) {
        ItemStack(ModItems.MAHJONG_TILE).also { MahjongTileItem.writeTileAssetKey(it, assetKey) }
    }

    private fun fallbackDefinition(cue: String) = WinCelebrationShowcaseDefinition(
        cueKey = cue.ifBlank { "mahjongcraft:generic" },
        titleTranslationKey = "showcase.mahjongcraft.generic",
        titleImageResourceId = FALLBACK_TITLE_IMAGE.toString(),
        palette = ShowcasePalette(0xFFFFD45A.toInt(), 0xFFC32128.toInt(), -1),
    )

    override fun getTexture(entity: WinCelebrationShowcaseEntity): Identifier? = null

    private fun fadeScaleFactor(elapsed: Double, fadeStart: Double): Double = if (elapsed < fadeStart) {
        1.0
    } else {
        1.0 - smoothStep(((elapsed - fadeStart) / WinCelebrationShowcaseEntity.FADE_OUT_TICKS).coerceIn(0.0, 1.0))
    }

    private fun equipmentVisibility(elapsed: Double): Double = when {
        elapsed < ELYTRA_FADE_START_TICK -> 1.0
        elapsed < EQUIPMENT_FADE_END_TICK -> 1.0 - smoothStep((elapsed - ELYTRA_FADE_START_TICK) / (EQUIPMENT_FADE_END_TICK - ELYTRA_FADE_START_TICK))
        else -> 0.0
    }

    /** 起飛裝備先由小到大具現，再銜接鞘翅展開；抵達時沿用既有反向收束。 */
    private fun equipmentAppearance(elapsed: Double): Double = easeOut(
        ((elapsed - ELYTRA_APPEAR_TICK) / (ELYTRA_OPEN_TICK - ELYTRA_APPEAR_TICK)).coerceIn(0.0, 1.0),
    )

    /** 胡牌張與接觸波紋共用的唯一垂直晃動來源。 */
    private fun winningTileBobOffset(elapsed: Double, fade: Double): Double = sin(elapsed * BOB_SPEED) * BOB_HEIGHT * fade

    /** 相對最近一次最低點的 tick；負值正在下降，正值已開始上升。 */
    private fun winningTileTicksFromBottom(elapsed: Double): Double {
        val twoPi = PI * 2.0
        val rawPhase = elapsed * BOB_SPEED - PI * 1.5
        val wrappedPhase = ((rawPhase + PI) % twoPi + twoPi) % twoPi - PI
        return wrappedPhase / BOB_SPEED
    }

    /** 胡牌張每次接近晃動最低點時，在固定的空中水面生成兩圈短促波紋。 */
    private fun renderWinningTileContactRipples(
        elapsed: Double,
        fade: Double,
        horizontalOffset: Vector3f,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val twoPi = PI * 2.0
        val ticksFromBottom = winningTileTicksFromBottom(elapsed)
        if (ticksFromBottom !in -WINNING_RIPPLE_LEAD_TICKS..WINNING_RIPPLE_FADE_TICKS) return
        val baseProgress = (ticksFromBottom + WINNING_RIPPLE_LEAD_TICKS) / (WINNING_RIPPLE_LEAD_TICKS + WINNING_RIPPLE_FADE_TICKS)
        val buffer = consumers.getBuffer(RenderLayer.getLightning())
        matrices.push()
        matrices.translate(
            horizontalOffset.x.toDouble(),
            SHOWCASE_HEIGHT + WINNING_TILE_HEIGHT_OFFSET - BOB_HEIGHT - WINNING_TILE_RENDER_HEIGHT / 2.0 - WINNING_RIPPLE_BOTTOM_GAP,
            horizontalOffset.z.toDouble(),
        )
        val matrix = matrices.peek().positionMatrix
        repeat(WINNING_RIPPLE_COUNT) { ring ->
            val progress = baseProgress - ring * WINNING_RIPPLE_DELAY_PROGRESS
            if (progress !in 0.0..1.0) return@repeat
            val radius = lerp(WINNING_RIPPLE_START_RADIUS, WINNING_RIPPLE_END_RADIUS, smoothStep(progress))
            val alpha = (sin(progress * PI) * WINNING_RIPPLE_ALPHA * fade).toInt()
            repeat(WINNING_RIPPLE_SEGMENTS) { index ->
                val a = index * twoPi / WINNING_RIPPLE_SEGMENTS
                val b = (index + 1) * twoPi / WINNING_RIPPLE_SEGMENTS
                thinQuad(
                    buffer,
                    matrix,
                    cos(a) * radius,
                    0.0,
                    sin(a) * radius,
                    cos(b) * radius,
                    0.0,
                    sin(b) * radius,
                    WINNING_RIPPLE_WIDTH,
                    alpha,
                    WINNING_RIPPLE_COLOR,
                )
            }
        }
        matrices.pop()
    }

    private fun thinQuad(buffer: VertexConsumer, matrix: Matrix4f, ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double, width: Double, alpha: Int, color: Int = 0xFFFFDC64.toInt()) {
        val dx = bx - ax
        val dz = bz - az
        val length = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.0001)
        val ox = -dz / length * width
        val oz = dx / length * width
        listOf(
            doubleArrayOf(ax - ox, ay, az - oz),
            doubleArrayOf(ax + ox, ay, az + oz),
            doubleArrayOf(bx + ox, by, bz + oz),
            doubleArrayOf(bx - ox, by, bz - oz),
            doubleArrayOf(bx - ox, by, bz - oz),
            doubleArrayOf(bx + ox, by, bz + oz),
            doubleArrayOf(ax + ox, ay, az + oz),
            doubleArrayOf(ax - ox, ay, az - oz),
        ).forEach { point ->
            buffer.vertex(matrix, point[0].toFloat(), point[1].toFloat(), point[2].toFloat())
                .color(color shr 16 and 0xFF, color shr 8 and 0xFF, color and 0xFF, alpha.coerceIn(0, 255)).next()
        }
    }

    private fun texturedVertex(buffer: VertexConsumer, matrix: Matrix4f, normal: Matrix3f, x: Float, y: Float, z: Float, u: Float, v: Float, light: Int, alpha: Double) {
        buffer.vertex(matrix, x, y, z).color(255, 255, 255, (alpha * 255).toInt()).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normal, 0f, 0f, 1f).next()
    }

    private fun formationCardX(entity: WinCelebrationShowcaseEntity, order: Int, count: Int, wingIndex: Int): Double = formationWingCenterX(entity, wingIndex) +
        ((count - 1) / 2.0 - order) * DISPLAY_CARD_SPACING

    /** 以每翼實際左右邊界排版，三家和時把中央共享胡牌張納入中央群組寬度。 */
    private fun formationWingCenters(entity: WinCelebrationShowcaseEntity): DoubleArray {
        val wingCount = entity.wings.size
        if (wingCount == 2) {
            val halfGap = maxOf(MULTI_WINNER_GROUP_GAP, WINNING_TILE_RENDER_WIDTH + WINNING_TILE_GAP * 2.0) / 2.0
            val leftHalf = formationBounds(entity, 0).width / 2.0
            val rightHalf = formationBounds(entity, 1).width / 2.0
            return doubleArrayOf(-(halfGap + leftHalf), halfGap + rightHalf)
        }
        val bounds = entity.wings.indices.map { formationBounds(entity, it) }
        val centers = DoubleArray(wingCount)
        var cursor = 0.0
        bounds.forEachIndexed { index, bound ->
            centers[index] = cursor - bound.minX
            cursor += bound.width + MULTI_WINNER_GROUP_GAP
        }
        val totalWidth = cursor - MULTI_WINNER_GROUP_GAP
        return centers.map { it - totalWidth / 2.0 }.toDoubleArray()
    }

    private fun formationWingCenterX(entity: WinCelebrationShowcaseEntity, wingIndex: Int): Double = formationWingCenters(entity)[wingIndex]

    private fun formationBounds(entity: WinCelebrationShowcaseEntity, wingIndex: Int): HorizontalBounds {
        val cardCount = entity.wings[wingIndex].cards.size.coerceAtLeast(1)
        val handSpan = (cardCount - 1) * DISPLAY_CARD_SPACING + DISPLAY_CARD_RENDER_WIDTH
        var minX = -maxOf(handSpan, TITLE_IMAGE_SCALE * TITLE_QUAD_WIDTH) / 2.0
        val maxX = -minX
        if ((entity.wings.size == 1 && wingIndex == 0) || (entity.wings.size == 3 && wingIndex == 1)) {
            minX = minOf(minX, winningTileRelativeX(cardCount) - WINNING_TILE_RENDER_WIDTH / 2.0)
        }
        return HorizontalBounds(minX, maxX)
    }

    private fun winningTileRelativeX(handCardCount: Int): Double = -(handCardCount - 1) / 2.0 * DISPLAY_CARD_SPACING -
        DISPLAY_CARD_RENDER_WIDTH / 2.0 - WINNING_TILE_GAP - WINNING_TILE_RENDER_WIDTH / 2.0

    private fun winningTileX(entity: WinCelebrationShowcaseEntity): Double = when (entity.wings.size) {
        1 -> formationWingCenterX(entity, 0) + winningTileRelativeX(entity.wings[0].cards.size.coerceAtLeast(1))
        3 -> formationWingCenterX(entity, 1) + winningTileRelativeX(entity.wings[1].cards.size.coerceAtLeast(1))
        else -> 0.0
    }

    private data class FlightPose(val position: Vector3f, val velocity: Vector3f)
    private data class HorizontalBounds(val minX: Double, val maxX: Double) {
        val width: Double get() = maxX - minX
    }
    private fun lerp(start: Double, end: Double, progress: Double) = start + (end - start) * progress
    private fun smoothStep(value: Double): Double = value * value * (3.0 - 2.0 * value)
    private fun easeOut(value: Double): Double = 1.0 - (1.0 - value) * (1.0 - value)
    private fun seededUnit(seed: Long, salt: Int): Double = (((seed xor (salt.toLong() * -7046029254386353131L)) ushr 11) and 0xFFFF).toDouble() / 65535.0

    private companion object {
        val ELYTRA_TEXTURE = Identifier("minecraft", "textures/entity/elytra.png")
        val GLOW_TEXTURE = Identifier("mahjongcraft", "textures/showcase/glow.png")
        val FALLBACK_TITLE_IMAGE = Identifier("mahjongcraft", "textures/showcase/generic.png")
        const val LIFT_END_TICK = 7.0
        const val RIPPLE_START_TICK = 0.0
        const val RIPPLE_END_TICK = 24.0
        const val RIPPLE_DELAY_TICKS = 2.2
        const val RIPPLE_DURATION_TICKS = 11.0
        const val RIPPLE_COUNT = 7
        const val RIPPLE_START_RADIUS = 0.025
        const val RIPPLE_END_RADIUS = 0.14
        const val RIPPLE_SEGMENTS = 16
        const val TABLE_RIPPLE_HEIGHT = 0.005
        const val RIPPLE_Y_OFFSET = 0.0
        const val SHAKE_START_TICK = 0.0
        const val SHAKE_END_TICK = 24.0
        const val SHAKE_OSCILLATIONS = 12.0
        const val STAND_START_TICK = 17.0
        const val STAND_END_TICK = 24.0
        const val SHAKE_PITCH_DEGREES = 3.5
        const val SHAKE_SECONDARY_TILT_DEGREES = 2.5
        const val SHAKE_SECONDARY_FREQUENCY_RATIO = 1.17
        const val ELYTRA_APPEAR_TICK = 40.0
        const val ELYTRA_OPEN_TICK = 44.0
        const val FIREWORK_APPEAR_TICK = 40.0
        const val FIREWORK_USE_START_TICK = 46.0
        const val FLIGHT_START_TICK = 50.0
        const val LAUNCH_TRANSITION_TICKS = 6.0
        const val FIREWORK_FLAME_END_TICK = 54.0
        const val FLIGHT_END_TICK = 104.0
        const val ELYTRA_FADE_START_TICK = 116.0
        const val ELYTRA_FADE_END_TICK = 128.0
        const val EQUIPMENT_FADE_END_TICK = 128.0
        const val SHOWCASE_START_TICK = 116.0
        const val ARRIVAL_TRANSITION_TICKS = SHOWCASE_START_TICK - FLIGHT_END_TICK
        const val LIFT_HEIGHT = 0.08
        const val SHOWCASE_HEIGHT = 1.25
        const val FLIGHT_ARC_HEIGHT = 0.65
        const val BOB_HEIGHT = 0.025
        const val BOB_SPEED = 0.22
        const val RECOIL_DISTANCE = 0.025
        val RIGHT_HAND_X = MahjongTileEntity.TILE_WIDTH / 2.0 + 0.010
        val RIGHT_HAND_Y = -MahjongTileEntity.TILE_HEIGHT * 0.12
        val RIGHT_HAND_Z = -MahjongTileEntity.TILE_DEPTH / 2.0 - 0.003
        val ELYTRA_BACK_Z = MahjongTileEntity.TILE_DEPTH / 2.0 + 0.006
        const val ELYTRA_ROOT_Y = 0.048
        const val ELYTRA_SCALE = 0.14
        const val ELYTRA_FLIGHT_PITCH = 0.16
        const val FIREWORK_ITEM_SCALE = 0.28f
        const val EQUIPMENT_APPEAR_START_SCALE = 0.2
        const val FIREWORK_SETTLE_END_TICK = 54.0
        const val DISPLAY_CARD_SPACING = 0.19
        const val DISPLAY_CARD_SCALE = 1.5
        const val WINNING_TILE_SCALE = DISPLAY_CARD_SCALE
        const val WINNING_TILE_GAP = 0.13
        const val WINNING_TILE_PITCH_SPEED = 0.075
        const val WINNING_TILE_PITCH_DEGREES = 2.5
        const val CARD_ENTRANCE_TICKS = 14.0
        const val CARD_ENTRANCE_STAGGER_TICKS = 0.4
        const val CARD_ENTRANCE_DROP = 0.18
        const val CARD_ENTRANCE_BOUNCE = 0.05
        const val WINNING_TILE_HEIGHT_OFFSET = 0.04
        const val WINNING_TILE_FORWARD_OFFSET = 0.025
        const val WINNING_RIPPLE_LEAD_TICKS = 5.0
        const val WINNING_RIPPLE_FADE_TICKS = 4.0
        const val WINNING_RIPPLE_COUNT = 2
        const val WINNING_RIPPLE_DELAY_PROGRESS = 0.16
        const val WINNING_RIPPLE_START_RADIUS = 0.035
        const val WINNING_RIPPLE_END_RADIUS = 0.18
        const val WINNING_RIPPLE_SEGMENTS = 20
        const val WINNING_RIPPLE_WIDTH = 0.004
        const val WINNING_RIPPLE_ALPHA = 165.0
        val WINNING_RIPPLE_COLOR = 0xFF8DDFFF.toInt()
        const val MULTI_WINNER_GROUP_GAP = 0.46
        const val TITLE_IMAGE_SCALE = 1.38f
        const val TITLE_IMAGE_HEIGHT = 0.65
        const val TITLE_QUAD_WIDTH = 2.0
        const val TITLE_TEXT_DROP = 0.38
        const val TITLE_TEXT_SCALE = 0.018f
        const val TITLE_TEXT_RGB = 0xFFF2D2
        val DISPLAY_CARD_RENDER_WIDTH = MahjongTileEntity.TILE_WIDTH * DISPLAY_CARD_SCALE
        val WINNING_TILE_RENDER_WIDTH = MahjongTileEntity.TILE_WIDTH * WINNING_TILE_SCALE
        val WINNING_TILE_RENDER_HEIGHT = MahjongTileEntity.TILE_HEIGHT * WINNING_TILE_SCALE
        const val WINNING_RIPPLE_BOTTOM_GAP = 0.008
        const val MAX_FLIGHT_DELAY_TICKS = 6.0
        const val TRAIL_SEGMENTS = 7
        const val TRAIL_SAMPLE_TICKS = 1.6
        const val WINNING_TILE_WING_INDEX = 31
        const val WINNING_TILE_ORDER = 97
        const val CARD_TRAIL_COLOR = 0xFFD86A
        const val WINNING_TILE_TRAIL_COLOR = 0x6FEAFF
    }
}
