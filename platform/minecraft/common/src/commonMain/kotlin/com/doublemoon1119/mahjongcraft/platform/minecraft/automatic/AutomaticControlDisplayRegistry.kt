package com.doublemoon1119.mahjongcraft.platform.minecraft.automatic

/** 管理自動操作 Minecraft 顯示資料的凍結式 registry。 */
interface AutomaticControlDisplayRegistry {
    /** 目前已登記 control ID 的穩定快照。 */
    val registrationKeys: Set<String>

    /** registry 是否已凍結。 */
    val isFrozen: Boolean

    /**
     * 登記一項自動操作的顯示資料。
     *
     * @param controlId 自動操作的穩定 ID。
     * @param display 要顯示的名稱、說明與順序。
     */
    fun register(controlId: String, display: AutomaticControlDisplay)

    /**
     * 查詢指定 control ID 的顯示資料；未登記時回傳 `null`。
     *
     * @param controlId 自動操作的穩定 ID。
     * @return 已登記的顯示資料，或未登記時的 null。
     */
    fun find(controlId: String): AutomaticControlDisplay?

    /** 凍結 registry，禁止後續登記。 */
    fun freeze()
}
