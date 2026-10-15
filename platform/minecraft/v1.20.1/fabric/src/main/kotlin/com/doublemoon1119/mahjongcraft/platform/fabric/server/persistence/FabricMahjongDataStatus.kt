package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.platform.fabric.text.bracketedInteractiveLabel
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.MutableText
import net.minecraft.text.Text
import net.minecraft.util.Formatting

/**
 * 記錄目前 server session 中無法讀取的麻將資料，並決定麻將功能是否可用與如何通知玩家。
 *
 * 只有 [MahjongDataSource.AUTHORITATIVE_STATE] 無法讀取時停用麻將功能；其餘來源各自降級，麻將照常可用。
 * 一般玩家在試圖使用麻將功能時從動作列收到簡短提示；管理員與單人世界擁有者進入世界時另收到詳細資訊。
 * session 開始與結束時由 [clear] 清除。
 */
class FabricMahjongDataStatus {
    /** 目前 session 中無法讀取的資料，依回報順序排列。 */
    @Volatile var problems: List<MahjongDataProblem> = emptyList()
        private set

    /** 麻將功能目前是否可用；房間、對局與待寫牌譜無法讀取時為 false。 */
    val isMahjongAvailable: Boolean
        get() = problems.none { it.source == MahjongDataSource.AUTHORITATIVE_STATE }

    /**
     * 回報一份無法讀取的資料。
     *
     * @param problem 無法讀取的資料與原因。
     */
    fun report(problem: MahjongDataProblem) {
        problems = problems + problem
    }

    /** 清除目前 session 的回報。 */
    fun clear() {
        problems = emptyList()
    }

    /**
     * 麻將功能停用時，從動作列提示玩家並回報應拒絕這次操作。
     *
     * @param player 試圖使用麻將功能的玩家。
     * @return 麻將功能停用、應拒絕操作時為 true。
     */
    fun rejectIfUnavailable(player: ServerPlayerEntity): Boolean {
        if (isMahjongAvailable) return false
        player.sendMessage(Text.translatable(MinecraftMessageKeys.DATA_UNAVAILABLE), true)
        return true
    }

    /**
     * 管理員或單人世界擁有者進入世界時，在聊天欄列出無法讀取的資料、原因與恢復建議。
     *
     * 單人世界未開作弊時擁有者沒有管理權限，因此另外以擁有者身分判斷。
     *
     * @param player 剛進入世界的玩家。
     */
    fun notifyOnJoin(player: ServerPlayerEntity) {
        val current = problems
        if (current.isEmpty()) return
        val isHost = player.server.isHost(player.gameProfile)
        if (!receivesDetailedNotice(player.hasPermissionLevel(DETAILED_NOTICE_PERMISSION_LEVEL), isHost)) return
        dataProblemMessages(current).forEach { player.sendMessage(it, false) }
    }

    private companion object {
        /** 收到詳細資訊的權限等級，與伺服器設定、牌譜管理指令相同。 */
        const val DETAILED_NOTICE_PERMISSION_LEVEL: Int = 2
    }
}

/**
 * 判斷玩家是否收到詳細資訊。
 *
 * @param hasAdminPermission 玩家是否具有管理權限。
 * @param isHost 玩家是否為單人世界的擁有者。
 * @return 管理員或擁有者為 true。
 */
internal fun receivesDetailedNotice(hasAdminPermission: Boolean, isHost: Boolean): Boolean = hasAdminPermission || isHost

/**
 * 依序建立詳細資訊：標題，以及每份資料的影響、檔案、原因與恢復建議。
 *
 * 每行只說明一件事；檔案路徑可點擊複製，錯誤摘要等細節放在指向 `[詳情]` 時顯示，避免長字串使訊息換行。
 * 只有房間、對局與待寫牌譜附上移走檔案的最後手段；其餘來源會自行重建或只影響牌譜。
 *
 * @param problems 無法讀取的資料。
 * @return 由客戶端依語系組字的訊息行。
 */
internal fun dataProblemMessages(problems: List<MahjongDataProblem>): List<Text> = buildList {
    add(Text.translatable(MinecraftMessageKeys.DATA_PROBLEM_HEADER).formatted(Formatting.GOLD))
    problems.forEach { problem ->
        val isAuthoritative = problem.source == MahjongDataSource.AUTHORITATIVE_STATE
        add(Text.translatable(problemKey(problem.source)).formatted(Formatting.YELLOW))
        add(indented(fileLine(problem.file, unchanged = isAuthoritative)))
        add(indented(causeLine(problem.cause)))
        if (isAuthoritative) add(indented(Text.translatable(MinecraftMessageKeys.DATA_RECOVERY_LAST_RESORT).formatted(Formatting.RED)))
    }
}

/**
 * 取得說明哪份資料受影響、目前如何降級的訊息 key。
 *
 * @param source 資料來源。
 * @return 對應的翻譯 key。
 */
private fun problemKey(source: MahjongDataSource): String = when (source) {
    MahjongDataSource.AUTHORITATIVE_STATE -> MinecraftMessageKeys.DATA_PROBLEM_AUTHORITATIVE_STATE
    MahjongDataSource.TABLE_LOCATIONS -> MinecraftMessageKeys.DATA_PROBLEM_TABLE_LOCATIONS
    MahjongDataSource.HISTORY_DATABASE -> MinecraftMessageKeys.DATA_PROBLEM_HISTORY_DATABASE
}

/**
 * 建立檔案路徑行；路徑可點擊複製。
 *
 * @param file 相對於世界資料夾的檔案路徑。
 * @param unchanged 是否註明檔案未被變更。
 * @return 帶可複製路徑的訊息。
 */
private fun fileLine(file: String, unchanged: Boolean): MutableText {
    val path = Text.literal(file).styled { style ->
        style.withColor(Formatting.AQUA)
            .withClickEvent(ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, file))
            .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable(MinecraftMessageKeys.DATA_FILE_COPY_HINT)))
    }
    val key = if (unchanged) MinecraftMessageKeys.DATA_FILE_UNCHANGED else MinecraftMessageKeys.DATA_FILE
    return Text.translatable(key, path).formatted(Formatting.GRAY)
}

/**
 * 建立說明讀取失敗原因的訊息；需要的細節放在 `[詳情]` 的提示中。
 *
 * @param cause 原因分類。
 * @return 原因訊息。
 */
private fun causeLine(cause: MahjongDataLoadCause): MutableText = when (cause) {
    is MahjongDataLoadCause.MissingExtension -> Text.translatable(MinecraftMessageKeys.DATA_CAUSE_MISSING_EXTENSION, cause.namespace)
        .formatted(Formatting.GRAY)
        .append(Text.literal(" "))
        .append(detailLabel(Text.translatable(MinecraftMessageKeys.DATA_CAUSE_MISSING_EXTENSION_DETAIL, cause.typeKey)))
    is MahjongDataLoadCause.NewerFormat -> Text.translatable(
        MinecraftMessageKeys.DATA_CAUSE_NEWER_FORMAT,
        cause.savedVersion,
        cause.supportedVersion,
    ).formatted(Formatting.GRAY)
    is MahjongDataLoadCause.Unreadable -> Text.translatable(MinecraftMessageKeys.DATA_CAUSE_UNREADABLE)
        .formatted(Formatting.GRAY)
        .append(Text.literal(" "))
        .append(
            detailLabel(
                Text.literal(cause.summary)
                    .append(Text.literal("\n"))
                    .append(Text.translatable(MinecraftMessageKeys.DATA_CAUSE_UNREADABLE_DETAIL)),
            ),
        )
}

/**
 * 建立指向後顯示細節的 `[詳情]` 標籤。
 *
 * @param detail 指向時顯示的內容。
 * @return 互動標籤。
 */
private fun detailLabel(detail: Text): MutableText = bracketedInteractiveLabel(Text.translatable(MinecraftMessageKeys.DATA_DETAIL_LABEL), detail)

/**
 * 在訊息前加上縮排，表示它屬於上一行的資料。
 *
 * @param text 要縮排的訊息。
 * @return 帶縮排的訊息。
 */
private fun indented(text: Text): MutableText = Text.literal(DETAIL_INDENT).append(text)

/** 檔案、原因與恢復建議相對於資料標題的縮排。 */
private const val DETAIL_INDENT: String = "  "
