package com.doublemoon1119.mahjongcraft.ai

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.uuid.Uuid

/**
 * 擴充動作轉成的一個 AI 命令候選。
 *
 * 命令本身的內容由規則定義，AI 無法解讀；[discardTileId] 與 [declaration] 以規則中立的形式說明執行後的結果，
 * 讓評估局面的策略可以把它當成「宣告後打出某張牌」比較。
 *
 * @property command 可直接送出的命令。
 * @property discardTileId 命令執行時會打出的牌；不打牌的命令為 null。
 * @property declaration 命令宣告的擴充動作；不宣告任何動作時為 null。
 * @property setAsideTileId 命令執行時會移出手牌、之後補一張牌的那張牌（例如三人日麻的拔北）；沒有時為 null。
 */
data class ExtensionCommandCandidate(
    val command: GameCommand,
    val discardTileId: Uuid? = null,
    val declaration: GameAction.Extension? = null,
    val setAsideTileId: Uuid? = null,
)
