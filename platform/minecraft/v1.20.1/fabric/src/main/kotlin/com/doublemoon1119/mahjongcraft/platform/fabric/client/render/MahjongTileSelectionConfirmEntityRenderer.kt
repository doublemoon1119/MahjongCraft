package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.platform.fabric.client.game.PlayerDecisionHudController
import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongTileSelectionConfirmEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.render.VertexConsumerProvider
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.entity.EntityRendererFactory
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.math.RotationAxis
import kotlin.math.abs
import kotlin.uuid.toKotlinUuid

/**
 * 多選選牌確認面板的 client renderer——只有本機玩家就是 [MahjongTileSelectionConfirmEntity.holderId]
 * 才畫出來，其他人的 client 完全不畫，等於視覺上對他們不存在（entity 本身仍透過原版 tracking 廣播給
 * 範圍內所有人，見該 entity KDoc 的取捨說明）。
 *
 * 畫法比照 [MahjongPlayerInfoEntityRenderer]：固定朝向 entity 本身的 `yaw`（不像 billboard 那樣永遠
 * 轉向鏡頭），並疊一次背面（往法線方向平移一點再轉 180 度）做雙面渲染，讓面板不管從哪個角度接近都看
 * 得到文字。半透明黑底風格跟其他世界空間面板（局況顯示、玩家資訊等）一致。
 *
 * 文字左右的 `>`／`<` 選取效果標記只在玩家目前準心正對著這組確認面板任一個 instance（見 [isHovered]）
 * 時才畫出來，暗示「現在可以互動」；但背景／可點擊寬度的量測**永遠把標記算進去**（[measurementLabel]），
 * 不管當下有沒有畫出來，避免標記出現／消失的瞬間背景寬度跟著跳動。文字顏色則另外依
 * [PlayerDecisionHudController.isTileSelectionConfirmable] 表示已選數量是否落在合法範圍內：綠色代表
 * 右鍵真的會送出，灰色代表選太少還不行——跟標記是否顯示是不同的資訊，分開判斷。不直接寫「右鍵」文字：
 * 避免用某個特定輸入裝置的動作名稱描述互動。
 *
 * [MahjongTileSelectionConfirmEntity] 的碰撞箱是原版慣用的「腳下往上」正立方體，跟這裡以錨點為中心
 * 對稱畫出的面板不同心，所以先把整塊面板往上平移半個 [MahjongTileSelectionConfirmEntity.HEIGHT]，讓
 * 視覺置中位置對齊碰撞箱的垂直中心。
 *
 * **每個 instance 每幀都會執行到這個方法**（不只 [MahjongTileSelectionConfirmEntity.isPrimary] 那個），
 * 用來依目前實際渲染出的文字寬度更新自己的 [MahjongTileSelectionConfirmEntity.isActive]（見該欄位
 * KDoc）；只有 [MahjongTileSelectionConfirmEntity.isPrimary] 那個 instance 會再進一步畫出文字／背景，
 * 其餘 instance 更新完 `isActive` 就直接返回、不畫任何東西。背景／文字寬度使用同一次量測結果，超過
 * [MahjongTileTableLayout.TILE_SELECTION_CONFIRM_SEGMENT_COUNT] 個 instance 能涵蓋的最大寬度時用
 * [WorldPanelRenderer.fitText] 截斷並加上刪節號（跟 [MahjongPlayerInfoEntityRenderer] 顯示玩家名稱
 * 同一套處理方式），不縮小字體。
 *
 * `isPrimary` 那個 instance 額外沿本地 X 軸平移
 * [MahjongTileTableLayout.tileSelectionConfirmSegmentAlongOffset] 的反方向，讓渲染內容對齊整組拼湊
 * 面板真正的中心，而不是這個 instance 自己的世界座標——兩者只有在
 * [MahjongTileTableLayout.TILE_SELECTION_CONFIRM_SEGMENT_COUNT] 是奇數、`isPrimary` 剛好選在正中央
 * 那個 index 時才會重合（偶數段數沒有任何一個 index 剛好落在正中央，這個平移就不是零）。
 */
class MahjongTileSelectionConfirmEntityRenderer(
    context: EntityRendererFactory.Context,
) : EntityRenderer<MahjongTileSelectionConfirmEntity>(context) {
    private val textRenderer: TextRenderer = context.textRenderer

    override fun render(
        entity: MahjongTileSelectionConfirmEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: MatrixStack,
        vertexConsumers: VertexConsumerProvider,
        light: Int,
    ) {
        if (entity.isInvisible) return
        val localPlayerId = MinecraftClient.getInstance().player?.uuid?.toKotlinUuid() ?: return
        if (entity.holderId != localPlayerId) return
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)

        val hovered = isHovered(entity)
        val baseText = Text.translatable("mahjongcraft.hud.tile_selection_confirm")
        val measurementLabel = markedLabel(baseText)
        val neededWidthBlocks = (((textRenderer.getWidth(measurementLabel) + PANEL_PADDING * 2) * TEXT_SCALE).toDouble()).coerceAtMost(MAX_PANEL_WIDTH_BLOCKS)
        val myOffset = MahjongTileTableLayout.tileSelectionConfirmSegmentAlongOffset(entity.segmentIndex)
        entity.isActive = abs(myOffset) <= neededWidthBlocks / 2.0

        if (!entity.isPrimary) return

        val confirmable = PlayerDecisionHudController.isTileSelectionConfirmable() == true
        val widthPx = (neededWidthBlocks / TEXT_SCALE).toFloat()
        val drawLabel = if (hovered) measurementLabel else baseText

        matrices.push()
        matrices.translate(0.0, MahjongTileSelectionConfirmEntity.HEIGHT / 2.0, 0.0)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw))
        matrices.translate(-myOffset, 0.0, 0.0)
        matrices.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE)
        renderFace(drawLabel, confirmable, widthPx, light, matrices, vertexConsumers)
        matrices.push()
        matrices.translate(0.0, 0.0, BACK_FACE_OFFSET.toDouble())
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180f))
        renderFace(drawLabel, confirmable, widthPx, light, matrices, vertexConsumers)
        matrices.pop()
        matrices.pop()
    }

    /** 本機玩家目前準心是否正對著這組確認面板的任一個 instance（跟 [entity] 同一個 holder／桌）。 */
    private fun isHovered(entity: MahjongTileSelectionConfirmEntity): Boolean {
        val target = (MinecraftClient.getInstance().crosshairTarget as? EntityHitResult)?.entity as? MahjongTileSelectionConfirmEntity
        return target != null && target.holderId == entity.holderId && target.managedTableId == entity.managedTableId
    }

    /** [text] 左右加上選取效果標記；見類別 KDoc「量測永遠把標記算進去」的說明。 */
    private fun markedLabel(text: Text): Text = Text.literal("$SELECTION_MARK_LEFT ").append(text).append(" $SELECTION_MARK_RIGHT")

    private fun renderFace(label: Text, confirmable: Boolean, widthPx: Float, light: Int, matrices: MatrixStack, consumers: VertexConsumerProvider) {
        val textColor = if (confirmable) CONFIRMABLE_TEXT_COLOR else NOT_CONFIRMABLE_TEXT_COLOR
        val height = textRenderer.fontHeight
        WorldPanelRenderer.drawBackground(
            -widthPx / 2f,
            -height / 2f - PANEL_PADDING,
            widthPx / 2f,
            height / 2f + PANEL_PADDING,
            BACKGROUND_COLOR,
            0f,
            matrices,
            consumers,
        )
        val maxTextWidthPx = (widthPx - PANEL_PADDING * 2f).toInt().coerceAtLeast(0)
        val fittedText = WorldPanelRenderer.fitText(textRenderer, label.string, maxTextWidthPx)
        val fittedWidth = textRenderer.getWidth(fittedText)
        WorldPanelRenderer.drawText(
            textRenderer,
            Text.literal(fittedText),
            -fittedWidth / 2.0f,
            -height / 2.0f,
            textColor,
            TEXT_Z,
            light,
            matrices,
            consumers,
        )
    }

    /** 純文字顯示，不提供單一 texture。 */
    override fun getTexture(entity: MahjongTileSelectionConfirmEntity): Identifier? = null

    private companion object {
        /** 文字縮放，起始估算值，預期進遊戲後用截圖比對調整。 */
        const val TEXT_SCALE: Float = 0.025f

        /** 單一背景四周的像素 padding。 */
        const val PANEL_PADDING: Float = 5f

        /** 背面相對正面向法線方向平移的距離，避免兩面共平面產生 Z-fighting。 */
        const val BACK_FACE_OFFSET: Float = 0.02f

        /** 已選數量落在合法範圍內、右鍵真的會送出時的文字顏色。 */
        const val CONFIRMABLE_TEXT_COLOR: Int = 0xFF6BCB6B.toInt()

        /** 已選數量還不在合法範圍內（太少或太多）時的文字顏色。 */
        const val NOT_CONFIRMABLE_TEXT_COLOR: Int = 0xFF9A9A9A.toInt()

        /** 文字左側的選取效果標記，暗示可互動，不寫死特定輸入裝置的動作名稱。 */
        const val SELECTION_MARK_LEFT: String = ">"

        /** 文字右側的選取效果標記。 */
        const val SELECTION_MARK_RIGHT: String = "<"

        /** 文字相對背景向觀看者方向移動的距離，避免兩者位於同一深度而閃爍。 */
        const val TEXT_Z: Float = -0.02f

        /** 文字背景色板，半透明黑底，跟其他世界空間面板（局況顯示等）風格一致。 */
        const val BACKGROUND_COLOR: Int = 0xB01A2232.toInt()

        /**
         * 拼湊碰撞箱能涵蓋的最大寬度（世界座標格數），換算自
         * [MahjongTileTableLayout.TILE_SELECTION_CONFIRM_SEGMENT_COUNT] 乘上
         * [MahjongTileTableLayout.TILE_SELECTION_CONFIRM_SEGMENT_SPACING]。背景／可點擊範圍依實際文字
         * 內容量測，用這個值當上限；不是 `const val`，運算式引用另一個模組的常數，讓兩邊調整拼湊面板
         * 尺寸時這個值自動保持一致。
         */
        val MAX_PANEL_WIDTH_BLOCKS: Double =
            MahjongTileTableLayout.TILE_SELECTION_CONFIRM_SEGMENT_COUNT * MahjongTileTableLayout.TILE_SELECTION_CONFIRM_SEGMENT_SPACING
    }
}
