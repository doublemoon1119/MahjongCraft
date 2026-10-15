package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.client.model.MahjongTileModelLoadingPlugin
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionTileOrientationDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.BuiltInMinecraftTileAppearances
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTilePreviewTexture
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileSetAppearanceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelColor
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelText
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tilePreviewTexture
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis
import org.joml.Matrix4f
import kotlin.math.roundToInt

/**
 * Fabric 所有 2D HUD、GUI 與面板牌面共用的材質及角落標籤 renderer。
 *
 * 呼叫端只決定位置、尺寸與方向；完整 UV、未知材質 fallback、`tile-labels-enabled`、forced label
 * 及標籤顏色均集中於此，避免不同 showcase 各自形成不一致的牌面。
 *
 * @property tileLabelRegistry 提供牌面角落標籤定義。
 * @property tileAssetRegistry 提供牌面資源的完整 namespace 與路徑映射。
 * @property clientConfigStore 提供標籤顯示設定。
 */
class MahjongTileFaceRenderer(
    private val tileLabelRegistry: TileLabelRegistry,
    private val tileAssetRegistry: MinecraftTileAssetRegistry,
    private val clientConfigStore: MahjongClientConfigStore,
) {
    /**
     * 在 GUI 座標系繪製一張完整牌面；橫置時回傳實際佔用寬高。
     *
     * @param context GUI 繪製上下文。
     * @param assetKey 牌面資產 key。
     * @param x 牌面左上角 X 座標。
     * @param y 牌面左上角 Y 座標。
     * @param width 直立牌面的寬度。
     * @param height 直立牌面的高度。
     * @param orientation 牌面朝向；預設直立。
     * @param appearance 公開規則指定的牌組外觀，不依隱藏牌種選擇。
     */
    fun renderGui(
        context: DrawContext,
        assetKey: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        orientation: DecisionTileOrientationDto = DecisionTileOrientationDto.UPRIGHT,
        appearance: MinecraftTileSetAppearanceDefinition = BuiltInMinecraftTileAppearances.DEFAULT,
    ) {
        if (orientation == DecisionTileOrientationDto.UPRIGHT) {
            renderUprightGui(context, assetKey, x, y, width, height, appearance)
            return
        }
        context.matrices.push()
        if (orientation == DecisionTileOrientationDto.ROTATED_RIGHT) {
            context.matrices.translate((x + height).toDouble(), y.toDouble(), 0.0)
            context.matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(90f))
        } else {
            context.matrices.translate(x.toDouble(), (y + width).toDouble(), 0.0)
            context.matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90f))
        }
        renderUprightGui(context, assetKey, 0, 0, width, height, appearance)
        context.matrices.pop()
    }

    /**
     * 在 billboard／showcase 的局部座標繪製牌面 quad 與相同的角落標籤。
     *
     * @param assetKey 牌面資產 key。
     * @param centerX 牌面中心 X 座標。
     * @param centerY 牌面中心 Y 座標。
     * @param width 牌面寬度。
     * @param height 牌面高度。
     * @param alpha 顯示透明度，會限制在 0 到 1。
     * @param z 局部深度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供牌面與標籤頂點緩衝。
     * @param appearance 公開規則指定的牌組外觀，不依隱藏牌種選擇。
     */
    fun renderWorldPanel(
        assetKey: String,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        alpha: Float,
        z: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        appearance: MinecraftTileSetAppearanceDefinition = BuiltInMinecraftTileAppearances.DEFAULT,
    ) {
        val preview = resolvePreviewTexture(assetKey, appearance)
        val buffer = consumers.getBuffer(ExhaustiveDrawSettlementTileFaceRenderLayer.get(Identifier(preview.resourceId)))
        val matrix = matrices.peek().positionMatrix
        val left = centerX - width / 2f
        val right = centerX + width / 2f
        val top = centerY - height / 2f
        val bottom = centerY + height / 2f
        val opacity = (alpha.coerceIn(0f, 1f) * 255).roundToInt()
        fun vertex(x: Float, y: Float, u: Float, v: Float) {
            buffer.vertex(matrix, x, y, z).color(255, 255, 255, opacity).texture(u, v)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).next()
        }
        vertex(left, top, preview.u0, preview.v0)
        vertex(right, top, preview.u1, preview.v0)
        vertex(right, bottom, preview.u1, preview.v1)
        vertex(left, bottom, preview.u0, preview.v1)
        val label = tileLabelRegistry.find(assetKey) ?: return
        if (!clientConfigStore.current.tileLabelsEnabled && !label.forced) return
        label.topLeft?.let { renderWorldLabel(it, left, right, top, width, height, alpha, false, z, matrices, consumers) }
        label.topRight?.let { renderWorldLabel(it, left, right, top, width, height, alpha, true, z, matrices, consumers) }
    }

    /**
     * 在 ItemRenderer 已完成的牌模型印刷面上疊加共用角落標籤。
     *
     * @param assetKey 牌面資產 key。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供標籤頂點緩衝。
     * @param light 世界光照值。
     */
    fun renderModelLabels(
        assetKey: String,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
    ) {
        val label = tileLabelRegistry.find(assetKey) ?: return
        if (!clientConfigStore.current.tileLabelsEnabled && !label.forced) return
        label.topLeft?.let { renderModelLabel(it, true, matrices, consumers, light) }
        label.topRight?.let { renderModelLabel(it, false, matrices, consumers, light) }
    }

    /**
     * 在 3D 牌模型的一角繪製標籤文字，固定印在左上或右上角。
     *
     * @param label 標籤內容與顏色。
     * @param isLeft 是否靠左側對齊。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供文字頂點緩衝。
     * @param light 世界光照值。
     */
    private fun renderModelLabel(
        label: TileLabelText,
        isLeft: Boolean,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
    ) {
        val renderer = MinecraftClient.getInstance().textRenderer
        val metrics = tileLabelMetrics(MahjongTileEntity.TILE_WIDTH, MahjongTileEntity.TILE_HEIGHT)
        matrices.push()
        val halfWidth = MahjongTileEntity.TILE_WIDTH / 2.0
        val halfHeight = MahjongTileEntity.TILE_HEIGHT / 2.0
        matrices.translate(
            if (isLeft) halfWidth - metrics.marginX else -halfWidth + metrics.marginX,
            halfHeight - metrics.marginY,
            -(MahjongTileEntity.TILE_DEPTH / 2.0) - MODEL_LABEL_SURFACE_OFFSET,
        )
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f))
        matrices.scale(metrics.scale, -metrics.scale, metrics.scale)
        val originX = if (isLeft) 0f else -renderer.getWidth(label.text).toFloat()
        drawLabelWithOutline(
            renderer,
            label,
            originX,
            0f,
            matrices.peek().positionMatrix,
            consumers,
            light,
        )
        matrices.pop()
    }

    /**
     * 在世界面板牌面的指定角落繪製標籤；縮放與邊距依實際牌面高寬換算，與 [renderModelLabel] 使用相同比例。
     *
     * @param label 標籤內容與顏色。
     * @param left 牌面左緣。
     * @param right 牌面右緣。
     * @param top 牌面上緣。
     * @param width 牌面寬度。
     * @param height 牌面高度。
     * @param alpha 標籤透明度。
     * @param alignRight 是否靠右側對齊。
     * @param z 標籤所在深度。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供文字頂點緩衝。
     */
    private fun renderWorldLabel(
        label: TileLabelText,
        left: Float,
        right: Float,
        top: Float,
        width: Float,
        height: Float,
        alpha: Float,
        alignRight: Boolean,
        z: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val renderer = MinecraftClient.getInstance().textRenderer
        val metrics = tileLabelMetrics(width, height)
        val textWidth = renderer.getWidth(label.text) * metrics.scale
        val x = if (alignRight) right - metrics.marginX - textWidth else left + metrics.marginX
        matrices.push()
        matrices.translate(x.toDouble(), (top + metrics.marginY).toDouble(), (z - WORLD_LABEL_Z_OFFSET).toDouble())
        matrices.scale(metrics.scale, metrics.scale, 1f)
        drawLabelWithOutline(
            renderer,
            label,
            0f,
            0f,
            matrices.peek().positionMatrix,
            consumers,
            LightmapTextureManager.MAX_LIGHT_COORDINATE,
            alpha,
        )
        matrices.pop()
    }

    /**
     * 繪製未旋轉材質與同一牌面座標系內的角落標籤。
     *
     * @param context GUI 繪製上下文。
     * @param assetKey 牌面資產 key。
     * @param x 牌面左上角 X 座標。
     * @param y 牌面左上角 Y 座標。
     * @param width 牌面寬度。
     * @param height 牌面高度。
     * @param appearance 公開規則指定的牌組外觀。
     */
    private fun renderUprightGui(
        context: DrawContext,
        assetKey: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        appearance: MinecraftTileSetAppearanceDefinition,
    ) {
        val preview = resolvePreviewTexture(assetKey, appearance)
        context.drawTexture(
            Identifier(preview.resourceId),
            x,
            y,
            width,
            height,
            preview.u0 * UV_DRAW_SCALE,
            preview.v0 * UV_DRAW_SCALE,
            ((preview.u1 - preview.u0) * UV_DRAW_SCALE).roundToInt(),
            ((preview.v1 - preview.v0) * UV_DRAW_SCALE).roundToInt(),
            UV_DRAW_SCALE,
            UV_DRAW_SCALE,
        )
        val label = tileLabelRegistry.find(assetKey) ?: return
        if (!clientConfigStore.current.tileLabelsEnabled && !label.forced) return
        label.topLeft?.let { renderGuiLabel(context, it, x, y, width, height, alignRight = false) }
        label.topRight?.let { renderGuiLabel(context, it, x, y, width, height, alignRight = true) }
    }

    /**
     * 在 GUI 牌面的指定角落繪製標籤；縮放與邊距按牌面實際尺寸換算，與世界中的麻將牌保持相同比例（見 [renderModelLabel]）。
     *
     * @param context GUI 繪製上下文。
     * @param label 標籤內容與顏色。
     * @param x 牌面左上角 X 座標。
     * @param y 牌面左上角 Y 座標。
     * @param width 牌面寬度。
     * @param height 牌面高度。
     * @param alignRight 是否靠右側對齊。
     */
    private fun renderGuiLabel(context: DrawContext, label: TileLabelText, x: Int, y: Int, width: Int, height: Int, alignRight: Boolean) {
        val renderer = MinecraftClient.getInstance().textRenderer
        val metrics = tileLabelMetrics(width.toFloat(), height.toFloat())
        val labelWidth = renderer.getWidth(label.text) * metrics.scale
        val drawX = if (alignRight) x + width - metrics.marginX - labelWidth else x + metrics.marginX
        context.matrices.push()
        context.matrices.translate(drawX.toDouble(), (y + metrics.marginY).toDouble(), LABEL_Z)
        context.matrices.scale(metrics.scale, metrics.scale, 1f)
        drawLabelWithOutline(
            renderer,
            label,
            0f,
            0f,
            context.matrices.peek().positionMatrix,
            context.vertexConsumers,
            LightmapTextureManager.MAX_LIGHT_COORDINATE,
        )
        context.matrices.pop()
        // 與原版繪製文字相同，立即送出暫存的文字，讓標籤套用目前的裁切範圍與繪製順序。
        context.draw()
    }

    /**
     * 使用原版字型描邊管線繪製牌面輔助標籤。
     *
     * @param renderer Minecraft 字型 renderer。
     * @param label 標籤內容與顏色。
     * @param x 文字基準 X 座標。
     * @param y 文字基準 Y 座標。
     * @param matrix 目前的變換矩陣。
     * @param consumers 提供文字頂點緩衝。
     * @param light 世界光照值。
     * @param alpha 顯示透明度。
     */
    private fun drawLabelWithOutline(
        renderer: TextRenderer,
        label: TileLabelText,
        x: Float,
        y: Float,
        matrix: Matrix4f,
        consumers: VertexConsumerProvider,
        light: Int,
        alpha: Float = 1f,
    ) {
        renderer.drawWithOutline(
            Text.literal(label.text).asOrderedText(),
            x,
            y,
            withAlpha(label.color.toArgb(), alpha),
            withAlpha(LABEL_OUTLINE_COLOR, alpha),
            matrix,
            consumers,
            light,
        )
    }

    /**
     * 依牌面顯示尺寸統一換算標籤縮放與邊距，供 GUI、世界面板與模型渲染路徑共用，避免公式漂移。
     *
     * @param width 牌面寬度。
     * @param height 牌面高度。
     * @return 供不同渲染路徑共用的標籤尺寸參數。
     */
    private fun tileLabelMetrics(width: Float, height: Float): TileLabelMetrics = TileLabelMetrics(
        scale = height * LABEL_SCALE_PER_HEIGHT,
        marginX = width * MODEL_LABEL_MARGIN_RATIO,
        marginY = height * MODEL_LABEL_MARGIN_RATIO,
    )

    /**
     * 一次標籤縮放與邊距換算的結果。
     *
     * @property scale 文字縮放倍率。
     * @property marginX 水平邊距。
     * @property marginY 垂直邊距。
     */
    private data class TileLabelMetrics(val scale: Float, val marginX: Float, val marginY: Float)

    /**
     * 解析牌面預覽；指定資源不存在時使用同牌組的未知牌正面，整組無效時使用內建外觀。
     *
     * @param assetKey 請求的牌面資產 key。
     * @param appearance 公開規則指定的牌組外觀。
     * @return 可載入的貼圖與正規化 UV 範圍。
     */
    private fun resolvePreviewTexture(assetKey: String, appearance: MinecraftTileSetAppearanceDefinition): MinecraftTilePreviewTexture {
        val effectiveAppearance = MahjongTileModelLoadingPlugin.effectiveAppearance(appearance)
        val requested = tilePreviewTexture(assetKey, tileAssetRegistry, effectiveAppearance)
        val requestedId = Identifier(requested.resourceId)
        return if (MinecraftClient.getInstance().resourceManager.getResource(requestedId).isPresent) {
            requested
        } else {
            val unknownPreview = MinecraftTilePreviewTexture(effectiveAppearance.unknownFaceTextureId, 0f, 0f, 1f, 1f)
            if (MinecraftClient.getInstance().resourceManager.getResource(Identifier(unknownPreview.resourceId)).isPresent) {
                unknownPreview
            } else {
                MinecraftTilePreviewTexture(BuiltInMinecraftTileAppearances.DEFAULT.unknownFaceTextureId, 0f, 0f, 1f, 1f)
            }
        }
    }

    private companion object {
        /** 將正規化 UV 換算為原版繪圖 API 的參考刻度；不限制實際貼圖解析度。 */
        const val UV_DRAW_SCALE = 1024
        const val LABEL_Z = 10.0
        const val WORLD_LABEL_Z_OFFSET = 0.001f
        const val MODEL_LABEL_SURFACE_OFFSET = 0.0005
        const val LABEL_OUTLINE_COLOR: Int = 0xFFC6C9C3.toInt()
        const val MODEL_LABEL_MARGIN_RATIO = 0.08f
        const val MODEL_LABEL_SCALE = 0.004f

        /**
         * 依牌面高度換算標籤縮放的比例常數，直接從真正放置在世界中的麻將牌反推（[MODEL_LABEL_SCALE]
         * 除以其固定世界高度 [MahjongTileEntity.TILE_HEIGHT]）——GUI 與世界面板的牌面尺寸各自使用不同
         * 的本地單位系統，但「標籤高度佔牌面高度的比例」本身跟單位系統無關，套用同一個比例常數就能讓
         * 不論面板把牌畫得多大或多小，標籤觀感都跟真正放置的麻將牌一致。
         */
        val LABEL_SCALE_PER_HEIGHT: Float = MODEL_LABEL_SCALE / MahjongTileEntity.TILE_HEIGHT
    }
}

/**
 * 將既有 ARGB 套用面板淡入透明度。
 *
 * @param color 原始 ARGB 顏色。
 * @param alpha 要套用的透明度。
 * @return alpha 由參數取代後的 ARGB 顏色。
 */
private fun withAlpha(color: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (color and 0x00FFFFFF)

/**
 * 將共用標籤顏色轉成 GUI ARGB。
 *
 * @return 此標籤顏色的不透明 ARGB 值。
 */
private fun TileLabelColor.toArgb(): Int = when (this) {
    TileLabelColor.BLACK -> 0xFF000000.toInt()
    TileLabelColor.RED -> 0xFFFF0000.toInt()
}
