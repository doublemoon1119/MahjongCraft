package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest

/**
 * 自動測試要執行哪些步驟，由開發執行的系統屬性 [PROPERTY] 決定。
 *
 * 屬性不存在、為空或為 `false` 時不執行自動測試；為 `true` 或 `all` 時執行全部步驟；其他值是以逗號分隔的步驟識別碼，
 * 只執行列出的步驟。
 *
 * @property everything 是否執行全部步驟。
 * @property stepIds 只執行的步驟識別碼；[everything] 為 true 時不使用。
 */
internal data class AutotestSelection(val everything: Boolean, val stepIds: Set<String>) {
    /**
     * 指定步驟是否要執行。
     *
     * @param stepId 步驟識別碼。
     * @return 要執行時為 true。
     */
    fun includes(stepId: String): Boolean = everything || stepId in stepIds

    /** 由系統屬性解析自動測試的選擇。 */
    companion object {
        /** 開發執行以這個系統屬性啟用自動測試。 */
        const val PROPERTY: String = "mahjongcraft.autotest"

        /**
         * 解析屬性值。
         *
         * @param value 系統屬性的值；不存在時為 null。
         * @return 自動測試的選擇；不執行自動測試時為 null。
         */
        fun parse(value: String?): AutotestSelection? {
            val trimmed = value?.trim().orEmpty()
            return when {
                trimmed.isEmpty() || trimmed.equals("false", ignoreCase = true) -> null
                trimmed.equals("true", ignoreCase = true) || trimmed.equals("all", ignoreCase = true) -> AutotestSelection(everything = true, stepIds = emptySet())
                else -> AutotestSelection(everything = false, stepIds = trimmed.split(',').map(String::trim).filter(String::isNotEmpty).toSet())
            }
        }

        /** 目前這次執行的選擇；不執行自動測試時為 null。 */
        val current: AutotestSelection? by lazy { parse(System.getProperty(PROPERTY)) }
    }
}
