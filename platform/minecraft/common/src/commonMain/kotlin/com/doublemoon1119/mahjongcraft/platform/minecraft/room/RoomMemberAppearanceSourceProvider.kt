package com.doublemoon1119.mahjongcraft.platform.minecraft.room

/** 解析房間成員外觀來源的 provider。 */
fun interface RoomMemberAppearanceSourceProvider {
    /**
     * 依成員身分解析外觀來源；無法處理時回傳 null。
     *
     * @param context 要解析的房間成員公開身分。
     * @return 可使用的外觀來源，或 provider 無法處理時的 null。
     */
    fun resolve(context: RoomMemberAppearanceContext): RoomMemberAppearanceSource?
}
