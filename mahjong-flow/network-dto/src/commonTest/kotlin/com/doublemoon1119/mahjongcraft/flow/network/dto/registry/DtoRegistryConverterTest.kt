package com.doublemoon1119.mahjongcraft.flow.network.dto.registry

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** 驗證網路 DTO registry 轉換時把所屬的唯讀轉換交給已登記的轉換函式。 */
class DtoRegistryConverterTest {
    /** 轉換函式在轉換時收到所屬整組 registry 的轉換，而不是登記時取得。 */
    @Test
    fun `mappers receive the owning converter when converting`() {
        val registries = DefaultNetworkDtoRegistries()
        val received = mutableListOf<NetworkDtoConverter>()
        val registry = DtoRegistry<Value, ValueDto> { registries }
        registry.register(
            DtoDefinition(
                domainClass = Value::class,
                dtoClass = ValueDto::class,
                serializer = ValueDto.serializer(),
                toDto = { value, converter -> ValueDto(value.amount).also { received += converter } },
                toDomain = { dto, converter -> Value(dto.amount).also { received += converter } },
            ),
        )

        val dto = registry.toDto(Value(3))
        val restored = registry.toDomain(dto)

        assertEquals(Value(3), restored)
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
     * 測試用的網路 DTO。
     *
     * @property amount 數值。
     */
    @Serializable
    private data class ValueDto(val amount: Int)
}
