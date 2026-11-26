package com.doublemoon1119.mahjongcraft.example.minecraft.mod.vanilla

import net.minecraft.world.item.ItemStack

/**
 * 物品是否有玩家取的名稱，例如在鐵砧改過名。
 *
 * @return 有自訂名稱時為 true。
 */
fun ItemStack.hasCustomName(): Boolean = hasCustomHoverName()
