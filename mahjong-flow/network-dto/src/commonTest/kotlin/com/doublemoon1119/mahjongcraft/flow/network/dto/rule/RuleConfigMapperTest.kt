package com.doublemoon1119.mahjongcraft.flow.network.dto.rule

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.riichi.toRiichiDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.toDomain
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.taiwan.toTaiwanDto
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledNetworkDtos
import kotlin.test.Test
import kotlin.test.assertFailsWith

/** 驗證規則設定的網路 DTO 帶著另一種規則的對局長度時，以參數錯誤拒絕，不讓型別轉換例外外洩。 */
class RuleConfigMapperTest {
    private val registries = DefaultNetworkDtoRegistries().apply { registerBundledNetworkDtos() }
    private val riichi = RiichiRuleConfig().toRiichiDto(registries)
    private val taiwan = TaiwanRuleConfig().toTaiwanDto(registries)

    /** 日麻設定混入台麻的對局長度、或反過來時，都以 [IllegalArgumentException] 拒絕。 */
    @Test
    fun `rule configs reject a game length from another rule`() {
        assertFailsWith<IllegalArgumentException> { riichi.copy(gameLength = taiwan.gameLength).toDomain(registries) }
        assertFailsWith<IllegalArgumentException> { taiwan.copy(gameLength = riichi.gameLength).toDomain(registries) }
    }
}
