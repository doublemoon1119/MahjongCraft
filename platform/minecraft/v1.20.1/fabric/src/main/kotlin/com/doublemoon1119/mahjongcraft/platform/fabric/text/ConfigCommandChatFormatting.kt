package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.platform.fabric.client.config.MahjongClientConfigState
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.DisconnectedPlayerPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftConfigCommandKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.OrphanedTablePolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.TableBreakPolicy
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import kotlin.math.roundToInt

/**
 * 設定指令 hover 使用的一個本地化欄位與格式化值。
 *
 * @property name 欄位名稱。
 * @property displayedValue 欄位目前值。
 */
data class ConfigPresentationEntry(
    val name: Text,
    val displayedValue: Text,
)

/** 建立統一帶有 mod 名稱前綴的 config 指令回饋。 */
fun prefixedConfigMessage(message: Text, color: Formatting): MutableText = Text
    .literal("[${MinecraftModMetadata.MOD_NAME}] ")
    .formatted(Formatting.GOLD)
    .append(message.copy().formatted(color))

/** 建立 client 設定的本地化欄位，名稱和值與 Client Config Screen 共用。 */
fun clientConfigEntries(config: MahjongClientConfigState): List<ConfigPresentationEntry> = listOf(
    ConfigPresentationEntry(
        Text.translatable(MinecraftClientConfigScreenKeys.AUTO_SORT_HAND),
        clientBooleanText(config.autoSortHandEnabled),
    ),
    ConfigPresentationEntry(
        Text.translatable(MinecraftClientConfigScreenKeys.TILE_LABELS),
        clientBooleanText(config.tileLabelsEnabled),
    ),
    ConfigPresentationEntry(
        Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_DECISION_PANEL),
        Text.literal("Y ${config.hudLayout.decisionPanelY.asPercent()}%"),
    ),
    ConfigPresentationEntry(
        Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_COMPACT_PROMPT),
        Text.literal("X ${config.hudLayout.compactPromptX.asPercent()}%, Y ${config.hudLayout.compactPromptY.asPercent()}%"),
    ),
    ConfigPresentationEntry(
        Text.translatable(MinecraftClientConfigScreenKeys.HUD_LAYOUT_DISCARD_ANALYSIS),
        Text.literal("Y ${config.hudLayout.discardAnalysisY.asPercent()}%"),
    ),
)

/** 將 HUD 比例轉換為不受系統語系影響的整數百分比。 */
private fun Double.asPercent(): Int = (this * 100).roundToInt()

/**
 * 對應 TOML 區塊的設定分類。
 *
 * @property name 分類的本地化名稱。
 * @property entries 此分類的設定名稱與有效值。
 */
data class ConfigPresentationSection(val name: Text, val entries: List<ConfigPresentationEntry>)

/** 建立 server 設定的平面欄位列表，供共用詳情呈現使用。 */
fun serverConfigEntries(config: MinecraftServerConfig): List<ConfigPresentationEntry> = serverConfigSections(config).flatMap { it.entries }

/** 依 TOML 的區塊順序建立 server 設定分類。 */
fun serverConfigSections(config: MinecraftServerConfig): List<ConfigPresentationSection> = listOf(
    ConfigPresentationSection(
        Text.translatable(MinecraftConfigCommandKeys.SERVER_CONFIG_SECTION_PLAYER_DISCONNECTION),
        listOf(
            ConfigPresentationEntry(
                Text.translatable(MinecraftConfigCommandKeys.DISCONNECTED_PLAYER_POLICY),
                Text.translatable(config.disconnectedPlayerPolicy.translationKey),
            ),
            ConfigPresentationEntry(
                Text.translatable(MinecraftConfigCommandKeys.DISCONNECTED_PLAYER_TIMEOUT),
                Text.literal(config.disconnectedPlayerTimeoutSeconds.toString()),
            ),
        ),
    ),
    ConfigPresentationSection(
        Text.translatable(MinecraftConfigCommandKeys.SERVER_CONFIG_SECTION_TABLE),
        listOf(
            ConfigPresentationEntry(
                Text.translatable(MinecraftConfigCommandKeys.TABLE_BREAK_POLICY),
                Text.translatable(config.tableBreakPolicy.translationKey),
            ),
            ConfigPresentationEntry(
                Text.translatable(MinecraftConfigCommandKeys.ORPHANED_TABLE_POLICY),
                Text.translatable(config.orphanedTablePolicy.translationKey),
            ),
        ),
    ),
    ConfigPresentationSection(
        Text.translatable(MinecraftConfigCommandKeys.SERVER_CONFIG_SECTION_MAHJONG_TILE),
        listOf(
            ConfigPresentationEntry(
                Text.translatable(MinecraftConfigCommandKeys.TILE_COLLISION),
                clientBooleanText(config.mahjongTilePhysicalCollisionEnabled),
            ),
        ),
    ),
    ConfigPresentationSection(
        Text.translatable(MinecraftConfigCommandKeys.SERVER_CONFIG_SECTION_HISTORY),
        listOf(
            ConfigPresentationEntry(
                Text.translatable(MinecraftConfigCommandKeys.HISTORY_ENABLED),
                clientBooleanText(config.history.enabled),
            ),
            ConfigPresentationEntry(
                Text.translatable(MinecraftConfigCommandKeys.HISTORY_INCLUDE_AI_MATCHES),
                clientBooleanText(config.history.includeAiMatches),
            ),
            ConfigPresentationEntry(
                Text.translatable(MinecraftConfigCommandKeys.HISTORY_INCLUDE_INTERRUPTED_MATCHES),
                clientBooleanText(config.history.includeInterruptedMatches),
            ),
            ConfigPresentationEntry(
                Text.translatable(MinecraftConfigCommandKeys.HISTORY_QUERY_ENABLED),
                clientBooleanText(config.history.queryEnabled),
            ),
            ConfigPresentationEntry(
                Text.translatable(MinecraftConfigCommandKeys.HISTORY_ALLOW_ADMIN_QUERY),
                clientBooleanText(config.history.allowAdminQuery),
            ),
            ConfigPresentationEntry(
                Text.translatable(MinecraftConfigCommandKeys.HISTORY_MAX_MATCHES),
                Text.literal(config.history.maxMatches.toString()),
            ),
            ConfigPresentationEntry(
                Text.translatable(MinecraftConfigCommandKeys.HISTORY_RETENTION_DAYS),
                Text.literal(config.history.retentionDays.toString()),
            ),
            ConfigPresentationEntry(
                Text.translatable(MinecraftConfigCommandKeys.HISTORY_MAX_DISK_MIB),
                Text.literal(config.history.maxDiskMiB.toString()),
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
fun serverConfigShowMessage(displayPath: String, config: MinecraftServerConfig): MutableText = prefixedConfigMessage(
    Text.translatable(
        MinecraftConfigCommandKeys.CURRENT,
        Text.translatable(MinecraftConfigCommandKeys.SERVER_CONFIG),
        bracketedInteractiveLabel(Text.translatable(MinecraftConfigCommandKeys.FILE_LOCATION), Text.literal(displayPath)),
    ),
    Formatting.AQUA,
).also { message ->
    serverConfigSections(config).forEach { section ->
        message.append(Text.literal("\n  • ").formatted(Formatting.GRAY))
            .append(section.name.copy().formatted(Formatting.GRAY))
            .append(" ")
            .append(
                bracketedInteractiveLabel(
                    Text.translatable(MinecraftConfigCommandKeys.SETTINGS),
                    section.name.copy().formatted(Formatting.GOLD).append(presentationEntryLines(section.entries)),
                ),
            )
    }
}

/** 建立 server／client config reload 失敗的本地化訊息，技術原因只放在 hover。 */
fun configReloadFailureMessage(configName: Text, details: String): MutableText = prefixedConfigMessage(
    Text.translatable(
        MinecraftConfigCommandKeys.RELOAD_FAILED,
        configName,
        bracketedInteractiveLabel(
            Text.translatable(MinecraftConfigCommandKeys.DETAILS),
            Text.literal(details).formatted(Formatting.RED),
            color = Formatting.RED,
        ),
    ),
    Formatting.RED,
)

/** 建立 client 設定欄位保存失敗的本地化訊息。 */
fun configSaveFailureMessage(settingName: Text, details: String): MutableText = prefixedConfigMessage(
    Text.translatable(
        MinecraftConfigCommandKeys.SAVE_FAILED,
        settingName,
        bracketedInteractiveLabel(
            Text.translatable(MinecraftConfigCommandKeys.DETAILS),
            Text.literal(details).formatted(Formatting.RED),
            color = Formatting.RED,
        ),
    ),
    Formatting.RED,
)

/** 建立 server／client config show 的本地化單行訊息。 */
fun configShowMessage(configName: Text, displayPath: String, entries: List<ConfigPresentationEntry>): MutableText = prefixedConfigMessage(
    Text.translatable(
        MinecraftConfigCommandKeys.CURRENT,
        configName,
        bracketedInteractiveLabel(
            Text.translatable(MinecraftConfigCommandKeys.DETAILS),
            configShowHoverText(displayPath, entries),
        ),
    ),
    Formatting.AQUA,
)

/** 建立設定指令的本地化 hover 內容。 */
fun configShowHoverText(displayPath: String, entries: List<ConfigPresentationEntry>): MutableText = Text
    .translatable(MinecraftConfigCommandKeys.PATH, displayPath)
    .formatted(Formatting.DARK_GRAY)
    .also { hover ->
        hover.append(presentationEntryLines(entries))
    }

/** 將本地化欄位和值排成共用的懸停詳情列表。 */
fun presentationEntryLines(entries: List<ConfigPresentationEntry>): MutableText = Text.empty().also { hover ->
    entries.forEach { entry ->
        hover.append("\n").append(entry.name.copy().formatted(Formatting.GRAY))
            .append(Text.literal(": ").formatted(Formatting.DARK_GRAY))
            .append(entry.displayedValue.copy().formatted(Formatting.GREEN))
    }
}

/** 建立帶中括號、hover 與選用 click event 的互動標籤。 */
fun bracketedInteractiveLabel(
    label: Text,
    hoverText: Text,
    clickEvent: ClickEvent? = null,
    color: Formatting = Formatting.AQUA,
): MutableText {
    var style = Style.EMPTY.withColor(color).withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText))
    if (clickEvent != null) style = style.withClickEvent(clickEvent)
    return Text.literal("[").setStyle(style)
        .append(label.copy().setStyle(style))
        .append(Text.literal("]").setStyle(style))
}

/** Client 設定 Boolean 的共用本地化文字。 */
private fun clientBooleanText(enabled: Boolean): Text = Text.translatable(
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
