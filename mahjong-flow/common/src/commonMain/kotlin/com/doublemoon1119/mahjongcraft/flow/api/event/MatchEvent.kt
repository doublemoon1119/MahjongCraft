package com.doublemoon1119.mahjongcraft.flow.api.event

import kotlin.uuid.Uuid

/**
 * 對局中發生、供第三方訂閱的事件。
 *
 * 事件與其中的資料只由事件系統建立：建構子不公開，建立入口對 Java 不可見；第三方只讀取屬性，不建立也不實作這些
 * 型別。所有集合屬性都是建立時複製、無法修改的快照。之後只以新增唯讀屬性擴充。
 */
sealed interface MatchEvent {
    /** 事件的唯一識別碼，供需要去重的訂閱者使用。 */
    val eventId: Uuid

    /** 場次 UUID，關聯同一場對局的事件；同一場會有多個事件，不是事件的唯一識別碼。 */
    val matchId: Uuid

    /** 場地 UUID；同一場地重新開局時不變。 */
    val venueId: Uuid

    /** 對局規則模組的 namespaced ID。 */
    val ruleModuleId: String
}
