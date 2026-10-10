package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import kotlinx.serialization.Serializable

/** 一張麻將桌目前被什麼佔用。 */
@Serializable
enum class TableOccupancyDto {
    /** 房間與對局都不存在。 */
    VACANT,

    /** 等待階段的房間。 */
    ROOM,

    /** 進行中的對局。 */
    GAME,
}
