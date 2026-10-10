package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 集中建立玩家公開快照，避免 use case 或 loader adapter 各自挑選規則資料；[aiPlayerIds] 為由 AI 操控的玩家。 */
object MahjongPlayerInfoPresentationFactory {
    fun create(
        tableState: TableState,
        module: MahjongRuleModule<*>,
        aiPlayerIds: Set<Uuid>,
        resolvePlayerName: (MahjongPlayer) -> String?,
    ): MahjongPlayerInfoPresentation = MahjongPlayerInfoPresentation(
        tableId = tableState.id,
        dealerPlayerId = tableState.dealerPlayerId,
        players = tableState.players.mapIndexed { seatIndex, player ->
            MahjongPlayerInfoEntry(
                playerId = player.id,
                playerName = resolvePlayerName(player),
                isAi = player.id in aiPlayerIds,
                seatIndex = seatIndex,
                seatWind = player.seatWind,
                score = player.score,
                indicators = module.getPublicPlayerIndicators(tableState, player),
            )
        },
    )
}
