package com.doublemoon1119.mahjongcraft.platform.minecraft.character

/** 角色切換的權威檢查結果；拒絕時不修改任何選擇。 */
enum class AiCharacterSelectionResult {
    /** 選擇已更新，或原本就是同一角色。 */
    ACCEPTED,

    /** 角色保存資料目前不可用。 */
    DATA_UNAVAILABLE,

    /** 已不在等待開局的房間內。 */
    ROOM_NOT_FOUND,

    /** 操作者不是房主。 */
    NOT_HOST,

    /** 目標不是該房間的 AI。 */
    NOT_AI_MEMBER,

    /** 沒有登記這個角色。 */
    UNKNOWN_CHARACTER,

    /** 外部成員使用提供者擷取的描述，不接受房間角色切換。 */
    SOURCE_MANAGED,
}
