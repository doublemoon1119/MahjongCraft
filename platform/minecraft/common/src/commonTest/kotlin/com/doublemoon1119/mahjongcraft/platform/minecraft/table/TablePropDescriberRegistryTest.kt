package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.RiichiTableProps
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerBuiltInRiichiTableProps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/** 驗證規則桌面物件描述的登記流程。 */
class TablePropDescriberRegistryTest {
    /** 驗證內建日麻與第三方規則的描述都透過同一個 registry 查到。 */
    @Test
    fun `built-in and third-party describers resolve by rule module id`() {
        val custom = TablePropDescriber { emptyList() }
        val registry = TablePropDescriberRegistryImpl().apply {
            registerBuiltInRiichiTableProps()
            register("example:custom", custom)
        }

        assertSame(RiichiTableProps, registry.find(BuiltInRuleModuleIds.RIICHI))
        assertSame(custom, registry.find("example:custom"))
        assertNull(registry.find(BuiltInRuleModuleIds.TAIWAN))
        assertEquals(setOf(BuiltInRuleModuleIds.RIICHI, "example:custom"), registry.registrationKeys)
    }

    /** 驗證 registry 凍結後禁止延遲登記。 */
    @Test
    fun `frozen registry rejects late registration`() {
        val registry = TablePropDescriberRegistryImpl().apply { freeze() }

        assertFailsWith<IllegalStateException> {
            registry.register("example:late", TablePropDescriber { emptyList() })
        }
    }

    /** 驗證同一個規則模組重複登記會被拒絕，避免第三方無意間覆蓋內建描述。 */
    @Test
    fun `duplicate rule module registration is rejected`() {
        val registry = TablePropDescriberRegistryImpl().apply { registerBuiltInRiichiTableProps() }

        assertFailsWith<IllegalArgumentException> {
            registry.register(BuiltInRuleModuleIds.RIICHI, TablePropDescriber { emptyList() })
        }
    }

    /** 驗證物件種類必須是命名空間識別碼。 */
    @Test
    fun `placement rejects a kind without namespace`() {
        assertFailsWith<IllegalArgumentException> {
            TablePropPlacement(kind = "scoring_stick", variant = "1000", seatIndex = 0, anchor = TableSeatAnchor.MELD_CORNER)
        }
    }
}
