package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry

/**
 * 第三方 Minecraft mod 在 MahjongCraft runtime 啟動前登記 Minecraft 專屬整合的共用契約。
 *
 * 與平台無關的 [com.doublemoon1119.mahjongcraft.extension.MahjongExtension] 分開定義，因為
 * [MinecraftTileAssetRegistry] 屬於 Minecraft adapter 概念，不應出現在平台無關的 extension API。
 * 第三方類別若想同時登記規則層與 Minecraft asset key，可以讓同一個類別同時實作兩個介面；loader
 * adapter 會從既有 extension 發現結果中篩選出有實作此介面的部分，不需要額外宣告第二個 entrypoint。
 */
interface MinecraftMahjongExtension {
    /** 第三方 extension 的穩定識別字串，用於診斷註冊錯誤。 */
    val id: String

    /**
     * 登記第三方牌種對應的 Minecraft asset key。
     *
     * 預設不註冊任何映射，使只提供規則層整合的 extension 不必加入空實作。
     */
    fun registerTileAssets(registry: MinecraftTileAssetRegistry) = Unit
}
