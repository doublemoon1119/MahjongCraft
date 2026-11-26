package com.doublemoon1119.mahjongcraft.extension

/**
 * 記錄單一診斷類別及某次快照中的 registration key。
 *
 * @property id 診斷類別的穩定識別字串。
 * @property displayName 診斷類別的顯示名稱。
 * @property registrationKeys 此類別在快照中記錄的 registration key。
 */
data class ExtensionRegistrationSnapshotCategory(
    val id: String,
    val displayName: String,
    val registrationKeys: Set<String>,
)
