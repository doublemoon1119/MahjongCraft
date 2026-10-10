package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

/**
 * 歷史規則專屬公開資訊。
 *
 * @property typeKey 穩定種類識別碼。
 * @property summary 不含私有命令或原始資料的公開摘要；種類未註冊時為 null，表示資訊不可取得。
 */
data class HistoryReplayRuleInformation(val typeKey: String, val summary: String?)
