package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.fabric.table.ExampleHandRevealChipTablePropKind
import com.doublemoon1119.mahjongcraft.platform.fabric.api.registration.FabricTablePropPreviewRegistrar
import com.doublemoon1119.mahjongcraft.platform.fabric.api.tableprop.FabricTablePropPreviewDefinition
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.TablePropKindRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** 驗證範例 Fabric extension 會登記攤牌籌碼種類。 */
class ExampleTablePropRegistrationTest {
    /** 攤牌籌碼使用共用 presentation ID，且接受固定變體。 */
    @Test
    fun `extension registers hand reveal chip kind`() {
        val registry = TablePropKindRegistryImpl()

        ExampleFabricMahjongExtension().registerTablePropKinds(registry)

        assertEquals(listOf(ExampleHandRevealChipTablePropKind.id), registry.registrationKeys.toList())
        assertSame(ExampleHandRevealChipTablePropKind, registry.find(ExampleHandRevealChipTablePropKind.id))
    }

    /** 客戶端擴充以同一份種類定義登記攤牌籌碼的預覽，登記時不建立實體。 */
    @Test
    fun `client extension registers hand reveal chip preview`() {
        val registered = mutableListOf<FabricTablePropPreviewDefinition>()
        ExampleFabricMahjongClientExtension().registerTablePropPreviews(
            registry = object : FabricTablePropPreviewRegistrar {
                override val registrationKeys: Set<String> get() = registered.map { it.id }.toSet()

                override val isFrozen: Boolean = false

                override fun register(definition: FabricTablePropPreviewDefinition) {
                    registered += definition
                }
            },
        )

        assertEquals(listOf(ExampleHandRevealChipTablePropKind.id), registered.map { it.id })
        assertSame(ExampleHandRevealChipTablePropKind.definition, registered.single().definition)
    }
}
