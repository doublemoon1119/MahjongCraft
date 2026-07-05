package com.doublemoon1119.mahjongcraft.logic.base

import java.util.*

/**
 * 定義麻將遊戲中玩家可以執行的合法動作。
 *
 * 這些動作可以是主動的（如摸牌、捨牌），也可以是被動的（如鳴牌、胡牌）。
 */
sealed class GameAction {
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
    data class Discard(val tileId: UUID) : GameAction()

    /**
     * 吃牌動作。
     * 玩家使用手牌中的兩張牌與他人的捨牌組成順子。
     * @property tileId 欲吃的他家捨牌的唯一識別碼。
     * @property withTiles 玩家手牌中用於組成順子的兩張牌的唯一識別碼。
     */
    data class Chi(val tileId: UUID, val withTiles: List<UUID>) : GameAction()

    /**
     * 碰牌動作。
     * 玩家使用手牌中的兩張牌與他人的捨牌組成刻子。
     * @property tileId 欲碰的他家捨牌的唯一識別碼。
     */
    data class Pon(val tileId: UUID) : GameAction()

    /**
     * 槓牌動作。
     * 包含明槓、暗槓、加槓。
     * @property type 槓牌的種類。
     * @property tileId 觸發槓牌的牌的唯一識別碼。
     * @property withTiles 玩家手牌中用於組成槓子的牌的唯一識別碼（不包含觸發牌）。
     */
    data class Kan(val type: KanType, val tileId: UUID, val withTiles: List<UUID>) : GameAction()

    /**
     * 胡牌動作（榮和）。
     * 玩家胡他人的捨牌。
     * @property tileId 欲胡的他家捨牌的唯一識別碼。
     */
    data class Ron(val tileId: UUID) : GameAction()

    /**
     * 胡牌動作（自摸）。
     * 玩家摸到一張牌後胡牌。
     */
    data object Tsumo : GameAction()

    /**
     * 立直動作。
     * 玩家宣告立直。
     */
    data object Riichi : GameAction()

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
        OPEN_KAN,       // 明槓 (大明槓)
        CLOSED_KAN,     // 暗槓
        ADDED_KAN       // 加槓 (小明槓)
    }
}
