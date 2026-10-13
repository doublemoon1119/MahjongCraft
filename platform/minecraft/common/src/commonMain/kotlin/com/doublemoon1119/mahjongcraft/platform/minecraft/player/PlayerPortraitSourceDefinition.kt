package com.doublemoon1119.mahjongcraft.platform.minecraft.player

/**
 * 保存頭像來源的 ID、優先序與解析器；解析時依優先序選取，登記時不呼叫解析器。
 *
 * @property providerId 來源的穩定識別碼。
 * @property priority 來源的優先序；數字越大越優先，同值依來源識別碼排序。
 * @property provider 取得呈現資料的來源；登記時不呼叫。
 */
data class PlayerPortraitSourceDefinition(
    val providerId: String,
    val priority: Int = 0,
    val provider: PlayerPortraitSourceProvider,
)
