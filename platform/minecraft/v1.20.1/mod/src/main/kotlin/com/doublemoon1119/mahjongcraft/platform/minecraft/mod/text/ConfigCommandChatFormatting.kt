package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.text

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.DisconnectedPlayerPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftConfigCommandKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MissingAiPlayerPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.OrphanedTablePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.TableBreakPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config.MahjongClientConfigState
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import kotlin.math.roundToInt

/**
 * 建立統一帶有 mod 名稱前綴的 config 指令回饋。
 *
 * @param message 要加上前綴的訊息。
 * @param color 訊息前綴文字顏色。
 * @return 已加上 mod 名稱與顏色的訊息。
 */
fun prefixedConfigMessage(message: Component, color: ChatFormatting): MutableComponent = Component
    .literal("[${MinecraftModMetadata.MOD_NAME}] ")
    .withStyle(ChatFormatting.GOLD)
    .append(message.copy().withStyle(color))

/**
 * 建立 client 設定的本地化欄位，名稱和值與 Client Config Screen 共用。
 *
 * @param config 目前客戶端設定。
 * @return 按畫面順序排列的設定欄位。
 */
fun clientConfigEntries(config: MahjongClientConfigState): List<ConfigPresentationEntry> = listOf(
    ConfigPresentationEntry(
        name = Component.translatable(MinecraftClientConfigScreenKeys.AUTO_SORT_HAND),
        displayedValue = clientBooleanText(config.autoSortHandEnabled),
    ),
    ConfigPresentationEntry(
        name = Component.translatable(MinecraftClientConfigScreenKeys.TILE_LABELS),
        displayedValue = clientBooleanText(config.tileLabelsEnabled),
    ),
    ConfigPresentationEntry(
        name = Component.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_DECISION_PANEL),
        displayedValue = Component.literal("Y ${config.hudLayout.decisionPanelY.asPercent()}%"),
    ),
    ConfigPresentationEntry(
        name = Component.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_COMPACT_PROMPT),
        displayedValue = Component.literal("X ${config.hudLayout.compactPromptX.asPercent()}%, Y ${config.hudLayout.compactPromptY.asPercent()}%"),
    ),
    ConfigPresentationEntry(
        name = Component.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_DISCARD_ANALYSIS),
        displayedValue = Component.literal("Y ${config.hudLayout.discardAnalysisY.asPercent()}%"),
    ),
)

/**
 * 將 HUD 比例轉換為不受系統語系影響的整數百分比。
 *
 * @return 四捨五入後的整數百分比。
 */
private fun Double.asPercent(): Int = (this * 100).roundToInt()

/**
 * 建立 server 設定的平面欄位列表，供共用詳情呈現使用。
 *
 * @param config 目前生效的伺服器設定。
 * @return 按 TOML 區塊順序攤平的設定欄位。
 */
fun serverConfigEntries(config: MinecraftServerConfig): List<ConfigPresentationEntry> = serverConfigSections(config).flatMap { it.entries }

/**
 * 依 TOML 的區塊順序建立 server 設定分類。
 *
 * @param config 目前生效的伺服器設定。
 * @return 按設定檔區塊順序排列的分類與欄位。
 */
fun serverConfigSections(config: MinecraftServerConfig): List<ConfigPresentationSection> = listOf(
    ConfigPresentationSection(
        name = Component.translatable(MinecraftConfigCommandKeys.SERVER_CONFIG_SECTION_PLAYER_DISCONNECTION),
        entries = listOf(
            ConfigPresentationEntry(
                name = Component.translatable(MinecraftConfigCommandKeys.DISCONNECTED_PLAYER_POLICY),
                displayedValue = Component.translatable(config.disconnectedPlayerPolicy.translationKey),
            ),
            ConfigPresentationEntry(
                name = Component.translatable(MinecraftConfigCommandKeys.DISCONNECTED_PLAYER_TIMEOUT),
                displayedValue = Component.literal(config.disconnectedPlayerTimeout.inWholeSeconds.toString()),
            ),
        ),
    ),
    ConfigPresentationSection(
        name = Component.translatable(MinecraftConfigCommandKeys.SERVER_CONFIG_SECTION_AI_PLAYER),
        entries = listOf(
            element = ConfigPresentationEntry(
                name = Component.translatable(MinecraftConfigCommandKeys.MISSING_AI_PLAYER_POLICY),
                displayedValue = Component.translatable(config.missingAiPlayerPolicy.translationKey),
            ),
        ),
    ),
    ConfigPresentationSection(
        name = Component.translatable(MinecraftConfigCommandKeys.SERVER_CONFIG_SECTION_TABLE),
        entries = listOf(
            ConfigPresentationEntry(
                name = Component.translatable(MinecraftConfigCommandKeys.TABLE_BREAK_POLICY),
                displayedValue = Component.translatable(config.tableBreakPolicy.translationKey),
            ),
            ConfigPresentationEntry(
                name = Component.translatable(MinecraftConfigCommandKeys.ORPHANED_TABLE_POLICY),
                displayedValue = Component.translatable(config.orphanedTablePolicy.translationKey),
            ),
        ),
    ),
    ConfigPresentationSection(
        name = Component.translatable(MinecraftConfigCommandKeys.SERVER_CONFIG_SECTION_MAHJONG_TILE),
        entries = listOf(
            element = ConfigPresentationEntry(
                name = Component.translatable(MinecraftConfigCommandKeys.TILE_COLLISION),
                displayedValue = clientBooleanText(config.mahjongTilePhysicalCollisionEnabled),
            ),
        ),
    ),
    ConfigPresentationSection(
        name = Component.translatable(MinecraftConfigCommandKeys.SERVER_CONFIG_SECTION_HISTORY),
        entries = listOf(
            ConfigPresentationEntry(
                name = Component.translatable(MinecraftConfigCommandKeys.HISTORY_ENABLED),
                displayedValue = clientBooleanText(config.history.enabled),
            ),
            ConfigPresentationEntry(
                name = Component.translatable(MinecraftConfigCommandKeys.HISTORY_INCLUDE_AI_MATCHES),
                displayedValue = clientBooleanText(config.history.includeAiMatches),
            ),
            ConfigPresentationEntry(
                name = Component.translatable(MinecraftConfigCommandKeys.HISTORY_INCLUDE_INTERRUPTED_MATCHES),
                displayedValue = clientBooleanText(config.history.includeInterruptedMatches),
            ),
            ConfigPresentationEntry(
                name = Component.translatable(MinecraftConfigCommandKeys.HISTORY_QUERY_ENABLED),
                displayedValue = clientBooleanText(config.history.queryEnabled),
            ),
            ConfigPresentationEntry(
                name = Component.translatable(MinecraftConfigCommandKeys.HISTORY_ALLOW_ADMIN_QUERY),
                displayedValue = clientBooleanText(config.history.allowAdminQuery),
            ),
            ConfigPresentationEntry(
                name = Component.translatable(MinecraftConfigCommandKeys.HISTORY_QUERY_MINIMUM_INTERVAL),
                displayedValue = Component.literal("${config.history.queryMinimumInterval.inWholeMilliseconds} ms"),
            ),
            ConfigPresentationEntry(
                name = Component.translatable(MinecraftConfigCommandKeys.HISTORY_QUERY_MAX_OUTSTANDING),
                displayedValue = Component.literal(config.history.queryMaxOutstanding.toString()),
            ),
            ConfigPresentationEntry(
                name = Component.translatable(MinecraftConfigCommandKeys.HISTORY_QUERY_REJECTION_REPLY_INTERVAL),
                displayedValue = Component.literal("${config.history.queryRejectionReplyInterval.inWholeMilliseconds} ms"),
            ),
            ConfigPresentationEntry(
                name = Component.translatable(MinecraftConfigCommandKeys.HISTORY_MAX_MATCHES),
                displayedValue = Component.literal(config.history.maxMatches.toString()),
            ),
            ConfigPresentationEntry(
                name = Component.translatable(MinecraftConfigCommandKeys.HISTORY_RETENTION_DAYS),
                displayedValue = Component.literal(config.history.retention.inWholeDays.toString()),
            ),
            ConfigPresentationEntry(
                name = Component.translatable(MinecraftConfigCommandKeys.HISTORY_MAX_DISK_MIB),
                displayedValue = Component.literal(config.history.maxDiskMiB.toString()),
            ),
        ),
    ),
)

/**
 * 建立單則換行條列的 server 設定訊息，各分類具有獨立懸停內容。
 *
 * @param displayPath 設定檔路徑。
 * @param config 目前生效的伺服器設定。
 * @return 僅首行帶有 mod 前綴的完整訊息。
 */
fun serverConfigShowMessage(displayPath: String, config: MinecraftServerConfig): MutableComponent = prefixedConfigMessage(
    message = Component.translatable(
        MinecraftConfigCommandKeys.CURRENT,
        Component.translatable(MinecraftConfigCommandKeys.SERVER_CONFIG),
        bracketedInteractiveLabel(Component.translatable(MinecraftConfigCommandKeys.FILE_LOCATION), Component.literal(displayPath)),
    ),
    color = ChatFormatting.AQUA,
).also { message ->
    serverConfigSections(config).forEach { section ->
        message.append(Component.literal("\n  • ").withStyle(ChatFormatting.GRAY))
            .append(section.name.copy().withStyle(ChatFormatting.GRAY))
            .append(" ")
            .append(
                bracketedInteractiveLabel(
                    label = Component.translatable(MinecraftConfigCommandKeys.SETTINGS),
                    hoverText = section.name.copy().withStyle(ChatFormatting.GOLD).append(presentationEntryLines(section.entries)),
                ),
            )
    }
}

/**
 * 建立 server／client config reload 失敗的本地化訊息，技術原因只放在 hover。
 *
 * @param configName 設定檔的顯示名稱。
 * @param details 僅供 hover 查看的人類可讀失敗細節。
 * @return 帶錯誤顏色與細節 hover 的回饋訊息。
 */
fun configReloadFailureMessage(configName: Component, details: String): MutableComponent = prefixedConfigMessage(
    message = Component.translatable(
        MinecraftConfigCommandKeys.RELOAD_FAILED,
        configName,
        bracketedInteractiveLabel(
            label = Component.translatable(MinecraftConfigCommandKeys.DETAILS),
            hoverText = Component.literal(details).withStyle(ChatFormatting.RED),
            color = ChatFormatting.RED,
        ),
    ),
    color = ChatFormatting.RED,
)

/**
 * 建立 client 設定欄位保存失敗的本地化訊息。
 *
 * @param settingName 發生保存失敗的設定名稱。
 * @param details 僅供 hover 查看的人類可讀失敗細節。
 * @return 帶錯誤顏色與細節 hover 的回饋訊息。
 */
fun configSaveFailureMessage(settingName: Component, details: String): MutableComponent = prefixedConfigMessage(
    message = Component.translatable(
        MinecraftConfigCommandKeys.SAVE_FAILED,
        settingName,
        bracketedInteractiveLabel(
            label = Component.translatable(MinecraftConfigCommandKeys.DETAILS),
            hoverText = Component.literal(details).withStyle(ChatFormatting.RED),
            color = ChatFormatting.RED,
        ),
    ),
    color = ChatFormatting.RED,
)

/**
 * 建立 server／client config show 的本地化單行訊息。
 *
 * @param configName 設定項或設定檔的顯示名稱。
 * @param displayPath 設定檔路徑。
 * @param entries 顯示於 hover 的設定欄位。
 * @return 帶欄位 hover 的單行訊息。
 */
fun configShowMessage(configName: Component, displayPath: String, entries: List<ConfigPresentationEntry>): MutableComponent = prefixedConfigMessage(
    message = Component.translatable(
        MinecraftConfigCommandKeys.CURRENT,
        configName,
        bracketedInteractiveLabel(
            label = Component.translatable(MinecraftConfigCommandKeys.DETAILS),
            hoverText = configShowHoverText(displayPath, entries),
        ),
    ),
    color = ChatFormatting.AQUA,
)

/**
 * 建立設定指令的本地化 hover 內容。
 *
 * @param displayPath 設定檔路徑。
 * @param entries hover 中按順序呈現的欄位。
 * @return 包含路徑與設定欄位的 hover 文字。
 */
fun configShowHoverText(displayPath: String, entries: List<ConfigPresentationEntry>): MutableComponent = Component
    .translatable(MinecraftConfigCommandKeys.PATH, displayPath)
    .withStyle(ChatFormatting.DARK_GRAY)
    .also { hover ->
        hover.append(presentationEntryLines(entries))
    }

/**
 * 將本地化欄位和值排成共用的懸停詳情列表。
 *
 * @param entries 要依序列出的欄位和值。
 * @return 格式化為多行的 hover 文字。
 */
fun presentationEntryLines(entries: List<ConfigPresentationEntry>): MutableComponent = Component.empty().also { hover ->
    entries.forEach { entry ->
        hover.append("\n").append(entry.name.copy().withStyle(ChatFormatting.GRAY))
            .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
            .append(entry.displayedValue.copy().withStyle(ChatFormatting.GREEN))
    }
}

/**
 * 建立帶中括號、hover 與選用 click event 的互動標籤。
 *
 * @param label 標籤顯示文字。
 * @param hoverText 指向標籤時顯示的內容。
 * @param clickEvent 選用的點擊事件；null 時不設定點擊行為。
 * @param color 標籤文字顏色。
 * @return 已套用樣式、hover 與選用點擊行為的文字。
 */
fun bracketedInteractiveLabel(
    label: Component,
    hoverText: Component,
    clickEvent: ClickEvent? = null,
    color: ChatFormatting = ChatFormatting.AQUA,
): MutableComponent {
    var style = Style.EMPTY.withColor(color).withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))
    if (clickEvent != null) style = style.withClickEvent(clickEvent)
    return Component.literal("[").setStyle(style)
        .append(label.copy().setStyle(style))
        .append(Component.literal("]").setStyle(style))
}

/**
 * Client 設定 Boolean 的共用本地化文字。
 *
 * @param enabled 設定是否啟用。
 * @return 對應啟用或停用狀態的文字。
 */
private fun clientBooleanText(enabled: Boolean): Component = Component.translatable(
    if (enabled) MinecraftClientConfigScreenKeys.ENABLED else MinecraftClientConfigScreenKeys.DISABLED,
)

/** 斷線玩家政策的本地化選項鍵。 */
private val DisconnectedPlayerPolicy.translationKey: String
    get() = when (this) {
        DisconnectedPlayerPolicy.KEEP_SEAT -> MinecraftConfigCommandKeys.KEEP_SEAT
        DisconnectedPlayerPolicy.LEAVE_IMMEDIATELY -> MinecraftConfigCommandKeys.LEAVE_IMMEDIATELY
        DisconnectedPlayerPolicy.LEAVE_AFTER_TIMEOUT -> MinecraftConfigCommandKeys.LEAVE_AFTER_TIMEOUT
    }

/** 麻將桌破壞政策的本地化選項鍵。 */
private val TableBreakPolicy.translationKey: String
    get() = when (this) {
        TableBreakPolicy.DENY_WHILE_OCCUPIED -> MinecraftConfigCommandKeys.DENY_WHILE_OCCUPIED
        TableBreakPolicy.ALLOW_WAITING_ROOM_ONLY -> MinecraftConfigCommandKeys.ALLOW_WAITING_ROOM_ONLY
        TableBreakPolicy.ALLOW_AND_TERMINATE -> MinecraftConfigCommandKeys.ALLOW_AND_TERMINATE
    }

/** 缺失麻將桌政策的本地化選項鍵。 */
private val OrphanedTablePolicy.translationKey: String
    get() = when (this) {
        OrphanedTablePolicy.KEEP_AND_WARN -> MinecraftConfigCommandKeys.KEEP_AND_WARN
        OrphanedTablePolicy.REMOVE_WAITING_ROOM -> MinecraftConfigCommandKeys.REMOVE_WAITING_ROOM
        OrphanedTablePolicy.REMOVE_ALL -> MinecraftConfigCommandKeys.REMOVE_ALL
    }

/** 找不到 AI 玩家時採用政策的本地化選項。 */
private val MissingAiPlayerPolicy.translationKey: String
    get() = when (this) {
        MissingAiPlayerPolicy.ABORT_MATCH -> MinecraftConfigCommandKeys.ABORT_MATCH
        MissingAiPlayerPolicy.CONTINUE_MATCH -> MinecraftConfigCommandKeys.CONTINUE_MATCH
    }
