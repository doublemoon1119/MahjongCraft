package com.doublemoon1119.mahjongcraft.flow.common.concurrent

/**
 * 建立可由多個執行緒同時讀寫的空 Map。
 *
 * 每次讀取、寫入與移除單一鍵都是原子操作，不需要呼叫端另外加鎖；走訪內容時看到的是某一刻前後的狀態，不會丟出
 * 並行修改例外。不接受 null 鍵或 null 值。
 *
 * @return 新的空 Map。
 */
expect fun <K : Any, V : Any> concurrentMutableMap(): MutableMap<K, V>
