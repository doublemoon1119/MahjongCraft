package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import net.minecraft.client.font.TextRenderer
import net.minecraft.text.Text

/**
 * 自動操作狀態列的文字內容與量測。
 *
 * 正式 HUD 與 HUD 位置編輯器的預覽共用這一份：面板大小完全由這些文字的實際寬高決定，兩邊各自量測會讓
 * 預覽框與實際面板對不上。
 */
internal object AutomaticControlStatusHudText {
    /** 啟用中的圓點。 */
    val ENABLED_DOT: Text = Text.literal("●")

    /** 未啟用的圓點。 */
    val DISABLED_DOT: Text = Text.literal("○")

    /** 圓點與項目名稱之間的間距。 */
    const val DOT_LABEL_GAP: Int = 5

    /** 文字陰影往右下各多佔的一個像素。 */
    const val SHADOW_EXTENT: Int = 1

    /** 兩種圓點中較寬的寬度，讓每一列的名稱起點對齊。 */
    fun dotWidth(renderer: TextRenderer): Int = maxOf(renderer.getWidth(ENABLED_DOT), renderer.getWidth(DISABLED_DOT))

    /** 每一列「圓點＋間距＋名稱」含陰影的實際寬度。 */
    fun rowWidths(renderer: TextRenderer, labels: List<Text>): List<Int> {
        val dotWidth = dotWidth(renderer)
        return labels.map { label -> dotWidth + DOT_LABEL_GAP + renderer.getWidth(label) + SHADOW_EXTENT }
    }

    /** 一列文字含陰影的實際高度。 */
    fun textHeight(renderer: TextRenderer): Int = renderer.fontHeight + SHADOW_EXTENT

    /** 放不下時代表剩餘項數的那一列。 */
    fun summaryText(hiddenCount: Int): Text = Text.translatable(
        MinecraftClientConfigScreenKeys.HUD_AUTOMATIC_CONTROL_MORE,
        hiddenCount,
    )

    /** 剩餘項數那一列含陰影的實際寬度。 */
    fun summaryWidth(renderer: TextRenderer, hiddenCount: Int): Int = renderer.getWidth(summaryText(hiddenCount)) + SHADOW_EXTENT
}
