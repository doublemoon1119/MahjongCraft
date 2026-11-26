package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level

/**
 * 世界目前載入的全部合成配方。
 *
 * @param world 世界。
 * @return 配方與識別碼。
 */
fun craftingRecipes(world: Level): List<IdentifiedCraftingRecipe> = world.recipeManager.getAllRecipesFor(RecipeType.CRAFTING)
    .map { IdentifiedCraftingRecipe(it.id, it) }
