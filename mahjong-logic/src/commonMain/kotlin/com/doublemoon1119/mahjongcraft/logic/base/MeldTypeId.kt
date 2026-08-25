package com.doublemoon1119.mahjongcraft.logic.base

/** 第三方副露種類的穩定 namespaced 識別碼。 */
data class MeldTypeId(val namespace: String, val path: String) {
    init {
        require(NAMESPACE_PATTERN.matches(namespace)) { "Invalid meld type namespace: $namespace" }
        require(PATH_PATTERN.matches(path)) { "Invalid meld type path: $path" }
    }

    /** 回傳穩定的 `namespace:path` 表示。 */
    override fun toString(): String = "$namespace:$path"

    /** 建立與解析 [MeldTypeId] 的工廠。 */
    companion object {
        /** namespace 的合法格式。 */
        private val NAMESPACE_PATTERN = Regex("[a-z0-9_.-]+")

        /** path 的合法格式。 */
        private val PATH_PATTERN = Regex("[a-z0-9_.-]+(?:/[a-z0-9_.-]+)*")

        /** 解析 `namespace:path` 格式的識別碼。 */
        fun parse(value: String): MeldTypeId {
            val separatorIndex = value.indexOf(':')
            require(separatorIndex > 0 && separatorIndex == value.lastIndexOf(':') && separatorIndex < value.lastIndex) {
                "Meld type ID must use namespace:path format: $value"
            }
            return MeldTypeId(value.substring(0, separatorIndex), value.substring(separatorIndex + 1))
        }
    }
}
