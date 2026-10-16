package com.doublemoon1119.mahjongcraft.platform.fabric.network

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.network.dto.config.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledNetworkDtos
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證一般客戶端要求的大小限制容納得下實際送出的完整內容。 */
class C2SRateLimitsTest {
    /** 每個內建規則的完整房間設定都在房間操作的大小上限內，並保留一半以上的餘裕。 */
    @Test
    fun `room settings for every bundled rule fit the room action limit`() {
        val registries = DefaultNetworkDtoRegistries().apply { registerBundledNetworkDtos() }
        val json = NetworkJson(registries)

        listOf(RiichiRuleConfig(), ThreePlayerRiichiRuleConfig(), TaiwanRuleConfig()).forEach { ruleConfig ->
            val action = RoomActionDto.UpdateConfig(Uuid.random().toString(), GameConfig(ruleConfig).toDto(registries))
            val bytes = json.encodeToString(RoomActionDto.serializer(), action).toByteArray(Charsets.UTF_8).size

            assertTrue(bytes * 2 <= C2SRateLimits.ROOM_ACTION_BYTES, "$ruleConfig uses $bytes bytes")
        }
    }
}
