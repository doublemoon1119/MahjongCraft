package com.doublemoon1119.mahjongcraft.logic.component

/** 組件在換局時的處理方式，不限制局內何時更新或失效。 */
enum class ComponentRoundTransition {
    /** 從下一局的情境建立新值。 */
    RESET,

    /** 保留尚未被正式動作更新或移除的值。 */
    RETAIN,

    /** 由已登記的規則轉換舊值。 */
    TRANSFORM,
}
