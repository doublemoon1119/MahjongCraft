package com.doublemoon1119.mahjongcraft.platform.forge.loader

import net.minecraftforge.fml.ModList
import java.lang.reflect.Modifier

/**
 * 從各 mod 的 `mods.toml` 讀出以 `modproperties` 宣告的類別，並取得它們的實例。
 *
 * 宣告方式與 Fabric 的 entrypoint 相同：屬性名稱代表用途，值是一個類別名稱或類別名稱的陣列，例如
 * `"mahjongcraft:extension" = "com.example.ExampleExtension"`。Kotlin `object` 取用它的唯一實例，其他類別以無參數建構子建立。
 */
internal object ForgeModPropertyClasses {
    /**
     * 取得所有 mod 以 [property] 宣告的類別實例。
     *
     * @param T 宣告的類別必須實作的型別。
     * @param property `modproperties` 中的屬性名稱。
     * @param type 宣告的類別必須實作的型別。
     * @return 依 Forge 的 mod 順序、每個 mod 內依宣告順序排列的實例。
     * @throws IllegalStateException 宣告的值格式錯誤、類別不存在、沒有實作 [type] 或無法建立。
     */
    fun <T : Any> instances(property: String, type: Class<T>): List<T> = ModList.get().mods.flatMap { mod ->
        classNames(mod.modId, property, mod.modProperties[property]).map { className -> instantiate(mod.modId, className, type) }
    }

    /**
     * 讀出屬性值中的類別名稱。
     *
     * @param modId 宣告的 mod，用於錯誤訊息。
     * @param property 屬性名稱，用於錯誤訊息。
     * @param value 屬性值。沒有宣告時為 null。
     * @return 宣告的類別名稱。沒有宣告時為空。
     * @throws IllegalStateException 屬性值不是字串或字串陣列。
     */
    internal fun classNames(modId: String, property: String, value: Any?): List<String> = when (value) {
        null -> emptyList()
        is String -> listOf(value.trim())
        is List<*> -> value.map { it as? String ?: error("Mod $modId declares a non-string class name in $property: $it") }.map(String::trim)
        else -> error("Mod $modId declares $property as neither a class name nor a list of class names: $value")
    }

    /**
     * 載入類別並取得實例。
     *
     * @param modId 宣告的 mod，用於錯誤訊息。
     * @param className 類別名稱。
     * @param type 類別必須實作的型別。
     * @return Kotlin `object` 的唯一實例，或以無參數建構子建立的新實例。
     */
    private fun <T : Any> instantiate(modId: String, className: String, type: Class<T>): T {
        val declared = try {
            Class.forName(className, true, ForgeModPropertyClasses::class.java.classLoader)
        } catch (error: ClassNotFoundException) {
            throw IllegalStateException("Mod $modId declares a missing class $className", error)
        }
        check(type.isAssignableFrom(declared)) { "Class $className declared by mod $modId does not implement ${type.name}" }
        val objectInstance = declared.declaredFields.firstOrNull { it.name == KOTLIN_OBJECT_INSTANCE && Modifier.isStatic(it.modifiers) }
        return type.cast(objectInstance?.get(null) ?: declared.getDeclaredConstructor().newInstance())
    }

    /** Kotlin `object` 存放唯一實例的靜態欄位名稱。 */
    private const val KOTLIN_OBJECT_INSTANCE = "INSTANCE"
}
