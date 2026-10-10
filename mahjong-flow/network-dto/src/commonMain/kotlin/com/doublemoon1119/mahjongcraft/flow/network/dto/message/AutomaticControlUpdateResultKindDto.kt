package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import kotlinx.serialization.Serializable

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
