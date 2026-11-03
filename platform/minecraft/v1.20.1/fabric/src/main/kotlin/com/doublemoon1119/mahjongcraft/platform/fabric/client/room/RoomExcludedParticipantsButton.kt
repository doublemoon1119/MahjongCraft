package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.tooltip.Tooltip
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.text.Text

/**
 * 房間頁籤旁的排除名單入口，使用原版按鈕底圖與屏障圖示。
 *
 * @param x 按鈕左界。
 * @param y 按鈕上界。
 * @property count 房主目前可讀的排除人數。
 * @param open 開啟名單畫面，不直接解除任何排除。
 */
internal class RoomExcludedParticipantsButton(
    x: Int,
    y: Int,
    private val count: () -> Int,
    open: () -> Unit,
) : ButtonWidget(x, y, 20, 20, Text.translatable(MinecraftRoomScreenKeys.EXCLUDED_PARTICIPANTS), { open() }, DEFAULT_NARRATION_SUPPLIER) {
    /** 原版圖示只建立一次，不在每一幀建立 ItemStack。 */
    private val icon = ItemStack(Items.BARRIER)

    override fun renderButton(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val label = Text.translatable(MinecraftRoomScreenKeys.EXCLUDED_COUNT, count())
        tooltip = Tooltip.of(label)
        message = Text.empty()
        try {
            super.renderButton(context, mouseX, mouseY, delta)
        } finally {
            message = label
        }
        context.drawItem(icon, x + 2, y + 2)
    }
}
