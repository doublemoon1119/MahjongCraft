package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameFlowConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolution
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
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
    /** 沒有規則選擇的規則，摘要也不會列出規則選擇時間。 */
    @Test
    fun `rule decision time is absent from summaries when the rule does not opt in`() {
        val before = GameConfig(RiichiRuleConfig())
        val after = before.copy(
            flowConfig = before.flowConfig.copy(ruleDecision = RuleDecisionConfig(45)),
        )

        val text = gameConfigDifferenceText(
            resolver = resolver(),
            ruleNames = RuleModuleDisplayNameRegistryImpl(),
            from = before,
            to = after,
        )

        assertTrue(text.siblings.isEmpty(), "A rule without rule decision must not produce a rule decision-time change row")
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

    /** 第三方規則宣告規則選擇後，規則選擇時間的實際差異會出現在摘要中。 */
    @Test
    fun `third party rule decision time changes appear when the rule opts in`() {
        val before = GameConfig(
            ruleConfig = RiichiRuleConfig(),
            flowConfig = GameFlowConfig(
                ruleDecision = RuleDecisionConfig(30),
            ),
        )
        val beforeRuleDecisionSeconds = before.flowConfig.ruleDecision?.baseSeconds ?: error("missing rule decision config")
        val afterRuleDecisionSeconds = beforeRuleDecisionSeconds + 15
        val after = before.copy(
            flowConfig = before.flowConfig.copy(ruleDecision = RuleDecisionConfig(afterRuleDecisionSeconds)),
        )

        val text = gameConfigDifferenceText(
            resolver = resolver(withRuleDecision = true),
            ruleNames = RuleModuleDisplayNameRegistryImpl(),
            from = before,
            to = after,
        )
        val change = text.siblings.single { sibling ->
            val content = sibling.content as? TranslatableTextContent ?: return@single false
            content.key == MinecraftRoomScreenKeys.CONFIG_VALUE_CHANGE &&
                (content.args[0] as Text).content is TranslatableTextContent &&
                ((content.args[0] as Text).content as TranslatableTextContent).key ==
                "mahjongcraft.room.config.field.rule_decision_base_seconds"
        }.content as TranslatableTextContent

        assertEquals(beforeRuleDecisionSeconds.toString(), (change.args[1] as Text).string)
        assertEquals(afterRuleDecisionSeconds.toString(), (change.args[2] as Text).string)
    }

    /** 建立指定是否提供規則選擇時間的第三方設定解析器。 */
    private fun resolver(withRuleDecision: Boolean = false): GameConfigPresentationResolver {
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
        val ruleDecisionResolvers = RuleDecisionResolverRegistry().apply {
            if (withRuleDecision) register(TestRuleDecisionResolver)
        }
        val presentations = GameConfigPresentationRegistryImpl(ruleDecisionResolvers).apply {
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

    private object TestRuleDecisionResolver : RuleDecisionResolver {
        override val ruleModuleId: String = "example:rule"
        override fun ruleDecisionConfig(ruleConfig: MahjongRuleConfig): RuleDecisionConfig = RuleDecisionConfig(30)

        override fun begin(tableState: TableState, ruleModule: MahjongRuleModule<*>) = error("not used")

        override fun resolve(
            tableState: TableState,
            ruleDecision: PendingRuleDecision,
            ruleModule: MahjongRuleModule<*>,
        ): RuleDecisionResolution = error("not used")
    }
}
