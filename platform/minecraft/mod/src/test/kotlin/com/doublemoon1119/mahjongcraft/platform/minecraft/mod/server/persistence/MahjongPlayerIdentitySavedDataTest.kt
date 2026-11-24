package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence

import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [MahjongPlayerIdentitySavedData] 的格式與錯誤資料測試。 */
class MahjongPlayerIdentitySavedDataTest {
    /** 確認版本一索引可完整往返玩家名稱。 */
    @Test
    fun `round trips version one player names`() {
        val playerId = UUID.randomUUID()
        val state = MahjongPlayerIdentitySavedData.create()

        assertTrue(state.remember(playerId, "PlayerOne"))
        val restored = MahjongPlayerIdentitySavedData.fromNbt(state.save(CompoundTag()))

        assertEquals("PlayerOne", restored.nameOf(playerId))
    }

    /** 確認未知格式版本不會被當成可用身分資料。 */
    @Test
    fun `rejects unsupported format version`() {
        val nbt = MahjongPlayerIdentitySavedData.create().save(CompoundTag()).apply {
            putInt("Version", MahjongPlayerIdentitySavedData.CURRENT_VERSION + 1)
        }

        assertEquals(emptyMap(), MahjongPlayerIdentitySavedData.fromNbt(nbt).snapshot())
    }

    /** 確認單筆損壞項目會略過，其他可讀項目仍能載入。 */
    @Test
    fun `skips malformed entries`() {
        val playerId = UUID.randomUUID()
        val entries = MahjongPlayerIdentitySavedData.create().apply {
            remember(playerId, "PlayerOne")
        }.save(CompoundTag()).getList("Entries", Tag.TAG_COMPOUND.toInt())
        entries.add(CompoundTag().apply { putString("PlayerId", "invalid") })
        val nbt = CompoundTag().apply {
            putInt("Version", MahjongPlayerIdentitySavedData.CURRENT_VERSION)
            put("Entries", entries)
        }

        assertEquals("PlayerOne", MahjongPlayerIdentitySavedData.fromNbt(nbt).nameOf(playerId))
    }
}
