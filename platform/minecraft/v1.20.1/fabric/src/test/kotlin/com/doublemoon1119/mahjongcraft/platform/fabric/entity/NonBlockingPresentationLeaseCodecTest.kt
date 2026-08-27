package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.TablePresentationBusyPolicy
import net.minecraft.nbt.NbtCompound
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 驗證「動畫不阻塞全桌」豁免的世界存檔往返走的是**生產程式碼的讀寫**（[NonBlockingPresentationLeaseCodec]，
 * 即 `MahjongTileEntity.readCustomDataFromNbt`／`writeCustomDataToNbt` 實際呼叫的那一份），而不是在測試裡
 * 另外拼一次 key 與型別。
 *
 * 直接實體化 `MahjongTileEntity` 做完整 entity NBT 往返在本專案不可行——那需要註冊表裡的 `EntityType`
 * 與一個 `World`，而這裡沒有 Minecraft bootstrap 的測試基礎建設。因此讀寫規則抽成 codec：這能擋下 key
 * 拼錯、型別用錯、預設值不對這幾類漏接，但擋不下「entity 忘記呼叫 codec」，那一項仍只能靠 review。
 */
class NonBlockingPresentationLeaseCodecTest {
    /** 寫進去的到期時間必須原封不動讀回來。 */
    @Test
    fun `a lease survives a write and read round trip`() {
        val nbt = NbtCompound()
        NonBlockingPresentationLeaseCodec.write(nbt, 1_234L)

        assertEquals(1_234L, NonBlockingPresentationLeaseCodec.read(nbt))
    }

    /** 讀回來的值必須真的還能繼續豁免——往返正確但語意接不上就沒有意義。 */
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

    /** 沒寫過這個欄位的存檔讀回「沒有豁免」，因此那些牌的動畫照常讓整桌忙碌。 */
    @Test
    fun `a save file without the lease reads back as no exemption`() {
        val legacy = NbtCompound()

        assertEquals(NonBlockingPresentationLeaseCodec.NO_LEASE, NonBlockingPresentationLeaseCodec.read(legacy))
        assertTrue(
            TablePresentationBusyPolicy.tileBlocksTableBusy(true, NonBlockingPresentationLeaseCodec.read(legacy), 0L),
            "Tiles from an older save must keep their original blocking behaviour.",
        )
    }
}
