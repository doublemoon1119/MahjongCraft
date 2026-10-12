package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

/** 自動整理手牌偏好同步至伺服器時使用的兩種語意。 */
interface ClientAutoSortHandPreferenceSender {
    /**
     * 通知玩家主動修改偏好，伺服器可立即重新整理目前手牌。
     *
     * @param enabled 玩家選擇的自動理牌偏好。
     */
    fun sendUserChange(enabled: Boolean)

    /**
     * 加入世界時恢復偏好，不視為本次玩家操作。
     *
     * @param enabled 本機保存的自動理牌偏好。
     */
    fun sendRestore(enabled: Boolean)
}
