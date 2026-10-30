package com.doublemoon1119.mahjongcraft.flow.network.dto.component

import com.doublemoon1119.mahjongcraft.flow.network.dto.model.TileFaceViewDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.model.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.MeldSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDto
import com.doublemoon1119.mahjongcraft.logic.base.MeldSnapshot
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewType
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** 驗證網路只還原裁切後的值，且實際序列化使用同一份動態登記。 */
class ComponentViewNetworkRegistryTest {
    /** 可在格式建立後登記，編解碼結果仍保持具型別的公開值。 */
    @Test
    fun `projected values round trip through dynamic network serializers`() {
        val registries = DefaultNetworkDtoRegistries()
        val json = NetworkJson(registries)
        val view = viewType()
        registries.componentViews.register(definition(view))
        val serializer = ListSerializer(PolymorphicSerializer(ComponentViewDto::class))
        val original = ComponentViewSet.EMPTY.with(view, 3)
        val encoded = json.encodeToString(serializer, registries.componentViews.encode(original))
        val decoded = json.decodeFromString(serializer, encoded)
        assertEquals(original, registries.componentViews.decode(decoded))
        assertEquals("[{\"type\":\"test:public_count\",\"count\":3}]", encoded)
        assertEquals("[]", json.encodeToString(serializer, registries.componentViews.encode(ComponentViewSet.EMPTY)))
    }

    /** 顯示牌面只攜帶裁切特徵與外觀，不借用局內牌張 UUID。 */
    @Test
    fun `identity free face views retain projected traits through network serialization`() {
        val registries = DefaultNetworkDtoRegistries()
        val view = viewType()
        registries.componentViews.register(definition(view))
        val original = TileFaceView(
            tile = Tile.Numeric(Tile.Suit.Dot, 5),
            views = ComponentViewSet.EMPTY.with(view, 2),
            appearanceId = "example:readable",
        )
        val json = NetworkJson(registries)
        val encoded = json.encodeToString(TileFaceViewDto.serializer(), original.toDto(registries))
        val decoded = json.decodeFromString(TileFaceViewDto.serializer(), encoded).toDomain(registries)
        assertEquals(original, decoded)
    }

    /** 副露以正式網路格式保留穩定 ID 與裁切值，不需要原始組件格式。 */
    @Test
    fun `meld snapshots round trip projected values and stable identity`() {
        val registries = DefaultNetworkDtoRegistries()
        val view = viewType()
        registries.componentViews.register(definition(view))
        val original = MeldSnapshot(
            id = Uuid.random(),
            type = MeldType.PON,
            tiles = emptyList(),
            sourceTile = null,
            sourceDirection = RelativeDirection.Self,
            faceDownTileIds = emptySet(),
            componentViews = ComponentViewSet.EMPTY.with(view, 4),
        )
        val json = NetworkJson(registries)
        val encoded = json.encodeToString(MeldSnapshotDto.serializer(), original.toDto(registries))
        val restored = json.decodeFromString(MeldSnapshotDto.serializer(), encoded).toDomain(registries)
        assertEquals(original, restored)
    }

    /** 重複輸出、未知型別及非法值不能成為有效輸出。 */
    @Test
    fun `duplicate unknown and invalid network values are rejected`() {
        val registry = ComponentViewNetworkRegistry()
        val view = viewType()
        registry.register(definition(view))
        assertFailsWith<IllegalArgumentException> {
            registry.decode(listOf(CountDto(1), CountDto(1)))
        }
        assertFailsWith<IllegalArgumentException> {
            registry.decode(listOf(CountDto(-1)))
        }
        assertFailsWith<IllegalStateException> {
            ComponentViewNetworkRegistry().decode(listOf(CountDto(1)))
        }
        val forged = viewType()
        assertFailsWith<IllegalArgumentException> {
            registry.encode(ComponentViewSet.EMPTY.with(forged, 1))
        }
        registry.freeze()
        assertFailsWith<IllegalStateException> { registry.register(definition(view)) }
    }

    /** 建立只允許非負數的公開輸出定義。 */
    private fun viewType(): ComponentViewType<Int> = ComponentViewType(
        id = "test:public_count",
        valueClass = Int::class,
        copyValue = { it },
        validateValue = { require(it >= 0) },
    )

    /**
     * 建立測試用的具型別網路轉換。
     *
     * @param view 公開輸出定義。
     * @return 與此定義綁定的網路轉換。
     */
    private fun definition(view: ComponentViewType<Int>): ComponentViewNetworkDefinition<Int, CountDto> = ComponentViewNetworkDefinition(
        viewType = view,
        dtoClass = CountDto::class,
        serializer = CountDto.serializer(),
        toDto = ::CountDto,
        toValue = { it.count },
    )

    /**
     * 已允許公開的數量，不攜帶權威資料或授權名單。
     *
     * @property count 公開數量。
     */
    @Serializable
    @SerialName("test:public_count")
    private data class CountDto(val count: Int) : ComponentViewDto
}
