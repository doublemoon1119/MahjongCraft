package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

/**
 * 載入器提供的用戶端事件。
 *
 * 只在用戶端使用；監聽器都在用戶端主執行緒上呼叫。各載入器各自實作，把自己的事件轉成這裡的呼叫。
 */
interface ClientEvents {
    /**
     * 登記一個用戶端指令；載入器建立用戶端指令表時加入。
     *
     * @param command 要登記的指令。
     */
    fun onRegisterClientCommand(command: ClientCommand)
}
