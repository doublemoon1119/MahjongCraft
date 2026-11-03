package com.doublemoon1119.mahjongcraft.flow.server.state

/** 權威狀態正在處理其他交易，本次不等待的寫入尚未執行。 */
class AuthoritativeStateBusyException : IllegalStateException("Authoritative state is busy")
