package com.doublemoon1119.mahjongcraft.platform.minecraft.character

/** 跨提供者辨識同一個世界實體的穩定來源，不使用能力登記 ID 當作身分。 */
object SeatedParticipantSources {
    /** 同一存檔內世界實體 UUID 的來源。 */
    const val WORLD_ENTITY: String = "mahjongcraft:world_entity"
}
