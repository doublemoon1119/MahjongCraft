package com.doublemoon1119.mahjongcraft.platform.minecraft.text

/** 自動入座失敗時只向受影響玩家顯示的訊息。 */
object MinecraftSeatingKeys {
    /** 他人占用，不傳送到同位置站立。 */
    const val OCCUPIED = "mahjongcraft.seating.occupied"

    /** 沒有安全站立或乘坐空間。 */
    const val BLOCKED = "mahjongcraft.seating.blocked"

    /** 已有無關載具。 */
    const val RIDING = "mahjongcraft.seating.riding"

    /** 提供者入座失敗或違約。 */
    const val FAILED = "mahjongcraft.seating.failed"
}
