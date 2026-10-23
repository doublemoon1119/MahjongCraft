package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.ActionTimeControl
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingRuleDecision
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RuleDecisionTiming
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatingPolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SpectatorHandVisibility
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.persistence.format.state.AuthoritativeStatePersistenceCodec
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolution
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolver
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiScoreConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledPersistenceRegistries
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.uuid.Uuid

/** 驗證伺服器依設定呈現重建房間設定：只接受房間畫面能做到的修改。 */
class RoomConfigSubmissionTest {
    /** 房間目前的設定。 */
    private val current = GameConfig(RiichiRuleConfig())

    /** 沒有改動或只改動可編輯欄位且數值在限制內時接受，結果與送來的設定相同。 */
    @Test
    fun `editable changes within limits are accepted`() {
        val edited = current.copy(ruleConfig = RiichiRuleConfig(scoreConfig = RiichiScoreConfig(initialScore = 30_000), redDoraCount = 0))

        assertEquals(RoomConfigSubmission.Result.Accepted(current), review(current))
        assertEquals(RoomConfigSubmission.Result.Accepted(edited), review(edited))
    }

    /** 數值超出編輯器範圍或選項不在清單內時整份拒絕。 */
    @Test
    fun `values the room screen cannot produce are rejected`() {
        val overflow = current.copy(ruleConfig = RiichiRuleConfig(scoreConfig = RiichiScoreConfig(initialScore = Int.MAX_VALUE)))
        val unknownOption = current.copy(ruleConfig = RiichiRuleConfig(redDoraCount = 7))

        assertEquals(RoomConfigSubmission.Result.Invalid, review(overflow))
        assertEquals(RoomConfigSubmission.Result.Invalid, review(unknownOption))
    }

    /** 改動房間畫面上沒有編輯器的內容時整份拒絕。 */
    @Test
    fun `changes to fields without an editor are rejected`() {
        listOf(45, Int.MAX_VALUE).forEach { seconds ->
            val ruleDecision = current.copy(flowConfig = current.flowConfig.copy(ruleDecision = RuleDecisionConfig(seconds)))
            assertEquals(RoomConfigSubmission.Result.Invalid, review(ruleDecision))
            assertEquals(
                RoomConfigSubmission.Result.Invalid,
                review(ruleDecision, current, customPresentations, customModules),
            )
        }
    }

    /** 換成另一個可選規則時，以新規則的預設設定與目前的流程設定為基礎，與房間畫面切換規則相同。 */
    @Test
    fun `switching to another selectable rule starts from its default settings`() {
        val currentWithFlow = current.copy(
            flowConfig = current.flowConfig.copy(
                timeControl = ActionTimeControl.from(12, 90),
                spectatorHandVisibility = SpectatorHandVisibility.HIDDEN,
            ),
        )
        val switched = currentWithFlow.copy(
            ruleConfig = ThreePlayerRiichiRuleConfig(),
            flowConfig = currentWithFlow.flowConfig.copy(ruleDecision = null),
        )

        assertEquals(
            RoomConfigSubmission.Result.Accepted(switched),
            review(switched, currentWithFlow, presentations, modules),
        )
    }

    /** 不可選的規則不能用於房間設定。 */
    @Test
    fun `unselectable rules are unavailable`() {
        assertEquals(RoomConfigSubmission.Result.RuleUnavailable, review(current.copy(ruleConfig = TaiwanRuleConfig())))
    }

    /** 已登記規則但沒有設定呈現時，不會因查不到 schema 而拋例外。 */
    @Test
    fun `missing presentation is unavailable`() {
        assertEquals(
            RoomConfigSubmission.Result.RuleUnavailable,
            review(current, current, customPresentations, modules),
        )
    }

    /** 第三方規則只提供自己的欄位時，共用流程欄位仍可實際修改並通過伺服器審查。 */
    @Test
    fun `third party field and shared flow fields can be submitted together`() {
        val customCurrent = GameConfig(RiichiRuleConfig())
        val submitted = customCurrent.copy(
            ruleConfig = RiichiRuleConfig(scoreConfig = RiichiScoreConfig(initialScore = 30_000)),
            flowConfig = customCurrent.flowConfig.copy(
                timeControl = ActionTimeControl.from(12, 90),
                spectatingPolicy = SpectatingPolicy.DISABLED,
            ),
        )

        assertEquals(
            RoomConfigSubmission.Result.Accepted(submitted),
            review(submitted, customCurrent, customPresentations, customModules),
        )
    }

    /** 第三方規則宣告選擇流程後，規則選擇時間的上下界可提交，超過上限則被伺服器拒絕。 */
    @Test
    fun `third party rule decision time accepts its supported bounds`() {
        val customCurrent = GameConfig(RiichiRuleConfig())
        listOf(1, 3600).forEach { seconds ->
            val submitted = customCurrent.copy(
                flowConfig = customCurrent.flowConfig.copy(ruleDecision = RuleDecisionConfig(seconds)),
            )
            assertEquals(
                RoomConfigSubmission.Result.Accepted(submitted),
                review(submitted, customCurrent, customRuleDecisionPresentations, customModules),
            )
        }

        val overflow = customCurrent.copy(
            flowConfig = customCurrent.flowConfig.copy(ruleDecision = RuleDecisionConfig(3601)),
        )
        assertEquals(
            RoomConfigSubmission.Result.Invalid,
            review(overflow, customCurrent, customRuleDecisionPresentations, customModules),
        )

        val accepted = assertIs<RoomConfigSubmission.Result.Accepted>(
            review(
                customCurrent.copy(flowConfig = customCurrent.flowConfig.copy(ruleDecision = RuleDecisionConfig(47))),
                customCurrent,
                customRuleDecisionPresentations,
                customModules,
            ),
        )
        val room = Room(id = Uuid.random(), hostId = Uuid.random(), gameConfig = accepted.config)
        val codec = AuthoritativeStatePersistenceCodec(bundledPersistenceRegistries())
        val decodedRoom = codec.decode(codec.encode(listOf(room), emptyList())).rooms.getValue(room.id)
        assertEquals(accepted.config, decodedRoom.gameConfig)
    }

    /** 從有規則選擇設定的規則切換到日麻時會清除該設定。 */
    @Test
    fun `switching to a rule without rule decision clears rule decision time`() {
        val currentWithRuleDecision = GameConfig(RiichiRuleConfig()).copy(
            flowConfig = current.flowConfig.copy(ruleDecision = RuleDecisionConfig(45)),
        )
        val switched = currentWithRuleDecision.copy(
            ruleConfig = ThreePlayerRiichiRuleConfig(),
            flowConfig = currentWithRuleDecision.flowConfig.copy(ruleDecision = null),
        )
        assertEquals(
            RoomConfigSubmission.Result.Accepted(switched),
            review(switched, currentWithRuleDecision, mixedPresentations, mixedModules),
        )

        val changedAfterSwitch = switched.copy(
            flowConfig = switched.flowConfig.copy(ruleDecision = RuleDecisionConfig(46)),
        )
        assertEquals(
            RoomConfigSubmission.Result.Invalid,
            review(changedAfterSwitch, currentWithRuleDecision, mixedPresentations, mixedModules),
        )
    }

    /** 從沒有規則選擇設定的規則切換到支援規則時，會建立核心註冊的預設規則選擇設定。 */
    @Test
    fun `switching to a rule decision rule creates its default rule decision time`() {
        val source = GameConfig(
            ruleConfig = ThreePlayerRiichiRuleConfig(),
            flowConfig = current.flowConfig.copy(
                timeControl = ActionTimeControl.from(12, 90),
                spectatorHandVisibility = SpectatorHandVisibility.HIDDEN,
            ),
        )
        val submitted = GameConfig(
            ruleConfig = RiichiRuleConfig(),
            flowConfig = source.flowConfig.copy(ruleDecision = RuleDecisionConfig(30)),
        )

        assertEquals(
            RoomConfigSubmission.Result.Accepted(submitted),
            review(submitted, source, mixedPresentations, mixedModules),
        )
    }

    /** 有規則選擇的規則若缺少規則選擇設定，伺服器拒絕不完整的提交。 */
    @Test
    fun `rule decision rule without rule decision config is rejected`() {
        val source = GameConfig(ThreePlayerRiichiRuleConfig())
        val submitted = GameConfig(RiichiRuleConfig())

        assertEquals(
            RoomConfigSubmission.Result.Invalid,
            review(submitted, source, mixedPresentations, mixedModules),
        )
    }

    /** 規則設定開啟規則選擇時接受自訂時間，關閉後清除舊時間並拒絕偷帶的時間。 */
    @Test
    fun `config aware rule decision is rebuilt from the effective rule config`() {
        val off = GameConfig(RiichiRuleConfig())
        val on = off.copy(
            ruleConfig = RiichiRuleConfig(scoreConfig = RiichiScoreConfig(initialScore = 30_000)),
            flowConfig = off.flowConfig.copy(ruleDecision = RuleDecisionConfig(47)),
        )
        val enabled = assertIs<RoomConfigSubmission.Result.Accepted>(
            review(on, off, configAwareRuleDecisionPresentations, customModules),
        )
        assertEquals(47, enabled.config.flowConfig.ruleDecision?.baseSeconds)
        assertEquals(
            RoomConfigSubmission.Result.Accepted(off),
            review(off, enabled.config, configAwareRuleDecisionPresentations, customModules),
        )
        assertEquals(
            RoomConfigSubmission.Result.Invalid,
            review(
                off.copy(flowConfig = off.flowConfig.copy(ruleDecision = RuleDecisionConfig(47))),
                enabled.config,
                configAwareRuleDecisionPresentations,
                customModules,
            ),
        )
    }

    /** 草稿先選擇手牌可見度再關閉觀戰，仍可提交；停用控制項不會清除先前選好的偏好。 */
    @Test
    fun `visibility selected before disabling spectating remains a valid draft`() {
        val definition = requireNotNull(presentations.find("mahjongcraft:riichi"))
        val visibility = definition.fields.single { it.id == "mahjongcraft:spectator_hand_visibility" }
        val spectating = definition.fields.single { it.id == "mahjongcraft:spectating_policy" }
        assertEquals(true, visibility.isEnabled(current))
        val withHiddenHands = requireNotNull(visibility.update)(
            current,
            GameConfigPresentationValue.ChoiceValue("mahjongcraft:hidden"),
        )
        val submitted = requireNotNull(spectating.update)(
            withHiddenHands,
            GameConfigPresentationValue.ChoiceValue("mahjongcraft:disabled"),
        )

        assertEquals(false, visibility.isEnabled(submitted))
        assertEquals(SpectatorHandVisibility.HIDDEN, submitted.flowConfig.spectatorHandVisibility)
        assertEquals(RoomConfigSubmission.Result.Accepted(submitted), review(submitted))
    }

    /** 編輯器判斷值的種類與限制；整數只檢查範圍與可否為空。 */
    @Test
    fun `editor specs accept only values they can produce`() {
        val integer = GameConfigEditorSpec.IntegerInput(minimum = 0, maximum = 10, step = 5)
        val nullableInteger = integer.copy(nullable = true)
        val choice = GameConfigEditorSpec.SingleChoice(listOf("test:a", "test:b"))

        assertEquals(true, integer.accepts(GameConfigPresentationValue.IntegerValue(3)))
        assertEquals(false, integer.accepts(GameConfigPresentationValue.IntegerValue(11)))
        assertEquals(false, integer.accepts(GameConfigPresentationValue.IntegerValue(null)))
        assertEquals(true, nullableInteger.accepts(GameConfigPresentationValue.IntegerValue(null)))
        assertEquals(true, choice.accepts(GameConfigPresentationValue.ChoiceValue("test:b")))
        assertEquals(false, choice.accepts(GameConfigPresentationValue.ChoiceValue("other:b")))
        assertEquals(false, GameConfigEditorSpec.BooleanToggle.accepts(GameConfigPresentationValue.IntegerValue(1)))
    }

    /**
     * 以內建設定呈現與規則模組審查送來的設定。
     *
     * @param submitted 客戶端送來的設定。
     * @return 審查結果。
     */
    private fun review(submitted: GameConfig): RoomConfigSubmission.Result = review(submitted, current, presentations, modules)

    /**
     * 使用指定的目前設定與 registry 審查送來的設定。
     *
     * @param submitted 客戶端送來的設定。
     * @param current 伺服器目前的設定。
     * @param presentations 設定呈現 registry。
     * @param modules 規則模組 registry。
     * @return 審查結果。
     */
    private fun review(
        submitted: GameConfig,
        current: GameConfig,
        presentations: GameConfigPresentationRegistry,
        modules: MahjongModuleRegistry,
    ): RoomConfigSubmission.Result = RoomConfigSubmission.review(
        submitted = submitted,
        current = current,
        presentations = presentations,
        moduleRegistry = modules,
    )

    private companion object {
        /** 內建設定呈現。 */
        val presentations: GameConfigPresentationRegistry = GameConfigPresentationRegistryImpl(
            RuleDecisionResolverRegistry(),
        ).apply {
            BundledMinecraftMahjongExtensions.all.forEach { it.registerGameConfigPresentations(this) }
        }

        /** 內建規則模組。 */
        val modules: MahjongModuleRegistry = MahjongModuleRegistryImpl().apply {
            registerBundledRuleModules()
            freeze()
        }

        /** 只包含第三方測試規則的設定呈現。 */
        val customPresentations: GameConfigPresentationRegistry = GameConfigPresentationRegistryImpl(
            RuleDecisionResolverRegistry(),
        ).apply {
            register(customDefinition())
        }

        /** 宣告需要規則選擇流程的第三方測試規則設定呈現。 */
        val customRuleDecisionPresentations: GameConfigPresentationRegistry = GameConfigPresentationRegistryImpl(ruleDecisionResolverRegistry()).apply {
            register(customDefinition())
        }

        /** 規則設定達到門檻時才提供規則選擇的設定呈現。 */
        val configAwareRuleDecisionPresentations: GameConfigPresentationRegistry =
            GameConfigPresentationRegistryImpl(ruleDecisionResolverRegistry(configAware = true)).apply {
                register(customDefinition())
            }

        /** 只包含第三方測試規則的模組 registry。 */
        val customModules: MahjongModuleRegistry = MahjongModuleRegistryImpl().apply {
            register(
                MahjongModuleDefinition(RiichiRuleConfig::class, "example:pairing") { config, id ->
                    RiichiRuleModule(id, config)
                },
            )
            freeze()
        }

        /** 同時包含有規則選擇的第三方規則與沒有規則選擇的內建規則的設定呈現。 */
        val mixedPresentations: GameConfigPresentationRegistry = GameConfigPresentationRegistryImpl(ruleDecisionResolverRegistry()).apply {
            BundledMinecraftMahjongExtensions.all.forEach { it.registerGameConfigPresentations(this) }
            register(customDefinition())
        }

        /** 同時包含有規則選擇的第三方規則與沒有規則選擇的內建規則的模組 registry。 */
        val mixedModules: MahjongModuleRegistry = MahjongModuleRegistryImpl().apply {
            register(
                MahjongModuleDefinition(RiichiRuleConfig::class, "example:pairing") { config, id ->
                    RiichiRuleModule(id, config)
                },
            )
            register(
                MahjongModuleDefinition(ThreePlayerRiichiRuleConfig::class, "mahjongcraft:riichi_three_player") { config, id ->
                    ThreePlayerRiichiRuleModule(id, config)
                },
            )
            freeze()
        }

        /** 建立第三方測試規則的設定呈現。 */
        private fun customDefinition() = GameConfigPresentationDefinition(
            ruleModuleId = "example:pairing",
            descriptionTranslationKey = "example.rule.description",
            selectable = true,
            defaultRuleConfig = ::RiichiRuleConfig,
            categories = listOf(GameConfigCategoryDefinition("example:rule", "example.rule")),
            fields = listOf(
                GameConfigFieldDefinition(
                    id = "example:initial_score",
                    categoryId = "example:rule",
                    nameTranslationKey = "example.initial_score",
                    descriptionTranslationKey = "example.initial_score.description",
                    editor = GameConfigEditorSpec.IntegerInput(1, 100_000),
                    isEditable = true,
                    read = { config ->
                        GameConfigPresentationValue.IntegerValue((config.ruleConfig as RiichiRuleConfig).scoreConfig.initialScore)
                    },
                    update = { config, value ->
                        val score = (value as GameConfigPresentationValue.IntegerValue).number
                            ?: error("missing initial score")
                        config.copy(
                            ruleConfig = (config.ruleConfig as RiichiRuleConfig).copy(
                                scoreConfig = (config.ruleConfig as RiichiRuleConfig).scoreConfig.copy(initialScore = score),
                            ),
                        )
                    },
                ),
            ),
        )

        private fun ruleDecisionResolverRegistry(configAware: Boolean = false) = RuleDecisionResolverRegistry().apply {
            register(object : RuleDecisionResolver {
                override val ruleModuleId: String = "example:pairing"
                override fun ruleDecisionConfig(ruleConfig: MahjongRuleConfig): RuleDecisionConfig? = if (!configAware || (ruleConfig as? RiichiRuleConfig)?.scoreConfig?.initialScore == 30_000) {
                    RuleDecisionConfig(30)
                } else {
                    null
                }

                override fun begin(timing: RuleDecisionTiming, tableState: TableState, ruleModule: MahjongRuleModule<*>) = error("not used")

                override fun resolve(
                    tableState: TableState,
                    decision: PendingRuleDecision,
                    ruleModule: MahjongRuleModule<*>,
                ): RuleDecisionResolution = error("not used")
            })
        }
    }
}
