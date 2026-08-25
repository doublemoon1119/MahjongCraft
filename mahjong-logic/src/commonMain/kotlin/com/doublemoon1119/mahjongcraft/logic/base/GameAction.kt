package com.doublemoon1119.mahjongcraft.logic.base

import com.doublemoon1119.mahjongcraft.logic.table.opening.DiceRollResult
import kotlin.uuid.Uuid

/**
 * 定義麻將遊戲中玩家可以執行的合法動作。
 *
 * 這些動作可以是主動的（如摸牌、捨牌），也可以是被動的（如鳴牌、胡牌）。
 */
sealed class GameAction {
    /**
     * 規則模組自訂的遊戲動作。
     *
     * @property value 規則模組提供的強型別動作內容。
     */
    data class Extension(val value: ExtensionGameAction) : GameAction()

    /**
     * 對局開始。
     * 用於開局時對外廣播「本局已開始」事件，不代表任何玩家的主動操作。
     */
    data object GameStarted : GameAction()

    /**
     * 下一局已開始。
     * 用於連莊/過莊後對外廣播「新的一局已開始」事件，不代表任何玩家的主動操作。
     */
    data object RoundStarted : GameAction()

    /**
     * 整場對局已結束。
     * 用於對外廣播「這場對局已依規則的 `GameLength` 結束」事件，不代表任何玩家的主動操作。廣播時
     * 附帶的桌況快照即為最終桌況，可直接從中讀出每位玩家的最終分數。
     */
    data object MatchEnded : GameAction()

    /**
     * 本局開門擲骰已完成。
     * 用於對外廣播「這次擲骰的點數是多少」事件，不代表任何玩家的主動操作；規則不支援開門流程時
     * 不會廣播這個事件。
     * @property dice 本次擲骰的個別點數。
     */
    data class DiceRolled(val dice: DiceRollResult) : GameAction()

    /**
     * 摸牌動作。
     * 通常由系統自動執行，或在特定情況下由玩家觸發。
     */
    data object Draw : GameAction()

    /**
     * 捨牌動作。
     * 玩家從手牌中選擇一張牌打出。
     * @property tileId 欲捨棄牌的唯一識別碼。
     */
    data class Discard(val tileId: Uuid) : GameAction()

    /**
     * 吃牌動作。
     * 玩家使用手牌中的兩張牌與他人的捨牌組成順子。
     * @property tileId 欲吃的他家捨牌的唯一識別碼。
     * @property withTiles 玩家手牌中用於組成順子的兩張牌的唯一識別碼。
     */
    data class Chi(val tileId: Uuid, val withTiles: List<Uuid>) : GameAction()

    /**
     * 碰牌動作。
     * 玩家使用手牌中的兩張牌與他人的捨牌組成刻子。
     * @property tileId 欲碰的他家捨牌的唯一識別碼。
     */
    data class Pon(val tileId: Uuid) : GameAction()

    /**
     * 槓牌動作。
     * 包含明槓、暗槓、加槓。
     * @property type 槓牌的種類。
     * @property tileId 觸發槓牌的牌的唯一識別碼。
     * @property withTiles 玩家手牌中用於組成槓子的牌的唯一識別碼（不包含觸發牌）。
     */
    data class Kan(val type: KanType, val tileId: Uuid, val withTiles: List<Uuid>) : GameAction()

    /**
     * 胡牌動作（榮和）。
     * 玩家胡他人的捨牌。
     * @property tileId 欲胡的他家捨牌的唯一識別碼。
     */
    data class Ron(val tileId: Uuid) : GameAction()

    /**
     * 胡牌動作（自摸）。
     * 玩家摸到一張牌後胡牌。
     */
    data object Tsumo : GameAction()

    /**
     * 過牌動作。
     * 玩家選擇不執行任何鳴牌或胡牌動作。
     */
    data object Pass : GameAction()

    /**
     * 和局動作。
     * 用於處理流局相關的動作（如日麻的九種九牌、四風連打）。
     *
     * @property reason 和局的原因。
     */
    data class ExhaustiveDraw(val reason: ExhaustiveDrawReason) : GameAction()

    /**
     * 槓牌的種類。
     */
    enum class KanType {
        OPEN_KAN, // 明槓 (大明槓)
        CLOSED_KAN, // 暗槓
        ADDED_KAN, // 加槓 (小明槓)
    }
}

/** 規則模組可擴充的強型別遊戲動作。 */
interface ExtensionGameAction {
    /** 跨網路、存檔與呈現層保持穩定的 namespaced ID。 */
    val id: String
}
