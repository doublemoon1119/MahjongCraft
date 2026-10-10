package com.doublemoon1119.mahjongcraft.platform.minecraft.api.event

/**
 * Minecraft 平台中途終止對局的原因 ID，經由對局結束事件的 `reasonId` 提供給訂閱者。
 *
 * 其他平台或第三方可以提供自己的原因 ID；訂閱者不得將此處常數視為完整列舉。
 */
object MinecraftMatchAbortReasonIds {
    /** 桌子確認缺失或被非玩家來源替換，且伺服器政策決定移除進行中的對局。 */
    const val TABLE_MISSING: String = "mahjongcraft:table_missing"

    /** 玩家破壞桌子，且伺服器政策允許移除進行中的對局。 */
    const val TABLE_BROKEN_BY_PLAYER: String = "mahjongcraft:table_broken_by_player"
}
