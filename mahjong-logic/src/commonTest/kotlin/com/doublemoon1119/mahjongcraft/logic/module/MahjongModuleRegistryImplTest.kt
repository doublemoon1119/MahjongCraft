package com.doublemoon1119.mahjongcraft.logic.module

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 針對 [MahjongModuleRegistryImpl] 進行單元測試。
 *
 * 驗證建構時是空的對照表，且只能透過 [MahjongModuleRegistry.register] 加入規則——
 * 連日麻/台麻這兩個內建規則都不是這個類別自己塞進去的，而是外部呼叫 [register] 註冊的
 * （實際的呼叫端是 `:mahjong-flow-common` 的 `registerBuiltInRuleModules()`，見該檔案）。
 */
class MahjongModuleRegistryImplTest {

    /**
     * 驗證建構時不含任何已註冊的模組。
     */
    @Test
    fun `test registry is empty by default`() {
        val registry = MahjongModuleRegistryImpl()

        assertTrue(registry.getAllModuleIds().isEmpty())
    }

    /**
     * 驗證透過公開的 [MahjongModuleRegistry.register] 註冊後，能解析出對應的模組。
     * 這裡刻意手動呼叫 register（而非依賴任何預設狀態），模擬第三方規則自行註冊的情境。
     */
    @Test
    fun `test register makes a module resolvable via getModule`() {
        val registry = MahjongModuleRegistryImpl()
        registry.register(RiichiRuleConfig::class, "mahjongcraft:riichi") { config, id -> RiichiRuleModule(id, config) }

        val module = registry.getModule(RiichiRuleConfig())

        assertIs<RiichiRuleModule>(module)
    }

    /**
     * 驗證傳入尚未註冊的規則配置型別時，會拋出例外而非靜默回傳錯誤模組。
     */
    @Test
    fun `test getModule throws for unregistered config class`() {
        val registry = MahjongModuleRegistryImpl()

        assertFailsWith<IllegalStateException> {
            registry.getModule(FakeMahjongRuleConfig())
        }
    }

    /**
     * 驗證 [MahjongModuleRegistry.getAllModuleIds] 與 [MahjongModuleRegistry.getConfigClass]
     * 僅反映實際註冊過的模組，而非任何預設清單。
     */
    @Test
    fun `test getAllModuleIds and getConfigClass reflect only what has been registered`() {
        val registry = MahjongModuleRegistryImpl()
        registry.register(RiichiRuleConfig::class, "mahjongcraft:riichi") { config, id -> RiichiRuleModule(id, config) }

        assertEquals(setOf("mahjongcraft:riichi"), registry.getAllModuleIds())
        assertEquals(RiichiRuleConfig::class, registry.getConfigClass("mahjongcraft:riichi"))
    }

    /**
     * 驗證查詢不存在的 ID 時回傳 null。
     */
    @Test
    fun `test getConfigClass returns null for unknown id`() {
        val registry = MahjongModuleRegistryImpl()

        assertNull(registry.getConfigClass("unknown"))
    }
}
