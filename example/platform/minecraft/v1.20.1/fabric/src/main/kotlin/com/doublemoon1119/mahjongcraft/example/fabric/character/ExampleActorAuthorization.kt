package com.doublemoon1119.mahjongcraft.example.fabric.character

import kotlin.uuid.Uuid

/** 範例以放置者授權參加其主持的房間，不以名稱辨識實體。 */
internal object ExampleActorAuthorization {
    /**
     * 確認保存的放置者就是本次房主。
     *
     * @param ownerId 人偶放置者；未記錄時為 null。
     * @param hostId 房間房主。
     * @return 只有明確相同的放置者可以接收人偶。
     */
    fun canJoin(ownerId: Uuid?, hostId: Uuid): Boolean = ownerId != null && ownerId == hostId
}
