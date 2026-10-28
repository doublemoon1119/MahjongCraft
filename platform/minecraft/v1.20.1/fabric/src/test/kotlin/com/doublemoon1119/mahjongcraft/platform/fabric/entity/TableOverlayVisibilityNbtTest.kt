package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationQueueDriver
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableOverlayVisibility
import net.minecraft.nbt.NbtCompound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證資訊面板以保存期限恢復隱藏，不依賴已消耗的動畫步驟。 */
class TableOverlayVisibilityNbtTest {
    /** 有限與無期限的截止時間均保持原值。 */
    @Test
    fun `finite and permanent deadlines round trip through nbt`() {
        listOf(100L, Long.MAX_VALUE).forEach { deadline ->
            val nbt = NbtCompound()
            TableOverlayVisibilityNbt.write(nbt, deadline)
            assertEquals(deadline, TableOverlayVisibilityNbt.read(nbt))
        }
    }

    /** 無欄位、不合法時間與錯誤型別皆不產生隱藏設定。 */
    @Test
    fun `missing or invalid deadline does not hide the overlay`() {
        val samples = listOf(
            NbtCompound(),
            NbtCompound().apply { putLong("HiddenUntilGameTime", 0L) },
            NbtCompound().apply { putLong("HiddenUntilGameTime", -1L) },
            NbtCompound().apply { putString("HiddenUntilGameTime", "invalid") },
        )
        samples.forEach { nbt ->
            val deadline = TableOverlayVisibilityNbt.read(nbt)
            assertEquals(Long.MIN_VALUE, deadline)
            assertFalse(TableOverlayVisibility.isHidden(deadline, 50L))
        }
    }

    /** 解除後重用同一份 NBT，舊的無期限隱藏不會留下來。 */
    @Test
    fun `clearing removes a previously saved permanent deadline`() {
        val nbt = NbtCompound()
        TableOverlayVisibilityNbt.write(nbt, Long.MAX_VALUE)
        TableOverlayVisibilityNbt.write(nbt, Long.MIN_VALUE)
        assertFalse(nbt.contains("HiddenUntilGameTime"))
        assertFalse(TableOverlayVisibility.isHidden(TableOverlayVisibilityNbt.read(nbt), 50L))
    }

    /** 隱藏期限的讀寫不影響資訊內容。 */
    @Test
    fun `writing a deadline preserves unrelated presentation data`() {
        val nbt = NbtCompound().apply { putString("Lines", "presentation") }
        TableOverlayVisibilityNbt.write(nbt, 100L)
        assertEquals("presentation", nbt.getString("Lines"))
    }

    /** 隱藏步驟已執行、只剩等待時，還原後仍隱藏到原本期限。 */
    @Test
    fun `restored deadline hides while only the waiting step remains`() {
        val progress = AnimationQueueDriver.tick<Nothing>(
            queue = listOf(AnimationStep.SetInvisible(true), AnimationStep.WaitUntil(100L), AnimationStep.SetInvisible(false)),
            activeStepEndGameTime = null,
            currentGameTime = 50L,
        )
        assertEquals(listOf(AnimationStep.WaitUntil(100L), AnimationStep.SetInvisible(false)), progress.remainingQueue)
        val nbt = NbtCompound()
        TableOverlayVisibilityNbt.write(nbt, 100L)
        val restored = TableOverlayVisibilityNbt.read(nbt)
        assertTrue(TableOverlayVisibility.isHidden(restored, 50L))
        assertFalse(TableOverlayVisibility.isHidden(restored, 100L))
    }

    /** 隱藏到移除的動畫已全部消耗，保存期限仍維持隱藏。 */
    @Test
    fun `restored permanent deadline hides with an empty animation queue`() {
        val progress = AnimationQueueDriver.tick<Nothing>(
            queue = listOf(AnimationStep.SetInvisible(true)),
            activeStepEndGameTime = null,
            currentGameTime = 50L,
        )
        assertTrue(progress.remainingQueue.isEmpty())
        val nbt = NbtCompound()
        TableOverlayVisibilityNbt.write(nbt, Long.MAX_VALUE)
        assertTrue(TableOverlayVisibility.isHidden(TableOverlayVisibilityNbt.read(nbt), 50L))
    }
}
