package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

/** 套用一筆 ACK 後，設定入口需要採取的後續動作。 */
data class ClientAutomaticControlDraftCompletion(
    /** ACK 是否接受草稿。 */
    val accepted: Boolean,
    /** 接受後是否應完成先前記住的關閉意圖。 */
    val closeAfterAcceptance: Boolean,
)
