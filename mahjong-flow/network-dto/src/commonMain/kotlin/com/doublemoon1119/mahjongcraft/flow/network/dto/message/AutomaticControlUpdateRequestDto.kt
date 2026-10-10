package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

/**
 * 客戶端要求替換本人本局自動操作集合的受控傳輸資料。
 *
 * 玩家身分不屬於 payload；伺服器 adapter 必須從已驗證的連線取得。
 *
 *
 * @property requestId 配對非同步處理結果的客戶端請求識別碼。
 * @property gameId 欲更新的對局識別碼。
 * @property expectedRevision 草稿所依據的權威自動操作 revision。
 * @property enabledControlIds 欲啟用的完整控制 ID 集合。
 */
@Serializable
data class AutomaticControlUpdateRequestDto(
    val requestId: String,
    val gameId: String,
    val expectedRevision: Long,
    val enabledControlIds: Set<String>,
)
