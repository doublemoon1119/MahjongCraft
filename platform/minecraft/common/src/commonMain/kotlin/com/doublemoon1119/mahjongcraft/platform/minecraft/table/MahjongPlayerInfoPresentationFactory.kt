package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 集中建立玩家公開快照，避免 use case 或 loader adapter 各自挑選規則資料；[aiPlayerIds] 為由 AI 操控的玩家。 */
object MahjongPlayerInfoPresentationFactory {
    /**
     * 依權威桌況與規則模組建立玩家公開資訊快照。
     *
     * @param tableState 權威桌況。
     * @param module 提供公開玩家標示的規則模組。
     * @param aiPlayerIds 由 AI 操控的玩家 ID。
     * @param resolvePlayerName 解析玩家顯示名稱的函式；無法解析時回傳 null。
     * @return 依固定座位順序排列的公開玩家資訊。
     */
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
