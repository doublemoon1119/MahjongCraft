package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

/** 驗證桌面牌位配置的登記、查詢與內建規則的登記。 */
class TableTileLayoutProfileRegistryTest {
    /** 已登記的規則取得自己的配置，未登記的規則取得標準配置。 */
    @Test
    fun `registered rules get their profile and others get the standard one`() {
        val profile = TableTileLayoutProfile(wallInset = 0.07)
        val registry = TableTileLayoutProfileRegistryImpl()
        registry.register(TableTileLayoutProfileDefinition("example:rule", profile))

        assertSame(profile, registry.profileFor("example:rule"))
        assertSame(TableTileLayoutProfile.STANDARD, registry.profileFor("example:unknown"))
        assertEquals(setOf("example:rule"), registry.registrationKeys)
    }

    /** 重複登記、未命名空間的識別碼與凍結後登記都會被拒絕。 */
    @Test
    fun `duplicate, unnamespaced and frozen registrations are rejected`() {
        val registry = TableTileLayoutProfileRegistryImpl()
        registry.register(TableTileLayoutProfileDefinition("example:rule", TableTileLayoutProfile.STANDARD))

        assertFailsWith<IllegalArgumentException> { registry.register(TableTileLayoutProfileDefinition("example:rule", TableTileLayoutProfile.STANDARD)) }
        assertFailsWith<IllegalArgumentException> { registry.register(TableTileLayoutProfileDefinition("rule", TableTileLayoutProfile.STANDARD)) }
        registry.freeze()
        assertFailsWith<IllegalStateException> { registry.register(TableTileLayoutProfileDefinition("example:other", TableTileLayoutProfile.STANDARD)) }
    }

    /** 內建的四人日麻、三人日麻與台麻都登記了配置。 */
    @Test
    fun `built in rules register table tile layout profiles`() {
        val registry = TableTileLayoutProfileRegistryImpl()
        BundledMinecraftMahjongExtensions.all.forEach { it.registerTableTileLayoutProfiles(registry) }

        assertEquals(
            setOf(BuiltInRuleModuleIds.RIICHI, BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, BuiltInRuleModuleIds.TAIWAN),
            registry.registrationKeys,
        )
    }
}
