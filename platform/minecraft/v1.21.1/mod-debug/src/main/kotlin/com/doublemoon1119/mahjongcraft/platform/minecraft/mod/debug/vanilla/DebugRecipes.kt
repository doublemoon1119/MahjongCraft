package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla

import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.crafting.Ingredient

/**
 * 伺服器載入的每個配方與它的材料格。有形配方的空格以 [Ingredient.EMPTY] 表示。
 *
 * @param server 目前伺服器。
 * @return 配方識別碼對應它的材料格。
 */
fun loadedRecipeIngredients(server: MinecraftServer): Map<ResourceLocation, List<Ingredient>> = server.recipeManager.recipes.associate { holder -> holder.id() to holder.value().ingredients.toList() }
