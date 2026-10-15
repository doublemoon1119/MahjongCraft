package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.UnregisteredPersistenceTypeException
import com.doublemoon1119.mahjongcraft.flow.persistence.format.migration.UnsupportedPersistenceSchemaVersionException
import kotlin.test.Test
import kotlin.test.assertEquals

/** [classifyDataLoadFailure] 依例外判斷讀取失敗原因的測試。 */
class MahjongDataLoadCauseTest {
    /** 找不到轉換器的型別即使被包在其他例外中，也判斷為缺少擴充並保留型別識別碼。 */
    @Test
    fun `wrapped unregistered type is a missing extension`() {
        val failure = IllegalArgumentException("decode failed", UnregisteredPersistenceTypeException("example:custom_rule"))

        val cause = classifyDataLoadFailure(failure)

        assertEquals(MahjongDataLoadCause.MissingExtension("example:custom_rule"), cause)
        assertEquals("example", (cause as MahjongDataLoadCause.MissingExtension).namespace)
    }

    /** 較新版本保存的資料帶出資料版本與目前支援的版本。 */
    @Test
    fun `newer schema version is a newer format`() {
        val cause = classifyDataLoadFailure(UnsupportedPersistenceSchemaVersionException(version = 9, currentVersion = 3))

        assertEquals(MahjongDataLoadCause.NewerFormat(savedVersion = 9, supportedVersion = 3), cause)
    }

    /** 無法判斷的失敗只保留例外名稱與第一行訊息，不帶出後續內容。 */
    @Test
    fun `other failures keep a one line summary`() {
        val cause = classifyDataLoadFailure(IllegalStateException("broken payload\nsecond line"))

        assertEquals(MahjongDataLoadCause.Unreadable("IllegalStateException: broken payload"), cause)
    }

    /** 沒有訊息的例外只以名稱摘要。 */
    @Test
    fun `failure without message is summarized by its name`() {
        assertEquals("IllegalStateException", failureSummary(IllegalStateException()))
    }

    /** 沒有命名空間的型別識別碼以整個識別碼作為擴充名稱。 */
    @Test
    fun `type key without namespace is its own namespace`() {
        assertEquals("custom", MahjongDataLoadCause.MissingExtension("custom").namespace)
    }
}
