package com.doublemoon1119.mahjongcraft.example.minecraft.character

/** 範例角色、世界能力與預覽登記使用的完整識別碼。 */
object ExampleCharacterIds {
    /** 可保存並借用的範例人偶，同時用作建立物品及預覽能力 ID。 */
    const val ACTOR = "mahjongcraft_example:actor"

    /** 由框架建立及清除的暫態代理，供自有能力測試。 */
    const val OWNED_ACTOR = "mahjongcraft_example:actor_proxy"

    /** 只有明確頭像、不需要原生世界能力的描述。 */
    const val FACE_ONLY = "mahjongcraft_example:actor_face"
}
