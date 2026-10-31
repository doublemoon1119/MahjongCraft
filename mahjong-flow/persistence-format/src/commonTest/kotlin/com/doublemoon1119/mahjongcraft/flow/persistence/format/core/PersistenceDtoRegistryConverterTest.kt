package com.doublemoon1119.mahjongcraft.flow.persistence.format.core

import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.emptyPersistenceRegistries
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** 驗證保存格式 registry 轉換時把所屬的唯讀轉換交給已登記的轉換函式。 */
class PersistenceDtoRegistryConverterTest {
    /** 轉換函式在編碼與解碼時都收到所屬整組 registry 的轉換。 */
    @Test
    fun `mappers receive the owning converter when converting`() {
        val registries = emptyPersistenceRegistries()
        val received = mutableListOf<PersistenceConverter>()
        val registry = PersistenceDtoRegistry<Value> { registries }
        registry.register(
            PersistenceDtoDefinition(
                typeKey = "test:value",
                domainClass = Value::class,
                serializer = ValueDto.serializer(),
                toDto = { value, converter -> ValueDto(value.amount).also { received += converter } },
                toDomain = { dto, converter -> Value(dto.amount).also { received += converter } },
            ),
        )

        val restored = registry.decode(registry.encode(Value(5)))

        assertEquals(Value(5), restored)
        assertEquals(2, received.size)
        received.forEach { assertSame(registries, it) }
    }

    /**
     * 測試用的領域值。
     *
     * @property amount 數值。
     */
    private data class Value(val amount: Int)

    /**
     * 測試用的保存 DTO。
     *
     * @property amount 數值。
     */
    @Serializable
    private data class ValueDto(val amount: Int)
}
