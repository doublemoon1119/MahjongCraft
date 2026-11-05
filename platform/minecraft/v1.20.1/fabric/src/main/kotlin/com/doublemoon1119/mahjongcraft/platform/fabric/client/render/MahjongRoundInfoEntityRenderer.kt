package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongRoundInfoEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.util.Identifier

/**
 * 將桌面中央的局況資訊畫成面向攝影機的文字面板。
 *
 * [MahjongRoundInfoEntity.lines] 只提供局況行 ID 與帶型別的參數。每行交給 [RoundInfoLineTextBuilder] 組成文字，
 * 再由用戶端依自己的語系顯示；牌名、牌面 emoji 與規則訊息都透過各自的 registry 查詢。
 * 未登記的行 ID 直接顯示原始 ID，不會讓整行消失。
 *
 * 繪製方式沿用原版實體名牌的座標方向：套用攝影機旋轉、以 `(-scale, -scale, scale)` 縮放，並使用
 * [TextRenderer.TextLayerType.NORMAL] 畫在半空中。
 *
 * @param context Minecraft renderer factory 提供的 client context。
 * @property configStore 提供局況面板可見性設定。
 * @param roundInfoLineDisplayRegistry 將同步的 line key 解析為本地化顯示定義。
 * @param tileDisplayNameRegistry 將擴充牌種轉成名稱翻譯。
 * @param tileAssetRegistry 將牌種轉成素材 key。
 * @param tileEmojiRegistry 將素材 key 轉成牌面 emoji。
 */
class MahjongRoundInfoEntityRenderer(
    context: EntityRendererFactory.Context,
    private val configStore: MahjongClientConfigStore,
    roundInfoLineDisplayRegistry: RoundInfoLineDisplayRegistry,
    tileDisplayNameRegistry: TileDisplayNameRegistry,
    tileAssetRegistry: MinecraftTileAssetRegistry,
    tileEmojiRegistry: TileEmojiRegistry,
) : EntityRenderer<MahjongRoundInfoEntity>(context) {
    /** 共用 Vanilla text renderer，畫法比照 [MahjongTileEntityRenderer]。 */
    private val textRenderer = context.textRenderer

    /** 局況文字參數診斷記錄器。 */
    private val logger = mahjongCraftLogger(MahjongRoundInfoEntityRenderer::class)

    /** 局況資料列文字組合器。 */
    private val lineTextBuilder = RoundInfoLineTextBuilder(
        roundInfoLineDisplayRegistry,
        tileDisplayNameRegistry,
        tileAssetRegistry,
        tileEmojiRegistry,
    ) { message -> logger.warn(message) }

    override fun render(
        entity: MahjongRoundInfoEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        if (!configStore.current.presentationVisibility.roundInfoEnabled) return
        if (entity.isInvisible) return
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)
        val lines = entity.lines.map(lineTextBuilder::buildLineText)

        matrices.push()
        matrices.multiply(dispatcher.rotation)
        matrices.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE)
        val lineHeight = textRenderer.fontHeight + LINE_SPACING
        val totalHeight = lineHeight * lines.size
        val maxWidth = lines.maxOfOrNull(textRenderer::getWidth) ?: 0
        WorldPanelRenderer.drawBackground(
            -maxWidth / 2f - PANEL_PADDING,
            -totalHeight / 2f - PANEL_PADDING,
            maxWidth / 2f + PANEL_PADDING,
            totalHeight / 2f + PANEL_PADDING,
            BACKGROUND_COLOR,
            0f,
            matrices,
            vertexConsumers,
        )
        lines.forEachIndexed { index, line ->
            val width = textRenderer.getWidth(line)
            val x = -width / 2.0f
            val y = -totalHeight / 2.0f + index * lineHeight
            WorldPanelRenderer.drawText(
                textRenderer,
                line,
                x,
                y,
                TEXT_COLOR,
                TEXT_Z,
                light,
                matrices,
                vertexConsumers,
            )
        }
        matrices.pop()
    }

    /** 純文字顯示，不提供單一 texture。 */
    override fun getTexture(entity: MahjongRoundInfoEntity): Identifier? = null

    companion object {
        /** 文字縮放。 */
        private const val TEXT_SCALE: Float = 0.025f

        /** 相鄰兩行文字之間的額外間距（像素，縮放前）。 */
        private const val LINE_SPACING: Int = 2

        /** 單一背景四周的像素 padding。 */
        private const val PANEL_PADDING: Float = 5f

        /** 文字顏色，不透明白色。 */
        private const val TEXT_COLOR: Int = 0xFFFFFFFF.toInt()

        /** 文字相對背景向觀看者方向移動的距離，避免兩者位於同一深度而閃爍。 */
        private const val TEXT_Z: Float = -0.02f

        /** 文字背景色板，半透明黑底，比照 vanilla 名牌背景慣例，提升可讀性。 */
        private const val BACKGROUND_COLOR: Int = 0xB01A2232.toInt()
    }
}
