package com.doublemoon1119.mahjongcraft.flow.common.di

import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * 針對 [registerBuiltInRuleModules] 進行單元測試。
 *
 * 驗證呼叫後，內建的日麻與台麻模組皆可透過 [com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry] 正確解析。
 */
class BuiltInMahjongModulesTest {

    /**
     * 驗證呼叫後能解析出日麻模組。
     */
    @Test
    fun `test registerBuiltInRuleModules resolves riichi module`() {
        val registry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }

        assertIs<RiichiRuleModule>(registry.getModule(RiichiRuleConfig()))
    }

    /**
     * 驗證呼叫後能解析出台麻模組。
     */
    @Test
    fun `test registerBuiltInRuleModules resolves taiwan module`() {
        val registry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }

        assertIs<TaiwanRuleModule>(registry.getModule(TaiwanRuleConfig()))
    }

    /**
     * 驗證呼叫後僅註冊了日麻與台麻兩種模組 ID，不多不少。
     */
    @Test
    fun `test registerBuiltInRuleModules registers exactly the built-in ids`() {
        val registry = MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }

        val ids = registry.getAllModuleIds()
        assertEquals(2, ids.size)
        assertTrue(ids.contains("mahjongcraft:riichi"))
        assertTrue(ids.contains("mahjongcraft:taiwan"))
    }
}
