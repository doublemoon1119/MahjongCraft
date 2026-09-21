package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerAutomaticControlSnapshot
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

/**
 * 客戶端要求替換本人本局自動操作集合的受控傳輸資料。
 *
 * 玩家身分不屬於 payload；伺服器 adapter 必須從已驗證的連線取得。
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

/**
 * 只提供給對應玩家本人的本局自動操作權威快照。
 *
 * @property gameId 所屬對局識別碼。
 * @property revision 建立快照時的權威 revision。
 * @property supportedControlIds 目前規則支援的完整控制 ID 集合。
 * @property enabledControlIds 該玩家目前已啟用的控制 ID 集合。
 */
@Serializable
data class AutomaticControlSnapshotDto(
    val gameId: String,
    val revision: Long,
    val supportedControlIds: Set<String>,
    val enabledControlIds: Set<String>,
)

/** 本局自動操作更新請求的權威處理結果種類。 */
@Serializable
enum class AutomaticControlUpdateResultKindDto {
    /** 請求已接受；[AutomaticControlUpdateResultDto.snapshot] 為接受後狀態。 */
    ACCEPTED,

    /** 提交內容無效或不受目前規則支援。 */
    REJECTED,

    /** 提交所依據的 revision 已過期。 */
    STALE,

    /** 指定對局不存在、已結束或目前無法提供設定。 */
    UNAVAILABLE,
}

/**
 * 本局自動操作更新請求的權威回覆。
 *
 * @property requestId 對應 [AutomaticControlUpdateRequestDto.requestId]。
 * @property gameId 對應請求中的對局識別碼。
 * @property result 權威處理結果。
 * @property snapshot 可安全提供時的最新個人權威快照。
 */
@Serializable
data class AutomaticControlUpdateResultDto(
    val requestId: String,
    val gameId: String,
    val result: AutomaticControlUpdateResultKindDto,
    val snapshot: AutomaticControlSnapshotDto? = null,
)

/** 將個人權威狀態轉換成網路快照。 */
fun PlayerAutomaticControlSnapshot.toDto(): AutomaticControlSnapshotDto = AutomaticControlSnapshotDto(
    gameId = gameId.toString(),
    revision = revision,
    supportedControlIds = supportedControlIds,
    enabledControlIds = enabledControlIds,
)

/** 將網路快照還原成個人權威狀態。 */
fun AutomaticControlSnapshotDto.toDomain(): PlayerAutomaticControlSnapshot = PlayerAutomaticControlSnapshot(
    gameId = Uuid.parse(gameId),
    revision = revision,
    supportedControlIds = supportedControlIds,
    enabledControlIds = enabledControlIds,
)
