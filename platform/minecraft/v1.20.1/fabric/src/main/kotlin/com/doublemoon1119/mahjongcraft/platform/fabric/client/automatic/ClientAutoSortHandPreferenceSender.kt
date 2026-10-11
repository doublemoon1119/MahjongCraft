package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

/** 自動整理手牌偏好同步至伺服器時使用的兩種語意。 */
interface ClientAutoSortHandPreferenceSender {
    /** 玩家主動修改偏好，伺服器可立即重新整理目前手牌。 */
    fun sendUserChange(enabled: Boolean)

    /** 加入世界時恢復偏好，不視為本次玩家操作。 */
    fun sendRestore(enabled: Boolean)
}
