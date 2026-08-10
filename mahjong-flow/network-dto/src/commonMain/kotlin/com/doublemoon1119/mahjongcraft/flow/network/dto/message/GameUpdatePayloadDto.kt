package com.doublemoon1119.mahjongcraft.flow.network.dto.message

import com.doublemoon1119.mahjongcraft.flow.network.dto.command.GameActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.TableStateSnapshotDto
import kotlinx.serialization.Serializable

/**
 * `mahjongcraft:game_update` S2C 頻道的網路信封——把 [com.doublemoon1119.mahjongcraft.logic.base.GameAction]
 * 事件本身跟該次事件觸發後的最新快照包在同一個封包裡送出。之所以合併成一個封包而不是各自獨立，
 * 是因為由呼叫端組出這個 DTO 時，快照本來就已經是這次事件觸發後的最新結果（見
 * `GameEventPublisherImpl` 的說明），合併送出讓客戶端收到的 `(action, snapshot)` 永遠是自洽的一對，
 * 不需要處理兩個獨立封包分別到達的順序/遺漏問題。
 */
@Serializable
data class GameUpdatePayloadDto(
    val gameId: String,
    val actorId: String,
    val action: GameActionDto,
    val snapshot: TableStateSnapshotDto,
)
