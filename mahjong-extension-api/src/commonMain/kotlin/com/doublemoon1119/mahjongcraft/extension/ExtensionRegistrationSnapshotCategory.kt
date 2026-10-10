package com.doublemoon1119.mahjongcraft.extension

/** 單一診斷類別在某一時間點的不可變 registration key 集合。 */
data class ExtensionRegistrationSnapshotCategory(
    val id: String,
    val displayName: String,
    val registrationKeys: Set<String>,
)
