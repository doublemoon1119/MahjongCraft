package com.doublemoon1119.mahjongcraft.platform.fabric.client.config

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.AutomaticControlDisplayResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.automatic.ClientAutomaticControlDraftState
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** 設定頁底部與未保存變更確認畫面共用的差異文字。 */
class MahjongClientConfigDifferenceFormatterTest {
    /** 呈現可見性變更時列出自動操作狀態 HUD。 */
    @Test
    fun `visibility changes include automatic control status`() {
        val from = MahjongClientConfigState()
        val to = from.copy(
            presentationVisibility = from.presentationVisibility.copy(automaticControlStatusEnabled = false),
        )

        val summary = clientConfigDifferenceText(from, to)

        val changeNames = summary.siblings.mapNotNull { sibling ->
            val change = sibling.contents as? TranslatableContents ?: return@mapNotNull null
            (change.args.firstOrNull() as? Component)?.contents as? TranslatableContents
        }

        assertTrue(changeNames.any { it.key == "mahjongcraft.client_config.presentation.automatic_control_status" })
    }

    /** HUD 位置異動時顯示自動操作狀態的位置差異。 */
    @Test
    fun `hud layout changes include the automatic control status position`() {
        val from = MahjongClientConfigState()
        val to = from.copy(
            hudLayout = from.hudLayout.copy(
                automaticControlStatusX = from.hudLayout.automaticControlStatusX + 0.1,
                automaticControlStatusY = from.hudLayout.automaticControlStatusY + 0.1,
            ),
        )

        val summary = clientConfigDifferenceText(from, to)

        val change = summary.siblings
            .mapNotNull { it.contents as? TranslatableContents }
            .single { it.key == "mahjongcraft.hud_layout.change.xy" }
        val name = assertIs<TranslatableContents>(assertIs<Component>(change.args[0]).contents)

        assertEquals("mahjongcraft.hud_layout.automatic_control_status", name.key)
    }

    /** 遠端本局草稿差異能顯示已登記名稱與未知 ID 回退名稱。 */
    @Test
    fun `remote-only changes include registered and unknown control names`() {
        val registry = AutomaticControlDisplayRegistryImpl().apply {
            register(AutomaticControlDisplayDefinition("example:auto", AutomaticControlDisplay("example.auto", "example.auto.description", 1)))
            freeze()
        }
        val baseline = AutomaticControlSnapshotDto(
            gameId = "game",
            ruleModuleId = "mahjongcraft:riichi",
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
        val changes = summary.siblings.mapNotNull { it.contents as? TranslatableContents }
        val names = changes.map { assertIs<Component>(it.args[0]) }

        assertEquals(2, changes.size)
        assertEquals("example.auto", assertIs<TranslatableContents>(names[0].contents).key)
        assertTrue(names[1].string.contains("example:unknown"))
        assertEquals("mahjongcraft.client_config.enabled", assertIs<TranslatableContents>(assertIs<Component>(changes[0].args[1]).contents).key)
        assertEquals("mahjongcraft.client_config.disabled", assertIs<TranslatableContents>(assertIs<Component>(changes[0].args[2]).contents).key)
    }
}
