package com.doublemoon1119.mahjongcraft.flow.common.observer.model

import com.doublemoon1119.mahjongcraft.flow.common.game.model.HandReadinessSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationSnapshot
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot

/**
 * 一位觀察者目前應該看到的權威狀態內容。
 *
 * 同一個識別碼在同一時間只會是房間或對局其中之一；兩者都不存在時以 `null` 表示，不另設狀態。內容依
 * 觀察者產生，因此同一個識別碼的不同觀察者可能持有不同的實例。
 */
sealed interface ObserverSnapshot {
    /**
     * 等待階段的房間內容。
     *
     * @property room 依觀察者產生的房間快照。
     */
    data class OfRoom(val room: RoomSnapshot) : ObserverSnapshot

    /**
     * 進行中的對局內容。
     *
     * @property game 依觀看政策裁切過的對局快照。
     * @property roundPreparation 只向本人公開的開局準備內容；沒有進行中的準備步驟時為 `null`。
     * @property handReadinessAnalysis 只向該局參與者本人公開的目前手牌分析；沒有聽牌或規則不支援時為 `null`。
     */
    data class OfGame(
        val game: TableStateSnapshot,
        val roundPreparation: RoundPreparationSnapshot?,
        val handReadinessAnalysis: HandReadinessSnapshot? = null,
    ) : ObserverSnapshot
}
