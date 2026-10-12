package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.TablePresentationBusyPolicy
import net.minecraft.nbt.NbtCompound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 驗證動畫豁免到期時間經由 [NonBlockingPresentationLeaseCodec] 寫入並讀回後，仍符合忙碌判斷規則。
 *
 * [NonBlockingPresentationLeaseCodec] 是 `MahjongTileEntity.readCustomDataFromNbt` 與
 * `writeCustomDataToNbt` 共用的讀寫實作。測試直接驗證此 codec，避免複製欄位名稱與型別。
 */
class NonBlockingPresentationLeaseCodecTest {
    /** 寫進去的到期時間必須原封不動讀回來。 */
    @Test
    fun `a lease survives a write and read round trip`() {
        val nbt = NbtCompound()
        NonBlockingPresentationLeaseCodec.write(nbt, 1_234L)

        assertEquals(1_234L, NonBlockingPresentationLeaseCodec.read(nbt))
    }

    /** 讀回的到期時間在到期前仍會讓牌的動畫維持豁免。 */
    @Test
    fun `a lease restored from a save file still exempts the tile`() {
        val nbt = NbtCompound()
        NonBlockingPresentationLeaseCodec.write(nbt, 1_234L)
        val restored = NonBlockingPresentationLeaseCodec.read(nbt)

        assertFalse(
            TablePresentationBusyPolicy.tileBlocksTableBusy(true, restored, 1_233L),
            "A lease restored from the save file must still exempt the tile.",
        )
        assertTrue(TablePresentationBusyPolicy.tileBlocksTableBusy(true, restored, 1_234L))
    }
}
