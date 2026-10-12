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

    /**
     * 取得兩種圓點中較寬的寬度，讓每一列的名稱起點對齊。
     *
     * @param renderer 文字量測器。
     * @return 圓點預留寬度（像素）。
     */
    fun dotWidth(renderer: TextRenderer): Int = maxOf(renderer.getWidth(ENABLED_DOT), renderer.getWidth(DISABLED_DOT))

    /**
     * 量測每一列「圓點＋間距＋名稱」含陰影的實際寬度。
     *
     * @param renderer 文字量測器。
     * @param labels 依顯示順序排列的項目名稱。
     * @return 與名稱順序相同的各列寬度（像素）。
     */
    fun rowWidths(renderer: TextRenderer, labels: List<Text>): List<Int> {
        val dotWidth = dotWidth(renderer)
        return labels.map { label -> dotWidth + DOT_LABEL_GAP + renderer.getWidth(label) + SHADOW_EXTENT }
    }

    /**
     * 量測一列文字含陰影的實際高度。
     *
     * @param renderer 文字量測器。
     * @return 文字高度加陰影範圍（像素）。
     */
    fun textHeight(renderer: TextRenderer): Int = renderer.fontHeight + SHADOW_EXTENT

    /**
     * 建立放不下時代表剩餘項數的摘要文字。
     *
     * @param hiddenCount 未能顯示的項目數。
     * @return 可本地化的項目數摘要。
     */
    fun summaryText(hiddenCount: Int): Text = Text.translatable(
        MinecraftClientConfigScreenKeys.HUD_AUTOMATIC_CONTROL_MORE,
        hiddenCount,
    )

    /**
     * 量測剩餘項數摘要含陰影的實際寬度。
     *
     * @param renderer 文字量測器。
     * @param hiddenCount 未能顯示的項目數。
     * @return 摘要寬度加陰影範圍（像素）。
     */
    fun summaryWidth(renderer: TextRenderer, hiddenCount: Int): Int = renderer.getWidth(summaryText(hiddenCount)) + SHADOW_EXTENT
}
