package com.doublemoon1119.mahjongcraft.platform.minecraft.room

/** 解析房間成員外觀來源的 provider。 */
fun interface RoomMemberAppearanceSourceProvider {
    /** 依成員身分解析外觀來源；無法處理時回傳 null。 */
    fun resolve(context: RoomMemberAppearanceContext): RoomMemberAppearanceSource?
}
