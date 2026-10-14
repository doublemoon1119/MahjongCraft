package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import com.mojang.datafixers.DSL
import com.mojang.datafixers.DataFixer
import com.mojang.datafixers.schemas.Schema
import com.mojang.serialization.Dynamic
import net.minecraft.SharedConstants
import net.minecraft.nbt.NbtCompound
import net.minecraft.world.PersistentState
import net.minecraft.world.PersistentStateManager
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 使用真正的原生管理器驗證解碼失敗不會觸發建立空資料或覆寫原檔。 */
class StrictPersistentStateLoadingTest {
    /** 檔案確定不存在時才能建立新資料，保存後可由新管理器讀回。 */
    @Test
    fun `missing state can be created saved and loaded`() = withManager { directory, manager ->
        var created = 0
        val state = loadPersistentStateStrict(manager, directory.resolve(FILE_NAME), KEY, ::decode) {
            created++
            TestState("new-world")
        }
        assertEquals(1, created)
        assertEquals("new-world", state.value)
        state.markDirty()
        manager.save()
        assertTrue(Files.exists(directory.resolve(FILE_NAME)))
        val second = PersistentStateManager(directory.toFile(), IdentityDataFixer)
        assertEquals("new-world", loadPersistentStateStrict(second, directory.resolve(FILE_NAME), KEY, ::decode) { error("Saved data must not use the create function") }.value)
    }

    /** 原版吞掉解碼例外後，嚴格入口仍拒絕建立空資料；後續 save 不改寫原始位元組。 */
    @Test
    fun `decode failure cannot register or save an empty replacement`() = withManager { directory, manager ->
        val file = directory.resolve(FILE_NAME)
        TestState("original").apply {
            markDirty()
            save(file.toFile())
        }
        val before = Files.readAllBytes(file)
        val expected = IllegalArgumentException("Unsupported saved data")
        var created = false
        val actual = assertFailsWith<IllegalArgumentException> {
            loadPersistentStateStrict(
                manager = manager,
                dataFile = file,
                key = KEY,
                decode = { throw expected },
                create = {
                    created = true
                    TestState("empty")
                },
            )
        }
        assertSame(expected, actual)
        assertFalse(created)
        manager.save()
        assertContentEquals(before, Files.readAllBytes(file))
    }

    /** 原生管理器連解碼器都未呼叫的 I/O 失敗，也不能退回新世界。 */
    @Test
    fun `corrupt file cannot fall back to a newly created state`() = withManager { directory, manager ->
        val file = directory.resolve(FILE_NAME)
        val corrupt = byteArrayOf(1, 2, 3, 4)
        Files.write(file, corrupt)
        var created = false
        assertFailsWith<IllegalStateException> {
            loadPersistentStateStrict(manager, file, KEY, ::decode) {
                created = true
                TestState("empty")
            }
        }
        assertFalse(created)
        manager.save()
        assertContentEquals(corrupt, Files.readAllBytes(file))
    }

    /**
     * 建立隔離的原生管理器，只刪除本測試建立的單一檔案與空目錄。
     *
     * @param test 使用暫存目錄與管理器執行的測試。
     */
    private fun withManager(test: (Path, PersistentStateManager) -> Unit) {
        SharedConstants.createGameVersion()
        val directory = Files.createTempDirectory("mahjongcraft-strict-state-")
        try {
            test(directory, PersistentStateManager(directory.toFile(), IdentityDataFixer))
        } finally {
            Files.deleteIfExists(directory.resolve(FILE_NAME))
            Files.deleteIfExists(directory)
        }
    }

    /**
     * 還原測試狀態，不使用 Minecraft 遊戲資料。
     *
     * @param nbt 已解壓的資料內容。
     * @return 保留原測試文字的狀態。
     */
    private fun decode(nbt: NbtCompound): TestState = TestState(nbt.getString(VALUE_KEY))

    /**
     * 只保存一段文字的原生狀態。
     *
     * @property value 測試用保存內容。
     */
    private class TestState(val value: String) : PersistentState() {
        /** 將測試文字完整寫入原生 NBT 容器。 */
        override fun writeNbt(nbt: NbtCompound): NbtCompound = nbt.apply { putString(VALUE_KEY, value) }
    }

    /** 測試不遷移遊戲資料，只讓原生管理器走完整的 NBT 載入流程。 */
    private object IdentityDataFixer : DataFixer {
        /** 保留輸入資料，不對測試內容進行遊戲版本遷移。 */
        override fun <T> update(
            type: DSL.TypeReference,
            input: Dynamic<T>,
            version: Int,
            newVersion: Int,
        ): Dynamic<T> = input

        /** 測試資料不查詢資料遷移 schema，意外呼叫時明確失敗。 */
        override fun getSchema(versionKey: Int): Schema = error("Schema lookup is not needed by this fixture")
    }

    /** 本測試使用的固定資料名稱。 */
    private companion object {
        /** 管理器使用的保存 key。 */
        const val KEY = "strict_state"

        /** 原生管理器在暫存目錄下建立的檔名。 */
        const val FILE_NAME = "$KEY.dat"

        /** 測試 NBT 的內容欄位。 */
        const val VALUE_KEY = "value"
    }
}
