package com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic

/**
 * 套用一筆伺服器回覆後，設定入口需要採取的後續動作。
 *
 * @property accepted 回覆是否接受草稿。
 * @property closeAfterAcceptance 接受後是否應完成先前記住的關閉意圖。
 */
data class ClientAutomaticControlDraftCompletion(
    val accepted: Boolean,
    val closeAfterAcceptance: Boolean,
)
