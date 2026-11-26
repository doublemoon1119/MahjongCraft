package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import java.lang.reflect.Modifier

/**
 * 讀出 mod 在中繼資料的屬性中宣告的類別，並取得它們的實例。例如 Forge 與 NeoForge 的 `mods.toml` 中 `modproperties` 的值。
 *
 * - 屬性值是一個類別名稱或類別名稱的陣列，例如 `"mahjongcraft:extension" = "com.example.ExampleExtension"`。
 * - Kotlin `object` 取用它的唯一實例，其他類別以無參數建構子建立。
 */
internal object ModPropertyClasses {
    /**
     * 讀出屬性值中的類別名稱。
     *
     * @param modId 宣告的 mod，用於錯誤訊息。
     * @param property 屬性名稱，用於錯誤訊息。
     * @param value 屬性值。沒有宣告時為 null。
     * @return 宣告的類別名稱。沒有宣告時為空。
     * @throws IllegalStateException 屬性值不是字串或字串陣列。
     */
    fun classNames(modId: String, property: String, value: Any?): List<String> = when (value) {
        null -> emptyList()
        is String -> listOf(value.trim())
        is List<*> -> value.map { it as? String ?: error("Mod $modId declares a non-string class name in $property: $it") }.map(String::trim)
        else -> error("Mod $modId declares $property as neither a class name nor a list of class names: $value")
    }

    /**
     * 載入類別並取得實例。
     *
     * @param T 類別必須實作的型別。
     * @param modId 宣告的 mod，用於錯誤訊息。
     * @param className 類別名稱。
     * @param type 類別必須實作的型別。
     * @return Kotlin `object` 的唯一實例，或以無參數建構子建立的新實例。
     * @throws IllegalStateException 類別不存在或沒有實作 [type]。
     */
    fun <T : Any> instantiate(modId: String, className: String, type: Class<T>): T {
        val declared = try {
            Class.forName(className, true, ModPropertyClasses::class.java.classLoader)
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
