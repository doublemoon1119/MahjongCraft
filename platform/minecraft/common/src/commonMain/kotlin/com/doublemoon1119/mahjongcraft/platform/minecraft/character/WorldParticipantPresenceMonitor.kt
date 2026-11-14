package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomParticipantIdentity
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameAbortHandler
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MinecraftMatchAbortReasonIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MissingAiPlayerPolicy

/**
 * 檢查正在進行的對局，找不到參與遊戲的 AI 玩家時依設定中止對局。
 *
 * 只檢查桌子已載入、且透過擴充模組接入既有實體的 AI 玩家。
 *
 * @property active 發起本輪檢查的伺服器是否仍在運作。
 * @property policy 找不到 AI 玩家時採用的處理方式。
 * @property tableActive 對局的桌子是否已載入且可正常使用。
 * @property available 對應的 AI 玩家是否仍存活、已載入，且位於桌子附近。
 * @property abortHandler 確認對局仍符合本次讀取的狀態後，中止對局並讓玩家返回房間。
 */
class WorldParticipantPresenceMonitor(
    private val active: () -> Boolean,
    private val policy: () -> MissingAiPlayerPolicy,
    private val tableActive: (Game) -> Boolean,
    private val available: (Game, RoomParticipantIdentity) -> Boolean,
    private val abortHandler: GameAbortHandler,
) {
    /**
     * 依序檢查各場對局；找到第一名不在場的 AI 玩家時，中止該場對局。
     * 繼續對局政策、已結束的對局與未載入的桌子會略過。
     *
     * @param games 本輪從伺服器讀取的對局。
     */
    suspend fun reconcile(games: List<Game>) {
        for (game in games) {
            if (!active() || policy() != MissingAiPlayerPolicy.ABORT_MATCH) return
            if (game.isMatchOver) continue
            val participants = game.roomAdmission.memberIdentities.filterValues { it.sourceId == SeatedParticipantSources.WORLD_ENTITY }
            if (participants.isEmpty() || !tableActive(game)) continue
            val missing = participants.entries.firstOrNull { (_, identity) -> !available(game, identity) } ?: continue
            if (!active() || policy() != MissingAiPlayerPolicy.ABORT_MATCH) return
            abortHandler.abort(
                expectedGame = game,
                reasonId = MinecraftMatchAbortReasonIds.AI_PLAYER_MISSING,
                diagnostic = "World participant is unavailable memberId=${missing.key} entityId=${missing.value.subjectId}",
            )
        }
    }
}
