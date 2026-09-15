package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayerSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.PublicPlayerIndicatorTextResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.aiPlayerDisplayName
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import net.minecraft.text.Text
import java.util.Locale
import kotlin.uuid.Uuid

/**
 * 成員卡片內容的組成。
 *
 * 只產生文字與顏色，不接觸繪製、entity 或視窗尺寸。
 *
 * @property indicatorTextResolver 將規則公開 indicator 解析成本地化文字與顏色。
 */
internal class RoomMemberPresentation(private val indicatorTextResolver: PublicPlayerIndicatorTextResolver) {
    /**
     * 卡片名稱下方的資訊列。
     *
     * 第一列是自風，莊家額外加上標記；第二列是分數；其後是規則提供的公開 indicator。
     */
    fun infoRows(player: MahjongPlayerInfoEntry, dealerPlayerId: Uuid?): List<Pair<Text, Int>> = buildList {
        val wind = windText(player.seatWind)
        add(
            if (player.playerId == dealerPlayerId) {
                Text.empty().append(wind).append(DEALER_MARKER) to WIND_COLOR
            } else {
                wind to WIND_COLOR
            },
        )
        add(Text.literal(formatRoomInteger(player.score)) to SCORE_COLOR)
        addAll(player.indicators.map(indicatorTextResolver::resolve))
    }

    internal companion object {
        /** 自風的本地化文字。 */
        fun windText(wind: Wind): Text = Text.translatable(
            when (wind) {
                Wind.EAST -> MinecraftMessageKeys.TILE_HONOR_EAST
                Wind.SOUTH -> MinecraftMessageKeys.TILE_HONOR_SOUTH
                Wind.WEST -> MinecraftMessageKeys.TILE_HONOR_WEST
                Wind.NORTH -> MinecraftMessageKeys.TILE_HONOR_NORTH
            },
        )

        /** 莊家自風後方的標記。 */
        const val DEALER_MARKER: String = "  ●"

        /** 自風列的顏色。 */
        const val WIND_COLOR: Int = 0xFFFFD45A.toInt()

        /** 分數列的顏色。 */
        const val SCORE_COLOR: Int = 0xFFF3F3F3.toInt()
    }
}

/**
 * Player Info entity 尚未同步時，由遊戲快照組出可顯示的玩家清單。
 *
 * AI 以登記順序產生顯示名稱，真人交由 [resolveHumanName] 解析；解析不到時保留 `null`，由呈現層決定
 * 顯示什麼。這份降級資料不含規則公開 indicator。
 */
internal fun roomMemberEntriesFrom(
    players: List<MahjongPlayerSnapshot>,
    resolveHumanName: (Uuid) -> String?,
): List<MahjongPlayerInfoEntry> {
    val orderedAiPlayerIds = players.filter { it.isAi }.map { it.id }
    return players.mapIndexed { index, player ->
        MahjongPlayerInfoEntry(
            playerId = player.id,
            playerName = if (player.isAi) aiPlayerDisplayName(player.id, orderedAiPlayerIds) else resolveHumanName(player.id),
            isAi = player.isAi,
            seatIndex = index,
            seatWind = player.seatWind,
            score = player.score,
            indicators = emptyList(),
        )
    }
}

/**
 * 房間畫面共用的千分位整數格式。
 *
 * 固定使用 [Locale.ROOT]，不隨系統語系改變分隔符號；成員分數與設定 tooltip 的數值範圍都用這一份。
 */
internal fun formatRoomInteger(number: Int): String = String.format(Locale.ROOT, "%,d", number)
