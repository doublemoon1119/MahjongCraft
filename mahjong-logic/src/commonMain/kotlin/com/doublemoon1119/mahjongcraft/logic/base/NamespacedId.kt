package com.doublemoon1119.mahjongcraft.logic.base

/**
 * 規則、流程與擴充共用的 `namespace:path` 字串 ID 格式。
 *
 * namespace 僅允許小寫英文字母、數字、底線、連字號與句點；path 另外允許 `/`。兩者皆不可為空，
 * 且整個 ID 只有一個冒號。呼叫端以 [requireValid] 提供符合自身情境的錯誤訊息。
 */
object NamespacedId {
    /** 完整 ID 的合法格式。 */
    private val PATTERN = Regex("[a-z0-9_.-]+:[a-z0-9/._-]+")

    /**
     * 判斷 [value] 是否為合法的 namespaced ID。
     *
     * @param value 要檢查的完整 ID。
     * @return 符合 `namespace:path` 格式時為 `true`。
     */
    fun isValid(value: String): Boolean = PATTERN.matches(value)

    /**
     * 要求 [value] 為合法的 namespaced ID。
     *
     * @param value 要檢查的完整 ID。
     * @param lazyMessage 不合法時使用的錯誤訊息。
     * @throws IllegalArgumentException [value] 不符合 `namespace:path` 格式。
     */
    inline fun requireValid(
        value: String,
        lazyMessage: () -> Any,
    ) {
        require(isValid(value), lazyMessage)
    }
}
