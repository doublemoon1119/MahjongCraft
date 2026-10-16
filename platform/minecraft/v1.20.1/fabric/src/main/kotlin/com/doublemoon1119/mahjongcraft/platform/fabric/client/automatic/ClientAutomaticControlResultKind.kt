package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlUpdateResultKindDto

/** 一筆自動操作更新請求最後的結果；包含伺服器回覆的結果，以及等不到回覆時由重新同步判斷的結果。 */
enum class ClientAutomaticControlResultKind {
    /** 伺服器接受請求，或重新同步確認請求內容已經生效。 */
    ACCEPTED,

    /** 伺服器判定提交內容無效或不受目前規則支援。 */
    REJECTED,

    /** 請求依據的版本已過期，或重新同步發現狀態已被其他變更取代。 */
    STALE,

    /** 指定對局不存在、已結束或目前無法提供設定。 */
    UNAVAILABLE,

    /** 沒有收到回覆，重新同步確認狀態沒有改變，請求沒有生效，可以重新送出。 */
    NOT_APPLIED,

    /** 沒有收到回覆，重新同步也沒有回應，無法確認請求是否生效。 */
    UNCONFIRMED,
}

/**
 * 對應伺服器回覆的結果種類。
 *
 * @return 對應的客戶端結果種類。
 */
fun AutomaticControlUpdateResultKindDto.toClientResultKind(): ClientAutomaticControlResultKind = when (this) {
    AutomaticControlUpdateResultKindDto.ACCEPTED -> ClientAutomaticControlResultKind.ACCEPTED
    AutomaticControlUpdateResultKindDto.REJECTED -> ClientAutomaticControlResultKind.REJECTED
    AutomaticControlUpdateResultKindDto.STALE -> ClientAutomaticControlResultKind.STALE
    AutomaticControlUpdateResultKindDto.UNAVAILABLE -> ClientAutomaticControlResultKind.UNAVAILABLE
}
