package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInWinCelebrationCueIds
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.ShowcaseCardSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationCinematicTimeline
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.WinCelebrationShowcaseEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.ShowcasePalette
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.ShowcaseVisualLayer
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftShowcaseKeys
import net.minecraft.block.Blocks
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.model.ModelPart
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.VertexConsumer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.block.BlockRenderManager
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
    private val blockRenderManager: BlockRenderManager = context.blockRenderManager
    private val textRenderer = context.textRenderer
    private val elytraRoot: ModelPart = context.getPart(EntityModelLayers.ELYTRA)
    private val leftWing: ModelPart = elytraRoot.getChild("left_wing")
    private val rightWing: ModelPart = elytraRoot.getChild("right_wing")
    private val tileStacks = mutableMapOf<String, ItemStack>()
    private val fireworkStack = ItemStack(Items.FIREWORK_ROCKET)
    private val tntStack = ItemStack(Items.TNT)
    private val flintAndSteelStack = ItemStack(Items.FLINT_AND_STEEL)

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
        val cardLayouts = buildCardLayouts(entity)
        renderTntCinematic(entity, elapsed, billboardRotation, matrices, vertexConsumers, light)
        entity.wings.forEachIndexed { wingIndex, wing ->
            wing.cards.forEach { card ->
                val layout = cardLayouts[CardKey(wingIndex, card.order, false)] ?: return@forEach
                renderCard(entity, card, wingIndex, elapsed, fadeStart, billboardRotation, matrices, vertexConsumers, light, layout.targetX, returnStartOverride = layout.returnStart)
            }
        }
        renderWinningTile(entity, elapsed, fadeStart, billboardRotation, matrices, vertexConsumers, light, cardLayouts[WINNING_CARD_KEY])
        if (elapsed >= TITLE_REVEAL_START_TICK) {
            renderShowcaseCenter(entity, elapsed, fadeStart, duration, billboardRotation, matrices, vertexConsumers, light)
        }
    }

    /** 單一低成本 TNT 模型、可辨識的引信閃白，以及向外擴張的原版風白色爆炸。 */
    private fun renderTntCinematic(
        entity: WinCelebrationShowcaseEntity,
        elapsed: Double,
        billboardRotation: Quaternionf,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
    ) {
        if (elapsed in TNT_PLACEMENT_TICK..<EXPLOSION_TICK) {
            val handoff = smoothStep(((elapsed - TNT_PLACEMENT_TICK) / TNT_HANDOFF_TICKS).coerceIn(0.0, 1.0))
            val fuse = ((elapsed - IGNITION_TICK) / (EXPLOSION_TICK - IGNITION_TICK)).coerceIn(0.0, 1.0)
            val floatProgress = smoothStep(((elapsed - FUSE_TICK) / (EXPLOSION_TICK - FUSE_TICK)).coerceIn(0.0, 1.0))
            val floatY = floatProgress * TNT_AIR_HEIGHT
            val pulse = if (elapsed < IGNITION_TICK) 1.0 else 1.0 + sin(fuse * fuse * PI * 18.0) * TNT_MAX_PULSE * fuse
            matrices.push()
            matrices.translate(0.0, TABLE_SURFACE_HEIGHT + floatY, 0.0)
            matrices.scale((TNT_WORLD_SCALE * handoff * pulse).toFloat(), (TNT_WORLD_SCALE * handoff * pulse).toFloat(), (TNT_WORLD_SCALE * handoff * pulse).toFloat())
            if (elapsed >= IGNITION_TICK && tntFlashVisible(fuse)) renderTntFlash(matrices, consumers, fuse)
            matrices.translate(-0.5, 0.0, -0.5)
            blockRenderManager.renderBlockAsEntity(Blocks.TNT.defaultState, matrices, consumers, light, OverlayTexture.DEFAULT_UV)
            matrices.pop()
        }
        if (elapsed in IGNITION_TICK..<FUSE_TICK) renderIgnitionSparks(entity, elapsed, billboardRotation, matrices, consumers)
        if (elapsed in EXPLOSION_TICK..<TITLE_TEXT_START_TICK) {
            renderTntExplosion(entity, elapsed, billboardRotation, matrices, consumers)
        }
    }

    private fun renderTntFlash(matrices: MatrixStack, consumers: VertexConsumerProvider, fuse: Double) {
        val buffer = consumers.getBuffer(RenderLayer.getLightning())
        val matrix = matrices.peek().positionMatrix
        val extent = 0.505 + fuse * 0.012
        val alpha = (155 + fuse * 100).toInt()
        val faces = listOf(
            arrayOf(doubleArrayOf(-extent, -0.005, -extent), doubleArrayOf(extent, -0.005, -extent), doubleArrayOf(extent, 1.005, -extent), doubleArrayOf(-extent, 1.005, -extent)),
            arrayOf(doubleArrayOf(extent, -0.005, extent), doubleArrayOf(-extent, -0.005, extent), doubleArrayOf(-extent, 1.005, extent), doubleArrayOf(extent, 1.005, extent)),
            arrayOf(doubleArrayOf(-extent, -0.005, extent), doubleArrayOf(-extent, -0.005, -extent), doubleArrayOf(-extent, 1.005, -extent), doubleArrayOf(-extent, 1.005, extent)),
            arrayOf(doubleArrayOf(extent, -0.005, -extent), doubleArrayOf(extent, -0.005, extent), doubleArrayOf(extent, 1.005, extent), doubleArrayOf(extent, 1.005, -extent)),
            arrayOf(doubleArrayOf(-extent, 1.005, -extent), doubleArrayOf(extent, 1.005, -extent), doubleArrayOf(extent, 1.005, extent), doubleArrayOf(-extent, 1.005, extent)),
            arrayOf(doubleArrayOf(-extent, -0.005, extent), doubleArrayOf(extent, -0.005, extent), doubleArrayOf(extent, -0.005, -extent), doubleArrayOf(-extent, -0.005, -extent)),
        )
        faces.forEach { face ->
            (face + face.reversedArray()).forEach { point ->
                buffer.vertex(matrix, point[0].toFloat(), point[1].toFloat(), point[2].toFloat())
                    .color(255, 255, 255, alpha.coerceIn(0, 255)).next()
            }
        }
    }

    /** 越接近爆炸，白色覆層出現得越頻繁、停留得越久。 */
    private fun tntFlashVisible(fuse: Double): Boolean {
        val cycle = 8.0 - fuse * 6.5
        val phase = (fuse * (EXPLOSION_TICK - IGNITION_TICK)) % cycle
        return phase < lerp(1.0, cycle * 0.72, fuse)
    }

    private fun renderIgnitionSparks(
        entity: WinCelebrationShowcaseEntity,
        elapsed: Double,
        billboardRotation: Quaternionf,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val progress = ((elapsed - IGNITION_TICK) / (FUSE_TICK - IGNITION_TICK)).coerceIn(0.0, 1.0)
        val origin = Vector3f(0.0f, (TABLE_SURFACE_HEIGHT + TNT_WORLD_SCALE).toFloat(), 0.0f)
        val buffer = consumers.getBuffer(RenderLayer.getLightning())
        val matrix = matrices.peek().positionMatrix
        repeat(7) { index ->
            val angle = seededUnit(entity.animationSeed, index * 37) * PI * 2.0
            val radius = progress * (0.05 + seededUnit(entity.animationSeed, index * 71) * 0.13)
            val end = horizontalRelative(cos(angle) * radius, origin.y + sin(progress * PI) * 0.08 + index * 0.004, sin(angle) * radius, billboardRotation)
            thinQuad(buffer, matrix, origin.x.toDouble(), origin.y.toDouble(), origin.z.toDouble(), end.x.toDouble(), end.y.toDouble(), end.z.toDouble(), 0.004, ((1.0 - progress) * 220).toInt(), SPARK_COLOR)
        }
    }

    private fun renderTntExplosion(
        entity: WinCelebrationShowcaseEntity,
        elapsed: Double,
        billboardRotation: Quaternionf,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val explosionProgress = ((elapsed - EXPLOSION_TICK) / EXPLOSION_VISUAL_TICKS).coerceIn(0.0, 1.0)
        val centerY = TABLE_SURFACE_HEIGHT + TNT_AIR_HEIGHT + TNT_WORLD_SCALE / 2.0
        val buffer = consumers.getBuffer(RenderLayer.getLightning())
        val matrix = matrices.peek().positionMatrix
        if (explosionProgress < 0.10) {
            val flashRadius = 0.18 + explosionProgress / 0.10 * 1.15
            repeat(16) { index ->
                val angle = index * PI / 8.0
                thinQuad(buffer, matrix, 0.0, centerY, 0.0, cos(angle) * flashRadius, centerY + sin(angle * 1.7) * flashRadius * 0.4, sin(angle) * flashRadius, 0.035, ((1.0 - explosionProgress / 0.10) * 255).toInt(), TNT_FLASH_COLOR)
            }
        }
        repeat(EXPLOSION_RING_COUNT) { ring ->
            val local = ((explosionProgress - ring * EXPLOSION_RING_DELAY) / (1.0 - ring * EXPLOSION_RING_DELAY)).coerceIn(0.0, 1.0)
            if (local <= 0.0 || local >= 1.0) return@repeat
            val ringProgress = easeOut(local)
            val radius = lerp(0.08, EXPLOSION_MAX_RADIUS, ringProgress)
            repeat(EXPLOSION_RING_SEGMENTS) { index ->
                val a = index * PI * 2.0 / EXPLOSION_RING_SEGMENTS
                val b = (index + 1) * PI * 2.0 / EXPLOSION_RING_SEGMENTS
                val waveY = centerY + sin(a * 2.0 + ring) * radius * 0.08
                thinQuad(buffer, matrix, cos(a) * radius, waveY, sin(a) * radius, cos(b) * radius, waveY, sin(b) * radius, 0.022, (sin(local * PI) * 235).toInt(), TNT_FLASH_COLOR)
            }
        }
        val wingCount = entity.wings.size.coerceAtLeast(1)
        val smokePerWing = when (wingCount) {
            1 -> 30
            2 -> 14
            else -> 10
        }
        val smokeCount = SHARED_SMOKE_COUNT + wingCount * smokePerWing
        repeat(smokeCount) { index ->
            val delay = seededUnit(entity.animationSeed, 700 + index * 29) * SMOKE_MAX_DELAY_TICKS
            val lifetime = SMOKE_MIN_LIFETIME_TICKS + seededUnit(entity.animationSeed, 800 + index * 37) * SMOKE_LIFETIME_VARIANCE_TICKS
            val progress = ((elapsed - EXPLOSION_TICK - delay) / lifetime).coerceIn(0.0, 1.0)
            if (progress <= 0.0 || progress >= 1.0) return@repeat
            val phase = seededUnit(entity.animationSeed, 900 + index * 43) * PI * 2.0
            val travel = easeOut(progress)
            val isShared = index < SHARED_SMOKE_COUNT
            val wingIndex = if (isShared) -1 else (index - SHARED_SMOKE_COUNT) % wingCount
            val wingCenterX = if (isShared) 0.0 else formationWingCenterX(entity, wingIndex)
            val spread = if (isShared) SMOKE_SHARED_SPREAD else SMOKE_WING_SPREAD
            val radialDistance = (0.45 + seededUnit(entity.animationSeed, 1200 + index * 59) * spread) * travel
            val localX = wingCenterX * travel + cos(phase) * radialDistance
            val localZ = sin(phase) * radialDistance * SMOKE_DEPTH_SCALE
            val rise = (SMOKE_MIN_RISE + seededUnit(entity.animationSeed, 1600 + index * 31) * SMOKE_RISE_VARIANCE) * travel
            val position = horizontalRelative(
                localX,
                centerY + rise + sin(phase * 1.7) * radialDistance * 0.18,
                localZ,
                billboardRotation,
            )
            renderSmokePuff(position, progress, index, billboardRotation, matrices, consumers)
        }
    }

    private fun renderSmokePuff(
        position: Vector3f,
        progress: Double,
        index: Int,
        billboardRotation: Quaternionf,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val appear = smoothStep((progress / SMOKE_APPEAR_FRACTION).coerceIn(0.0, 1.0))
        val disappear = 1.0 - smoothStep(((progress - SMOKE_FADE_START_FRACTION) / (1.0 - SMOKE_FADE_START_FRACTION)).coerceIn(0.0, 1.0))
        val alpha = (appear * disappear * SMOKE_MAX_ALPHA).toInt().coerceIn(0, SMOKE_MAX_ALPHA.toInt())
        if (alpha <= 1) return
        val sizeVariance = seededUnit(index.toLong(), 2300 + index * 17) * SMOKE_SIZE_VARIANCE
        val size = (SMOKE_START_SIZE + progress * SMOKE_GROWTH + sizeVariance).toFloat()
        matrices.push()
        matrices.translate(position.x.toDouble(), position.y.toDouble(), position.z.toDouble())
        matrices.multiply(billboardRotation)
        val entry = matrices.peek()
        val smokeFrame = (progress * SMOKE_TEXTURES.size).toInt().coerceIn(0, SMOKE_TEXTURES.lastIndex)
        val buffer = consumers.getBuffer(RenderLayer.getEntityTranslucent(SMOKE_TEXTURES[smokeFrame]))
        val color = if (index % 4 == 0) EXPLOSION_WHITE_COLOR else SMOKE_COLOR
        texturedVertex(buffer, entry.positionMatrix, entry.normalMatrix, -size, -size, 0f, 0f, 1f, 15728880, alpha / 255.0, color)
        texturedVertex(buffer, entry.positionMatrix, entry.normalMatrix, size, -size, 0f, 1f, 1f, 15728880, alpha / 255.0, color)
        texturedVertex(buffer, entry.positionMatrix, entry.normalMatrix, size, size, 0f, 1f, 0f, 15728880, alpha / 255.0, color)
        texturedVertex(buffer, entry.positionMatrix, entry.normalMatrix, -size, size, 0f, 0f, 0f, 15728880, alpha / 255.0, color)
        matrices.pop()
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
        layout: CardLayout?,
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
                targetXOverride = layout?.targetX ?: winningTileX(entity),
                returnStartOverride = layout?.returnStart,
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
        returnStartOverride: Double? = null,
        winningTile: Boolean = false,
    ) {
        val count = entity.wings[wingIndex].cards.size.coerceAtLeast(1)
        val startX = card.startOffsetX
        val startY = card.startOffsetY
        val startZ = card.startOffsetZ
        val localTargetX = targetXOverride ?: formationCardX(entity, card.order, count, wingIndex)
        val returnStart = returnStartOverride ?: WinCelebrationCinematicTimeline.returnStartTick(formationReturnRank(entity, localTargetX, winningTile), totalVisualCardCount(entity))
        val returnProgress = smoothStep(((elapsed - returnStart) / RETURN_DURATION_TICKS).coerceIn(0.0, 1.0))
        val seededPhase = seededUnit(entity.animationSeed, wingIndex * 97 + card.order * 13) * PI * 2.0
        val entranceStart = returnStart
        val entranceProgress = ((elapsed - entranceStart) / CARD_ENTRANCE_TICKS).coerceIn(0.0, 1.0)
        val entranceEase = easeOut(entranceProgress)

        val lift = easeOut(((elapsed - ARMING_START_TICK) / (LIFT_END_TICK - ARMING_START_TICK)).coerceIn(0.0, 1.0)) * LIFT_HEIGHT
        var x = startX
        var y = startY + lift
        var z = startZ
        if (elapsed >= FLIGHT_START_TICK) {
            val pose = flightPose(entity, card, wingIndex, startX, startY, startZ, localTargetX, returnStart, elapsed, billboardRotation, winningTile)
            x = pose.position.x.toDouble()
            y = pose.position.y.toDouble()
            z = pose.position.z.toDouble()
            if (elapsed < returnStart + RETURN_DURATION_TICKS) renderFlightTrail(entity, card, wingIndex, startX, startY, startZ, localTargetX, returnStart, elapsed, billboardRotation, matrices, vertexConsumers, light, winningTile)
        } else if (elapsed >= SHOWCASE_START_TICK) {
            val target = Vector3f(localTargetX.toFloat(), 0.0f, 0.0f).rotate(billboardRotation)
            x = target.x.toDouble()
            z = target.z.toDouble()
        }
        val fade = fadeScaleFactor(elapsed, fadeStart)
        if (returnProgress >= 1.0) {
            val bob = if (entranceProgress >= 1.0) {
                if (winningTile) winningTileBobOffset(elapsed, fade) else sin((elapsed - SHOWCASE_START_TICK) * BOB_SPEED + seededPhase) * BOB_HEIGHT * fade
            } else {
                0.0
            }
            y = SHOWCASE_HEIGHT + if (winningTile) WINNING_TILE_HEIGHT_OFFSET else 0.0
            y += -(1.0 - entranceEase) * CARD_ENTRANCE_DROP + bob
        }

        if (winningTile && returnProgress >= 1.0) {
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
            elapsed >= returnStart -> {
                val pose = flightPose(entity, card, wingIndex, startX, startY, startZ, localTargetX, returnStart, elapsed.coerceAtMost(returnStart + RETURN_DURATION_TICKS - 0.01), billboardRotation, winningTile)
                val impactRecovery = smoothStep(((returnProgress - EXPLOSION_IMPACT_FRACTION) / EXPLOSION_POSE_RECOVERY_FRACTION).coerceIn(0.0, 1.0))
                val arrival = smoothStep(((returnProgress - BILLBOARD_TRANSITION_START) / (1.0 - BILLBOARD_TRANSITION_START)).coerceIn(0.0, 1.0))
                blastFacingRotation(pose.position)
                    .slerp(flightRotation(pose.velocity), impactRecovery.toFloat())
                    .slerp(Quaternionf(billboardRotation), arrival.toFloat())
            }
            winningTile && elapsed in TNT_PLACEMENT_TICK..<WINNING_REJOIN_START_TICK -> Quaternionf(billboardRotation)
            winningTile && elapsed in WINNING_REJOIN_START_TICK..<WINNING_RETREAT_END_TICK -> {
                val progress = smoothStep((elapsed - WINNING_REJOIN_START_TICK) / (WINNING_RETREAT_END_TICK - WINNING_REJOIN_START_TICK)).toFloat()
                val pose = flightPose(entity, card, wingIndex, startX, startY, startZ, localTargetX, returnStart, elapsed, billboardRotation, true)
                Quaternionf(billboardRotation).slerp(flightRotation(pose.velocity), progress)
            }
            elapsed < STAND_START_TICK -> faceUpRotation(card.startYaw)
            elapsed < STAND_END_TICK -> {
                val progress = smoothStep((elapsed - STAND_START_TICK) / (STAND_END_TICK - STAND_START_TICK)).toFloat()
                faceUpRotation(card.startYaw).slerp(standingRotation(card.startYaw), progress)
            }
            elapsed < FLIGHT_START_TICK -> standingRotation(card.startYaw)
            else -> {
                val flightElapsed = elapsed.coerceIn(FLIGHT_START_TICK, FLIGHT_END_TICK - 0.01)
                val pose = flightPose(entity, card, wingIndex, startX, startY, startZ, localTargetX, returnStart, flightElapsed, billboardRotation, winningTile)
                val pathRotation = flightRotation(pose.velocity)
                val turnProgress = smoothStep(((elapsed - DEPARTURE_TURN_START_TICK) / DEPARTURE_TURN_DURATION_TICKS).coerceIn(0.0, 1.0))
                standingRotation(card.startYaw).slerp(
                    pathRotation,
                    turnProgress.toFloat(),
                )
            }
        }
        matrices.multiply(poseRotation)
        if (elapsed >= returnStart) {
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((360.0 * returnProgress).toFloat()))
        }
        if (winningTile && entranceProgress >= 1.0) {
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((sin((elapsed - FLIGHT_END_TICK - CARD_ENTRANCE_TICKS) * WINNING_TILE_PITCH_SPEED) * WINNING_TILE_PITCH_DEGREES).toFloat()))
            matrices.translate(0.0, 0.0, WINNING_TILE_FORWARD_OFFSET)
        }
        if (elapsed >= returnStart) {
            val arrival = returnProgress
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
            if (winningTile) renderWinningTileHeldItem(elapsed, matrices, vertexConsumers, light, entity)
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

    /** 以左手握持點為樞紐快速擺動煙火；不再用前後平移模仿右鍵。 */
    private fun renderHeldFirework(
        elapsed: Double,
        order: Int,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
        entity: WinCelebrationShowcaseEntity,
    ) {
        val heightOffset = seededUnit(entity.animationSeed, order * 31) * 0.025
        val useProgress = ((elapsed - FIREWORK_USE_START_TICK) / (FIREWORK_SETTLE_END_TICK - FIREWORK_USE_START_TICK)).coerceIn(0.0, 1.0)
        val armSwing = if (elapsed in FIREWORK_USE_START_TICK..<FIREWORK_SETTLE_END_TICK) sin(sqrt(useProgress) * PI) else 0.0
        val visibility = equipmentVisibility(elapsed)
        val appearance = equipmentAppearance(elapsed)
        val scale = visibility * lerp(EQUIPMENT_APPEAR_START_SCALE, 1.0, appearance)
        matrices.push()
        matrices.translate(LEFT_HAND_X, RIGHT_HAND_Y + heightOffset, RIGHT_HAND_Z)
        matrices.translate(0.0, -FIREWORK_GRIP_OFFSET, 0.0)
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((armSwing * FIREWORK_SWING_ROLL).toFloat()))
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((armSwing * FIREWORK_SWING_PITCH).toFloat()))
        matrices.translate(0.0, FIREWORK_GRIP_OFFSET, 0.0)
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(FIREWORK_HOLD_PITCH.toFloat()))
        matrices.scale((FIREWORK_ITEM_SCALE * scale).toFloat(), (FIREWORK_ITEM_SCALE * scale).toFloat(), (FIREWORK_ITEM_SCALE * scale).toFloat())
        itemRenderer.renderItem(fireworkStack, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV, matrices, consumers, entity.world, order + 4000)
        matrices.pop()
    }

    /** 胡牌張右手依序拿出 TNT、完成放置交接，再換成打火石點燃。 */
    private fun renderWinningTileHeldItem(
        elapsed: Double,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
        entity: WinCelebrationShowcaseEntity,
    ) {
        if (elapsed !in TNT_APPROACH_TICK..<WINNING_RETURN_TICK) return
        val holdingTnt = elapsed < TOOL_SWAP_TICK
        val appearance = if (holdingTnt) {
            smoothStep(((elapsed - TNT_APPROACH_TICK) / TNT_HELD_APPEAR_TICKS).coerceIn(0.0, 1.0)) *
                (1.0 - smoothStep(((elapsed - TNT_PLACEMENT_TICK) / TNT_HANDOFF_TICKS).coerceIn(0.0, 1.0)))
        } else {
            smoothStep(((elapsed - TOOL_SWAP_TICK) / FLINT_APPEAR_TICKS).coerceIn(0.0, 1.0)) *
                (1.0 - smoothStep(((elapsed - FLINT_FADE_TICK) / (WINNING_RETURN_TICK - FLINT_FADE_TICK)).coerceIn(0.0, 1.0)))
        }
        if (appearance <= 0.001) return
        val useProgress = ((elapsed - IGNITION_TICK) / (FUSE_TICK - IGNITION_TICK)).coerceIn(0.0, 1.0)
        val swing = if (elapsed in IGNITION_TICK..<FUSE_TICK) sin(useProgress * PI) else 0.0
        val placement = if (holdingTnt) smoothStep(((elapsed - TNT_PLACEMENT_TICK) / TNT_HANDOFF_TICKS).coerceIn(0.0, 1.0)) else 0.0
        matrices.push()
        matrices.translate(
            RIGHT_HAND_X + placement * (WINNING_PLACEMENT_SIDE_OFFSET - RIGHT_HAND_X) + swing * FLINT_REACH_SIDE,
            RIGHT_HAND_Y - placement * 0.05 + swing * 0.025,
            RIGHT_HAND_Z - placement * WINNING_PLACEMENT_FORWARD - swing * FLINT_REACH_FORWARD,
        )
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((-18.0 - swing * 62.0).toFloat()))
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((-22.0 - placement * 28.0).toFloat()))
        val itemScale = (if (holdingTnt) HELD_TNT_SCALE else FLINT_ITEM_SCALE) * appearance
        matrices.scale(itemScale.toFloat(), itemScale.toFloat(), itemScale.toFloat())
        itemRenderer.renderItem(if (holdingTnt) tntStack else flintAndSteelStack, ModelTransformationMode.GROUND, light, OverlayTexture.DEFAULT_UV, matrices, consumers, entity.world, 6100)
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
        returnStart: Double,
        elapsed: Double,
        billboardRotation: Quaternionf,
        winningTile: Boolean,
    ): FlightPose {
        val now = flightPosition(entity, card, wingIndex, startX, startY, startZ, localTargetX, returnStart, elapsed, billboardRotation, winningTile)
        val next = flightPosition(entity, card, wingIndex, startX, startY, startZ, localTargetX, returnStart, elapsed + 0.2, billboardRotation, winningTile)
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
        returnStart: Double,
        elapsed: Double,
        billboardRotation: Quaternionf,
        winningTile: Boolean,
    ): Vector3f {
        val target = horizontalRelative(localTargetX, SHOWCASE_HEIGHT + if (winningTile) WINNING_TILE_HEIGHT_OFFSET else 0.0, 0.0, billboardRotation)
        val seedA = seededUnit(entity.animationSeed, wingIndex * 211 + card.order * 71)
        val seedB = seededUnit(entity.animationSeed, wingIndex * 263 + card.order * 89)
        val start = Vector3f(startX.toFloat(), (startY + LIFT_HEIGHT).toFloat(), startZ.toFloat())
        val orbit = orbitPosition(entity, wingIndex, card.order, elapsed, billboardRotation, winningTile)
        val departure = initialDepartureDirection(startX, startZ, card.startYaw)
        if (elapsed < BOOST_END_TICK) {
            val progress = ((elapsed - FLIGHT_START_TICK) / (BOOST_END_TICK - FLIGHT_START_TICK)).coerceIn(0.0, 1.0)
            val fastStart = progress * (2.0 - BOOST_DECELERATION * progress) / (2.0 - BOOST_DECELERATION)
            return Vector3f(start)
                .add(Vector3f(departure).mul((fastStart * BOOST_DISTANCE).toFloat()))
                .add(0.0f, (fastStart * BOOST_RISE).toFloat(), 0.0f)
        }
        if (elapsed < ORBIT_ENTRY_END_TICK) {
            val progress = ((elapsed - BOOST_END_TICK) / (ORBIT_ENTRY_END_TICK - BOOST_END_TICK)).coerceIn(0.0, 1.0)
            val duration = ORBIT_ENTRY_END_TICK - BOOST_END_TICK
            val boostEnd = Vector3f(start).add(Vector3f(departure).mul(BOOST_DISTANCE.toFloat())).add(0.0f, BOOST_RISE.toFloat(), 0.0f)
            val orbitEntry = orbitPosition(entity, wingIndex, card.order, ORBIT_ENTRY_END_TICK, billboardRotation, winningTile)
            val orbitNext = orbitPosition(entity, wingIndex, card.order, ORBIT_ENTRY_END_TICK + 0.2, billboardRotation, winningTile)
            val startTangent = Vector3f(departure).mul((BOOST_END_FORWARD_VELOCITY * duration).toFloat())
                .add(0.0f, (BOOST_END_RISE_VELOCITY * duration).toFloat(), 0.0f)
            val endTangent = Vector3f(orbitNext).sub(orbitEntry).mul((duration / 0.2).toFloat())
            val curved = hermite(boostEnd, orbitEntry, startTangent, endTangent, progress)
            val envelope = sin(progress * PI)
            val right = horizontalRelative(1.0, 0.0, 0.0, billboardRotation)
            val forward = horizontalRelative(0.0, 0.0, 1.0, billboardRotation)
            val turningEnvelope = smoothStep((progress * 1.35).coerceIn(0.0, 1.0)) * envelope * envelope
            return curved.add(right.mul((sin(progress * PI * (1.2 + seedA * 0.5) + seedB * PI) * turningEnvelope * (0.20 + seedA * 0.22)).toFloat()))
                .add(forward.mul((cos(progress * PI * (1.1 + seedB * 0.35)) * turningEnvelope * 0.18).toFloat()))
                .add(0.0f, (turningEnvelope * (0.16 + seedB * 0.18)).toFloat(), 0.0f)
        }
        if (winningTile && elapsed in TNT_APPROACH_TICK..<WINNING_RETREAT_END_TICK) {
            val placementPose = horizontalRelative(-WINNING_PLACEMENT_SIDE_OFFSET, WINNING_PLACEMENT_HEIGHT, WINNING_PLACEMENT_FORWARD, billboardRotation)
            if (elapsed < TNT_PLACEMENT_TICK) {
                val approach = smoothStep(((elapsed - TNT_APPROACH_TICK) / (TNT_PLACEMENT_TICK - TNT_APPROACH_TICK)).coerceIn(0.0, 1.0))
                val approachOrigin = orbitPosition(entity, wingIndex, card.order, TNT_APPROACH_TICK, billboardRotation, true)
                return Vector3f(approachOrigin).lerp(placementPose, approach.toFloat()).add(0.0f, (sin(approach * PI) * 0.12).toFloat(), 0.0f)
            }
            if (elapsed < FUSE_TICK) return Vector3f(placementPose).add(0.0f, (sin(elapsed * 0.35) * 0.008).toFloat(), 0.0f)
            val recoilPose = horizontalRelative(-WINNING_PLACEMENT_SIDE_OFFSET, WINNING_PLACEMENT_HEIGHT + WINNING_RECOIL_RISE, WINNING_PLACEMENT_FORWARD + WINNING_RECOIL_DISTANCE, billboardRotation)
            if (elapsed < WINNING_REJOIN_START_TICK) {
                val recoil = easeOut(((elapsed - FUSE_TICK) / (WINNING_REJOIN_START_TICK - FUSE_TICK)).coerceIn(0.0, 1.0))
                return Vector3f(placementPose).lerp(recoilPose, recoil.toFloat())
            }
            val rejoin = ((elapsed - WINNING_REJOIN_START_TICK) / (WINNING_RETREAT_END_TICK - WINNING_REJOIN_START_TICK)).coerceIn(0.0, 1.0)
            val duration = WINNING_RETREAT_END_TICK - WINNING_REJOIN_START_TICK
            val orbitEntry = orbitPosition(entity, wingIndex, card.order, WINNING_RETREAT_END_TICK, billboardRotation, true)
            val orbitNext = orbitPosition(entity, wingIndex, card.order, WINNING_RETREAT_END_TICK + 0.2, billboardRotation, true)
            val endTangent = Vector3f(orbitNext).sub(orbitEntry).mul((duration / 0.2).toFloat())
            val curved = hermite(recoilPose, orbitEntry, Vector3f(), endTangent, rejoin)
            return curved.add(0.0f, (sin(rejoin * PI) * sin(rejoin * PI) * WINNING_REJOIN_ARC).toFloat(), 0.0f)
        }
        if (elapsed < returnStart) return orbit
        val returnProgress = smoothStep(((elapsed - returnStart) / RETURN_DURATION_TICKS).coerceIn(0.0, 1.0))
        val returnOrigin = orbitPosition(entity, wingIndex, card.order, returnStart, billboardRotation, winningTile)
        val radial = Vector3f(returnOrigin.x, 0.0f, returnOrigin.z)
        if (radial.lengthSquared() < 0.000001f) radial.set((seedA - 0.5).toFloat(), 0.0f, (seedB - 0.5).toFloat())
        radial.normalize()
        val blastDistance = EXPLOSION_CARD_PUSH_MIN + seedB * EXPLOSION_CARD_PUSH_VARIANCE
        val blastPoint = Vector3f(returnOrigin).add(Vector3f(radial).mul(blastDistance.toFloat())).add(0.0f, ((seedA - 0.35) * EXPLOSION_IMPACT_HEIGHT_VARIANCE).toFloat(), 0.0f)
        if (returnProgress < EXPLOSION_IMPACT_FRACTION) {
            val impact = easeOut(returnProgress / EXPLOSION_IMPACT_FRACTION)
            return Vector3f(returnOrigin).lerp(blastPoint, impact.toFloat())
        }
        val travel = smoothStep(((returnProgress - EXPLOSION_IMPACT_FRACTION) / (1.0 - EXPLOSION_IMPACT_FRACTION)).coerceIn(0.0, 1.0))
        val result = Vector3f(blastPoint).lerp(target, travel.toFloat())
        val arc = sin(travel * PI) * (EXPLOSION_CARD_ARC_MIN + seedA * EXPLOSION_CARD_ARC_VARIANCE)
        return result.add(0.0f, arc.toFloat(), 0.0f)
    }

    private fun orbitPosition(entity: WinCelebrationShowcaseEntity, wingIndex: Int, order: Int, elapsed: Double, billboardRotation: Quaternionf, winningTile: Boolean): Vector3f {
        val salt = wingIndex * 67 + order * 19 + if (winningTile) 701 else 0
        val wingCount = entity.wings.size.coerceAtLeast(1)
        val directionOffset = (seededUnit(entity.animationSeed, wingIndex * 137 + 29) * 3.0).toInt()
        val counterClockwise = if (winningTile) {
            (wingIndex + directionOffset) % 2 == 0
        } else {
            (order + wingIndex + directionOffset) % 3 == 0
        }
        val direction = if (counterClockwise) -1.0 else 1.0
        val phase = elapsed * ORBIT_SPEED * direction + seededUnit(entity.animationSeed, salt) * PI * 2.0 + wingIndex * PI * 2.0 / wingCount
        val directionLane = if (counterClockwise) 1.0 else 0.0
        val radius = 0.88 + wingIndex * 0.16 + directionLane * 0.13 + seededUnit(entity.animationSeed, salt + 11) * 0.22
        val heightLane = (order + wingIndex) % 3
        val height = 1.26 + wingIndex * 0.12 + directionLane * 0.09 + heightLane * 0.055 + sin(phase * 1.7) * 0.13
        val flattening = if (counterClockwise) 0.68 else 0.60
        return horizontalRelative(cos(phase) * radius, height, sin(phase) * radius * flattening, billboardRotation)
    }

    /** 只取 camera billboard 的水平朝向，讓牌流繞桌而不會因玩家抬頭而傾斜整條軌道。 */
    private fun horizontalRelative(localX: Double, y: Double, localZ: Double, billboardRotation: Quaternionf): Vector3f {
        val right = Vector3f(1.0f, 0.0f, 0.0f).rotate(billboardRotation).setComponent(1, 0.0f)
        val forward = Vector3f(0.0f, 0.0f, 1.0f).rotate(billboardRotation).setComponent(1, 0.0f)
        if (right.lengthSquared() < 0.000001f) right.set(1.0f, 0.0f, 0.0f) else right.normalize()
        if (forward.lengthSquared() < 0.000001f) forward.set(0.0f, 0.0f, 1.0f) else forward.normalize()
        return right.mul(localX.toFloat()).add(forward.mul(localZ.toFloat())).add(0.0f, y.toFloat(), 0.0f)
    }

    private fun totalVisualCardCount(entity: WinCelebrationShowcaseEntity): Int = entity.wings.sumOf { it.cards.size } + if (entity.winningTileSnapshot != null) 1 else 0

    /** 每幀只建立、排序一次最終版面，供全部牌的目標 X 與歸位起點共用。 */
    private fun buildCardLayouts(entity: WinCelebrationShowcaseEntity): Map<CardKey, CardLayout> {
        val centers = formationWingCenters(entity)
        val targets = buildList {
            entity.wings.forEachIndexed { wingIndex, wing ->
                wing.cards.forEach { card ->
                    add(CardKey(wingIndex, card.order, false) to (centers[wingIndex] + ((wing.cards.size - 1) / 2.0 - card.order) * DISPLAY_CARD_SPACING))
                }
            }
            if (entity.winningTileSnapshot != null) {
                val winningX = when (entity.wings.size) {
                    1 -> centers[0] + winningTileRelativeX(entity.wings[0].cards.size.coerceAtLeast(1))
                    3 -> centers[1] + winningTileRelativeX(entity.wings[1].cards.size.coerceAtLeast(1))
                    else -> 0.0
                }
                add(WINNING_CARD_KEY to winningX)
            }
        }.sortedWith(compareByDescending<Pair<CardKey, Double>> { it.second }.thenBy { if (it.first.winningTile) 1 else 0 })
        return targets.mapIndexed { rank, (key, targetX) ->
            key to CardLayout(targetX, WinCelebrationCinematicTimeline.returnStartTick(rank, targets.size))
        }.toMap()
    }

    private fun formationReturnRank(entity: WinCelebrationShowcaseEntity, targetX: Double, winningTile: Boolean): Int {
        val targets = buildList {
            entity.wings.forEachIndexed { wingIndex, wing -> wing.cards.forEach { add(formationCardX(entity, it.order, wing.cards.size.coerceAtLeast(1), wingIndex)) } }
            if (entity.winningTileSnapshot != null) add(winningTileX(entity))
        }.sortedDescending()
        val matches = targets.withIndex().filter { kotlin.math.abs(it.value - targetX) < 0.0001 }
        return if (winningTile) targets.lastIndex else matches.firstOrNull()?.index ?: 0
    }

    /** 讓牌的長軸沿路徑切線，使牌面平面與飛行方向平行。 */
    private fun flightRotation(velocity: Vector3f): Quaternionf = Quaternionf().rotationTo(Vector3f(0.0f, 1.0f, 0.0f), Vector3f(velocity).normalize())

    /** 爆炸初段讓牌背朝爆心、牌面朝外，以直立姿態被衝擊波推出。 */
    private fun blastFacingRotation(position: Vector3f): Quaternionf {
        val outward = Vector3f(position.x, 0.0f, position.z)
        if (outward.lengthSquared() < 0.000001f) outward.set(0.0f, 0.0f, 1.0f) else outward.normalize()
        return Quaternionf().rotationTo(Vector3f(0.0f, 0.0f, 1.0f), outward.negate())
    }

    /** 牌先沿桌心到自身的放射方向滑出；只有位置退化時才用原始 yaw 作備援。 */
    private fun initialDepartureDirection(startX: Double, startZ: Double, startYaw: Float): Vector3f {
        val direction = Vector3f(startX.toFloat(), 0.0f, startZ.toFloat())
        if (direction.lengthSquared() < 0.000001f) {
            val yaw = Math.toRadians(startYaw.toDouble())
            direction.set(-sin(yaw).toFloat(), 0.0f, cos(yaw).toFloat())
        }
        return direction.normalize()
    }

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
        returnStart: Double,
        elapsed: Double,
        billboardRotation: Quaternionf,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
        winningTile: Boolean,
    ) {
        val buffer = consumers.getBuffer(RenderLayer.getEntityTranslucent(GLOW_TEXTURE))
        val entry = matrices.peek()
        var previous = flightPosition(entity, card, wingIndex, startX, startY, startZ, localTargetX, returnStart, elapsed, billboardRotation, winningTile)
        val segmentCount = when {
            entity.wings.size >= 3 -> 3
            elapsed in ORBIT_BUILDUP_TICK..<FORMATION_RETURN_TICK -> 4
            else -> TRAIL_SEGMENTS
        }
        repeat(segmentCount) { index ->
            val sampleElapsed = elapsed - (index + 1) * TRAIL_SAMPLE_TICKS
            if (sampleElapsed < FLIGHT_START_TICK) return@repeat
            val point = flightPosition(entity, card, wingIndex, startX, startY, startZ, localTargetX, returnStart, sampleElapsed, billboardRotation, winningTile)
            val strength = 1.0 - index.toDouble() / segmentCount
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
        thinQuad(buffer, matrix, LEFT_HAND_X, RIGHT_HAND_Y, RIGHT_HAND_Z, LEFT_HAND_X, RIGHT_HAND_Y - length, RIGHT_HAND_Z, 0.012, alpha)
        thinQuad(buffer, matrix, LEFT_HAND_X - length / 2.0, RIGHT_HAND_Y - 0.03, RIGHT_HAND_Z, LEFT_HAND_X + length / 2.0, RIGHT_HAND_Y - 0.03, RIGHT_HAND_Z, 0.008, alpha)
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
        if (elapsed >= SHOWCASE_START_TICK && ShowcaseVisualLayer.SparkField in definition.layers) {
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
                elapsed,
                fade,
                formationWingCenterX(entity, index),
                billboardRotation,
                matrices,
                consumers,
            )
            renderTitleText(
                wingDefinition,
                elapsed,
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
        elapsed: Double,
        fade: Double,
        localCenterX: Double,
        billboardRotation: Quaternionf,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val texture = Identifier.tryParse(definition.titleImageResourceId) ?: FALLBACK_TITLE_IMAGE
        matrices.push()
        val offset = Vector3f(localCenterX.toFloat(), 0.0f, 0.0f).rotate(billboardRotation)
        val reveal = ((elapsed - TITLE_REVEAL_START_TICK) / (SHOWCASE_START_TICK - TITLE_REVEAL_START_TICK)).coerceIn(0.0, 1.0)
        val revealScale = when {
            reveal < 0.28 -> lerp(1.7, 0.9, smoothStep(reveal / 0.28))
            reveal < 0.62 -> lerp(0.9, 1.05, smoothStep((reveal - 0.28) / 0.34))
            else -> lerp(1.05, 1.0, smoothStep((reveal - 0.62) / 0.38))
        }
        matrices.translate(offset.x.toDouble(), SHOWCASE_HEIGHT + TITLE_IMAGE_HEIGHT + offset.y, offset.z.toDouble())
        matrices.multiply(billboardRotation)
        matrices.scale((TITLE_IMAGE_SCALE * revealScale).toFloat(), (TITLE_IMAGE_SCALE * revealScale).toFloat(), (TITLE_IMAGE_SCALE * revealScale).toFloat())
        val entry = matrices.peek()
        val buffer = consumers.getBuffer(WinCelebrationTitleRenderLayer.get(texture))
        val revealAlpha = fade * smoothStep((reveal / 0.22).coerceIn(0.0, 1.0))
        titleVertex(buffer, entry.positionMatrix, -1f, -0.5f, 0f, 1f, 1f, revealAlpha)
        titleVertex(buffer, entry.positionMatrix, 1f, -0.5f, 0f, 0f, 1f, revealAlpha)
        titleVertex(buffer, entry.positionMatrix, 1f, 0.5f, 0f, 0f, 0f, revealAlpha)
        titleVertex(buffer, entry.positionMatrix, -1f, 0.5f, 0f, 1f, 0f, revealAlpha)
        matrices.pop()
    }

    /** 在書法圖下方補上清楚、可本地化的小役名。 */
    private fun renderTitleText(
        definition: WinCelebrationShowcaseDefinition,
        elapsed: Double,
        fade: Double,
        localCenterX: Double,
        billboardRotation: Quaternionf,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
    ) {
        if (elapsed < TITLE_TEXT_START_TICK) return
        val reveal = smoothStep(((elapsed - TITLE_TEXT_START_TICK) / TITLE_TEXT_FADE_TICKS).coerceIn(0.0, 1.0))
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
            ((fade * reveal * 255.0).toInt().coerceIn(0, 255) shl 24) or TITLE_TEXT_RGB,
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
        cueKey = cue.ifBlank { BuiltInWinCelebrationCueIds.GENERIC },
        titleTranslationKey = MinecraftShowcaseKeys.GENERIC,
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

    private fun texturedVertex(buffer: VertexConsumer, matrix: Matrix4f, normal: Matrix3f, x: Float, y: Float, z: Float, u: Float, v: Float, light: Int, alpha: Double, color: Int = 0xFFFFFF) {
        buffer.vertex(matrix, x, y, z).color(color shr 16 and 0xFF, color shr 8 and 0xFF, color and 0xFF, (alpha * 255).toInt()).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(normal, 0f, 0f, 1f).next()
    }

    private fun titleVertex(buffer: VertexConsumer, matrix: Matrix4f, x: Float, y: Float, z: Float, u: Float, v: Float, alpha: Double) {
        buffer.vertex(matrix, x, y, z).color(255, 255, 255, (alpha * 255).toInt()).texture(u, v).light(FULL_BRIGHT_LIGHT).next()
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
    private data class CardKey(val wingIndex: Int, val order: Int, val winningTile: Boolean)
    private data class CardLayout(val targetX: Double, val returnStart: Double)
    private data class HorizontalBounds(val minX: Double, val maxX: Double) {
        val width: Double get() = maxX - minX
    }
    private fun hermite(start: Vector3f, end: Vector3f, startTangent: Vector3f, endTangent: Vector3f, progress: Double): Vector3f {
        val t = progress.toFloat()
        val t2 = t * t
        val t3 = t2 * t
        return Vector3f(start).mul(2f * t3 - 3f * t2 + 1f)
            .add(Vector3f(startTangent).mul(t3 - 2f * t2 + t))
            .add(Vector3f(end).mul(-2f * t3 + 3f * t2))
            .add(Vector3f(endTangent).mul(t3 - t2))
    }
    private fun lerp(start: Double, end: Double, progress: Double) = start + (end - start) * progress
    private fun smoothStep(value: Double): Double = value * value * (3.0 - 2.0 * value)
    private fun easeOut(value: Double): Double = 1.0 - (1.0 - value) * (1.0 - value)
    private fun seededUnit(seed: Long, salt: Int): Double = (((seed xor (salt.toLong() * -7046029254386353131L)) ushr 11) and 0xFFFF).toDouble() / 65535.0

    private companion object {
        val ELYTRA_TEXTURE = Identifier("minecraft", "textures/entity/elytra.png")
        val GLOW_TEXTURE = Identifier("mahjongcraft", "textures/showcase/glow.png")
        val SMOKE_TEXTURES = Array(12) { frame -> Identifier("minecraft", "textures/particle/big_smoke_$frame.png") }
        val FALLBACK_TITLE_IMAGE = Identifier("mahjongcraft", "textures/showcase/generic.png")
        const val FULL_BRIGHT_LIGHT = 15728880
        val WINNING_CARD_KEY = CardKey(WINNING_TILE_WING_INDEX, WINNING_TILE_ORDER, true)
        const val ARMING_START_TICK = 0.0
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
        const val ELYTRA_APPEAR_TICK = 32.0
        const val ELYTRA_OPEN_TICK = 40.0
        const val FIREWORK_APPEAR_TICK = 32.0
        const val FIREWORK_USE_START_TICK = 48.0
        const val FLIGHT_START_TICK = 52.0
        const val BOOST_END_TICK = 66.0
        const val BOOST_DISTANCE = 0.18
        const val BOOST_RISE = 0.54
        const val BOOST_DECELERATION = 0.8
        const val BOOST_END_FORWARD_VELOCITY = 0.004285714285714286
        const val BOOST_END_RISE_VELOCITY = 0.012857142857142857
        const val DEPARTURE_TURN_START_TICK = 60.0
        const val DEPARTURE_TURN_DURATION_TICKS = 20.0
        const val FIREWORK_FLAME_END_TICK = 56.0
        const val FLIGHT_END_TICK = 260.0
        const val ELYTRA_FADE_START_TICK = 260.0
        const val ELYTRA_FADE_END_TICK = 276.0
        const val EQUIPMENT_FADE_END_TICK = 276.0
        const val SHOWCASE_START_TICK = 300.0
        const val ARRIVAL_TRANSITION_TICKS = SHOWCASE_START_TICK - FLIGHT_END_TICK
        const val ORBIT_BUILDUP_TICK = 80.0
        const val ORBIT_ENTRY_END_TICK = 124.0
        const val TNT_APPROACH_TICK = 124.0
        const val TNT_PLACEMENT_TICK = 148.0
        const val TOOL_SWAP_TICK = 158.0
        const val IGNITION_TICK = 166.0
        const val FUSE_TICK = 174.0
        const val EXPLOSION_TICK = 212.0
        const val TITLE_REVEAL_TICK = 276.0
        const val TITLE_REVEAL_START_TICK = TITLE_REVEAL_TICK
        const val TITLE_TEXT_START_TICK = 288.0
        const val TITLE_TEXT_FADE_TICKS = 8.0
        const val FORMATION_RETURN_TICK = 214.0
        const val FORMATION_RETURN_START_TICK = FORMATION_RETURN_TICK
        const val FLINT_FADE_TICK = 252.0
        const val WINNING_RETURN_TICK = 260.0
        const val LIFT_HEIGHT = 0.08
        const val SHOWCASE_HEIGHT = 1.25
        const val FLIGHT_ARC_HEIGHT = 0.65
        const val BOB_HEIGHT = 0.025
        const val BOB_SPEED = 0.22
        const val RECOIL_DISTANCE = 0.025
        val RIGHT_HAND_X = MahjongTileEntity.TILE_WIDTH / 2.0 + 0.010
        val LEFT_HAND_X = -RIGHT_HAND_X
        val RIGHT_HAND_Y = -MahjongTileEntity.TILE_HEIGHT * 0.12
        val RIGHT_HAND_Z = -MahjongTileEntity.TILE_DEPTH / 2.0 - 0.003
        val ELYTRA_BACK_Z = MahjongTileEntity.TILE_DEPTH / 2.0 + 0.006
        const val ELYTRA_ROOT_Y = 0.048
        const val ELYTRA_SCALE = 0.14
        const val ELYTRA_FLIGHT_PITCH = 0.16
        const val FIREWORK_ITEM_SCALE = 0.28f
        const val FIREWORK_HOLD_PITCH = -35.0
        const val FIREWORK_SWING_PITCH = -10.0
        const val FIREWORK_SWING_ROLL = 48.0
        const val FIREWORK_GRIP_OFFSET = 0.085
        const val HELD_TNT_SCALE = 0.25
        const val FLINT_ITEM_SCALE = 0.28
        const val EQUIPMENT_APPEAR_START_SCALE = 0.2
        const val FIREWORK_SETTLE_END_TICK = 58.0
        const val RETURN_DURATION_TICKS = 40.0
        const val WINNING_REJOIN_START_TICK = 180.0
        const val WINNING_RETREAT_END_TICK = 204.0
        const val WINNING_RECOIL_DISTANCE = 0.18
        const val WINNING_RECOIL_RISE = 0.06
        const val WINNING_REJOIN_ARC = 0.14
        const val TNT_HELD_APPEAR_TICKS = 6.0
        const val TNT_HANDOFF_TICKS = 4.0
        const val FLINT_APPEAR_TICKS = 6.0
        const val WINNING_PLACEMENT_HEIGHT = 0.34
        const val WINNING_PLACEMENT_FORWARD = 0.40
        const val WINNING_PLACEMENT_SIDE_OFFSET = 0.58
        const val FLINT_REACH_SIDE = 0.50
        const val FLINT_REACH_FORWARD = 0.34
        const val TABLE_SURFACE_HEIGHT = 0.005
        const val TNT_WORLD_SCALE = 0.8
        const val TNT_AIR_HEIGHT = 1.05
        const val TNT_MAX_PULSE = 0.08
        const val EXPLOSION_VISUAL_TICKS = 24.0
        const val SHARED_SMOKE_COUNT = 10
        const val SMOKE_MAX_DELAY_TICKS = 6.0
        const val SMOKE_MIN_LIFETIME_TICKS = 58.0
        const val SMOKE_LIFETIME_VARIANCE_TICKS = 12.0
        const val SMOKE_SHARED_SPREAD = 1.75
        const val SMOKE_WING_SPREAD = 1.05
        const val SMOKE_DEPTH_SCALE = 0.72
        const val SMOKE_MIN_RISE = 0.28
        const val SMOKE_RISE_VARIANCE = 0.72
        const val SMOKE_APPEAR_FRACTION = 0.12
        const val SMOKE_FADE_START_FRACTION = 0.42
        const val SMOKE_MAX_ALPHA = 190.0
        const val SMOKE_START_SIZE = 0.22
        const val SMOKE_GROWTH = 0.34
        const val SMOKE_SIZE_VARIANCE = 0.12
        const val EXPLOSION_RING_COUNT = 5
        const val EXPLOSION_RING_DELAY = 0.085
        const val EXPLOSION_RING_SEGMENTS = 24
        const val EXPLOSION_MAX_RADIUS = 1.75
        const val EXPLOSION_CARD_PUSH_MIN = 0.42
        const val EXPLOSION_CARD_PUSH_VARIANCE = 0.48
        const val EXPLOSION_CARD_ARC_MIN = 0.30
        const val EXPLOSION_CARD_ARC_VARIANCE = 0.35
        const val EXPLOSION_IMPACT_HEIGHT_VARIANCE = 0.25
        const val EXPLOSION_IMPACT_FRACTION = 0.20
        const val EXPLOSION_POSE_RECOVERY_FRACTION = 0.20
        const val BILLBOARD_TRANSITION_START = 0.72
        const val ORBIT_SPEED = 0.065
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
        const val TRAIL_SEGMENTS = 7
        const val TRAIL_SAMPLE_TICKS = 1.6
        const val WINNING_TILE_WING_INDEX = 31
        const val WINNING_TILE_ORDER = 97
        const val CARD_TRAIL_COLOR = 0xFFD86A
        const val WINNING_TILE_TRAIL_COLOR = 0x6FEAFF
        const val TNT_FLASH_COLOR = 0xFFF8F1
        const val SPARK_COLOR = 0xFFD45A
        const val EXPLOSION_WHITE_COLOR = 0xFFFDF5
        const val SMOKE_COLOR = 0xD8D8D8
    }
}
