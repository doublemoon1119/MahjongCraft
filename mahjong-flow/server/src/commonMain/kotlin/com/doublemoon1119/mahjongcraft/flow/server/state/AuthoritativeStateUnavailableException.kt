package com.doublemoon1119.mahjongcraft.flow.server.state

/**
 * 目前 session 的已保存資料無法讀取，[AuthoritativeStateStore] 拒絕任何寫入時拋出。
 *
 * 寫入會建立在缺少已保存資料的空狀態上，等原始資料恢復後會與它衝突，因此一律拒絕，而不是當成沒有資料。
 */
class AuthoritativeStateUnavailableException : IllegalStateException("Authoritative state is unavailable for this session")
