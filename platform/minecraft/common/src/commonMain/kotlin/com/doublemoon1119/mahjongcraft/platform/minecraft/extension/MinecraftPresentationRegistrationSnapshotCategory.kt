package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

/**
 * 保存單一 Minecraft 呈現 registry 的名稱與註冊 key。
 *
 * @property id registry 類別的穩定識別碼。
 * @property displayName registry 類別的診斷名稱。
 * @property registrationKeys 目前已登記的 key。
 */
data class MinecraftPresentationRegistrationSnapshotCategory(
    val id: String,
    val displayName: String,
    val registrationKeys: Set<String>,
)
