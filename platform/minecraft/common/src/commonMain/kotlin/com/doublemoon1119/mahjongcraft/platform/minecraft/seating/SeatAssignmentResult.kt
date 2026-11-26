package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

/** 固定席位協調結果，成功不需要額外玩家提示。 */
enum class SeatAssignmentResult {
    /** 本人已在正確座位。 */
    SEATED,

    /** 已安全站到固定席位。 */
    STANDING,

    /** 目標已被占用，保留原位。 */
    OCCUPIED,

    /** 無安全空間，保留原位。 */
    BLOCKED,

    /** 已有無關載具，不強制卸乘。 */
    RIDING,

    /** 提供者失敗或違約，停止自動處理。 */
    FAILED,
}
