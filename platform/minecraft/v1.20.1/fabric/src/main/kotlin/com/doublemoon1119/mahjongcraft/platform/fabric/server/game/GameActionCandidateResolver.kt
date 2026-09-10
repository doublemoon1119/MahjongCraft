package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContext
import com.doublemoon1119.mahjongcraft.flow.server.game.service.PlayerActionContextResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetLegalActionsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.TileSelectionRequirement
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** `discard` 與動作選牌使用的手牌候選項目。 */
data class HandTileCandidate(val tileId: Uuid, val token: String, val tile: Tile)

/**
 * `action` 指令的合法動作候選項目。
 *
 * @property action 選中後實際要送出的完整動作。
 * @property token 玩家實際輸入的字面值。
 * @property referenceTile 該動作涉及的牌面；不涉及特定牌面的動作為 null。
 * @property tileSelectionRequirement 動作提交前需要額外選取的手牌契約；不需選牌時為 null。
 */
data class GameActionCandidate(
    val action: GameAction,
    val token: String,
    val referenceTile: Tile?,
    val tileSelectionRequirement: TileSelectionRequirement?,
)

/**
 * 依玩家目前的桌況列出 `/mahjongcraft game discard|action` 的候選項目。
 *
 * 候選 token 直接用可讀的牌面／動作簡寫（例如 `5m`／`3p`／`7s`／`east`、`chi`／`pon`／`ron`），不透過
 * tooltip 才看得懂；同一輪候選裡如果有重複（例如手牌兩張同樣的牌、或同時有多種吃法），第一個維持
 * 原本的簡寫，其餘依序加上 `_2`／`_3`……後綴避免碰撞。[Tile.Extension] 沒有簡寫可用，直接用它本身的
 * `namespace:path` 字串當 token。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property membershipRepository 玩家目前所在桌子（房間／對局共用同一個 Uuid）的歸屬查詢。
 * @property getLegalActions 查詢目前合法動作清單的既有 use case。
 * @property moduleRegistry 麻將規則模組註冊中心，供候選動作解析額外選牌契約。
 * @property actionContextResolver 玩家目前操作情境的權威解析器。
 */
@Single
class GameActionCandidateResolver(
    private val gameRepository: GameRepository,
    private val membershipRepository: PlayerMembershipRepository,
    private val getLegalActions: GetLegalActionsUseCase,
    private val moduleRegistry: MahjongModuleRegistry,
    private val actionContextResolver: PlayerActionContextResolver,
) {
    /** 列出玩家目前手牌（含剛摸到的牌）作為捨牌或動作選牌候選。 */
    suspend fun listHandTileCandidates(playerId: Uuid): List<HandTileCandidate> {
        val state = resolveTableState(playerId) ?: return emptyList()
        val player = state.players.firstOrNull { it.id == playerId } ?: return emptyList()
        return disambiguateTokens(player.hand.standingTiles, { it.tile.notationToken() }) { identifiedTile, token ->
            HandTileCandidate(identifiedTile.id, token, identifiedTile.tile)
        }
    }

    /** 列出玩家目前合法的特殊動作及各動作的額外選牌契約。 */
    suspend fun listActionCandidates(playerId: Uuid): List<GameActionCandidate> {
        val gameId = membershipRepository.getTableId(playerId) ?: return emptyList()
        val state = gameRepository.getTableState(gameId) ?: return emptyList()
        val referenceTile = resolveReferenceTile(state, playerId)

        val outcome = getLegalActions(gameId, playerId)
        if (outcome !is Outcome.Success) return emptyList()

        val player = state.players.firstOrNull { it.id == playerId } ?: return emptyList()
        val validator = moduleRegistry.getModule(state.config).createLegalActionValidator()
        val actions = outcome.value
        return disambiguateTokens(actions, GameAction::baseToken) { action, token ->
            GameActionCandidate(
                action = action,
                token = token,
                referenceTile = referenceTile,
                tileSelectionRequirement = validator.tileSelectionRequirement(state, player, action),
            )
        }
    }

    /** 列出指定動作契約允許選取的手牌候選。 */
    suspend fun listTileSelectionCandidates(
        playerId: Uuid,
        candidate: GameActionCandidate,
    ): List<HandTileCandidate> {
        val eligibleTileIds = candidate.tileSelectionRequirement?.eligibleTileIds ?: return emptyList()
        return listHandTileCandidates(playerId).filter { it.tileId in eligibleTileIds }
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
    private fun resolveReferenceTile(state: TableState, playerId: Uuid): Tile? = when (val context = actionContextResolver.resolveFor(state, playerId)) {
        is PlayerActionContext.KanReaction -> context.pending.robbedTile.tile
        is PlayerActionContext.DiscardReaction -> {
            val discarder = state.players.first { it.id == context.pending.discarderId }
            discarder.discardPile.entries.first { it.tile.id == context.pending.tileId }.tile.tile
        }

        is PlayerActionContext.OwnTurn -> state.players.first { it.id == playerId }.hand.lastDrawn?.tile
        null -> null
    }

    /** 以玩家目前的房間歸屬解析目標桌況；不在任何桌子或桌況不是對局時回傳 null。 */
    private suspend fun resolveTableState(playerId: Uuid): TableState? {
        val gameId = membershipRepository.getTableId(playerId) ?: return null
        return gameRepository.getTableState(gameId)
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
    is GameAction.ExhaustiveDraw -> reason.id
    is GameAction.Extension -> value.id
    // Discard／GameStarted／RoundStarted／Draw 不會出現在合法動作清單裡。
    else -> "action"
}
