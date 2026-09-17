package com.doublemoon1119.mahjongcraft.flow.common.observer.service

import kotlin.uuid.Uuid

/**
 * 提供目前各個房間或對局的觀察者。
 *
 * 由平台層實作：觀察者的判定屬於平台的可見範圍機制，Flow 層只使用結果決定推送對象，不自行維護
 * 觀察者名單。
 */
interface ObserverAudienceSource {
    /**
     * 取得目前每個識別碼的觀察者。
     *
     * @return 以房間或對局的 Uuid 索引的觀察者集合；沒有任何觀察者的識別碼不會出現在結果中。
     */
    suspend fun observersById(): Map<Uuid, Set<Uuid>>
}
