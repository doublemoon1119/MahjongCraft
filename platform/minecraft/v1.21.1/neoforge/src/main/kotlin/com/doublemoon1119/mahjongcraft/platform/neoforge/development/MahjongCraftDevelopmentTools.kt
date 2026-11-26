package com.doublemoon1119.mahjongcraft.platform.neoforge.development

import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.development.MahjongCraftDevelopmentEntrypoint
import com.doublemoon1119.mahjongcraft.platform.neoforge.loader.NeoForgeModPropertyClasses

/** 找出開發專用模組的進入點。正式環境一律沒有。 */
internal object MahjongCraftDevelopmentTools {
    /** 開發專用模組在 `neoforge.mods.toml` 的 `modproperties` 宣告進入點的屬性名稱。 */
    const val PROPERTY: String = "mahjongcraft:development"

    /**
     * 取得開發專用模組的進入點。
     *
     * @param environment 判斷是否為開發環境。
     * @return 開發環境中宣告的進入點。正式環境為空。
     */
    fun discover(environment: MinecraftEnvironment): List<MahjongCraftDevelopmentEntrypoint> = if (environment.isDevelopment) {
        NeoForgeModPropertyClasses.instances(PROPERTY, MahjongCraftDevelopmentEntrypoint::class.java)
    } else {
        emptyList()
    }
}
