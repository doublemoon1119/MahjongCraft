package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.model.PlayerDecisionOptions
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.GetPlayerDecisionOptionsUseCase
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.DiscardReadinessAnalysis
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
 * @property discardAnalyses 該動作選牌的捨牌分析；不適用時為空。
 */
data class GameActionCandidate(
    val action: GameAction,
    val token: String,
    val referenceTile: Tile?,
    val tileSelectionRequirement: TileSelectionRequirement?,
    val discardAnalyses: List<DiscardReadinessAnalysis>,
)

/**
 * 同一次 Flow 查詢建立的特殊動作候選與一般捨牌分析。
 *
 * @property ruleModuleId 這局採用的規則模組 ID，決定動作用語。
 * @property actions Minecraft 指令與 HUD 共用的特殊動作候選。
 * @property discardAnalyses 自己回合的一般捨牌分析；不適用時為空。
 */
data class ResolvedGameActionCandidates(
    val ruleModuleId: String,
    val actions: List<GameActionCandidate>,
    val discardAnalyses: List<DiscardReadinessAnalysis>,
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
 * @property getPlayerDecisionOptions 查詢完整合法動作、選牌需求與捨牌分析的 Flow use case。
 */
@Single
class GameActionCandidateResolver(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val membershipRepository: PlayerMembershipRepository,
    private val getPlayerDecisionOptions: GetPlayerDecisionOptionsUseCase,
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
    suspend fun listActionCandidates(playerId: Uuid): List<GameActionCandidate> = resolveActionCandidates(playerId)?.actions.orEmpty()

    /** 以同一次 Flow 查詢解析特殊動作候選與一般捨牌分析。 */
    suspend fun resolveActionCandidates(playerId: Uuid): ResolvedGameActionCandidates? {
        val gameId = membershipRepository.getTableId(playerId) ?: return null
        return resolveActionCandidates(gameId, playerId)
    }

    /** 依已知對局解析特殊動作候選與一般捨牌分析，避免再次透過房間歸屬反查對局。 */
    suspend fun resolveActionCandidates(gameId: Uuid, playerId: Uuid): ResolvedGameActionCandidates? {
        val outcome = getPlayerDecisionOptions(gameId, playerId)
        if (outcome !is Outcome.Success) return null
        val state = gameRepository.getTableState(gameId) ?: return null
        return outcome.value.toCandidates(moduleRegistry.getModule(state.config).id)
    }

    /** 將 Flow 的完整決策選項映射成 Minecraft 指令與 HUD 共用的候選資料。 */
    private fun PlayerDecisionOptions.toCandidates(ruleModuleId: String): ResolvedGameActionCandidates {
        val candidates = disambiguateTokens(actions, { it.action.baseToken() }) { option, token ->
            GameActionCandidate(
                action = option.action,
                token = token,
                referenceTile = referenceTile,
                tileSelectionRequirement = option.tileSelectionRequirement,
                discardAnalyses = option.discardAnalyses,
            )
        }
        return ResolvedGameActionCandidates(ruleModuleId, candidates, discardAnalyses)
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
