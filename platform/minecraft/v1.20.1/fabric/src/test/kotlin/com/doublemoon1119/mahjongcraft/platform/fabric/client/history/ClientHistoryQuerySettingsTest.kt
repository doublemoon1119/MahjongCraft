package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryListRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistoryRuleSettingsRequestDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.HistorySummaryRequestDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftHistoryConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryQuerySettingsPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

/** 驗證伺服器公布的歷史查詢間隔邊界與工作階段清理。 */
class ClientHistoryQuerySettingsTest {
    /** 合法值會套用，超出共用邊界的值會保留原本設定。 */
    @Test
    fun `test apply accepts valid value and ignores invalid values`() {
        val settings = ClientHistoryQuerySettings()
        settings.apply(HistoryQuerySettingsPayload(500))
        assertEquals(500.milliseconds, settings.minimumInterval.value)

        settings.apply(HistoryQuerySettingsPayload(MinecraftHistoryConfig.MIN_QUERY_MINIMUM_INTERVAL_MILLISECONDS - 1))
        assertEquals(500.milliseconds, settings.minimumInterval.value)
        settings.apply(HistoryQuerySettingsPayload(MinecraftHistoryConfig.MAX_QUERY_MINIMUM_INTERVAL_MILLISECONDS + 1))
        assertEquals(500.milliseconds, settings.minimumInterval.value)
    }

    /** reset 會恢復與伺服器設定共享的預設間隔。 */
    @Test
    fun `test reset restores default interval`() {
        val settings = ClientHistoryQuerySettings()
        settings.apply(HistoryQuerySettingsPayload(500))
        settings.reset()

        assertEquals(
            MinecraftHistoryConfig.DEFAULT_QUERY_MINIMUM_INTERVAL_MILLISECONDS.milliseconds,
            settings.minimumInterval.value,
        )
    }

    /** 協調器 clear 會清除要求並恢復查詢間隔預設值。 */
    @Test
    fun `test coordinator clear resets settings`() {
        val settings = ClientHistoryQuerySettings()
        settings.apply(HistoryQuerySettingsPayload(500))
        val coordinator = ClientHistoryQueryCoordinator(
            sender = object : HistoryQuerySender {
                override fun sendList(request: HistoryListRequestDto) = Unit
                override fun sendSummary(request: HistorySummaryRequestDto) = Unit
                override fun sendRuleSettings(request: HistoryRuleSettingsRequestDto) = Unit
            },
            settings = settings,
        )

        coordinator.clear()

        assertIs<ClientHistoryQueryState.Idle>(coordinator.state.value)
        assertEquals(
            MinecraftHistoryConfig.DEFAULT_QUERY_MINIMUM_INTERVAL_MILLISECONDS.milliseconds,
            settings.minimumInterval.value,
        )
    }
}
