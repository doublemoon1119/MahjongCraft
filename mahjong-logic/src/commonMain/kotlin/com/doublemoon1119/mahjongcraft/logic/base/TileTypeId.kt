package com.doublemoon1119.mahjongcraft.logic.base

/**
 * 可擴充麻將牌種類的穩定識別碼。
 *
 * [namespace] 識別提供牌種定義的模組，[path] 識別該模組內的牌種。兩者共同形成可供 network 與
 * persistence 使用的 `namespace:path` 字串，避免不同 extension 的牌種互相碰撞。
 *
 * @property namespace 僅允許小寫英文字母、數字、底線、連字號與句點。
 * @property path 由一個或多個 `/` 分隔的合法小寫片段組成。
 */
data class TileTypeId(
    val namespace: String,
    val path: String,
) {
    init {
        require(NAMESPACE_PATTERN.matches(namespace)) { "Invalid tile type namespace: $namespace" }
        require(PATH_PATTERN.matches(path)) { "Invalid tile type path: $path" }
    }

    /** 回傳穩定的 `namespace:path` 表示。 */
    override fun toString(): String = "$namespace:$path"

    /** 建立與解析 [TileTypeId] 的工廠。 */
    companion object {
        /** namespace 的合法格式。 */
        private val NAMESPACE_PATTERN = Regex("[a-z0-9_.-]+")

        /** path 的合法格式；斜線只能分隔非空片段。 */
        private val PATH_PATTERN = Regex("[a-z0-9_.-]+(?:/[a-z0-9_.-]+)*")

        /**
         * 解析 `namespace:path` 格式的穩定識別字串。
         *
         * @throws IllegalArgumentException 當字串缺少 namespace、path 或包含多餘冒號時拋出。
         */
        fun parse(value: String): TileTypeId {
            val separatorIndex = value.indexOf(':')
            require(separatorIndex > 0 && separatorIndex == value.lastIndexOf(':') && separatorIndex < value.lastIndex) {
                "Tile type ID must use namespace:path format: $value"
            }
            return TileTypeId(
                namespace = value.substring(0, separatorIndex),
                path = value.substring(separatorIndex + 1),
            )
        }
    }
}
