package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRoundStateDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.platform.fabric.client.tableprop.ClientTablePropPreviews
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryReplayTableProps
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.ReplayTableNotes
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideScenePlayerInfo
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneRenderGuard
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.guide.scene.GuideSceneRoundInfo
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropTarget
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ArrangedTile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import net.minecraft.network.chat.Component
import kotlin.uuid.Uuid

/**
 * 牌譜回放場景中依牌譜記下的資料畫出的本局資訊、玩家資訊與桌上物件；回放牌桌的基準方塊在原點、朝北。
 *
 * @property session 牌譜瀏覽的共用資料。
 */
internal class HistoryReplayTableDisplays(private val session: HistoryBrowseSession) {
    /** 最近一次讀取的呈現附註；同一份文字不重複解析。 */
    private var notesCache: Pair<String?, ReplayTableNotes?>? = null

    /**
     * 讀取這一步的呈現附註。
     *
     * @param state 牌面。
     * @return 呈現附註；沒有或無法解析時為 null。
     */
    fun notes(state: HistoryRoundStateDto): ReplayTableNotes? {
        val text = state.presentationNotes
        notesCache?.takeIf { it.first == text }?.let { return it.second }
        val notes = ReplayTableNotes.decode(text, session.networkRegistries)
        notesCache = text to notes
        return notes
    }

    /**
     * 這一步桌上的物件，位置與對局中相同；客戶端沒有登記的種類、還沒建立好的物件不畫。
     *
     * @param state 牌面。
     * @param arranged 擺好的牌桌。
     * @return 以原版繪製器畫出的物件。
     */
    fun tableProps(state: HistoryRoundStateDto, arranged: Map<Int, ArrangedTile>): List<GuideSceneEntity> {
        if (!session.replayVisibility.tableProps) return emptyList()
        val placements = notes(state)?.tableProps.orEmpty()
        if (placements.isEmpty()) return emptyList()
        val module = session.ruleModule(state) ?: return emptyList()
        val previews = session.tablePropPreviews
        val targets = HistoryReplayTableProps.targets(
            placements = placements,
            ruleModuleId = module.id,
            playerCount = state.players.size,
            arranged = arranged,
            definitions = previews.definitions,
        )
        return targets.mapNotNull { target ->
            val entity = previews.model(target) ?: return@mapNotNull null
            GuideSceneEntity(
                entity = entity,
                x = target.x,
                y = target.y,
                z = target.z,
                yaw = target.yaw,
                seated = false,
                guard = TablePropGuard(previews, target),
            )
        }
    }

    /**
     * 桌子中央上方的本局資訊。
     *
     * @param state 牌面。
     * @return 本局資訊；設定為不畫或牌譜沒有記下內容時為 null。
     */
    fun roundInfo(state: HistoryRoundStateDto): GuideSceneRoundInfo? {
        if (!session.replayVisibility.roundInfo) return null
        val lines = notes(state)?.roundInfo?.takeIf { it.isNotEmpty() } ?: return null
        val placement = MahjongTileTableLayout.roundInfoDisplayPlacement(0, 0, 0)
        return GuideSceneRoundInfo(lines = lines, x = placement.x, y = placement.y, z = placement.z)
    }

    /**
     * 各家後上方的玩家資訊：分數與風位取自這一步的牌面，公開標記取自牌譜記下的資料；只畫設定為顯示的座位。
     *
     * @param state 牌面。
     * @param hiddenSeat 一律不畫面板的座位，例如鏡頭所在的座位；沒有時為 null。
     * @param name 依初始座位取得玩家名稱。
     * @return 玩家資訊；沒有要畫的座位時為 null。
     */
    fun playerInfo(state: HistoryRoundStateDto, hiddenSeat: Int?, name: (Int) -> Component): GuideScenePlayerInfo? {
        val shownSeats = state.players.indices.filter { index ->
            index != hiddenSeat && session.replayVisibility.playerInfo(state.players[index].initialSeatIndex)
        }.toSet()
        if (shownSeats.isEmpty()) return null
        val summary = session.controller.state.value.summary?.detail?.summary
        val indicators = notes(state)?.playerIndicators.orEmpty()
        val players = state.players.mapIndexed { index, player ->
            val playerId = state.identity.players.firstOrNull { it.initialSeatIndex == player.initialSeatIndex }?.playerId
            val participant = playerId?.let { id -> summary?.participants?.firstOrNull { it.playerId.equals(id, ignoreCase = true) } }
            MahjongPlayerInfoEntry(
                playerId = playerId?.let { runCatching { Uuid.parse(it) }.getOrNull() } ?: Uuid.fromLongs(0L, index + 1L),
                playerName = name(player.initialSeatIndex).string,
                isAi = participant?.aiStrategyId != null,
                seatIndex = index,
                seatWind = player.seatWind.toDomain(),
                score = player.score,
                indicators = indicators[index].orEmpty(),
            )
        }
        return GuideScenePlayerInfo(
            players = players,
            dealerPlayerId = players.getOrNull(state.dealerSeat)?.playerId,
            shownSeats = shownSeats,
            x = TABLE_CENTER,
            y = 0.0,
            z = TABLE_CENTER,
        )
    }

    /**
     * 隔離桌上物件的繪製失敗；失敗後該物件在等待重試期間不畫。
     *
     * @property previews 桌上物件的實體來源。
     * @property target 實體所屬的物件。
     */
    private data class TablePropGuard(
        private val previews: ClientTablePropPreviews,
        private val target: TablePropTarget,
    ) : GuideSceneRenderGuard {
        override fun run(draw: () -> Unit) {
            previews.guard(target, draw)
        }
    }

    /** 回放牌桌的位置。 */
    private companion object {
        /** 桌子基準方塊中心的水平座標。 */
        const val TABLE_CENTER = 0.5
    }
}
