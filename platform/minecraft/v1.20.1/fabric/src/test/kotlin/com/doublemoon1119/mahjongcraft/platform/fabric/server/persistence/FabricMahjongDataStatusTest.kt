package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import net.minecraft.text.ClickEvent
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import net.minecraft.util.Formatting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** [FabricMahjongDataStatus] 的停用範圍、通知對象與詳細資訊內容測試。 */
class FabricMahjongDataStatusTest {
    /** 只有房間與對局無法讀取時停用麻將功能；位置索引與牌譜資料庫各自降級。 */
    @Test
    fun `only authoritative state disables mahjong`() {
        val status = FabricMahjongDataStatus()
        status.report(problem(MahjongDataSource.TABLE_LOCATIONS))
        status.report(problem(MahjongDataSource.HISTORY_DATABASE))

        assertTrue(status.isMahjongAvailable)
        status.report(problem(MahjongDataSource.AUTHORITATIVE_STATE))
        assertFalse(status.isMahjongAvailable)
        status.clear()
        assertTrue(status.isMahjongAvailable)
        assertTrue(status.problems.isEmpty())
    }

    /** 管理員與單人世界擁有者收到詳細資訊，一般玩家不收。 */
    @Test
    fun `detailed notice goes to administrators and the host`() {
        assertTrue(receivesDetailedNotice(hasAdminPermission = true, isHost = false))
        assertTrue(receivesDetailedNotice(hasAdminPermission = false, isHost = true))
        assertFalse(receivesDetailedNotice(hasAdminPermission = false, isHost = false))
    }

    /** 每份資料依序列出影響、檔案與原因；只有房間與對局附上紅色的最後手段。 */
    @Test
    fun `messages list each problem with its file and cause`() {
        val messages = dataProblemMessages(
            listOf(
                MahjongDataProblem(
                    source = MahjongDataSource.AUTHORITATIVE_STATE,
                    file = "data/state.dat",
                    cause = MahjongDataLoadCause.MissingExtension("example:custom_rule"),
                ),
                MahjongDataProblem(
                    source = MahjongDataSource.TABLE_LOCATIONS,
                    file = "data/locations.dat",
                    cause = MahjongDataLoadCause.NewerFormat(savedVersion = 4, supportedVersion = 2),
                ),
            ),
        )

        assertEquals(
            expected = listOf(
                MinecraftMessageKeys.DATA_PROBLEM_HEADER,
                MinecraftMessageKeys.DATA_PROBLEM_AUTHORITATIVE_STATE,
                MinecraftMessageKeys.DATA_FILE_UNCHANGED,
                MinecraftMessageKeys.DATA_CAUSE_MISSING_EXTENSION,
                MinecraftMessageKeys.DATA_RECOVERY_LAST_RESORT,
                MinecraftMessageKeys.DATA_PROBLEM_TABLE_LOCATIONS,
                MinecraftMessageKeys.DATA_FILE,
                MinecraftMessageKeys.DATA_CAUSE_NEWER_FORMAT,
            ),
            actual = messages.map { translatable(it).key },
        )
        assertEquals(listOf("example"), translatable(messages[3]).args.toList())
        assertEquals(listOf(4, 2), translatable(messages[7]).args.toList())
        assertEquals(Formatting.RED.colorValue, mainText(messages[4]).style.color?.rgb)
    }

    /** 檔案路徑可點擊複製，複製的內容是路徑本身。 */
    @Test
    fun `file path can be copied`() {
        val fileLine = dataProblemMessages(listOf(problem(MahjongDataSource.HISTORY_DATABASE)))[2]

        val path = translatable(fileLine).args.single() as Text
        val click = assertNotNull(path.style.clickEvent)
        assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, click.action)
        assertEquals("data/test.dat", click.value)
    }

    /** 無法判斷原因時，錯誤摘要放在指向 `[詳情]` 時顯示的內容中，不佔用訊息本身。 */
    @Test
    fun `unreadable summary is shown on hover`() {
        val causeLine = mainText(dataProblemMessages(listOf(problem(MahjongDataSource.HISTORY_DATABASE))).last())

        assertEquals(MinecraftMessageKeys.DATA_CAUSE_UNREADABLE, (causeLine.content as TranslatableTextContent).key)
        val hover = causeLine.siblings.firstNotNullOf { it.style.hoverEvent?.getValue(HoverEvent.Action.SHOW_TEXT) }
        assertTrue(hover.string.startsWith("IOException: locked"))
    }

    /**
     * 取出訊息本身，略過表示從屬關係的縮排。
     *
     * @param text 由 [dataProblemMessages] 建立的訊息。
     * @return 縮排後的訊息，沒有縮排時為原訊息。
     */
    private fun mainText(text: Text): Text = if (text.content is TranslatableTextContent) text else text.siblings.single()

    /**
     * 取出訊息本身的翻譯內容。
     *
     * @param text 由 [dataProblemMessages] 建立的訊息。
     * @return 訊息的翻譯 key 與參數。
     */
    private fun translatable(text: Text): TranslatableTextContent = mainText(text).content as TranslatableTextContent

    /**
     * 建立指定來源、原因無法判斷的問題。
     *
     * @param source 資料來源。
     * @return 測試用的問題。
     */
    private fun problem(source: MahjongDataSource): MahjongDataProblem = MahjongDataProblem(
        source = source,
        file = "data/test.dat",
        cause = MahjongDataLoadCause.Unreadable("IOException: locked"),
    )
}
