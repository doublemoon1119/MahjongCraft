package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

/** 自動理牌偏好更新的來源意圖，決定是否允許立即整理並重新呈現手牌。 */
enum class HandSortPreferenceUpdateMode {
    /** 重新連線時只恢復伺服器端暫存偏好，不重新發布手牌。 */
    RESTORE,

    /** 玩家主動變更設定，允許在安全情況下立即整理並重新呈現手牌。 */
    USER_CHANGE,
}
