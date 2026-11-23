package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import net.minecraft.client.gui.components.EditBox

/** 每個遊戲刻更新輸入框的游標閃爍；由所屬畫面的 `tick` 呼叫。 */
fun EditBox.tickCursor() {
    tick()
}
