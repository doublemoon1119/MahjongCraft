package com.doublemoon1119.mahjongcraft.example.fabric

import com.doublemoon1119.mahjongcraft.example.fabric.table.ExampleHandRevealChipTablePropKind
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** 驗證範例 Fabric extension 會登記攤牌籌碼種類。 */
class ExampleTablePropRegistrationTest {
    /** 攤牌籌碼使用共用 presentation ID，且接受固定變體。 */
    @Test
    fun `extension registers hand reveal chip kind`() {
        val registry = FabricTablePropKindRegistryImpl()

        ExampleFabricMahjongExtension().registerTablePropKinds(registry)

        assertEquals(listOf(ExampleHandRevealChipTablePropKind.id), registry.registrationKeys.toList())
        assertSame(ExampleHandRevealChipTablePropKind, registry.find(ExampleHandRevealChipTablePropKind.id))
    }
}
