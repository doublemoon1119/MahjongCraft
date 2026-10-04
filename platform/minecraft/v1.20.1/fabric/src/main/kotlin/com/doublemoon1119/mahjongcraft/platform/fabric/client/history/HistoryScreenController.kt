package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.client.concurrency.ClientThreadCoroutineDispatcher
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.MahjongTileFaceRenderer
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.screen.Screen
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 歷史快捷鍵、聊天及指令共用的延後開啟入口。
 *
 * @property transport 歷史連線配對與工作階段版本。
 * @property participants 玩家名稱與頭像解析。
 * @property ruleNames 規則名稱 registry。
 * @property configResolver 將歷史開局設定轉為唯讀欄位的呈現解析器。
 * @property networkRegistries 解碼歷史規則設定的正式網路註冊表。
 * @property moduleRegistry 解析歷史規則的牌面顯示順序。
 * @property actionVocabulary 歷史動作的規則專屬名稱來源。
 * @property exhaustiveDrawReasons 流局原因名稱來源。
 * @property settlementTemplates 結算明細欄位的規則專屬標題來源。
 * @property tileFaces 共用 GUI 牌面 renderer。
 * @property tileAssets 牌種與 Minecraft 素材的映射來源。
 * @property archiveStatus 保存狀態查詢。
 * @property dispatcher 客戶端主執行緒排程。
 */
@Single
class HistoryScreenController internal constructor(
    private val transport: HistoryQueryTransport,
    private val participants: HistoryParticipantPresentationResolver,
    @Provided private val ruleNames: RuleModuleDisplayNameRegistry,
    @Provided private val configResolver: GameConfigPresentationResolver,
    @Provided private val networkRegistries: NetworkDtoRegistries,
    @Provided private val moduleRegistry: MahjongModuleRegistry,
    @Provided private val actionVocabulary: GameActionVocabularyRegistry,
    @Provided private val exhaustiveDrawReasons: ExhaustiveDrawReasonDisplayNameRegistry,
    @Provided private val settlementTemplates: WinSettlementPresentationTemplateRegistry,
    private val tileFaces: MahjongTileFaceRenderer,
    @Provided private val tileAssets: MinecraftTileAssetRegistry,
    private val archiveStatus: HistoryArchiveStatusTransport,
    private val dispatcher: ClientThreadCoroutineDispatcher,
) {
    /** 等待下一個 tick 的單一開啟要求。 */
    private var pending: OpenRequest? = null

    /** 目前唯一的瀏覽 session。 */
    private var active: HistoryBrowseSession? = null

    /** 避免重複註冊事件。 */
    private var registered = false

    /** 在客戶端初始化時註冊一次延後開啟與失效檢查。 */
    fun register() {
        if (registered) return
        registered = true
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
    }

    /** 指令或快捷鍵入口不保留聊天畫面。 */
    fun openFromCommand() = requestOpen(null)

    /**
     * 從結算排名聊天入口開啟歷史列表；無效或缺少對局識別碼時仍開啟一般列表。
     *
     * @param matchId 欲優先查找的對局 UUID；無效值會被忽略。
     */
    fun openFromMatchResult(matchId: String?) {
        val validatedMatchId = matchId?.let { runCatching { Uuid.parse(it).toString() }.getOrNull() }
        active?.let {
            it.openMatchResult(validatedMatchId)
            return
        }
        requestOpen(null, validatedMatchId)
    }

    /**
     * 接受目前連線的一項開啟意圖，不在輸入 callback 內替換 Screen。
     *
     * @param parent 原設定頁，其他入口為 null。
     * @param matchId 已驗證的對局識別碼；一般列表為 null。
     */
    private fun requestOpen(parent: Screen?, matchId: String? = null) {
        val client = MinecraftClient.getInstance()
        val connection = client.networkHandler ?: return
        if (client.world == null || client.player == null || active != null) return
        pending = OpenRequest(parent, connection, transport.sessionRevision.value, matchId)
    }

    /**
     * 下一 tick 才開啟，並拒絕原連線或原設定頁已失效的要求。
     *
     * @param client 客戶端執行環境。
     */
    private fun tick(client: MinecraftClient) {
        active?.tick()
        val request = pending ?: return
        pending = null
        if (!request.isValid(client.networkHandler, transport.sessionRevision.value)) return
        if (client.world == null || client.player == null || active != null) return
        if (request.parent != null && client.currentScreen !== request.parent) return
        val session = HistoryBrowseSession(
            transport,
            archiveStatus,
            participants,
            ruleNames,
            configResolver,
            networkRegistries,
            moduleRegistry,
            actionVocabulary,
            exhaustiveDrawReasons,
            settlementTemplates,
            tileFaces,
            tileAssets,
            dispatcher,
            request.parent,
            request.matchId,
        ) { active = null }
        active = session
        session.open()
    }
}

/**
 * 延後開啟要求的連線身分，避免舊指令在新世界開啟。
 *
 * @property parent 返回畫面。
 * @property connection 執行指令時的網路 handler 身分。
 * @property revision 執行指令時的工作階段版本。
 * @property matchId 聊天入口指定的對局 UUID；一般入口為 null。
 */
internal data class OpenRequest(val parent: Screen?, val connection: Any, val revision: Long, val matchId: String? = null) {
    /**
     * 判斷目前仍是建立要求時的同一連線。
     *
     * @param currentConnection 現在的網路 handler。
     * @param currentRevision 現在的工作階段版本。
     * @return 是否仍可執行此開啟要求。
     */
    fun isValid(currentConnection: Any?, currentRevision: Long): Boolean = connection === currentConnection && revision == currentRevision
}
