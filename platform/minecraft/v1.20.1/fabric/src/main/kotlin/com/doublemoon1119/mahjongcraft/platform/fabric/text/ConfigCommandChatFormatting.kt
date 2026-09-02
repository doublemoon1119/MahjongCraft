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

/** 設定指令 hover 使用的一個本地化欄位與格式化值。 */
data class ConfigPresentationEntry(
    /** 欄位名稱。 */
    val name: Text,
    /** 欄位目前值。 */
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
)

/** 建立 server 設定的本地化欄位。 */
fun serverConfigEntries(config: MinecraftServerConfig): List<ConfigPresentationEntry> = listOf(
    ConfigPresentationEntry(
        Text.translatable(MinecraftConfigCommandKeys.DISCONNECTED_PLAYER_POLICY),
        Text.translatable(config.disconnectedPlayerPolicy.translationKey),
    ),
    ConfigPresentationEntry(
        Text.translatable(MinecraftConfigCommandKeys.DISCONNECTED_PLAYER_TIMEOUT),
        Text.literal(config.disconnectedPlayerTimeoutSeconds.toString()),
    ),
    ConfigPresentationEntry(
        Text.translatable(MinecraftConfigCommandKeys.TABLE_BREAK_POLICY),
        Text.translatable(config.tableBreakPolicy.translationKey),
    ),
    ConfigPresentationEntry(
        Text.translatable(MinecraftConfigCommandKeys.ORPHANED_TABLE_POLICY),
        Text.translatable(config.orphanedTablePolicy.translationKey),
    ),
    ConfigPresentationEntry(
        Text.translatable(MinecraftConfigCommandKeys.TILE_COLLISION),
        clientBooleanText(config.mahjongTilePhysicalCollisionEnabled),
    ),
)

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
