package com.doublemoon1119.mahjongcraft.platform.minecraft.room

/**
 * 保存房間成員外觀來源的 ID、優先序與解析器；登記時不呼叫解析器。
 *
 * @property providerId 來源的穩定識別碼。
 * @property priority 來源的優先序；數字越大越優先，同值依來源識別碼排序。
 * @property provider 取得呈現資料的來源；登記時不呼叫。
 */
data class RoomMemberAppearanceSourceDefinition(
    val providerId: String,
    val priority: Int = 0,
    val provider: RoomMemberAppearanceSourceProvider,
)
