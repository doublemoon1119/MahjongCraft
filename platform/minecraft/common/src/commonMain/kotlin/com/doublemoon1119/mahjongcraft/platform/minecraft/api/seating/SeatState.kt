package com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating

/** 座位檢查或入座結果；正常失敗不得留下移動、乘坐關係或多餘實體。 */
enum class SeatState {
    /** 目前可嘗試入座，執行前仍需驗證。 */
    AVAILABLE,

    /** 本人確實已在目標座位上。 */
    SEATED,

    /** 他人占用，不得退回同位置站立。 */
    OCCUPIED,

    /** 目標消失或卸載。 */
    INVALID,

    /** 不接受乘坐者或不符合席位。 */
    UNSUPPORTED,

    /** 身體空間受阻。 */
    BLOCKED,

    /** 執行失敗且沒有留下副作用。 */
    FAILED,
}
