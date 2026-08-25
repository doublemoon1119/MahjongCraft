package com.doublemoon1119.mahjongcraft.metadata

/**
 * MahjongCraft 跨平台共用的專案識別資訊。
 *
 * 實際值由 root Gradle project metadata 單一來源產生；規則、flow 與各平台應引用此物件，不應自行
 * 寫入 MahjongCraft 的 namespace。
 */
object MahjongCraftMetadata {
    /** MahjongCraft 的穩定專案 ID，同時作為內建資料的 namespace。 */
    const val PROJECT_ID: String = GeneratedMahjongCraftMetadata.PROJECT_ID

    /** 玩家可見的專案名稱。 */
    const val PROJECT_DISPLAY_NAME: String = GeneratedMahjongCraftMetadata.PROJECT_DISPLAY_NAME

    /**
     * 將相對路徑組成 MahjongCraft 內建的完整 namespaced ID。
     *
     * @param path 不含 namespace 與冒號的相對識別路徑。
     * @return 使用 [PROJECT_ID] 作為 namespace 的完整識別碼。
     * @throws IllegalArgumentException 若 [path] 為空或已包含 namespace。
     */
    fun id(path: String): String {
        require(path.isNotBlank()) { "ID path must not be blank." }
        require(':' !in path) { "ID path must not contain a namespace: $path" }
        return "$PROJECT_ID:$path"
    }
}
