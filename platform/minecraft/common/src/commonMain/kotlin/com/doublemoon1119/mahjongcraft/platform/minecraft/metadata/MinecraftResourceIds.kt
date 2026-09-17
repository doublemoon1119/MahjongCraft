package com.doublemoon1119.mahjongcraft.platform.minecraft.metadata

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * Minecraft resource identifier（dimension、sound、texture 等）的格式。
 *
 * 目前支援的 Minecraft 版本中，`Identifier` 的 namespace 與 path 允許字元與 [NamespacedId] 相同，因此直接
 * 沿用；Minecraft 規則與 MahjongCraft 的 ID 規則分歧時，只調整此物件。
 */
object MinecraftResourceIds {
    /**
     * 判斷 [value] 是否為合法的 Minecraft resource identifier。
     *
     * @param value 要檢查的完整 identifier。
     * @return 符合 `namespace:path` 格式時為 `true`。
     */
    fun isValid(value: String): Boolean = NamespacedId.isValid(value)

    /**
     * 要求 [value] 為合法的 Minecraft resource identifier。
     *
     * @param value 要檢查的完整 identifier。
     * @param lazyMessage 不合法時使用的錯誤訊息。
     * @throws IllegalArgumentException [value] 不符合 Minecraft identifier 格式。
     */
    inline fun requireValid(
        value: String,
        lazyMessage: () -> Any,
    ) {
        require(isValid(value), lazyMessage)
    }
}
