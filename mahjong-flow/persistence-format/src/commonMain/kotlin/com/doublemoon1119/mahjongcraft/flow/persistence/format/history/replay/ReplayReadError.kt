package com.doublemoon1119.mahjongcraft.flow.persistence.format.history.replay

/** Replay 讀取作業失敗時可回報的錯誤種類。 */
enum class ReplayReadError {
    /** 文件內容不符合格式契約。 */
    INVALID_DOCUMENT,

    /** 文件使用目前讀取器不支援的內容。 */
    UNSUPPORTED_CONTENT,

    /** 文件超出讀取資源上限。 */
    LIMIT_EXCEEDED,

    /** 請求的選取項目不存在。 */
    SELECTION_NOT_FOUND,
}
