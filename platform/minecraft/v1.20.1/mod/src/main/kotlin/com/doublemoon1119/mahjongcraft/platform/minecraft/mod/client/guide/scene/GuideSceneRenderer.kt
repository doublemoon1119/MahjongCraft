package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideScene
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneFigure
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneHover
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideScenePlayerInfo
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneRegion
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneTile
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneTileDrawOrder
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneTileGlow
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneWinEffect
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongBotEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongPlayerInfoEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongTileEntityRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.MahjongTileItemRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.SceneEntityTranslucency
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render.WinCelebrationEffectGeometry
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.tile.LatestDiscardHighlightController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.tile.MatchingTileHighlightController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongBotEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongPlayerInfoEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongRoundInfoEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongStoolSeatEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.BuiltInMinecraftTileAppearances
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions
import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.BufferBuilder
import com.mojang.blaze3d.vertex.BufferUploader
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.Tesselator
import com.mojang.blaze3d.vertex.VertexFormat
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.model.TridentModel
import net.minecraft.client.model.geom.ModelLayers
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.GameRenderer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderDispatcher
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemDisplayContext
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.lwjgl.opengl.GL11
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * 在畫面上的一塊範圍內繪製 [GuideScene]：固定斜上方視角，依範圍大小等比例縮放並置中；
 * 場景指定關注範圍時只顯示該範圍，否則完整顯示整個場景。
 *
 * 方塊、角色、牌、局況與玩家資訊都交給遊戲自己的繪製器，以最亮的光照繪製；方向光從鏡頭前上方照來，
 * 朝向玩家的面最亮。發光的牌畫上貼著輪廓、顏色與遊戲同種牌提示相同的外框；滑鼠指著標示了區塊的東西時，
 * 同一區塊的牌一起畫上白色外框。場景前後各清一次深度，讓場景不被先前的介面擋住，之後繪製的介面也能蓋在場景上。
 * 預覽實體不加入世界，換世界時重建，[close] 時釋放。
 */
internal class GuideSceneRenderer : AutoCloseable {
    /** 繪製角色共用的預覽實體。 */
    private var figureEntity: MahjongBotEntity? = null

    /** 繪製局況的預覽實體。 */
    private var roundInfoEntity: MahjongRoundInfoEntity? = null

    /** 繪製玩家資訊的預覽實體。 */
    private var playerInfoEntity: MahjongPlayerInfoEntity? = null

    /** 胡牌特效的畫法；第一次畫特效時才建立。 */
    private var winEffectGeometry: WinCelebrationEffectGeometry? = null

    /** 讓坐著的生物騎乘的凳子座位，與遊戲中坐凳子時相同；不在世界中，也不是生物，不影響乘客的朝向。 */
    private var seatEntity: Entity? = null

    /** 上一次繪製時滑鼠指著、離鏡頭最近的牌的 [GuideSceneTile.key]；沒有指著牌時為 null。 */
    var hoveredTileKey: Int? = null
        private set

    /**
     * 在指定範圍內繪製場景，並回傳滑鼠指著的區塊。
     *
     * @param context GUI 繪製上下文。
     * @param scene 要繪製的場景。
     * @param left 範圍左界。
     * @param top 範圍上界。
     * @param width 範圍寬度。
     * @param height 範圍高度。
     * @param mouseX 滑鼠水平位置。
     * @param mouseY 滑鼠垂直位置。
     * @param yawOffset 玩家拖曳轉動的水平角度，加在場景視角上；只有可轉動的場景使用。
     * @param zoom 在放得下整個範圍的大小上再放大的倍數。
     * @param panX 放大後場景中心往右移動的像素。
     * @param panY 放大後場景中心往下移動的像素。
     * @return 滑鼠指著、離鏡頭最近的東西所屬的區塊與它在畫面上的範圍；沒有指著任何區塊時為 null。
     */
    fun render(
        context: GuiGraphics,
        scene: GuideScene,
        left: Int,
        top: Int,
        width: Int,
        height: Int,
        mouseX: Int,
        mouseY: Int,
        yawOffset: Float = 0f,
        zoom: Float = 1f,
        panX: Float = 0f,
        panY: Float = 0f,
    ): GuideSceneHover? {
        if (width <= 0 || height <= 0 || scene.blocks.isEmpty() && scene.figures.isEmpty() && scene.tiles.isEmpty()) return null
        val client = Minecraft.getInstance()
        val yaw = scene.view.yawDegrees + if (scene.rotatable) yawOffset else 0f
        val rotation = rotation(scene.view.pitchDegrees, yaw)
        val bounds = if (scene.rotatable) rotatableBounds(scene, rotation) else projectedBounds(scene, rotation)
        val scale = min(width / bounds.width, height / bounds.height) * FILL_RATIO * zoom
        val centerX = left + width / 2.0 + panX
        val centerY = top + height / 2.0 + panY
        val clip = if (scene.focus == null) {
            ScreenRect(left, top, left + width, top + height)
        } else {
            // 指定關注範圍的場景只顯示該範圍，範圍外的桌腳等不出現。
            val halfWidth = bounds.width * scale / 2.0
            val halfHeight = bounds.height * scale / 2.0
            ScreenRect(
                left = maxOf(left, floor(centerX - halfWidth).toInt()),
                top = maxOf(top, floor(centerY - halfHeight).toInt()),
                right = minOf(left + width, ceil(centerX + halfWidth).toInt()),
                bottom = minOf(top + height, ceil(centerY + halfHeight).toInt()),
            )
        }
        context.flush()
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX)
        context.enableScissor(clip.left, clip.top, clip.right, clip.bottom)
        val matrices = context.pose()
        matrices.pushPose()
        try {
            matrices.translate(centerX, centerY, SCENE_Z)
            // 上下翻轉只套用在位置；法線只受視角旋轉影響，光線因此可以直接以鏡頭的方向指定。
            matrices.mulPoseMatrix(Matrix4f().scaling(scale, -scale, scale))
            matrices.translate(-bounds.centerX.toDouble(), -bounds.centerY.toDouble(), 0.0)
            matrices.mulPose(Quaternionf().setFromNormalized(rotation))
            val sceneToScreen = Matrix4f(matrices.last().pose())
            val hovered = if (clip.contains(mouseX, mouseY)) hoveredRegion(scene, sceneToScreen, scale, mouseX, mouseY) else null
            hoveredTileKey = if (clip.contains(mouseX, mouseY)) hoveredTile(scene, sceneToScreen, mouseX, mouseY) else null
            RenderSystem.setShaderLights(
                Vector3f(-SIDE_LIGHT, UP_LIGHT, FRONT_LIGHT).normalize(),
                Vector3f(SIDE_LIGHT, UP_LIGHT, FRONT_LIGHT).normalize(),
            )
            RenderSystem.enableDepthTest()
            val consumers = client.renderBuffers().bufferSource()
            try {
                scene.blocks.forEach { block ->
                    matrices.pushPose()
                    matrices.translate(block.position.x.toDouble(), block.position.y.toDouble(), block.position.z.toDouble())
                    client.blockRenderer.renderSingleBlock(block.state, matrices, consumers, FULL_LIGHT, OverlayTexture.NO_OVERLAY)
                    matrices.popPose()
                }
                renderFigures(client, scene.figures.filter { it.alpha >= 1f }, context, consumers)
                renderEntities(client, scene.entities.filter { it.alpha >= 1f }, context, consumers)
                val tiles = GuideSceneTileDrawOrder.split(scene.tiles, sceneToScreen, ::isTranslucent)
                tiles.opaque.forEach { tile -> renderTile(tile, context, consumers) }
                renderInfo(client, scene, rotation, context, consumers)
                // 半透明的牌與人物最後畫：不透明的東西先全部畫進畫面，半透明的部分才透得出它後面的東西。
                consumers.endBatch()
                tiles.translucentFarToNear.forEach { tile -> renderTile(tile, context, consumers) }
                consumers.endBatch()
                renderFigures(client, scene.figures.filter { it.alpha < 1f }, context, consumers)
                renderEntities(client, scene.entities.filter { it.alpha < 1f }, context, consumers)
                renderWinEffects(client, scene.winEffects, context, consumers)
            } finally {
                consumers.endBatch()
            }
            val time = (client.level?.gameTime ?: 0L) + client.frameTime.toDouble()
            val outlined = scene.tiles.mapNotNull { tile ->
                when {
                    hovered != null && tile.region == hovered.region -> tile to HOVER_COLOR
                    tile.glow == GuideSceneTileGlow.TARGET -> tile to MatchingTileHighlightController.targetColor(time)
                    tile.glow == GuideSceneTileGlow.MATCHING -> tile to MatchingTileHighlightController.otherMatchColor(time)
                    tile.glow == GuideSceneTileGlow.LATEST_DISCARD -> tile to LatestDiscardHighlightController.HIGHLIGHT_COLOR
                    else -> null
                }
            }
            renderOutlines(client, outlined, context, expansion = GLOW_PIXELS / scale)
            return hovered
        } finally {
            matrices.popPose()
            context.disableScissor()
            Lighting.setupFor3DItems()
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX)
        }
    }

    /**
     * 找出滑鼠指著的東西：比較每個標示了區塊的東西在畫面上的範圍，取離鏡頭最近的一個。
     *
     * @param scene 場景。
     * @param sceneToScreen 場景座標換算成畫面座標的矩陣。
     * @param scale 每格方塊在畫面上的像素數。
     * @param mouseX 滑鼠水平位置。
     * @param mouseY 滑鼠垂直位置。
     * @return 滑鼠指著的東西所屬的區塊與它在畫面上的範圍；沒有時為 null。
     */
    private fun hoveredRegion(scene: GuideScene, sceneToScreen: Matrix4f, scale: Float, mouseX: Int, mouseY: Int): GuideSceneHover? {
        val candidates = buildList {
            scene.tiles.forEach { tile ->
                val region = tile.region ?: return@forEach
                add(region to projectBox(tileModelMatrix(sceneToScreen, tile), tileModelMin(), tileModelMax()))
            }
            scene.figures.forEach { figure ->
                val region = figure.region ?: return@forEach
                add(
                    element = region to projectBox(
                        matrix = sceneToScreen,
                        min = Vector3f((figure.x - FIGURE_HALF_WIDTH).toFloat(), figure.y.toFloat(), (figure.z - FIGURE_HALF_WIDTH).toFloat()),
                        max = Vector3f((figure.x + FIGURE_HALF_WIDTH).toFloat(), (figure.y + FIGURE_HEIGHT).toFloat(), (figure.z + FIGURE_HALF_WIDTH).toFloat()),
                    ),
                )
            }
            scene.roundInfo?.let { info ->
                val center = sceneToScreen.transformPosition(Vector3f(info.x.toFloat(), info.y.toFloat(), info.z.toFloat()))
                val halfWidth = ROUND_INFO_HALF_WIDTH * scale
                val halfHeight = ROUND_INFO_HALF_HEIGHT * scale
                add(GuideSceneRegion.ROUND_INFO to ProjectedBox(center.x - halfWidth, center.y - halfHeight, center.x + halfWidth, center.y + halfHeight, center.z))
            }
            scene.playerInfo?.let { info ->
                playerPanelBoxes(info).forEach { (min, max) -> add(GuideSceneRegion.PLAYER_INFO to projectBox(sceneToScreen, min, max)) }
            }
        }
        return candidates
            .filter { (_, box) -> mouseX >= box.minX && mouseX <= box.maxX && mouseY >= box.minY && mouseY <= box.maxY }
            .maxByOrNull { (_, box) -> box.depth }
            ?.let { (region, box) ->
                GuideSceneHover(
                    region = region,
                    left = floor(box.minX).toInt(),
                    top = floor(box.minY).toInt(),
                    right = ceil(box.maxX).toInt(),
                    bottom = ceil(box.maxY).toInt(),
                )
            }
    }

    /**
     * 找出滑鼠指著、離鏡頭最近且有 [GuideSceneTile.key] 的牌。
     *
     * @param scene 場景。
     * @param sceneToScreen 場景座標換算成畫面座標的矩陣。
     * @param mouseX 滑鼠水平位置。
     * @param mouseY 滑鼠垂直位置。
     * @return 牌的識別值；沒有指著這樣的牌時為 null。
     */
    private fun hoveredTile(scene: GuideScene, sceneToScreen: Matrix4f, mouseX: Int, mouseY: Int): Int? = scene.tiles
        .mapNotNull { tile -> tile.key?.let { key -> key to projectBox(tileModelMatrix(sceneToScreen, tile), tileModelMin(), tileModelMax()) } }
        .filter { (_, box) -> mouseX >= box.minX && mouseX <= box.maxX && mouseY >= box.minY && mouseY <= box.maxY }
        .maxByOrNull { (_, box) -> box.depth }
        ?.first

    /**
     * 玩家資訊面板在場景座標中的大略範圍，與遊戲中面板的位置相同。
     *
     * @param info 玩家資訊。
     * @return 各座位面板的最小角與最大角。
     */
    private fun playerPanelBoxes(info: GuideScenePlayerInfo): List<Pair<Vector3f, Vector3f>> {
        val centerX = info.x.toFloat()
        val centerZ = info.z.toFloat()
        val y = (info.y + PLAYER_PANEL_HEIGHT).toFloat()
        return listOf(
            Pair(Vector3f(centerX - PLAYER_PANEL_HALF_WIDTH, y - PLAYER_PANEL_HALF_HEIGHT, centerZ - PLAYER_PANEL_DISTANCE), Vector3f(centerX + PLAYER_PANEL_HALF_WIDTH, y + PLAYER_PANEL_HALF_HEIGHT, centerZ - PLAYER_PANEL_DISTANCE)),
            Pair(Vector3f(centerX - PLAYER_PANEL_HALF_WIDTH, y - PLAYER_PANEL_HALF_HEIGHT, centerZ + PLAYER_PANEL_DISTANCE), Vector3f(centerX + PLAYER_PANEL_HALF_WIDTH, y + PLAYER_PANEL_HALF_HEIGHT, centerZ + PLAYER_PANEL_DISTANCE)),
            Pair(Vector3f(centerX - PLAYER_PANEL_DISTANCE, y - PLAYER_PANEL_HALF_HEIGHT, centerZ - PLAYER_PANEL_HALF_WIDTH), Vector3f(centerX - PLAYER_PANEL_DISTANCE, y + PLAYER_PANEL_HALF_HEIGHT, centerZ + PLAYER_PANEL_HALF_WIDTH)),
            Pair(Vector3f(centerX + PLAYER_PANEL_DISTANCE, y - PLAYER_PANEL_HALF_HEIGHT, centerZ - PLAYER_PANEL_HALF_WIDTH), Vector3f(centerX + PLAYER_PANEL_DISTANCE, y + PLAYER_PANEL_HALF_HEIGHT, centerZ + PLAYER_PANEL_HALF_WIDTH)),
        )
    }

    /**
     * 以預覽實體依序繪製角色。
     *
     * @param client 目前客戶端。
     * @param figures 要繪製的角色。
     * @param context GUI 繪製上下文，矩陣已套用場景視角。
     * @param consumers 頂點緩衝。
     */
    private fun renderFigures(
        client: Minecraft,
        figures: List<GuideSceneFigure>,
        context: GuiGraphics,
        consumers: MultiBufferSource,
    ) {
        if (figures.isEmpty()) return
        val world = client.level ?: return
        val entity = figureEntity(world)
        val renderer = client.entityRenderDispatcher.getRenderer(entity) as? MahjongBotEntityRenderer ?: return
        val matrices = context.pose()
        figures.forEach { figure ->
            if (figure.alpha <= 0f) return@forEach
            entity.faceAssetKey = figure.faceAssetKey
            matrices.pushPose()
            matrices.translate(figure.x, figure.y, figure.z)
            renderer.renderPose(
                entity = entity,
                yaw = figure.yaw,
                tickDelta = 0f,
                matrices = matrices,
                vertexConsumers = consumers,
                light = FULL_LIGHT,
                seated = figure.seated,
                swingProgress = figure.swingProgress,
                alpha = figure.alpha,
            )
            matrices.popPose()
        }
    }

    /**
     * 以遊戲的局況與玩家資訊繪製器畫出浮在桌上的資訊；局況會轉向鏡頭，與遊戲中相同。
     *
     * @param client 目前客戶端。
     * @param scene 場景。
     * @param rotation 場景視角。
     * @param context GUI 繪製上下文，矩陣已套用場景視角。
     * @param consumers 頂點緩衝。
     */
    private fun renderInfo(
        client: Minecraft,
        scene: GuideScene,
        rotation: Matrix4f,
        context: GuiGraphics,
        consumers: MultiBufferSource,
    ) {
        val world = client.level ?: return
        val matrices = context.pose()
        val dispatcher = client.entityRenderDispatcher
        scene.roundInfo?.let { info ->
            val entity = roundInfoEntity(world).apply { lines = info.lines }
            // 讓轉向鏡頭的面板正對畫面：抵銷場景視角，再轉半圈對齊原版名牌的座標方向。
            dispatcher.overrideCameraOrientation(Quaternionf().setFromNormalized(rotation).conjugate().rotateY(Math.PI.toFloat()))
            matrices.pushPose()
            matrices.translate(info.x, info.y, info.z)
            dispatcher.getRenderer(entity).render(entity, 0f, 0f, matrices, consumers, FULL_LIGHT)
            matrices.popPose()
        }
        scene.playerInfo?.let { info ->
            val entity = playerInfoEntity(world).apply {
                players = info.players
                dealerPlayerId = info.dealerPlayerId
                tableFacing = MahjongTableFacing.NORTH
            }
            val renderer = dispatcher.getRenderer(entity) as? MahjongPlayerInfoEntityRenderer ?: return
            matrices.pushPose()
            matrices.translate(info.x, info.y, info.z)
            renderer.renderPanels(entity, matrices, consumers, FULL_LIGHT) { it in info.shownSeats }
            matrices.popPose()
        }
    }

    /**
     * 在胡牌張的位置畫出胡牌特效，與半透明的東西一起最後畫，不擋住後面的牌。
     *
     * @param client 目前客戶端。
     * @param effects 要畫的特效。
     * @param context GUI 繪製上下文，矩陣已套用場景視角。
     * @param consumers 頂點緩衝。
     */
    private fun renderWinEffects(
        client: Minecraft,
        effects: List<GuideSceneWinEffect>,
        context: GuiGraphics,
        consumers: MultiBufferSource,
    ) {
        if (effects.isEmpty()) return
        val geometry = winEffectGeometry
            ?: WinCelebrationEffectGeometry(TridentModel(client.entityModels.bakeLayer(ModelLayers.TRIDENT))).also { winEffectGeometry = it }
        val matrices = context.pose()
        effects.forEach { effect ->
            matrices.pushPose()
            matrices.translate(effect.x, effect.y, effect.z)
            geometry.render(
                elapsedTicks = effect.elapsedTicks,
                seed = effect.seed,
                matrices = matrices,
                vertexConsumers = consumers,
                light = FULL_LIGHT,
            )
            matrices.popPose()
        }
    }

    /**
     * 這張牌是否以半透明方式繪製；看不到牌面的牌以未知牌畫出，不是半透明。
     *
     * @param tile 場景中的牌。
     * @return 半透明時為 true。
     */
    private fun isTranslucent(tile: GuideSceneTile): Boolean = tile.assetKey?.let { assetKey ->
        MahjongTileItemRenderer.isTranslucent(appearanceId = BuiltInMinecraftTileAppearances.DEFAULT.id, assetKey = assetKey)
    } == true

    /**
     * 以對局中相同的擺法與姿態繪製一張牌。
     *
     * @param tile 要繪製的牌。
     * @param context GUI 繪製上下文，矩陣已套用場景視角。
     * @param consumers 頂點緩衝。
     */
    private fun renderTile(tile: GuideSceneTile, context: GuiGraphics, consumers: MultiBufferSource) {
        val matrices = context.pose()
        matrices.pushPose()
        matrices.translate(tile.x, tile.y, tile.z)
        // 往前進方向的傾斜繞牌底、以世界水平軸旋轉，在朝向之前套用，與對局中的牌相同。
        if (tile.lean.degrees != 0.0f) {
            matrices.mulPose(Quaternionf().rotateAxis(Math.toRadians(tile.lean.degrees.toDouble()).toFloat(), tile.lean.axisX.toFloat(), 0.0f, tile.lean.axisZ.toFloat()))
        }
        matrices.mulPose(Axis.YP.rotationDegrees(-tile.yaw + HALF_TURN_DEGREES))
        val poseDegrees = tile.poseDegrees ?: tile.pose.rotationDegrees
        matrices.mulPose(Axis.XP.rotationDegrees(poseDegrees))
        val (offsetX, offsetY, offsetZ) = if (tile.poseDegrees != null) {
            MahjongTileEntityRenderer.displayPoseOriginOffset(poseDegrees)
        } else {
            MahjongTileEntityRenderer.poseOriginOffset(poseDegrees)
        }
        matrices.translate(offsetX, offsetY, offsetZ)
        val assetKey = tile.assetKey
        if (assetKey == null) {
            MahjongTileItemRenderer.renderUnknownWithAppearance(
                appearanceId = BuiltInMinecraftTileAppearances.DEFAULT.id,
                mode = ItemDisplayContext.HEAD,
                matrices = matrices,
                vertexConsumers = consumers,
                light = FULL_LIGHT,
                overlay = OverlayTexture.NO_OVERLAY,
            )
        } else {
            MahjongTileItemRenderer.renderWithAppearance(
                appearanceId = BuiltInMinecraftTileAppearances.DEFAULT.id,
                assetKey = assetKey,
                mode = ItemDisplayContext.HEAD,
                matrices = matrices,
                vertexConsumers = consumers,
                light = FULL_LIGHT,
                overlay = OverlayTexture.NO_OVERLAY,
            )
        }
        matrices.popPose()
    }

    /**
     * 為牌畫上貼著輪廓的外框：先畫一個比牌大一圈的單色長方體，再把牌畫回上面，只留下周圍一圈。
     * 外框與遊戲中的發光一樣不被其他東西擋住。
     *
     * @param client 目前客戶端。
     * @param outlined 要畫外框的牌與外框的 RGB 顏色。
     * @param context GUI 繪製上下文，矩陣已套用場景視角。
     * @param expansion 外框往外擴的距離，以場景座標（方塊）為單位。
     */
    private fun renderOutlines(
        client: Minecraft,
        outlined: List<Pair<GuideSceneTile, Int>>,
        context: GuiGraphics,
        expansion: Float,
    ) {
        if (outlined.isEmpty()) return
        val sceneMatrix = context.pose().last().pose()
        RenderSystem.disableDepthTest()
        RenderSystem.disableCull()
        RenderSystem.setShader(GameRenderer::getPositionColorShader)
        val buffer = Tesselator.getInstance().builder
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR)
        val grow = Vector3f(expansion, expansion, expansion)
        outlined.forEach { (tile, color) ->
            addBox(
                buffer = buffer,
                matrix = tileModelMatrix(sceneMatrix, tile),
                min = tileModelMin().sub(grow),
                max = tileModelMax().add(grow),
                color = color,
            )
        }
        BufferUploader.drawWithShader(buffer.end())
        RenderSystem.enableCull()
        RenderSystem.enableDepthTest()
        val consumers = client.renderBuffers().bufferSource()
        outlined.forEach { (tile, _) -> renderTile(tile, context, consumers) }
        consumers.endBatch()
    }

    /**
     * 牌模型座標換算成畫面座標的矩陣：與繪製牌時相同的位置、朝向、姿態與姿態補償。
     *
     * @param sceneMatrix 場景座標換算成畫面座標的矩陣。
     * @param tile 場景中的牌。
     * @return 以牌的幾何中心為原點的矩陣。
     */
    private fun tileModelMatrix(sceneMatrix: Matrix4f, tile: GuideSceneTile): Matrix4f {
        val (offsetX, offsetY, offsetZ) = MahjongTileEntityRenderer.poseOriginOffset(tile.pose.rotationDegrees)
        return Matrix4f(sceneMatrix)
            .translate(tile.x.toFloat(), tile.y.toFloat(), tile.z.toFloat())
            .rotateY(Math.toRadians((-tile.yaw + HALF_TURN_DEGREES).toDouble()).toFloat())
            .rotateX(Math.toRadians(tile.pose.rotationDegrees.toDouble()).toFloat())
            .translate(offsetX.toFloat(), offsetY.toFloat(), offsetZ.toFloat())
    }

    /** 以牌的幾何中心為原點時，牌的最小角。 */
    private fun tileModelMin(): Vector3f = Vector3f(
        -(MahjongTileDimensions.TILE_WIDTH / 2.0).toFloat(),
        -(MahjongTileDimensions.TILE_HEIGHT / 2.0).toFloat(),
        -(MahjongTileDimensions.TILE_DEPTH / 2.0).toFloat(),
    )

    /** 以牌的幾何中心為原點時，牌的最大角。 */
    private fun tileModelMax(): Vector3f = Vector3f(
        (MahjongTileDimensions.TILE_WIDTH / 2.0).toFloat(),
        (MahjongTileDimensions.TILE_HEIGHT / 2.0).toFloat(),
        (MahjongTileDimensions.TILE_DEPTH / 2.0).toFloat(),
    )

    /**
     * 計算長方體八個角在畫面上的外接矩形與深度。
     *
     * @param matrix 長方體座標換算成畫面座標的矩陣。
     * @param min 最小角。
     * @param max 最大角。
     * @return 畫面上的外接矩形；深度越大越靠近鏡頭。
     */
    private fun projectBox(matrix: Matrix4f, min: Vector3f, max: Vector3f): ProjectedBox {
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var depth = 0f
        for (x in floatArrayOf(min.x, max.x)) {
            for (y in floatArrayOf(min.y, max.y)) {
                for (z in floatArrayOf(min.z, max.z)) {
                    val point = matrix.transformPosition(Vector3f(x, y, z))
                    minX = min(minX, point.x)
                    maxX = max(maxX, point.x)
                    minY = min(minY, point.y)
                    maxY = max(maxY, point.y)
                    depth += point.z / BOX_CORNERS
                }
            }
        }
        return ProjectedBox(minX, minY, maxX, maxY, depth)
    }

    /**
     * 加入一個單色長方體的六個面。
     *
     * @param buffer 以四邊形、位置加顏色格式開始的緩衝。
     * @param matrix 長方體座標換算成畫面座標的矩陣。
     * @param min 長方體的最小角。
     * @param max 長方體的最大角。
     * @param color RGB 顏色。
     */
    private fun addBox(buffer: BufferBuilder, matrix: Matrix4f, min: Vector3f, max: Vector3f, color: Int) {
        val red = color shr RED_SHIFT and CHANNEL_MASK
        val green = color shr GREEN_SHIFT and CHANNEL_MASK
        val blue = color and CHANNEL_MASK
        val faces = listOf(
            listOf(Vector3f(min.x, min.y, max.z), Vector3f(max.x, min.y, max.z), Vector3f(max.x, max.y, max.z), Vector3f(min.x, max.y, max.z)),
            listOf(Vector3f(min.x, min.y, min.z), Vector3f(min.x, max.y, min.z), Vector3f(max.x, max.y, min.z), Vector3f(max.x, min.y, min.z)),
            listOf(Vector3f(min.x, min.y, min.z), Vector3f(min.x, min.y, max.z), Vector3f(min.x, max.y, max.z), Vector3f(min.x, max.y, min.z)),
            listOf(Vector3f(max.x, min.y, min.z), Vector3f(max.x, max.y, min.z), Vector3f(max.x, max.y, max.z), Vector3f(max.x, min.y, max.z)),
            listOf(Vector3f(min.x, max.y, min.z), Vector3f(min.x, max.y, max.z), Vector3f(max.x, max.y, max.z), Vector3f(max.x, max.y, min.z)),
            listOf(Vector3f(min.x, min.y, min.z), Vector3f(max.x, min.y, min.z), Vector3f(max.x, min.y, max.z), Vector3f(min.x, min.y, max.z)),
        )
        faces.flatten().forEach { corner ->
            buffer.vertex(matrix, corner.x, corner.y, corner.z).color(red, green, blue, CHANNEL_MASK).endVertex()
        }
    }

    /**
     * 用原版繪製器畫出實體；坐姿的實體在繪製期間騎在不在世界中的座位上。
     *
     * 原版依實體與鏡頭的距離決定是否畫名牌；實體不在世界中，繪製前放到遠離鏡頭的位置，名牌就不會出現。
     *
     * @param client 目前客戶端。
     * @param entities 要畫的生物。
     * @param context GUI 繪製上下文，矩陣已套用場景視角。
     * @param consumers 繪製用的頂點緩衝。
     */
    private fun renderEntities(
        client: Minecraft,
        entities: List<GuideSceneEntity>,
        context: GuiGraphics,
        consumers: MultiBufferSource,
    ) {
        if (entities.isEmpty()) return
        val world = client.level ?: return
        val dispatcher = client.entityRenderDispatcher
        val seat = seatEntity(world)
        dispatcher.setRenderShadow(false)
        try {
            entities.forEach { figure ->
                if (!canDraw(figure, dispatcher)) return@forEach
                val draw = { renderEntity(figure, seat, dispatcher, context, consumers) }
                val guard = figure.guard
                if (guard == null) draw() else guard.run(draw)
            }
        } finally {
            dispatcher.setRenderShadow(true)
        }
    }

    /**
     * 判斷一個實體這一幀要不要畫：不透明度為 0 時不畫；半透明時只有原版生物繪製器畫的生物能畫，其他不畫。
     *
     * @param figure 要畫的實體與不透明度。
     * @param dispatcher 原版實體繪製器。
     * @return 要畫時為 true。
     */
    private fun canDraw(figure: GuideSceneEntity, dispatcher: EntityRenderDispatcher): Boolean = when {
        figure.alpha <= 0f -> false
        figure.alpha >= 1f -> true
        else -> figure.entity is LivingEntity && dispatcher.getRenderer(figure.entity) is LivingEntityRenderer<*, *>
    }

    /**
     * 畫出一個實體，畫完後離開座位，不影響同一個實體在其他畫面的繪製。
     *
     * @param figure 要畫的實體與位置。
     * @param seat 坐姿使用的座位。
     * @param dispatcher 原版實體繪製器。
     * @param context GUI 繪製上下文，矩陣已套用場景視角。
     * @param consumers 繪製用的頂點緩衝。
     */
    private fun renderEntity(
        figure: GuideSceneEntity,
        seat: Entity,
        dispatcher: EntityRenderDispatcher,
        context: GuiGraphics,
        consumers: MultiBufferSource,
    ) {
        val entity = figure.entity
        entity.stopRiding()
        if (figure.seated) entity.startRiding(seat, true)
        try {
            entity.setPos(0.0, DETACHED_Y, 0.0)
            if (entity is LivingEntity) {
                entity.yBodyRot = figure.yaw
                entity.yBodyRotO = figure.yaw
                entity.yHeadRot = figure.yaw
                entity.yHeadRotO = figure.yaw
                entity.swingingArm = InteractionHand.MAIN_HAND
                entity.attackAnim = figure.swingProgress
                entity.oAttackAnim = figure.swingProgress
            }
            entity.setYRot(figure.yaw)
            entity.yRotO = figure.yaw
            entity.setXRot(0f)
            entity.xRotO = 0f
            val render = { dispatcher.render(entity, figure.x, figure.y, figure.z, figure.yaw, 1f, context.pose(), consumers, FULL_LIGHT) }
            if (figure.alpha < 1f) SceneEntityTranslucency.render(figure.alpha, render) else render()
        } finally {
            entity.stopRiding()
            if (entity is LivingEntity) {
                entity.attackAnim = 0f
                entity.oAttackAnim = 0f
            }
        }
    }

    /**
     * 取得屬於 [world] 的座位實體；世界改變時重建。
     *
     * @param world 目前客戶端世界。
     * @return 不在世界中的座位實體。
     */
    private fun seatEntity(world: ClientLevel): Entity {
        seatEntity?.takeIf { it.level() === world }?.let { return it }
        seatEntity?.discard()
        return MahjongStoolSeatEntity(world = world).also { seatEntity = it }
    }

    /**
     * 取得屬於 [world] 的角色預覽實體；世界改變時重建。
     *
     * @param world 目前客戶端世界。
     * @return 不在世界中的預覽實體。
     */
    private fun figureEntity(world: ClientLevel): MahjongBotEntity {
        figureEntity?.takeIf { it.level() === world }?.let { return it }
        figureEntity?.discard()
        return MahjongBotEntity(world = world).also { figureEntity = it }
    }

    /**
     * 取得屬於 [world] 的局況預覽實體；世界改變時重建。
     *
     * @param world 目前客戶端世界。
     * @return 不在世界中的預覽實體。
     */
    private fun roundInfoEntity(world: ClientLevel): MahjongRoundInfoEntity {
        roundInfoEntity?.takeIf { it.level() === world }?.let { return it }
        roundInfoEntity?.discard()
        return MahjongRoundInfoEntity(world = world).also { roundInfoEntity = it }
    }

    /**
     * 取得屬於 [world] 的玩家資訊預覽實體；世界改變時重建。
     *
     * @param world 目前客戶端世界。
     * @return 不在世界中的預覽實體。
     */
    private fun playerInfoEntity(world: ClientLevel): MahjongPlayerInfoEntity {
        playerInfoEntity?.takeIf { it.level() === world }?.let { return it }
        playerInfoEntity?.discard()
        return MahjongPlayerInfoEntity(world = world).also { playerInfoEntity = it }
    }

    /** 釋放預覽實體。 */
    override fun close() {
        seatEntity?.discard()
        seatEntity = null
        figureEntity?.discard()
        figureEntity = null
        roundInfoEntity?.discard()
        roundInfoEntity = null
        playerInfoEntity?.discard()
        playerInfoEntity = null
    }

    /**
     * 建立先水平旋轉、再往下俯視的視角旋轉。
     *
     * @param pitchDegrees 俯視角度。
     * @param yawDegrees 水平旋轉角度。
     * @return 視角旋轉矩陣。
     */
    private fun rotation(pitchDegrees: Float, yawDegrees: Float): Matrix4f = Matrix4f()
        .rotateX(Math.toRadians(pitchDegrees.toDouble()).toFloat())
        .rotateY(Math.toRadians(yawDegrees.toDouble()).toFloat())

    /**
     * 計算可轉動場景的範圍：以場景中心為轉軸，取各個水平角度都放得下的大小，轉動時場景不會忽大忽小，
     * 中心也固定在畫面同一點。
     *
     * @param scene 可轉動的場景。
     * @param rotation 目前的視角旋轉。
     * @return 置中於轉軸、各角度都放得下的範圍。
     */
    private fun rotatableBounds(scene: GuideScene, rotation: Matrix4f): ProjectedBounds {
        val unrotated = projectedBounds(scene, Matrix4f())
        val pivot = Vector3f(unrotated.centerX, (unrotated.minY + unrotated.maxY) / 2f, sceneCenterZ(scene))
        var halfWidth = 0f
        var halfHeight = 0f
        for (step in 0 until ROTATION_SAMPLES) {
            val sample = rotation(scene.view.pitchDegrees, step * FULL_TURN_DEGREES / ROTATION_SAMPLES)
            val bounds = projectedBounds(scene, sample)
            val center = sample.transformPosition(Vector3f(pivot))
            halfWidth = max(halfWidth, max(center.x - bounds.minX, bounds.maxX - center.x))
            halfHeight = max(halfHeight, max(center.y - bounds.minY, bounds.maxY - center.y))
        }
        val center = rotation.transformPosition(Vector3f(pivot))
        return ProjectedBounds(minX = center.x - halfWidth, maxX = center.x + halfWidth, minY = center.y - halfHeight, maxY = center.y + halfHeight)
    }

    /**
     * 場景在 Z 方向的中心：有顯示或框住的範圍時取該範圍，否則取所有方塊、角色與牌。
     *
     * @param scene 場景。
     * @return Z 方向範圍的中點。
     */
    private fun sceneCenterZ(scene: GuideScene): Float {
        (scene.focus ?: scene.frame)?.let { return ((it.minZ + it.maxZ) / 2.0).toFloat() }
        val zs = scene.blocks.flatMap { listOf(it.position.z.toDouble(), it.position.z + 1.0) } +
            scene.figures.map { it.z } +
            scene.tiles.map { it.z }
        return ((zs.min() + zs.max()) / 2.0).toFloat()
    }

    /**
     * 計算場景旋轉後在畫面平面上的範圍。
     *
     * @param scene 場景。
     * @param rotation 場景視角。
     * @return 旋轉後的範圍，Y 朝上。
     */
    private fun projectedBounds(scene: GuideScene, rotation: Matrix4f): ProjectedBounds {
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY

        /** 將一個場景點納入範圍。 */
        fun include(x: Double, y: Double, z: Double) {
            val point = rotation.transformPosition(Vector3f(x.toFloat(), y.toFloat(), z.toFloat()))
            minX = min(minX, point.x)
            maxX = max(maxX, point.x)
            minY = min(minY, point.y)
            maxY = max(maxY, point.y)
        }

        /** 將一個長方體的八個角納入範圍。 */
        fun includeBox(minCorner: Vector3f, maxCorner: Vector3f) {
            for (x in floatArrayOf(minCorner.x, maxCorner.x)) {
                for (y in floatArrayOf(minCorner.y, maxCorner.y)) {
                    for (z in floatArrayOf(minCorner.z, maxCorner.z)) include(x.toDouble(), y.toDouble(), z.toDouble())
                }
            }
        }
        val focus = scene.focus ?: scene.frame
        if (focus != null) {
            includeBox(
                minCorner = Vector3f(focus.minX.toFloat(), focus.minY.toFloat(), focus.minZ.toFloat()),
                maxCorner = Vector3f(focus.maxX.toFloat(), focus.maxY.toFloat(), focus.maxZ.toFloat()),
            )
            return ProjectedBounds(minX = minX, maxX = maxX, minY = minY, maxY = maxY)
        }
        val tileReach = MahjongTileDimensions.TILE_HEIGHT.toFloat()
        scene.tiles.forEach { tile ->
            val center = Vector3f(tile.x.toFloat(), tile.y.toFloat(), tile.z.toFloat())
            includeBox(Vector3f(center).sub(tileReach, 0f, tileReach), Vector3f(center).add(tileReach, tileReach, tileReach))
        }
        scene.blocks.forEach { block ->
            val corner = Vector3f(block.position.x.toFloat(), block.position.y.toFloat(), block.position.z.toFloat())
            includeBox(corner, Vector3f(corner).add(1f, 1f, 1f))
        }
        scene.figures.forEach { figure ->
            includeBox(
                minCorner = Vector3f((figure.x - FIGURE_HALF_WIDTH).toFloat(), figure.y.toFloat(), (figure.z - FIGURE_HALF_WIDTH).toFloat()),
                maxCorner = Vector3f((figure.x + FIGURE_HALF_WIDTH).toFloat(), (figure.y + FIGURE_HEIGHT).toFloat(), (figure.z + FIGURE_HALF_WIDTH).toFloat()),
            )
        }
        scene.roundInfo?.let { info ->
            val center = Vector3f(info.x.toFloat(), info.y.toFloat(), info.z.toFloat())
            includeBox(Vector3f(center).sub(ROUND_INFO_HALF_WIDTH, ROUND_INFO_HALF_HEIGHT, ROUND_INFO_HALF_WIDTH), Vector3f(center).add(ROUND_INFO_HALF_WIDTH, ROUND_INFO_HALF_HEIGHT, ROUND_INFO_HALF_WIDTH))
        }
        scene.playerInfo?.let { info -> playerPanelBoxes(info).forEach { (min, max) -> includeBox(min, max) } }
        return ProjectedBounds(minX = minX, maxX = maxX, minY = minY, maxY = maxY)
    }

    /**
     * 旋轉後的場景範圍。
     *
     * @property minX 最左側。
     * @property maxX 最右側。
     * @property minY 最下方。
     * @property maxY 最上方。
     */
    private data class ProjectedBounds(val minX: Float, val maxX: Float, val minY: Float, val maxY: Float) {
        /** 寬度，至少一格避免除以零。 */
        val width: Float = (maxX - minX).coerceAtLeast(1f)

        /** 高度，至少一格避免除以零。 */
        val height: Float = (maxY - minY).coerceAtLeast(1f)

        /** 水平中心。 */
        val centerX: Float = (minX + maxX) / 2f

        /** 垂直中心。 */
        val centerY: Float = (minY + maxY) / 2f
    }

    /**
     * 一樣東西在畫面上的外接矩形與深度。
     *
     * @property minX 左界。
     * @property minY 上界。
     * @property maxX 右界。
     * @property maxY 下界。
     * @property depth 深度，越大越靠近鏡頭。
     */
    private data class ProjectedBox(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float, val depth: Float)

    /**
     * 畫面上的矩形範圍。
     *
     * @property left 左界。
     * @property top 上界。
     * @property right 右界。
     * @property bottom 下界。
     */
    private data class ScreenRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        /**
         * 指定位置是否在範圍內。
         *
         * @param x 水平位置。
         * @param y 垂直位置。
         * @return 在範圍內時為 true。
         */
        fun contains(x: Int, y: Int): Boolean = x in left until right && y in top until bottom
    }

    /** 視角、尺寸與顏色。 */
    private companion object {
        /** 場景佔可用範圍的比例，四周留一點空白。 */
        const val FILL_RATIO: Float = 0.92f

        /** 場景在 GUI 深度上的位置；前後已清除深度，只需落在投影範圍內。 */
        const val SCENE_Z: Double = 200.0

        /** 最亮的方塊與天空光照。 */
        const val FULL_LIGHT: Int = LightTexture.FULL_BRIGHT

        /** 繪製實體前放置的高度，遠離鏡頭，名牌不會出現。 */
        const val DETACHED_Y: Double = -10_000.0

        /** 計算範圍時，角色的半寬。 */
        const val FIGURE_HALF_WIDTH: Double = 0.45

        /** 計算範圍時，角色的高度。 */
        const val FIGURE_HEIGHT: Double = 1.4

        /** 局況面板在場景座標中的大略半寬。 */
        const val ROUND_INFO_HALF_WIDTH: Float = 0.9f

        /** 局況面板在場景座標中的大略半高。 */
        const val ROUND_INFO_HALF_HEIGHT: Float = 0.3f

        /** 玩家資訊面板離桌子中心的距離，與遊戲中相同。 */
        const val PLAYER_PANEL_DISTANCE: Float = 3.1f

        /** 玩家資訊面板中心離桌子底部的高度，與遊戲中相同。 */
        const val PLAYER_PANEL_HEIGHT: Double = 2.7

        /** 玩家資訊面板在場景座標中的大略半寬。 */
        const val PLAYER_PANEL_HALF_WIDTH: Float = 0.9f

        /** 玩家資訊面板在場景座標中的大略半高。 */
        const val PLAYER_PANEL_HALF_HEIGHT: Float = 0.35f

        /** 長方體的角數。 */
        const val BOX_CORNERS: Float = 8f

        /** 牌的擺放朝向與模型正面相差半圈，與對局中的牌相同。 */
        const val HALF_TURN_DEGREES: Float = 180f

        /** 計算可轉動場景的大小時取樣的水平角度數。 */
        const val ROTATION_SAMPLES: Int = 24

        /** 一整圈的角度。 */
        const val FULL_TURN_DEGREES: Float = 360f

        /** 外框的粗細，以 GUI 像素為單位。 */
        const val GLOW_PIXELS: Float = 1f

        /** 滑鼠指著的區塊的外框顏色。 */
        const val HOVER_COLOR: Int = 0xFFFFFF

        /** 方向光左右偏開的分量。 */
        const val SIDE_LIGHT: Float = 0.2f

        /** 方向光從上方照來的分量。 */
        const val UP_LIGHT: Float = 0.6f

        /** 方向光從鏡頭照向場景的分量。 */
        const val FRONT_LIGHT: Float = 1f

        /** 紅色通道的位移。 */
        const val RED_SHIFT: Int = 16

        /** 綠色通道的位移。 */
        const val GREEN_SHIFT: Int = 8

        /** 單一顏色通道的遮罩，也是不透明的 alpha。 */
        const val CHANNEL_MASK: Int = 0xFF
    }
}
