package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateSnapshot
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.uuid.Uuid

/**
 * 將角色選擇、開局外觀與牌局合成同一份不可變快照。
 *
 * 牌局更新由權威儲存的同步保存接點呼叫，只做記憶體計算，不呼叫角色工廠。
 * 角色切換與牌局提交以同一個原子參考比對，不能覆寫交錯完成的更新。
 *
 * @property registry 已登記且凍結的角色描述。
 * @property changed 資料變更時通知平台需要保存；不可重入權威儲存。
 */
@OptIn(ExperimentalAtomicApi::class)
class AiCharacterStateStore(
    private val registry: AiCharacterRegistry,
    private val changed: () -> Unit,
) {
    /** 目前完整保存封套；新實例尚未載入任何世界。 */
    private val state = AtomicReference(AiCharacterStateSnapshot(AuthoritativeStateSnapshot(), null, Uuid.random(), 0L))

    /** 讀取一次即取得彼此對應的牌局與角色資料。 */
    fun snapshot(): AiCharacterStateSnapshot = state.load()

    /**
     * 從單一封套建立一張桌子的公開同步資料。
     *
     * 呼叫端仍須檢查收件者是否可查看該桌；此方法不提供觀察授權。
     *
     * @param tableId 要查詢的桌子。
     * @return 可傳遞的完整角色資料。
     */
    fun syncSnapshot(tableId: Uuid): AiCharacterSyncSnapshot {
        val current = state.load()
        val metadata = current.characters
        val game = current.authoritative.games[tableId]
        val players = if (metadata == null) {
            emptyMap()
        } else if (game != null) {
            metadata.matches[game.matchId].orEmpty()
        } else {
            metadata.selections[tableId].orEmpty().mapValues { (_, id) -> AiCharacterSnapshot(id, registry.find(id)) }
        }
        return AiCharacterSyncSnapshot(current.sessionId, current.revision, tableId, metadata != null, registry.definitions(), players)
    }

    /**
     * 載入另一個 session；不從目前登記內容重新產生歷史描述。
     *
     * @param authoritative 已解碼的牌局。
     * @param characters 已解碼的角色；null 代表不可用，保留原始保存資料。
     */
    fun restore(authoritative: AuthoritativeStateSnapshot, characters: AiCharacterMetadata?) {
        state.store(AiCharacterStateSnapshot(authoritative, characters, Uuid.random(), 0L))
    }

    /**
     * 擷取同一次提交的牌局與開局外觀，保留歷史仍引用的場次。
     *
     * @param authoritative 新的權威牌局。
     */
    fun update(authoritative: AuthoritativeStateSnapshot) {
        while (true) {
            val before = state.load()
            val metadata = before.characters?.let {
                if (needsReconcile(before.authoritative, authoritative)) reconcile(it, authoritative) else it
            }
            val after = before.copy(authoritative = authoritative, characters = metadata, revision = nextRevision(before))
            if (state.compareAndSet(before, after)) {
                changed()
                return
            }
        }
    }

    /**
     * 讓房主為等待中的 AI 指定已登記的角色。
     *
     * 呼叫端必須在權威交易內執行，讓開局與角色切換使用相同的序列化邊界。
     *
     * @param authoritative 同一權威交易讀到的狀態。
     * @param tableId 房間所在桌子。
     * @param aiPlayerId 該房間的 AI。
     * @param operatorId 發起選擇的玩家。
     * @param characterId 要選擇的完整角色 ID。
     * @return 檢查與套用結果。
     */
    fun select(
        authoritative: AuthoritativeStateSnapshot,
        tableId: Uuid,
        aiPlayerId: Uuid,
        operatorId: Uuid,
        characterId: String,
    ): AiCharacterSelectionResult {
        while (true) {
            val before = state.load()
            val metadata = before.characters ?: return AiCharacterSelectionResult.DATA_UNAVAILABLE
            check(before.authoritative == authoritative) { "Character selection must use the current authoritative transaction" }
            val room = authoritative.rooms[tableId] ?: return AiCharacterSelectionResult.ROOM_NOT_FOUND
            if (room.hostId != operatorId) return AiCharacterSelectionResult.NOT_HOST
            if (aiPlayerId !in room.aiPlayerStrategyKeys) return AiCharacterSelectionResult.NOT_AI_MEMBER
            if (registry.find(characterId) == null) return AiCharacterSelectionResult.UNKNOWN_CHARACTER
            val selected = metadata.selections
            if (selected[tableId]?.get(aiPlayerId) == characterId) return AiCharacterSelectionResult.ACCEPTED
            val updated = AiCharacterMetadata(
                selections = selected + (tableId to (selected[tableId].orEmpty() + (aiPlayerId to characterId))),
                matches = metadata.matches,
            )
            if (state.compareAndSet(before, before.copy(characters = updated, revision = nextRevision(before)))) {
                changed()
                return AiCharacterSelectionResult.ACCEPTED
            }
        }
    }

    /**
     * 只有成員、場次或歷史保留範圍變更時重建角色 map；一般摸打沿用原有角色資料。
     *
     * @param before 前一次牌局。
     * @param after 這次牌局。
     * @return 必須更新角色生命週期時為 true。
     */
    private fun needsReconcile(before: AuthoritativeStateSnapshot, after: AuthoritativeStateSnapshot): Boolean {
        if (before.rooms.keys != after.rooms.keys || before.games.keys != after.games.keys) return true
        if (after.rooms.any { (id, room) -> before.rooms[id]?.aiPlayerStrategyKeys?.keys != room.aiPlayerStrategyKeys.keys }) return true
        if (after.games.any { (id, game) ->
                val previous = before.games[id]
                previous == null || previous.matchId != game.matchId || previous.aiPlayerStrategyKeys.keys != game.aiPlayerStrategyKeys.keys
            }
        ) {
            return true
        }
        val previous = before.historyRecordingState
        val next = after.historyRecordingState
        return previous.nextSequenceByMatchId.keys != next.nextSequenceByMatchId.keys ||
            previous.decisionsByMatchId.keys != next.decisionsByMatchId.keys ||
            previous.terminalByMatchId.keys != next.terminalByMatchId.keys ||
            previous.transfersByMatchId.keys != next.transfersByMatchId.keys ||
            (previous.pendingEvents !== next.pendingEvents && previous.pendingEvents.map { it.matchId }.toSet() != next.pendingEvents.map { it.matchId }.toSet())
    }

    /**
     * 為目前成員補上固定預設，並只為新場次擷取描述。
     *
     * @param metadata 這次更新前的角色資料。
     * @param authoritative 更新後的權威牌局。
     * @return 維持相同歷史來源的角色資料。
     */
    private fun reconcile(metadata: AiCharacterMetadata, authoritative: AuthoritativeStateSnapshot): AiCharacterMetadata {
        val previousSelections = metadata.selections
        val rosters = authoritative.rooms.mapValues { (_, room) -> room.aiPlayerStrategyKeys.keys } +
            authoritative.games.mapValues { (_, game) -> game.aiPlayerStrategyKeys.keys }
        val selections = rosters.mapValues { (tableId, players) ->
            players.associateWith { playerId -> previousSelections[tableId]?.get(playerId) ?: BuiltInAiCharacters.TILE_MASCOT_ID }
        }
        val history = authoritative.historyRecordingState
        val retained = authoritative.games.values.map { it.matchId }.toSet() + history.nextSequenceByMatchId.keys +
            history.pendingEvents.map { it.matchId } + history.decisionsByMatchId.keys + history.terminalByMatchId.keys + history.transfersByMatchId.keys
        val matches = metadata.matches.filterKeys { it in retained }.toMutableMap()
        authoritative.games.values.forEach { game ->
            if (game.matchId !in matches) {
                matches[game.matchId] = game.aiPlayerStrategyKeys.keys.associateWith { playerId ->
                    val id = selections[game.id]?.get(playerId) ?: BuiltInAiCharacters.TILE_MASCOT_ID
                    AiCharacterSnapshot(
                        characterId = id,
                        definition = AiCharacterMatchAppearance.capture(
                            definition = registry.find(id),
                            matchId = game.matchId,
                            playerId = playerId,
                        ),
                    )
                }
            }
        }
        return AiCharacterMetadata(selections, matches)
    }

    /**
     * 取得嚴格遞增的版本，避免溢位後誤收舊同步。
     *
     * @param before 更新前的封套。
     * @return 下一個版本。
     */
    private fun nextRevision(before: AiCharacterStateSnapshot): Long {
        check(before.revision < Long.MAX_VALUE) { "Character state revision exhausted" }
        return before.revision + 1L
    }
}
