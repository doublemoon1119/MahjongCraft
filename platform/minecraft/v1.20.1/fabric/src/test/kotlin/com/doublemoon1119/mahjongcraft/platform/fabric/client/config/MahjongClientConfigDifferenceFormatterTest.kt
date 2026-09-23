package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.AutomaticControlDisplayResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.client.automatic.ClientAutomaticControlDraftState
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistryImpl
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 設定頁底部與未保存變更確認畫面共用的差異文字。 */
class MahjongClientConfigDifferenceFormatterTest {
    @Test
    fun `visibility changes include automatic control status`() {
        val from = MahjongClientConfigState()
        val to = from.copy(
            presentationVisibility = from.presentationVisibility.copy(automaticControlStatusEnabled = false),
        )

        val summary = clientConfigDifferenceText(from, to)

        val changeNames = summary.siblings.mapNotNull { sibling ->
            val change = sibling.content as? TranslatableTextContent ?: return@mapNotNull null
            (change.args.firstOrNull() as? Text)?.content as? TranslatableTextContent
        }

        assertTrue(changeNames.any { it.key == "mahjongcraft.client_config.presentation.automatic_control_status" })
    }

    @Test
    fun `remote-only changes include registered and unknown control names`() {
        val registry = AutomaticControlDisplayRegistryImpl().apply {
            register("example:auto", AutomaticControlDisplay("example.auto", "example.auto.description", 1))
            freeze()
        }
        val baseline = AutomaticControlSnapshotDto(
            gameId = "game",
            revision = 1,
            supportedControlIds = setOf("example:auto", "example:unknown"),
            enabledControlIds = setOf("example:auto"),
        )
        val remote = ClientAutomaticControlDraftState(
            baseline = baseline,
            enabledControlIds = setOf("example:unknown"),
            pendingRequestId = null,
            stale = false,
            failure = null,
            unavailable = false,
        )
        val config = MahjongClientConfigState()

        val summary = clientConfigDifferenceText(config, config, remote, AutomaticControlDisplayResolver(registry))
        val changes = summary.siblings.mapNotNull { it.content as? TranslatableTextContent }
        val names = changes.map { assertIs<Text>(it.args[0]) }

        assertEquals(2, changes.size)
        assertEquals("example.auto", assertIs<TranslatableTextContent>(names[0].content).key)
        assertTrue(names[1].string.contains("example:unknown"))
        assertEquals("mahjongcraft.client_config.enabled", assertIs<TranslatableTextContent>(assertIs<Text>(changes[0].args[1]).content).key)
        assertEquals("mahjongcraft.client_config.disabled", assertIs<TranslatableTextContent>(assertIs<Text>(changes[0].args[2]).content).key)
    }
}
