package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

/**
 * 載入並接上單次 session 的持久化狀態，成功前不公開 attached 狀態。
 *
 * 失敗只清除本次建立的記憶體與 listener，不刪除或改寫載入的存檔。
 * 取消及致命錯誤也會清理；清理失敗附加在原始失敗上，不取代根因。
 *
 * @param S 載入的持久化狀態型別。
 * @param load 載入原始狀態；失敗不可轉成空狀態。
 * @param initialize 建立衍生索引與 listener。
 * @param cleanupFailedAttach 清除本次失敗留下的記憶體與 listener。
 * @return 完整初始化後才可公開的狀態。
 */
internal inline fun <S> attachPersistenceState(
    load: () -> S,
    initialize: (S) -> Unit,
    cleanupFailedAttach: () -> Unit,
): S {
    try {
        val state = load()
        initialize(state)
        return state
    } catch (failure: Throwable) {
        try {
            cleanupFailedAttach()
        } catch (cleanupFailure: Throwable) {
            if (cleanupFailure !== failure) failure.addSuppressed(cleanupFailure)
        }
        throw failure
    }
}
