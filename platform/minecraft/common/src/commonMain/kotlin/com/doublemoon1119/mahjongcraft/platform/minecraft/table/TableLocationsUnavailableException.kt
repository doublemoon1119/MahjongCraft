package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * 目前 session 的桌子位置資料無法讀取，[TableLocationRegistry] 拒絕登記或移除位置時拋出。
 *
 * 在缺少已保存位置的空索引上登記或移除，等原始資料恢復後會與它衝突，因此一律拒絕。
 */
class TableLocationsUnavailableException : IllegalStateException("Table locations are unavailable for this session")
