package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionTileOrientationDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelColor
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelText
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.tileTextureAssetPath
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.render.LightmapTextureManager
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier
import net.minecraft.util.math.RotationAxis
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.math.roundToInt

/**
 * Fabric 所有 2D HUD、GUI 與面板牌面共用的材質及角落標籤 renderer。
 *
 * 呼叫端只決定位置、尺寸與方向；完整 UV、未知材質 fallback、`tile-labels-enabled`、forced label
 * 及標籤顏色均集中於此，避免不同 showcase 各自形成不一致的牌面。
 */
@Single
class MahjongTileFaceRenderer(
    @Provided private val tileLabelRegistry: TileLabelRegistry,
    private val clientConfigStore: MahjongClientConfigStore,
) {
    /** 在 GUI 座標系繪製一張完整牌面；橫置時回傳實際佔用寬高。 */
    fun renderGui(
        context: DrawContext,
        assetKey: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        orientation: DecisionTileOrientationDto = DecisionTileOrientationDto.UPRIGHT,
    ) {
        if (orientation == DecisionTileOrientationDto.UPRIGHT) {
            renderUprightGui(context, assetKey, x, y, width, height)
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
        renderUprightGui(context, assetKey, 0, 0, width, height)
        context.matrices.pop()
    }

    /** 在 billboard／showcase 的局部座標繪製牌面 quad 與相同的角落標籤。 */
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
    ) {
        val buffer = consumers.getBuffer(ExhaustiveDrawSettlementTileFaceRenderLayer.get(resolveTexture(assetKey)))
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
        vertex(left, top, 0f, 0f)
        vertex(right, top, 1f, 0f)
        vertex(right, bottom, 1f, 1f)
        vertex(left, bottom, 0f, 1f)
        val label = tileLabelRegistry.find(assetKey) ?: return
        if (!clientConfigStore.current.tileLabelsEnabled && !label.forced) return
        label.topLeft?.let { renderWorldLabel(it, left, right, top, width, alpha, false, z, matrices, consumers) }
        label.topRight?.let { renderWorldLabel(it, left, right, top, width, alpha, true, z, matrices, consumers) }
    }

    /** 在 ItemRenderer 已完成的牌模型印刷面上疊加共用角落標籤。 */
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

    /** 將一段文字固定印在 3D 牌模型的左上／右上角。 */
    private fun renderModelLabel(
        label: TileLabelText,
        isLeft: Boolean,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
        light: Int,
    ) {
        val renderer = MinecraftClient.getInstance().textRenderer
        matrices.push()
        val halfWidth = MahjongTileEntity.TILE_WIDTH / 2.0
        val halfHeight = MahjongTileEntity.TILE_HEIGHT / 2.0
        val marginX = MahjongTileEntity.TILE_WIDTH * MODEL_LABEL_MARGIN_RATIO
        val marginY = MahjongTileEntity.TILE_HEIGHT * MODEL_LABEL_MARGIN_RATIO
        matrices.translate(
            if (isLeft) halfWidth - marginX else -halfWidth + marginX,
            halfHeight - marginY,
            -(MahjongTileEntity.TILE_DEPTH / 2.0),
        )
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f))
        matrices.scale(MODEL_LABEL_SCALE, -MODEL_LABEL_SCALE, MODEL_LABEL_SCALE)
        val originX = if (isLeft) 0f else -renderer.getWidth(label.text).toFloat()
        renderer.draw(
            label.text,
            originX,
            0f,
            label.color.toArgb(),
            false,
            matrices.peek().positionMatrix,
            consumers,
            TextRenderer.TextLayerType.POLYGON_OFFSET,
            0,
            light,
        )
        matrices.pop()
    }

    /** 在世界面板牌面的左右上角繪製隨尺寸縮放的標籤。 */
    private fun renderWorldLabel(
        label: TileLabelText,
        left: Float,
        right: Float,
        top: Float,
        width: Float,
        alpha: Float,
        alignRight: Boolean,
        z: Float,
        matrices: MatrixStack,
        consumers: VertexConsumerProvider,
    ) {
        val renderer = MinecraftClient.getInstance().textRenderer
        val scale = width / TEXTURE_WIDTH
        val margin = width / TEXTURE_WIDTH
        val textWidth = renderer.getWidth(label.text) * scale
        val x = if (alignRight) right - margin - textWidth else left + margin
        matrices.push()
        matrices.translate(x.toDouble(), (top + margin).toDouble(), (z - WORLD_LABEL_Z_OFFSET).toDouble())
        matrices.scale(scale, scale, 1f)
        renderer.draw(
            label.text,
            0f,
            0f,
            withAlpha(label.color.toArgb(), alpha),
            false,
            matrices.peek().positionMatrix,
            consumers,
            TextRenderer.TextLayerType.POLYGON_OFFSET,
            0,
            LightmapTextureManager.MAX_LIGHT_COORDINATE,
        )
        matrices.pop()
    }

    /** 繪製未旋轉材質與同一牌面座標系內的角落標籤。 */
    private fun renderUprightGui(context: DrawContext, assetKey: String, x: Int, y: Int, width: Int, height: Int) {
        context.drawTexture(
            resolveTexture(assetKey),
            x,
            y,
            width,
            height,
            0f,
            0f,
            TEXTURE_WIDTH,
            TEXTURE_HEIGHT,
            TEXTURE_WIDTH,
            TEXTURE_HEIGHT,
        )
        val label = tileLabelRegistry.find(assetKey) ?: return
        if (!clientConfigStore.current.tileLabelsEnabled && !label.forced) return
        label.topLeft?.let { renderGuiLabel(context, it, x, y, width, alignRight = false) }
        label.topRight?.let { renderGuiLabel(context, it, x, y, width, alignRight = true) }
    }

    /** 依牌面顯示尺寸縮放標籤，固定留在牌面左上／右上角。 */
    private fun renderGuiLabel(context: DrawContext, label: TileLabelText, x: Int, y: Int, width: Int, alignRight: Boolean) {
        val renderer = MinecraftClient.getInstance().textRenderer
        val scale = (width / TEXTURE_WIDTH.toFloat()).coerceAtLeast(MIN_LABEL_SCALE)
        val labelWidth = renderer.getWidth(label.text) * scale
        val drawX = if (alignRight) x + width - LABEL_MARGIN - labelWidth else x + LABEL_MARGIN
        context.matrices.push()
        context.matrices.translate(drawX.toDouble(), (y + LABEL_MARGIN).toDouble(), LABEL_Z)
        context.matrices.scale(scale, scale, 1f)
        context.drawText(renderer, label.text, 0, 0, label.color.toArgb(), false)
        context.matrices.pop()
    }

    /** 解析材質，不存在的第三方 asset 安全退回 unknown 牌。 */
    private fun resolveTexture(assetKey: String): Identifier {
        val requested = Identifier(MinecraftModMetadata.MOD_ID, tileTextureAssetPath(assetKey))
        return if (MinecraftClient.getInstance().resourceManager.getResource(requested).isPresent) {
            requested
        } else {
            Identifier(MinecraftModMetadata.MOD_ID, tileTextureAssetPath(UNKNOWN_TILE_ASSET_KEY))
        }
    }

    private companion object {
        const val TEXTURE_WIDTH = 48
        const val TEXTURE_HEIGHT = 64
        const val LABEL_MARGIN = 1
        const val LABEL_Z = 10.0
        const val MIN_LABEL_SCALE = 0.5f
        const val WORLD_LABEL_Z_OFFSET = 0.001f
        const val MODEL_LABEL_MARGIN_RATIO = 0.08
        const val MODEL_LABEL_SCALE = 0.004f
    }
}

/** 將既有 ARGB 套用面板淡入透明度。 */
private fun withAlpha(color: Int, alpha: Float): Int = ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or (color and 0x00FFFFFF)

/** 將共用標籤顏色轉成 GUI ARGB。 */
private fun TileLabelColor.toArgb(): Int = when (this) {
    TileLabelColor.BLACK -> 0xFF000000.toInt()
    TileLabelColor.RED -> 0xFFFF0000.toInt()
}
