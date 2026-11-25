package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.crafting.CraftingRecipe

/**
 * 一個合成配方與它的識別碼。
 *
 * @property id 配方識別碼。
 * @property recipe 配方。
 */
data class IdentifiedCraftingRecipe(val id: ResourceLocation, val recipe: CraftingRecipe)
