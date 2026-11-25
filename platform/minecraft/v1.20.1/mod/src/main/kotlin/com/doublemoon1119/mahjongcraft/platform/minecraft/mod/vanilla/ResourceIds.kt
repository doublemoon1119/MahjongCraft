package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import net.minecraft.resources.ResourceLocation

/**
 * 以命名空間與路徑建立資源識別碼。
 *
 * @param namespace 命名空間，例如 `mahjongcraft`。
 * @param path 路徑。
 * @return 資源識別碼。
 * @throws net.minecraft.ResourceLocationException 命名空間或路徑含有不允許的字元。
 */
fun resourceId(namespace: String, path: String): ResourceLocation = ResourceLocation(namespace, path)

/**
 * 解析 `命名空間:路徑` 形式的資源識別碼。沒有命名空間時為 `minecraft`。
 *
 * @param id 資源識別碼字串。
 * @return 資源識別碼。
 * @throws net.minecraft.ResourceLocationException 字串不是合法的資源識別碼。
 */
fun resourceId(id: String): ResourceLocation = ResourceLocation(id)
