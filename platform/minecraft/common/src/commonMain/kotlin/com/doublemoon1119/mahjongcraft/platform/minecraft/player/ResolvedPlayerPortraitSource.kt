package com.doublemoon1119.mahjongcraft.platform.minecraft.player

/**
 * 帶有 provider 身分的解析結果，供平台在資源失敗時去重記錄診斷。
 *
 * @property providerId 提供此外觀來源的供應者識別碼。
 * @property source 已解析的玩家外觀來源。
 */
data class ResolvedPlayerPortraitSource(
    val providerId: String,
    val source: PlayerPortraitSource,
)
