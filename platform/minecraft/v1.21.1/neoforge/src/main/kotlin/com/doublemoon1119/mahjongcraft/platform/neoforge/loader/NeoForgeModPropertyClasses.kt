package com.doublemoon1119.mahjongcraft.platform.neoforge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModPropertyClasses
import net.neoforged.fml.ModList

/** 從各 mod 的 `neoforge.mods.toml` 讀出以 `modproperties` 宣告的類別，並取得它們的實例。宣告方式見 [ModPropertyClasses]。 */
internal object NeoForgeModPropertyClasses {
    /**
     * 取得所有 mod 以 [property] 宣告的類別實例。
     *
     * @param T 宣告的類別必須實作的型別。
     * @param property `modproperties` 中的屬性名稱。
     * @param type 宣告的類別必須實作的型別。
     * @return 依 NeoForge 的 mod 順序、每個 mod 內依宣告順序排列的實例。
     * @throws IllegalStateException 宣告的值格式錯誤、類別不存在、沒有實作 [type] 或無法建立。
     */
    fun <T : Any> instances(property: String, type: Class<T>): List<T> = ModList.get().mods.flatMap { mod ->
        ModPropertyClasses.classNames(mod.modId, property, mod.modProperties[property]).map { className ->
            ModPropertyClasses.instantiate(mod.modId, className, type)
        }
    }
}
