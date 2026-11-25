package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.game.PlayerDecisionHudController
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongTileSelectionConfirmEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftHudKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.phys.EntityHitResult
import kotlin.math.abs
import kotlin.uuid.toKotlinUuid

/**
 * 多選選牌確認面板的 client renderer。
 *
 * 可見範圍：
 *
 * - 只有本機玩家就是 [MahjongTileSelectionConfirmEntity.holderId] 時才畫出來，其他人的 client 不畫。
 * - entity 本身仍透過原版 tracking 廣播給範圍內所有人，取捨見該 entity KDoc。
 *
 * 外觀：
 *
 * - 畫法比照 [MahjongPlayerInfoEntityRenderer]：固定朝向 entity 本身的 `yaw`，不像 billboard 那樣轉向鏡頭。
 * - 疊一次背面（往法線方向平移一點再轉 180 度）做雙面渲染，從哪個角度接近都看得到文字。
 * - 半透明黑底風格與其他世界空間面板（局況顯示、玩家資訊等）一致。
 * - 文字左右的 `>`／`<` 標記只在準心正對這組面板任一個 instance（見 [isHovered]）時畫出，表示現在可以互動。
 * - 背景與可點擊寬度的量測一律把標記算進去（`measurementLabel`），標記出現或消失時背景寬度不會跳動。
 * - 文字顏色依 [PlayerDecisionHudController.isTileSelectionConfirmable] 表示已選數量是否在合法範圍內：
 *   綠色代表右鍵會送出，灰色代表還選得太少。這與標記是否顯示是不同的資訊，分開判斷。
 * - 不寫「右鍵」文字，避免用特定輸入裝置的動作名稱描述互動。
 *
 * 位置與寬度：
 *
 * - [MahjongTileSelectionConfirmEntity] 的碰撞箱是原版慣用的「腳下往上」正立方體，與以錨點為中心畫出的面板不同心。
 *   因此整塊面板先往上平移半個 [MahjongTileSelectionConfirmEntity.HEIGHT]，對齊碰撞箱的垂直中心。
 * - 每個 instance 每幀都會執行到這個方法，依實際渲染出的文字寬度更新自己的 [MahjongTileSelectionConfirmEntity.isActive]。
 * - 只有 [MahjongTileSelectionConfirmEntity.isPrimary] 那個 instance 會畫出文字與背景，其餘更新完 `isActive` 就返回。
 * - 背景與文字寬度使用同一次量測結果。超過 [MahjongTileTableLayout.TILE_SELECTION_CONFIRM_SEGMENT_COUNT] 個 instance
 *   能涵蓋的最大寬度時，用 [WorldPanelRenderer.fitText] 截斷並加上刪節號，不縮小字體。
 * - `isPrimary` 那個 instance 額外沿本地 X 軸往 [MahjongTileTableLayout.tileSelectionConfirmSegmentAlongOffset] 的反方向平移，
 *   讓內容對齊整組面板的中心。段數為奇數、`isPrimary` 剛好在正中央時，這個平移為零。
 *
 * @param context Minecraft renderer factory 提供的 client context。
 */
class MahjongTileSelectionConfirmEntityRenderer(
    context: EntityRendererProvider.Context,
) : EntityRenderer<MahjongTileSelectionConfirmEntity>(context) {
    private val textRenderer: Font = context.font

    override fun render(
        entity: MahjongTileSelectionConfirmEntity,
        yaw: Float,
        tickDelta: Float,
        matrices: PoseStack,
        vertexConsumers: MultiBufferSource,
        light: Int,
    ) {
        if (entity.isInvisible) return
        val localPlayerId = Minecraft.getInstance().player?.uuid?.toKotlinUuid() ?: return
        if (entity.holderId != localPlayerId) return
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light)

        val hovered = isHovered(entity)
        val baseText = Component.translatable(MinecraftHudKeys.TILE_SELECTION_CONFIRM)
        val measurementLabel = markedLabel(baseText)
        val neededWidthBlocks = (((textRenderer.width(measurementLabel) + PANEL_PADDING * 2) * TEXT_SCALE).toDouble()).coerceAtMost(MAX_PANEL_WIDTH_BLOCKS)
        val myOffset = MahjongTileTableLayout.tileSelectionConfirmSegmentAlongOffset(entity.segmentIndex)
        entity.isActive = abs(myOffset) <= neededWidthBlocks / 2.0

        if (!entity.isPrimary) return

        val confirmable = PlayerDecisionHudController.isTileSelectionConfirmable() == true
        val widthPx = (neededWidthBlocks / TEXT_SCALE).toFloat()
        val drawLabel = if (hovered) measurementLabel else baseText

        WorldPanelDeferral.draw(matrices, vertexConsumers) { panelMatrices, panelConsumers ->
            panelMatrices.pushPose()
            panelMatrices.translate(0.0, MahjongTileSelectionConfirmEntity.HEIGHT / 2.0, 0.0)
            panelMatrices.mulPose(Axis.YP.rotationDegrees(yaw))
            panelMatrices.translate(-myOffset, 0.0, 0.0)
            panelMatrices.scale(-TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE)
            // 兩面背對背，只畫觀看者那一側的一面，兩面不會互相透出或閃爍。
            if (!WorldPanelRenderer.facesViewer(panelMatrices)) panelMatrices.mulPose(Axis.YP.rotationDegrees(180f))
            renderFace(drawLabel, confirmable, widthPx, light, panelMatrices, panelConsumers)
            panelMatrices.popPose()
        }
    }

    /**
     * 本機玩家目前準心是否正對著這組確認面板的任一個 instance（與 [entity] 屬於同一 holder／桌）。
     *
     * @param entity 要比對持有者與桌子的確認面板。
     * @return 準心目標屬於相同 holder 與桌子時為 `true`。
     */
    private fun isHovered(entity: MahjongTileSelectionConfirmEntity): Boolean {
        val target = (Minecraft.getInstance().hitResult as? EntityHitResult)?.entity as? MahjongTileSelectionConfirmEntity
        return target != null && target.holderId == entity.holderId && target.managedTableId == entity.managedTableId
    }

    /**
     * 在 [text] 左右加上選取效果標記。版面量測永遠會把標記算進去，見類別 KDoc。
     *
     * @param text 要加上標記的文字。
     * @return 前後含標記的文字。
     */
    private fun markedLabel(text: Component): Component = Component.literal("$SELECTION_MARK_LEFT ").append(text).append(" $SELECTION_MARK_RIGHT")

    /**
     * 繪製確認面板的一面背景與置中文字。
     *
     * @param label 要顯示的文字。
     * @param confirmable 選取數量是否符合可送出的條件。
     * @param widthPx 背景及文字可用寬度。
     * @param light 世界光照值。
     * @param matrices 目前的模型矩陣。
     * @param consumers 提供面板與文字頂點緩衝。
     */
    private fun renderFace(label: Component, confirmable: Boolean, widthPx: Float, light: Int, matrices: PoseStack, consumers: MultiBufferSource) {
        val textColor = if (confirmable) CONFIRMABLE_TEXT_COLOR else NOT_CONFIRMABLE_TEXT_COLOR
        val height = textRenderer.lineHeight
        WorldPanelRenderer.drawBackground(
            left = -widthPx / 2f,
            top = -height / 2f - PANEL_PADDING,
            right = widthPx / 2f,
            bottom = height / 2f + PANEL_PADDING,
            color = BACKGROUND_COLOR,
            z = 0f,
            matrices = matrices,
            consumers = consumers,
        )
        val maxTextWidthPx = (widthPx - PANEL_PADDING * 2f).toInt().coerceAtLeast(0)
        val fittedText = WorldPanelRenderer.fitText(textRenderer, label.string, maxTextWidthPx)
        val fittedWidth = textRenderer.width(fittedText)
        WorldPanelRenderer.drawText(
            renderer = textRenderer,
            text = Component.literal(fittedText),
            x = -fittedWidth / 2.0f,
            y = -height / 2.0f,
            color = textColor,
            z = TEXT_Z,
            light = light,
            matrices = matrices,
            consumers = consumers,
        )
    }

    /** 純文字顯示，不提供單一 texture。原版規定回傳值不可為空，因此回傳方塊材質圖集。 */
    override fun getTextureLocation(entity: MahjongTileSelectionConfirmEntity): ResourceLocation = InventoryMenu.BLOCK_ATLAS

    private companion object {
        /** 文字縮放。 */
        const val TEXT_SCALE: Float = 0.025f

        /** 單一背景四周的像素 padding。 */
        const val PANEL_PADDING: Float = 5f

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
         * 內容量測，用這個值當上限。不是 `const val`，運算式引用另一個模組的常數，讓兩邊調整拼湊面板
         * 尺寸時這個值自動保持一致。
         */
        const val MAX_PANEL_WIDTH_BLOCKS: Double =
            MahjongTileTableLayout.TILE_SELECTION_CONFIRM_SEGMENT_COUNT * MahjongTileTableLayout.TILE_SELECTION_CONFIRM_SEGMENT_SPACING
    }
}
