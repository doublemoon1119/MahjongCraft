package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [MahjongPlayerIdentityPersistentState] 的格式與錯誤資料測試。 */
class MahjongPlayerIdentityPersistentStateTest {
    /** 確認版本一索引可完整往返玩家名稱。 */
    @Test
    fun `round trips version one player names`() {
        val playerId = UUID.randomUUID()
        val state = MahjongPlayerIdentityPersistentState.create()

        assertTrue(state.remember(playerId, "PlayerOne"))
        val restored = MahjongPlayerIdentityPersistentState.fromNbt(state.writeNbt(NbtCompound()))

        assertEquals("PlayerOne", restored.nameOf(playerId))
    }

    /** 確認未知格式版本不會被當成可用身分資料。 */
    @Test
    fun `rejects unsupported format version`() {
        val nbt = MahjongPlayerIdentityPersistentState.create().writeNbt(NbtCompound()).apply {
            putInt("Version", MahjongPlayerIdentityPersistentState.CURRENT_VERSION + 1)
        }

        assertEquals(emptyMap(), MahjongPlayerIdentityPersistentState.fromNbt(nbt).snapshot())
    }

    /** 確認單筆損壞項目會略過，其他可讀項目仍能載入。 */
    @Test
    fun `skips malformed entries`() {
        val playerId = UUID.randomUUID()
        val entries = MahjongPlayerIdentityPersistentState.create().apply {
            remember(playerId, "PlayerOne")
        }.writeNbt(NbtCompound()).getList("Entries", NbtElement.COMPOUND_TYPE.toInt())
        entries.add(NbtCompound().apply { putString("PlayerId", "invalid") })
        val nbt = NbtCompound().apply {
            putInt("Version", MahjongPlayerIdentityPersistentState.CURRENT_VERSION)
            put("Entries", entries)
        }

        assertEquals("PlayerOne", MahjongPlayerIdentityPersistentState.fromNbt(nbt).nameOf(playerId))
    }
}
