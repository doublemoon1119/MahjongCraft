package com.doublemoon1119.mahjongcraft.example.minecraft.mod.vanilla

import net.minecraft.resources.ResourceLocation

/**
 * 由完整的識別碼字串建立資源識別碼，例如 `mahjongcraft_example:chair`。
 *
 * 範例把各 Minecraft 版本寫法不同的呼叫集中在這個套件，每個版本各一份、名稱相同；其他程式只呼叫這裡，與版本無關。
 *
 * @param id 完整的識別碼。
 * @return 資源識別碼。
 */
fun resourceId(id: String): ResourceLocation = ResourceLocation.parse(id)
