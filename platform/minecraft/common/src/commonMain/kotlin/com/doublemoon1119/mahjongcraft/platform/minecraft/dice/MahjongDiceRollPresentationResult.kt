package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

/** 正式骰子呈現請求的處理結果。 */
enum class MahjongDiceRollPresentationResult {
    /** 已替換同桌舊擲骰呈現，並建立所有桌面骰子及聚合結果舞台。 */
    PRESENTED,

    /** 指定 dimension、controller 或桌子 UUID 與目前世界不一致。 */
    TABLE_NOT_FOUND,

    /** 任一桌面骰子或聚合結果舞台無法加入世界；已回滾本次建立的 entity。 */
    SPAWN_FAILED,
}
