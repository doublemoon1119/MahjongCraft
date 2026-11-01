package com.doublemoon1119.mahjongcraft.platform.minecraft.api.character

/** 世界角色的所有權；只有自有代理可以由 MahjongCraft 移除。 */
enum class AiCharacterOwnership {
    /** 為這次代理建立，解除綁定後可移除。 */
    OWNED,

    /** 由其他系統持有，只恢復行為並解除綁定。 */
    BORROWED,
}
