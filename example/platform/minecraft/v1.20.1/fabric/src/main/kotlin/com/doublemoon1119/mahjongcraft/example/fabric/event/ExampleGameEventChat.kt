package com.doublemoon1119.mahjongcraft.example.fabric.event

import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleEventMessageKeys
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEndedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchPlayer
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchStanding
import com.doublemoon1119.mahjongcraft.flow.api.event.MatchStartedEvent
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundScoreChange
import com.doublemoon1119.mahjongcraft.flow.api.event.RoundSettledEvent
import com.doublemoon1119.mahjongcraft.platform.fabric.api.event.FabricGameEventContexts
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.GameEventContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MahjongCraftEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MatchEndedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.MatchStartedListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.RoundSettledListener
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.event.TableLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import java.util.UUID
import kotlin.uuid.Uuid

/**
 * 將公開遊戲事件以單行摘要與滑鼠懸停詳情提供給目前在線的管理員。
 *
 * 觀察器只讀取事件欄位，不查詢權威對局或歷史資料；每次回呼都重新驗證事件情境所屬的伺服器。
 *
 * 事件訂閱由範例模組的主初始化流程呼叫一次。沒有權限等級 2 的在線玩家時，不發送任何訊息。
 */
object ExampleGameEventChat {
    /** 此程序是否已完成事件訂閱。 */
    private var registered = false

    /** 註冊三種事件的聊天觀察器；重複呼叫不會重複註冊。 */
    fun register() {
        if (registered) return
        MahjongCraftEvents.MATCH_STARTED.register(MatchStartedListener(::onMatchStarted))
        MahjongCraftEvents.ROUND_SETTLED.register(RoundSettledListener(::onRoundSettled))
        MahjongCraftEvents.MATCH_ENDED.register(MatchEndedListener(::onMatchEnded))
        registered = true
    }

    /** 處理對局開始事件。 */
    private fun onMatchStarted(event: MatchStartedEvent, context: GameEventContext) {
        deliver(context) { server -> startedText(event, context, server) }
    }

    /** 處理一局結算事件。 */
    private fun onRoundSettled(event: RoundSettledEvent, context: GameEventContext) {
        deliver(context) { server -> settledText(event, context, server) }
    }

    /** 處理對局結束事件。 */
    private fun onMatchEnded(event: MatchEndedEvent, context: GameEventContext) {
        deliver(context) { server -> endedText(event, context, server) }
    }

    /** 將訊息送給目前有管理權限的在線玩家。 */
    private fun deliver(context: GameEventContext, message: (MinecraftServer) -> MutableText) {
        val server = try {
            FabricGameEventContexts.server(context)
        } catch (_: IllegalStateException) {
            return
        }
        val recipients = administratorRecipients(server.playerManager.playerList) { it.hasPermissionLevel(2) }
        if (recipients.isEmpty()) return
        val text = message(server)
        recipients.forEach { player -> player.sendMessage(text.copy(), false) }
    }

    /** 建立對局開始事件文字。 */
    private fun startedText(event: MatchStartedEvent, context: GameEventContext, server: MinecraftServer): MutableText {
        val details = eventText(event, context, ExampleEventMessageKeys.MATCH_STARTED).also { text ->
            event.players.forEach { player ->
                text.append(line(startedPlayerValue(player, server)))
            }
        }
        return eventMessage(
            summary = Text.translatable(ExampleEventMessageKeys.MATCH_STARTED_SUMMARY, event.players.size),
            details = details,
        )
    }

    /** 建立一局結算事件文字。 */
    private fun settledText(event: RoundSettledEvent, context: GameEventContext, server: MinecraftServer): MutableText {
        val details = eventText(event, context, ExampleEventMessageKeys.ROUND_SETTLED).also { text ->
            text.append(line(ExampleEventMessageKeys.ROUND_NUMBER, event.roundNumber))
                .append(line(ExampleEventMessageKeys.OUTCOME_ID, event.outcomeId))
                .append(line(ExampleEventMessageKeys.SETTLEMENT_KIND, event.kind.name))
                .append(line(ExampleEventMessageKeys.BENEFICIARIES, sortedPlayerNames(event.beneficiaryPlayerIds, server)))
                .append(line(ExampleEventMessageKeys.RESPONSIBLE_PLAYERS, sortedPlayerNames(event.responsiblePlayerIds, server)))
            event.players.forEach { player -> text.append(line(scoreChangeValue(player, server))) }
        }
        return eventMessage(
            summary = Text.translatable(
                ExampleEventMessageKeys.ROUND_SETTLED_SUMMARY,
                event.roundNumber,
            ),
            details = details,
        )
    }

    /** 建立對局結束事件文字。 */
    private fun endedText(event: MatchEndedEvent, context: GameEventContext, server: MinecraftServer): MutableText {
        val details = eventText(event, context, ExampleEventMessageKeys.MATCH_ENDED).also { text ->
            text.append(line(ExampleEventMessageKeys.COMPLETION, event.completion.name))
                .append(line(ExampleEventMessageKeys.REASON_ID, event.reasonId))
            event.standings.forEach { standing -> text.append(line(standingValue(standing, server))) }
        }
        return eventMessage(
            summary = Text.translatable(ExampleEventMessageKeys.MATCH_ENDED_SUMMARY),
            details = details,
        )
    }

    /**
     * 用 AQUA 色中括號標籤呈現事件摘要，整段都可懸停查看完整欄位。
     *
     * @param summary 要直接顯示在聊天欄的單行摘要。
     * @param details 指向摘要時顯示的完整公開事件欄位。
     * @return 帶中括號、顏色與滑鼠懸停內容的單行摘要。
     */
    internal fun eventMessage(summary: Text, details: Text): MutableText {
        val style = Style.EMPTY.withColor(Formatting.AQUA)
            .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, details))
        return Text.literal("[").setStyle(style)
            .append(summary.copy().setStyle(style))
            .append(Text.literal("]").setStyle(style))
    }

    /** 建立所有事件共用的識別資料文字。 */
    private fun eventText(event: MatchEvent, context: GameEventContext, title: String): MutableText = eventText(event, context.tableLocation, title)

    /**
     * 建立所有事件共用的識別資料文字。
     *
     * @param event 公開遊戲事件。
     * @param tableLocation 事件產生時保存的桌子位置；沒有位置時為 null。
     * @param title 事件標題的翻譯鍵。
     * @return 包含共通事件欄位的聊天文字。
     */
    private fun eventText(event: MatchEvent, tableLocation: TableLocation?, title: String): MutableText = eventText(
        eventId = event.eventId,
        matchId = event.matchId,
        venueId = event.venueId,
        ruleModuleId = event.ruleModuleId,
        tableLocation = tableLocation,
        title = title,
    )

    /**
     * 建立事件標題與共通欄位文字。
     *
     * @param eventId 事件的唯一識別碼。
     * @param matchId 場次識別碼。
     * @param venueId 場地識別碼。
     * @param ruleModuleId 規則模組識別碼。
     * @param tableLocation 事件產生時保存的桌子位置；沒有位置時為 null。
     * @param title 事件標題的翻譯鍵。
     * @return 包含共通事件欄位的聊天文字。
     */
    internal fun eventText(
        eventId: Uuid,
        matchId: Uuid,
        venueId: Uuid,
        ruleModuleId: String,
        tableLocation: TableLocation?,
        title: String,
    ): MutableText = Text.translatable(ExampleEventMessageKeys.TITLE)
        .formatted(Formatting.AQUA)
        .append("\n")
        .append(Text.translatable(title).formatted(Formatting.GOLD))
        .append(line(ExampleEventMessageKeys.EVENT_ID, eventId.toString()))
        .append(line(ExampleEventMessageKeys.MATCH_ID, matchId.toString()))
        .append(line(ExampleEventMessageKeys.VENUE_ID, venueId.toString()))
        .append(line(ExampleEventMessageKeys.RULE_MODULE_ID, ruleModuleId))
        .append(
            line(
                ExampleEventMessageKeys.TABLE_LOCATION,
                locationText(tableLocation),
            ),
        )

    /** 建立一行翻譯文字。 */
    private fun line(key: String, value: Any): MutableText = line(Text.translatable(key, value))

    /** 將已組好的欄位文字加入聊天訊息並換行。 */
    private fun line(value: MutableText): MutableText = Text.literal("\n")
        .append(value.formatted(Formatting.GRAY))

    /**
     * 從目前在線玩家中篩出有管理權限的接收者，沒有符合者時回傳空清單。
     *
     * @param T 玩家資料型別。
     * @param onlinePlayers 當次在線玩家清單。
     * @param hasPermission 判斷玩家是否具有權限等級 2。
     * @return 維持原順序的管理員清單。
     */
    internal fun <T> administratorRecipients(onlinePlayers: List<T>, hasPermission: (T) -> Boolean): List<T> = onlinePlayers.filter(hasPermission)

    /** 建立桌子位置文字；沒有位置時交由翻譯鍵顯示未知。 */
    internal fun locationText(location: TableLocation?): Text = location?.let { Text.literal(formatTableLocation(it)) }
        ?: Text.translatable(ExampleEventMessageKeys.UNKNOWN_LOCATION)

    /** 將桌子位置轉成事件訊息中的單行內容。 */
    private fun formatTableLocation(location: TableLocation): String = "${location.dimensionId} (${location.x}, ${location.y}, ${location.z})"

    /** 建立開始事件中的玩家欄位值。 */
    private fun startedPlayerValue(player: MatchPlayer, server: MinecraftServer): MutableText = playerValue(player.playerId, player.isAi, player.initialSeatIndex, playerName(server, player.playerId))

    /**
     * 建立玩家名稱、UUID、AI 標記與起始座位的同一行文字。
     *
     * @param id 事件提供的玩家 UUID。
     * @param isAi 是否由 AI 操控。
     * @param initialSeatIndex 起始座位。
     * @param name 當前名稱；未提供時顯示 UUID。
     * @return 帶齊四個參數的玩家翻譯文字。
     */
    internal fun playerValue(id: Uuid, isAi: Boolean, initialSeatIndex: Int, name: Text? = null): MutableText = Text.translatable(
        ExampleEventMessageKeys.PLAYER,
        name ?: Text.literal(id.toString()),
        id.toString(),
        isAi.toString(),
        initialSeatIndex,
    )

    /** 建立結算分數欄位值。 */
    private fun scoreChangeValue(change: RoundScoreChange, server: MinecraftServer): MutableText = scoreChangeValue(
        playerId = change.playerId,
        previousScore = change.previousScore,
        currentScore = change.currentScore,
        previousRank = change.previousRank,
        currentRank = change.currentRank,
        name = playerName(server, change.playerId),
    )

    /**
     * 建立結算分數欄位值。
     *
     * @param playerId 玩家 UUID。
     * @param previousScore 結算前分數。
     * @param currentScore 結算後分數。
     * @param previousRank 結算前名次。
     * @param currentRank 結算後名次。
     * @param name 顯示名稱；沒有名稱時使用玩家 UUID。
     * @return 帶有完整結算欄位的翻譯文字。
     */
    internal fun scoreChangeValue(
        playerId: Uuid,
        previousScore: Int,
        currentScore: Int,
        previousRank: Int,
        currentRank: Int,
        name: Text? = null,
    ): MutableText = Text.translatable(
        ExampleEventMessageKeys.SCORE_CHANGE,
        name ?: Text.literal(playerId.toString()),
        playerId.toString(),
        previousScore,
        currentScore,
        previousRank,
        currentRank,
    )

    /** 建立最終排名欄位值。 */
    private fun standingValue(standing: MatchStanding, server: MinecraftServer): MutableText = standingValue(
        playerId = standing.playerId,
        isAi = standing.isAi,
        score = standing.score,
        rank = standing.rank,
        name = playerName(server, standing.playerId),
    )

    /**
     * 建立最終排名欄位值。
     *
     * @param playerId 玩家 UUID。
     * @param isAi 是否由 AI 操控。
     * @param score 最終分數。
     * @param rank 最終名次。
     * @param name 顯示名稱；沒有名稱時使用玩家 UUID。
     * @return 帶有完整排名欄位的翻譯文字。
     */
    internal fun standingValue(
        playerId: Uuid,
        isAi: Boolean,
        score: Int,
        rank: Int,
        name: Text? = null,
    ): MutableText = Text.translatable(
        ExampleEventMessageKeys.STANDING,
        name ?: Text.literal(playerId.toString()),
        playerId.toString(),
        isAi.toString(),
        score,
        rank,
    )

    /** 依 UUID 字串排序玩家識別碼並加入在線名稱。 */
    private fun sortedPlayerNames(ids: Set<Uuid>, server: MinecraftServer): MutableText {
        val names = formatPlayerLabels(ids) { id -> playerName(server, id).string }
        return Text.literal(names.joinToString(", "))
    }

    /** 依 UUID 穩定排序玩家，找不到名稱時由呼叫端提供 UUID 文字。 */
    internal fun formatPlayerLabels(ids: Set<Uuid>, nameFor: (Uuid) -> String): List<String> = ids.sortedBy(Uuid::toString).map { id -> "${nameFor(id)} ($id)" }

    /** 取得在線玩家名稱；離線玩家及 AI 使用 UUID。 */
    private fun playerName(server: MinecraftServer, id: Uuid): Text {
        val player: ServerPlayerEntity? = server.playerManager.getPlayer(UUID.fromString(id.toString()))
        return player?.displayName?.copy() ?: Text.literal(id.toString())
    }
}
