package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRoundPreparation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundPreparationConfig
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolution
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.MinecraftRoomScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistryImpl
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 驗證房間變更摘要會使用 registry 自動加入的共用流程欄位。 */
class GameConfigDifferenceFormatterTest {
    /** 不使用準備操作的規則，摘要也不會列出準備時間。 */
    @Test
    fun `preparation time is absent from summaries when the rule does not opt in`() {
        val before = GameConfig(RiichiRuleConfig())
        val after = before.copy(
            flowConfig = before.flowConfig.copy(roundPreparation = RoundPreparationConfig(45)),
        )

        val text = gameConfigDifferenceText(
            resolver = resolver(),
            ruleNames = RuleModuleDisplayNameRegistryImpl(),
            from = before,
            to = after,
        )

        assertTrue(text.siblings.isEmpty(), "A rule without preparation must not produce a preparation-time change row")
    }

    /** 第三方不宣告任何流程欄位，修改基本思考時間仍能產生本地化的差異摘要。 */
    @Test
    fun `third party base time changes appear in the shared difference formatter`() {
        val before = GameConfig(RiichiRuleConfig())
        val beforeBaseSeconds = before.flowConfig.timeControl.baseSeconds
        val afterBaseSeconds = beforeBaseSeconds + 15
        val after = before.copy(
            flowConfig = before.flowConfig.copy(
                timeControl = ActionTimeControl.from(afterBaseSeconds, before.flowConfig.timeControl.reserveSeconds),
            ),
        )

        val text = gameConfigDifferenceText(
            resolver = resolver(),
            ruleNames = RuleModuleDisplayNameRegistryImpl(),
            from = before,
            to = after,
        )
        val change = text.siblings.last().content as TranslatableTextContent

        assertEquals(MinecraftRoomScreenKeys.CONFIG_VALUE_CHANGE, change.key)
        assertEquals(
            "mahjongcraft.room.config.field.base_seconds",
            ((change.args[0] as Text).content as TranslatableTextContent).key,
        )
        assertEquals(beforeBaseSeconds.toString(), (change.args[1] as Text).string)
        assertEquals(afterBaseSeconds.toString(), (change.args[2] as Text).string)
    }

    /** 第三方規則宣告開局準備後，準備時間的實際差異會出現在摘要中。 */
    @Test
    fun `third party preparation time changes appear when the rule opts in`() {
        val before = GameConfig(
            ruleConfig = RiichiRuleConfig(),
            flowConfig = GameFlowConfig(
                roundPreparation = RoundPreparationConfig(30),
            ),
        )
        val beforePreparationSeconds = before.flowConfig.roundPreparation?.baseSeconds ?: error("missing preparation config")
        val afterPreparationSeconds = beforePreparationSeconds + 15
        val after = before.copy(
            flowConfig = before.flowConfig.copy(roundPreparation = RoundPreparationConfig(afterPreparationSeconds)),
        )

        val text = gameConfigDifferenceText(
            resolver = resolver(withPreparation = true),
            ruleNames = RuleModuleDisplayNameRegistryImpl(),
            from = before,
            to = after,
        )
        val change = text.siblings.single { sibling ->
            val content = sibling.content as? TranslatableTextContent ?: return@single false
            content.key == MinecraftRoomScreenKeys.CONFIG_VALUE_CHANGE &&
                (content.args[0] as Text).content is TranslatableTextContent &&
                ((content.args[0] as Text).content as TranslatableTextContent).key ==
                "mahjongcraft.room.config.field.preparation_base_seconds"
        }.content as TranslatableTextContent

        assertEquals(beforePreparationSeconds.toString(), (change.args[1] as Text).string)
        assertEquals(afterPreparationSeconds.toString(), (change.args[2] as Text).string)
    }

    /** 建立指定是否提供局前準備時間的第三方設定解析器。 */
    private fun resolver(withPreparation: Boolean = false): GameConfigPresentationResolver {
        val modules = MahjongModuleRegistryImpl().apply {
            register(
                MahjongModuleDefinition(
                    id = "example:rule",
                    configClass = RiichiRuleConfig::class,
                    factory = { config, id -> RiichiRuleModule(id, config) },
                ),
            )
            freeze()
        }
        val preparationResolvers = RoundPreparationResolverRegistry().apply {
            if (withPreparation) register(TestPreparationResolver)
        }
        val presentations = GameConfigPresentationRegistryImpl(preparationResolvers).apply {
            register(
                GameConfigPresentationDefinition(
                    ruleModuleId = "example:rule",
                    descriptionTranslationKey = "example.rule.description",
                    selectable = true,
                    defaultRuleConfig = ::RiichiRuleConfig,
                    categories = emptyList(),
                    fields = emptyList(),
                ),
            )
            freeze()
        }
        return GameConfigPresentationResolver(presentations, modules)
    }

    private object TestPreparationResolver : RoundPreparationResolver {
        override val ruleModuleId: String = "example:rule"
        override val defaultRoundPreparationConfig: RoundPreparationConfig = RoundPreparationConfig(30)

        override fun begin(tableState: TableState, ruleModule: MahjongRuleModule<*>) = error("not used")

        override fun resolve(
            tableState: TableState,
            preparation: PendingRoundPreparation,
            ruleModule: MahjongRuleModule<*>,
        ): RoundPreparationResolution = error("not used")
    }
}
