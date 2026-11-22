package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items

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
) : Button(x, y, 20, 20, Component.translatable(MinecraftRoomScreenKeys.EXCLUDED_PARTICIPANTS), { open() }, DEFAULT_NARRATION) {
    /** 原版圖示只建立一次，不在每一幀建立 ItemStack。 */
    private val icon = ItemStack(Items.BARRIER)

    override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        val label = Component.translatable(MinecraftRoomScreenKeys.EXCLUDED_COUNT, count())
        tooltip = Tooltip.create(label)
        message = Component.empty()
        try {
            super.renderWidget(context, mouseX, mouseY, delta)
        } finally {
            message = label
        }
        context.renderItem(icon, x + 2, y + 2)
    }
}
