package com.doublemoon1119.mahjongcraft.flow.common.game.model

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlin.uuid.Uuid

/**
 * 玩家想執行的一次遊戲操作，供 `:mahjong-flow-server` 的 `GameActionRouter` 分派到對應的
 * Game Use Case。
 *
 * 放在 `:mahjong-flow-common`（而非只有 `GameActionRouter` 所在的 `:mahjong-flow-server`）——
 * 這是一個雙方都要建構/認識的共用字彙：除了 `GameActionRouter` 會消費它，`:mahjong-ai` 的
 * AI 策略也需要建構它作為決策的最終輸出，且不該為此依賴整個 `:mahjong-flow-server` 的 use case 層。
 *
 * 規則專屬操作以 [Extension] 包裝強型別命令；核心不窮舉立直、拔北或特定途中流局。
 */
sealed interface GameCommand {
    /** 規則 extension 提供的強型別命令。 */
    data class Extension(val value: ExtensionGameCommand) : GameCommand

    /** 摸牌命令。 */
    data object Draw : GameCommand

    /** 捨棄指定牌的命令。 */
    data class Discard(val tileId: Uuid) : GameCommand

    /** 自摸命令。 */
    data object Tsumo : GameCommand

    /** 槓指定牌的命令。 */
    data class Kan(val type: GameAction.KanType, val tileId: Uuid) : GameCommand

    /** 回應捨牌反應窗口的命令。 */
    data class RespondToDiscard(val action: GameAction) : GameCommand

    /** 回應槓牌反應窗口的規則中立命令。 */
    data class RespondToKan(val action: GameAction) : GameCommand

    /** 以具體原因宣告途中流局的命令。 */
    data class DeclareExhaustiveDraw(val reason: ExhaustiveDrawReason) : GameCommand
}

/** 規則 extension 可提供的強型別遊戲命令。 */
interface ExtensionGameCommand
