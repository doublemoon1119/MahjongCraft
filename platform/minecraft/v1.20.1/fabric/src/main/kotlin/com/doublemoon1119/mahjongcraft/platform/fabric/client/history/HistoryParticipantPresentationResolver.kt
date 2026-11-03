package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryParticipantSummaryDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.player.ClientPlayerDisplayNameResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.PlayerPortraitRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.MinecraftHistoryScreenKeys
import net.minecraft.client.gui.DrawContext
import net.minecraft.text.Text
import kotlin.uuid.Uuid

/**
 * 歷史對局參與者的名稱與頭像呈現。
 *
 * @property names 目前與歷史共用的名稱解析器。
 * @property portraits 既有 portrait source 的 GUI 繪製器。
 */
class HistoryParticipantPresentationResolver(
    private val names: ClientPlayerDisplayNameResolver,
    private val portraits: PlayerPortraitRenderer,
) {
    /**
     * 取得參與者顯示名稱；profile 不在快取時使用穩定的未知玩家翻譯文字。
     *
     * @param participant 欲顯示的參與者。
     * @param participants 該場參與者，用於依座位計算 AI 名稱。
     * @param matchId 歷史場次，沒有時不查目前角色。
     * @return 原生玩家名稱、AI 名稱或未知玩家文字。
     */
    fun name(participant: HistoryParticipantSummaryDto, participants: List<HistoryParticipantSummaryDto>, matchId: String? = null): Text {
        val id = parseUuid(participant.playerId) ?: return Text.translatable(UNKNOWN_PLAYER_KEY)
        val aiIds = participants.filter { it.aiStrategyId != null }.sortedBy { it.seatIndex }.mapNotNull { parseUuid(it.playerId) }
        return Text.literal(names.historical(id, matchId?.let(::parseUuid), participant.aiStrategyId != null, aiIds))
    }

    /**
     * 將參與者頭像繪製於 GUI；未知真人使用 Minecraft 原生 default skin，AI 維持既有牌面 fallback。
     *
     * @param participant 欲顯示的參與者。
     * @param context 畫面繪製上下文。
     * @param x 左上角水平座標。
     * @param y 左上角垂直座標。
     * @param size 頭像邊長。
     * @param matchId 歷史場次。
     */
    fun render(participant: HistoryParticipantSummaryDto, context: DrawContext, x: Int, y: Int, size: Int, matchId: String? = null) {
        val parsedId = parseUuid(participant.playerId)
        val id = parsedId ?: FALLBACK_UUID
        val isAi = participant.aiStrategyId != null
        portraits.renderHistoryGui(
            matchId = matchId?.let(::parseUuid) ?: FALLBACK_UUID,
            playerId = id,
            isAi = parsedId != null && isAi,
            context = context,
            x = x,
            y = y,
            size = size,
        )
    }

    /**
     * 安全解析伺服器回傳的參與者識別碼。
     *
     * @param value UUID 字串。
     * @return 有效 UUID；格式錯誤時為 null。
     */
    private fun parseUuid(value: String): Uuid? = runCatching { Uuid.parse(value) }.getOrNull()

    /** 共用的未知參與者呈現值。 */
    private companion object {
        /** 避免無效 UUID 使 GUI portrait 缺席。 */
        val FALLBACK_UUID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000000")

        /** 真人名稱未知時使用的翻譯鍵。 */
        const val UNKNOWN_PLAYER_KEY = MinecraftHistoryScreenKeys.PLAYER_UNKNOWN
    }
}
