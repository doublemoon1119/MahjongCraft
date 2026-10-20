package com.doublemoon1119.mahjongcraft.flow.persistence.format.game

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.PersistenceDtoDefinition
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.emptyPersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.base.ExtensionGameAction
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 驗證擴充動作保存與讀回時，使用動作本身的 ID，且保留動作攜帶的資料。 */
class ExtensionGameActionPersistenceIdentityTest {
    /** 一個動作類別的不同參數值共用動作 ID，不需為每種參數另建保存型別。 */
    @Test
    fun `parameterized extension actions round trip with their action ID`() {
        val registries = registries(TestIds.ACTION)
        for (amount in listOf(0, 7, 23)) {
            val action = GameAction.Extension(TestAction(amount))
            val encoded = assertIs<GameActionPersistenceDto.Extension>(
                action.toPersistenceDto(registries.exhaustiveDrawReasons, registries.extensionGameActions),
            )

            assertEquals(TestIds.ACTION, encoded.value.typeKey)
            assertEquals(action, encoded.toDomain(registries.exhaustiveDrawReasons, registries.extensionGameActions))
        }
    }

    /** 不一致的保存登記不能產生可寫入牌譜的動作資料，錯誤列出兩個識別碼。 */
    @Test
    fun `encoding rejects a persistence key different from the action ID`() {
        val registries = registries(TestIds.WRONG_FORMAT)
        val error = assertFailsWith<IllegalArgumentException> {
            GameAction.Extension(TestAction(7)).toPersistenceDto(registries.exhaustiveDrawReasons, registries.extensionGameActions)
        }

        assertTrue(error.message.orEmpty().contains(TestIds.ACTION))
        assertTrue(error.message.orEmpty().contains(TestIds.WRONG_FORMAT))
    }

    /** 即使保存型別已登記，還原後的動作也必須與存檔記錄的 ID 相同。 */
    @Test
    fun `decoding rejects a persistence key different from the restored action ID`() {
        val registries = registries(TestIds.WRONG_FORMAT)
        val encoded = GameActionPersistenceDto.Extension(
            TypedPersistenceDto(TestIds.WRONG_FORMAT, JsonObject(mapOf("amount" to JsonPrimitive(7)))),
        )
        val error = assertFailsWith<IllegalArgumentException> {
            encoded.toDomain(registries.exhaustiveDrawReasons, registries.extensionGameActions)
        }

        assertTrue(error.message.orEmpty().contains(TestIds.ACTION))
        assertTrue(error.message.orEmpty().contains(TestIds.WRONG_FORMAT))
    }

    /** 登記轉換時不呼叫第三方 mapper，避免為了檢查身分而建立任意動作或執行副作用。 */
    @Test
    fun `registration does not invoke conversion callbacks`() {
        val registries = emptyPersistenceRegistries()
        var calls = 0
        registries.extensionGameActions.register(
            PersistenceDtoDefinition(
                typeKey = TestIds.ACTION,
                domainClass = TestAction::class,
                serializer = TestActionDto.serializer(),
                toDto = {
                    calls++
                    TestActionDto(it.amount)
                },
                toDomain = {
                    calls++
                    TestAction(it.amount)
                },
            ),
        )
        registries.freeze()

        assertEquals(0, calls)
    }

    /**
     * 建立單一帶參數動作的保存註冊表。
     *
     * @param typeKey 測試指定的保存識別碼，可刻意與動作 ID 不同。
     * @return 已凍結的保存註冊表。
     */
    private fun registries(typeKey: String): PersistenceRegistries = emptyPersistenceRegistries().apply {
        extensionGameActions.register(
            PersistenceDtoDefinition(
                typeKey = typeKey,
                domainClass = TestAction::class,
                serializer = TestActionDto.serializer(),
                toDto = { TestActionDto(it.amount) },
                toDomain = { TestAction(it.amount) },
            ),
        )
        freeze()
    }

    /**
     * 測試用帶參數動作；所有參數值使用同一個動作 ID。
     *
     * @property amount 動作攜帶的數值。
     */
    private data class TestAction(val amount: Int) : ExtensionGameAction {
        /** 與保存登記及玩家用語共用的動作識別碼。 */
        override val id: String = TestIds.ACTION
    }

    /**
     * 測試動作的保存資料。
     *
     * @property amount 保存的動作數值。
     */
    @Serializable
    private data class TestActionDto(val amount: Int)

    /** 本測試使用的動作 ID 與刻意不一致的保存 ID。 */
    private object TestIds {
        /** 唯一合法的動作及保存識別碼。 */
        const val ACTION = "identity_test:declare"

        /** 用於確認錯誤保存登記會被拒絕的識別碼。 */
        const val WRONG_FORMAT = "identity_test:declaration_format"
    }
}
