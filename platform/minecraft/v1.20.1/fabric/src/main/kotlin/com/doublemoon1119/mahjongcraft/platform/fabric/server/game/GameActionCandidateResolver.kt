package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.GameTurnStatus
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** `discard`／`riichi` 指令的手牌候選項目：`tileId` 用來建構 [GameCommand]，`token` 是玩家實際輸入的字面值，`tile` 供 tooltip 顯示。 */
data class HandTileCandidate(val tileId: Uuid, val token: String, val tile: Tile)

/**
 * 玩家目前該用哪一種 [GameCommand] 信封包裝選定動作，
 * 依 [GetLegalActionsUseCase] 內部同一套優先順序判斷：搶槓 > 回應捨牌 > 自己回合 > 都不是
 * （沒有任何額外動作可做，只能被動等待）。
 */
enum class GamePendingMode {
    KAN_REACTION,
    DISCARD_REACTION,
    OWN_TURN,
    NONE,
    ;

    /** 對應到 [GameTurnStatus] 的粗粒度分類，供 [MinecraftPlayerFeedback.ShowHand] 顯示提示文字使用。 */
    fun toTurnStatus(): GameTurnStatus = when (this) {
        KAN_REACTION, DISCARD_REACTION -> GameTurnStatus.AWAITING_RESPONSE
        OWN_TURN -> GameTurnStatus.OWN_TURN
        NONE -> GameTurnStatus.WAITING
    }
}

/**
 * `action` 指令的合法動作候選項目。
 *
 * @property action 選中後實際要送出的完整動作。
 * @property token 玩家實際輸入的字面值。
 * @property referenceTile 該動作涉及的牌面，供 tooltip／回饋訊息組字用；[GameAction.Tsumo] 等不涉及
 *   特定牌面的動作為 null。
 */
data class GameActionCandidate(val action: GameAction, val token: String, val referenceTile: Tile?)

/**
 * 依玩家目前的桌況列出 `/mahjongcraft game discard|riichi|action` 三個指令的候選項目。
 *
 * 候選 token 直接用可讀的牌面／動作簡寫（例如 `5m`／`3p`／`7s`／`east`、`chi`／`pon`／`ron`），不透過
 * tooltip 才看得懂；同一輪候選裡如果有重複（例如手牌兩張同樣的牌、或同時有多種吃法），第一個維持
 * 原本的簡寫，其餘依序加上 `_2`／`_3`……後綴避免碰撞。[Tile.Extension] 沒有簡寫可用，直接用它本身的
 * `namespace:path` 字串當 token。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property membershipRepository 玩家目前所在桌子（房間／對局共用同一個 Uuid）的歸屬查詢。
 * @property getLegalActions 查詢目前合法動作清單的既有 use case。
 * @property moduleRegistry 麻將規則模組註冊中心，供 [listRiichiTileCandidates] 解析向聽數計算器。
 */
@Single
class GameActionCandidateResolver(
    private val gameRepository: GameRepository,
    private val membershipRepository: PlayerMembershipRepository,
    private val getLegalActions: GetLegalActionsUseCase,
    private val moduleRegistry: MahjongModuleRegistry,
) {
    /** 列出玩家目前手牌（含剛摸到的牌）作為 `discard`／`riichi` 的候選項目。 */
    suspend fun listHandTileCandidates(playerId: Uuid): List<HandTileCandidate> {
        val state = resolveTableState(playerId) ?: return emptyList()
        val player = state.players.firstOrNull { it.id == playerId } ?: return emptyList()
        return disambiguateTokens(player.hand.standingTiles, { it.tile.notationToken() }) { identifiedTile, token ->
            HandTileCandidate(identifiedTile.id, token, identifiedTile.tile)
        }
    }

    /**
     * 列出玩家目前手牌裡「打了這張牌之後仍然聽牌」的候選項目，供 `riichi` 指令使用——跟
     * [listHandTileCandidates] 共用同一份候選來源，只是額外過濾掉打了會失去聽牌的牌。目前這位玩家
     * 完全不可能立直時（未輪到自己回合、已經立直過、非門前清、點數不足等，交給
     * [GetLegalActionsUseCase] 判斷，這裡不重複實作規則專屬的立直前置條件）直接回傳空清單，不需要
     * 逐張再算一次向聽數。
     */
    suspend fun listRiichiTileCandidates(playerId: Uuid): List<HandTileCandidate> {
        val state = resolveTableState(playerId) ?: return emptyList()
        val player = state.players.firstOrNull { it.id == playerId } ?: return emptyList()
        val gameId = membershipRepository.getTableId(playerId) ?: return emptyList()

        val legalActionsOutcome = getLegalActions(gameId, playerId)
        val riichiPossible = legalActionsOutcome is Outcome.Success && RIICHI_GAME_ACTION in legalActionsOutcome.value
        if (!riichiPossible) return emptyList()

        val calculator = moduleRegistry.getModule(state.config).createShantenCalculator()
        return listHandTileCandidates(playerId).filter { candidate ->
            val discardResult = player.hand.discardById(candidate.tileId) ?: return@filter false
            calculator.calculate(Hand(tiles = discardResult.hand.tiles, melds = discardResult.hand.melds)) is ShantenResult.Tenpai
        }
    }

    /**
     * 列出玩家目前合法的特殊動作作為 `action` 的候選項目，過濾掉 [com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_GAME_ACTION]——它不帶
     * tileId，由專屬的 `riichi` 指令另外處理，不透過這裡的候選機制送出。
     */
    suspend fun listActionCandidates(playerId: Uuid): List<GameActionCandidate> {
        val gameId = membershipRepository.getTableId(playerId) ?: return emptyList()
        val state = gameRepository.getTableState(gameId) ?: return emptyList()
        val referenceTile = resolveReferenceTile(state, playerId)

        val outcome = getLegalActions(gameId, playerId)
        if (outcome !is Outcome.Success) return emptyList()

        val actions = outcome.value.filterNot { it == RIICHI_GAME_ACTION }
        return disambiguateTokens(actions, GameAction::baseToken) { action, token ->
            GameActionCandidate(action, token, referenceTile)
        }
    }

    /**
     * 依 [baseToken] 把 [items] 各自組成候選 token：第一次出現該 base token 時直接使用，之後重複出現
     * 依序加上 `_2`／`_3`……避免碰撞。
     */
    private fun <T, R> disambiguateTokens(items: List<T>, baseToken: (T) -> String, build: (T, String) -> R): List<R> {
        val seenCounts = mutableMapOf<String, Int>()
        return items.map { item ->
            val base = baseToken(item)
            val count = (seenCounts[base] ?: 0) + 1
            seenCounts[base] = count
            build(item, if (count == 1) base else "${base}_$count")
        }
    }

    /** 找出該情境下「進來的那張牌」，供候選動作清單共用的 [referenceTile][GameActionCandidate.referenceTile]。 */
    private fun resolveReferenceTile(state: TableState, playerId: Uuid): Tile? = when (resolvePendingMode(state, playerId)) {
        GamePendingMode.KAN_REACTION -> state.pendingKanReaction?.robbedTile?.tile
        GamePendingMode.DISCARD_REACTION -> {
            val pendingReaction = state.pendingReaction
            val discarder = state.players.first { it.id == pendingReaction?.discarderId }
            discarder.discardPile.entries.first { it.tile.id == pendingReaction?.tileId }.tile.tile
        }

        GamePendingMode.OWN_TURN -> state.players.first { it.id == playerId }.hand.lastDrawn?.tile
        GamePendingMode.NONE -> null
    }

    /** 以玩家目前的房間歸屬解析目標桌況；不在任何桌子或桌況不是對局時回傳 null。 */
    private suspend fun resolveTableState(playerId: Uuid): TableState? {
        val gameId = membershipRepository.getTableId(playerId) ?: return null
        return gameRepository.getTableState(gameId)
    }

    companion object {
        /**
         * 依玩家目前搶槓／回應捨牌／自己回合的優先順序判斷目前所處情境。複製自
         * [GetLegalActionsUseCase] 內部的同一套優先順序判斷——那個 use case 只回傳
         * `List<GameAction>`，不會單獨暴露目前是哪種情境，這裡才需要自己再判斷一次；
         * [MahjongTableGameActionService] 決定要把選中的動作包成哪種 [GameCommand] 信封時也需要同一個判斷結果，
         * 因此獨立成 companion 函式供兩處共用，避免各自複製一份。
         */
        fun resolvePendingMode(state: TableState, playerId: Uuid): GamePendingMode {
            val pendingKanReaction = state.pendingKanReaction
            val pendingReaction = state.pendingReaction
            return when {
                pendingKanReaction != null &&
                    playerId in pendingKanReaction.eligiblePlayerIds &&
                    playerId !in pendingKanReaction.responses -> GamePendingMode.KAN_REACTION

                pendingReaction != null &&
                    playerId in pendingReaction.eligiblePlayerIds &&
                    playerId !in pendingReaction.responses -> GamePendingMode.DISCARD_REACTION

                state.currentPlayer.id == playerId &&
                    state.players.first { it.id == playerId }.hand.lastDrawn != null &&
                    pendingKanReaction == null &&
                    pendingReaction == null -> GamePendingMode.OWN_TURN

                else -> GamePendingMode.NONE
            }
        }
    }
}

/** 將牌面轉成可讀、可直接輸入指令的簡寫，例如 `5m`／`3p`／`7s`／`east`。 */
private fun Tile.notationToken(): String = when (this) {
    is Tile.Numeric -> "$value${suit.letter()}"
    Tile.Honor.East -> "east"
    Tile.Honor.South -> "south"
    Tile.Honor.West -> "west"
    Tile.Honor.North -> "north"
    Tile.Honor.Red -> "red"
    Tile.Honor.Green -> "green"
    Tile.Honor.White -> "white"
    is Tile.Extension -> typeId.toString()
}

/** 數牌花色的單字母簡寫：萬 m、筒 p、條 s，沿用麻將圈慣用的 man／pin／sou 記法。 */
private fun Tile.Suit.letter(): String = when (this) {
    Tile.Suit.Character -> "m"
    Tile.Suit.Dot -> "p"
    Tile.Suit.Bamboo -> "s"
}

/** 將動作轉成可讀的簡寫，供 [GameActionCandidateResolver.disambiguateTokens] 組出候選 token。 */
private fun GameAction.baseToken(): String = when (this) {
    GameAction.Tsumo -> "tsumo"
    is GameAction.Chi -> "chi"
    is GameAction.Pon -> "pon"
    is GameAction.Kan -> when (type) {
        GameAction.KanType.OPEN_KAN -> "kan_open"
        GameAction.KanType.CLOSED_KAN -> "kan_closed"
        GameAction.KanType.ADDED_KAN -> "kan_added"
    }

    is GameAction.Ron -> "ron"
    GameAction.Pass -> "pass"
    is GameAction.ExhaustiveDraw -> "kyuushu"
    // Riichi 已被呼叫端過濾；Discard／GameStarted／RoundStarted／Draw 不會出現在合法動作清單裡。
    else -> "action"
}
