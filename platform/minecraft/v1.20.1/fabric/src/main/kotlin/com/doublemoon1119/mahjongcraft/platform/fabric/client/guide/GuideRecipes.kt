package com.doublemoon1119.mahjongcraft.platform.fabric.client.guide

import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongStoolBlock
import com.doublemoon1119.mahjongcraft.platform.fabric.block.MahjongTableBlock
import net.minecraft.client.MinecraftClient
import net.minecraft.item.BlockItem
import net.minecraft.item.ItemStack
import net.minecraft.recipe.Ingredient
import net.minecraft.recipe.RecipeType
import net.minecraft.recipe.ShapedRecipe

/**
 * 一個合成配方在 3×3 材料格中的擺法。
 *
 * @property slots 九個材料格由左到右、由上到下的可用材料；空格為空清單。
 * @property output 成品。
 */
internal data class GuideRecipe(val slots: List<List<ItemStack>>, val output: ItemStack)

/** 從目前世界的配方中找出麻將桌與麻將凳的合成配方。 */
internal object GuideRecipes {
    /**
     * 目前世界中成品為麻將桌或麻將凳的合成配方，麻將桌在前，同類依配方識別碼排序。
     *
     * 資料包可以增刪配方，因此每次開啟畫面時重新查詢。
     *
     * @param client 客戶端。
     * @return 配方；沒有世界或找不到時為空清單。
     */
    fun find(client: MinecraftClient): List<GuideRecipe> {
        val world = client.world ?: return emptyList()
        val registries = world.registryManager
        return world.recipeManager.listAllOfType(RecipeType.CRAFTING)
            .mapNotNull { recipe ->
                val output = recipe.getOutput(registries)
                val order = when ((output.item as? BlockItem)?.block) {
                    is MahjongTableBlock -> TABLE_ORDER
                    is MahjongStoolBlock -> STOOL_ORDER
                    else -> return@mapNotNull null
                }
                val width = (recipe as? ShapedRecipe)?.width ?: GuideRecipeLayout.GRID_SIZE
                Triple(order, recipe.id.toString(), GuideRecipe(slots = gridSlots(recipe.ingredients, width), output = output.copy()))
            }
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { it.third }
    }

    /**
     * 把配方材料放進 3×3 材料格；有形配方依寬度換行，無形配方由左到右依序放。
     *
     * @param ingredients 配方材料。
     * @param width 一列的材料數。
     * @return 九個材料格的可用材料。
     */
    private fun gridSlots(ingredients: List<Ingredient>, width: Int): List<List<ItemStack>> {
        val slots = MutableList(GuideRecipeLayout.GRID_SIZE * GuideRecipeLayout.GRID_SIZE) { emptyList<ItemStack>() }
        ingredients.forEachIndexed { index, ingredient ->
            val row = index / width
            val column = index % width
            if (row < GuideRecipeLayout.GRID_SIZE && column < GuideRecipeLayout.GRID_SIZE) {
                slots[row * GuideRecipeLayout.GRID_SIZE + column] = ingredient.matchingStacks.filterNot(ItemStack::isEmpty)
            }
        }
        return slots
    }

    /** 麻將桌配方的排序。 */
    private const val TABLE_ORDER = 0

    /** 麻將凳配方的排序。 */
    private const val STOOL_ORDER = 1
}
