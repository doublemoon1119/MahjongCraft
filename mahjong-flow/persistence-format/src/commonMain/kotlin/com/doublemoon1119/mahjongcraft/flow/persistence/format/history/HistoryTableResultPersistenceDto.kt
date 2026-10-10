package com.doublemoon1119.mahjongcraft.flow.persistence.format.history

import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TableStatePersistenceDto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 同一筆權威交易唯一的桌況結果。 */
@Serializable
sealed interface HistoryTableResultPersistenceDto {
    /**
     * 可從前一桌況還原的局部變化。
     *
     * @property change 本次交易的結構差異。
     */
    @Serializable
    @SerialName("change")
    /**
     * Change 的 persistence 資料型別。
     *
     * @property change 本次交易的結構差異。
     */
    data class Change(val change: HistoryTableChangePersistenceDto) : HistoryTableResultPersistenceDto

    /**
     * 無法安全表示差異時保存的完整桌況。
     *
     * @property reasonId 採用完整檢查點的穩定原因 ID。
     * @property state 交易提交後的完整桌況。
     */
    @Serializable
    @SerialName("checkpoint")
/**
     * 無法以差異表示桌況時保存的完整檢查點。
     *
     * @property reasonId 建立完整檢查點的原因識別碼。
     * @property state 交易提交後保存的完整狀態。
     */
    data class Checkpoint(val reasonId: String, val state: TableStatePersistenceDto) : HistoryTableResultPersistenceDto
}
