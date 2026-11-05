package com.doublemoon1119.mahjongcraft.flow.common.game.service

import com.doublemoon1119.mahjongcraft.logic.table.TableState

/**
 * 一次權威交易中依序發生的一步：這一步之後的桌況，以及這一步發生的事。
 *
 * 牌從哪一區移到哪一區、位置與姿態怎麼改變，都由呈現端比較前後兩步的桌況得出；步驟本身不帶呈現提示。
 * 同一步的變化同時發生，下一步在這一步完成之後才開始。
 *
 * [tableState] 通常是權威桌況或交易途中的中間桌況；等待其他玩家搶和時，也可以是宣告已完成、權威資料尚未套用的
 * 候選桌況，之後的步驟從這份桌況接續。
 *
 * @property tableState 這一步之後的桌況。
 * @property events 這一步發生的事，依發生順序排列；沒有特別事件時為空清單。
 */
data class TableStep(
    val tableState: TableState,
    val events: List<TableStepEvent>,
)
