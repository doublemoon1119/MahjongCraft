package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

/**
 * 附加開局批次、世代與 stage 的不可恢復 presentation 例外。
 *
 * @property ticket 發生失敗的開局批次；尚未取得批次時為 null。
 * @property stage 發生失敗的呈現階段。
 * @param cause 原始失敗原因。
 */
class OpeningPresentationOperationException(
    val ticket: TableOpeningPresentationOperationTracker.Ticket?,
    val stage: String,
    cause: Throwable,
) : RuntimeException(
    "Opening presentation failed: tableId=${ticket?.tableId}, generation=${ticket?.generation}, stage=$stage",
    cause,
)
