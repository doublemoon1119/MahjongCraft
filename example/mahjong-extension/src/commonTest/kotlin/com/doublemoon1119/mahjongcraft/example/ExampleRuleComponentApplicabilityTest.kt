package com.doublemoon1119.mahjongcraft.example

import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingIds
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleModule
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistry
import com.doublemoon1119.mahjongcraft.logic.component.initializeRuleComponents
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializer
import com.doublemoon1119.mahjongcraft.logic.table.RoundAdvancementResult
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.logic.table.testRoundRandomness
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 同時載入內建規則與範例時，每套規則開局與換局只建立屬於自己的組件。 */
class ExampleRuleComponentApplicabilityTest {
    /** 內建規則與範例共用的正式組件登記。 */
    private val registry: ComponentRegistry = bundledCoreExtensionRegistries(additionalExtensions = listOf(ExampleMahjongExtension())).componentRegistry

    /** 日麻、三人日麻只帶日麻資料，台麻沒有規則資料，範例只帶範例資料；開局與換局都一樣。 */
    @Test
    fun `each rule creates only its own components at opening and round change`() {
        val cases = listOf(
            Case(RiichiRuleModule(BuiltInRuleModuleIds.RIICHI, RiichiRuleConfig()), 4, RIICHI_NAMESPACE),
            Case(ThreePlayerRiichiRuleModule(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, ThreePlayerRiichiRuleConfig()), 3, RIICHI_NAMESPACE),
            Case(TaiwanRuleModule(BuiltInRuleModuleIds.TAIWAN, TaiwanRuleConfig()), 4, null),
            Case(ExamplePairingRuleModule(ExamplePairingIds.RULE_MODULE, ExamplePairingRuleConfig()), 2, EXAMPLE_NAMESPACE),
        )
        cases.forEach { case ->
            val opening = GameInitializer.initialize(Uuid.random(), List(case.playerCount) { Uuid.random() }, case.module, randomness = testRoundRandomness())
                .initializeRuleComponents(case.module.id, registry, random = testRoundRandomness().components)
                .tableState
            val next = GameInitializer.startNextRound(
                gameId = opening.id,
                roundAdvancement = RoundAdvancementResult(
                    players = opening.players,
                    dealerPlayerId = opening.dealerPlayerId,
                    roundNumber = 2,
                    comboCount = 0,
                    prevalentWind = opening.prevalentWind,
                    roundSerial = 1,
                ),
                module = case.module,
                randomness = testRoundRandomness(),
            ).initializeRuleComponents(case.module.id, registry, testRoundRandomness().components, opening).tableState

            listOf(opening, next).forEach { state ->
                val ids = state.ruleComponentIds()
                if (case.ownNamespace == null) {
                    assertEquals(emptySet(), ids, case.module.id)
                } else {
                    assertTrue(ids.isNotEmpty(), case.module.id)
                    assertTrue(ids.all { it.startsWith(case.ownNamespace) }, "${case.module.id}: $ids")
                }
            }
        }
    }

    /**
     * 列出對局與玩家帶的全部組件識別碼。
     *
     * @return 對局與各玩家組件的識別碼。
     */
    private fun TableState.ruleComponentIds(): Set<String> = (components.types + players.flatMap { it.components.types }).mapTo(mutableSetOf()) { it.id }

    /**
     * 一套規則的測試情境。
     *
     * @property module 受測規則。
     * @property playerCount 開局人數。
     * @property ownNamespace 這套規則組件的識別碼前綴；不建立任何組件時為 null。
     */
    private data class Case(
        val module: MahjongRuleModule<*>,
        val playerCount: Int,
        val ownNamespace: String?,
    )

    /** 識別碼前綴。 */
    private companion object {
        /** 日麻組件的識別碼前綴。 */
        const val RIICHI_NAMESPACE = "mahjongcraft:riichi/"

        /** 範例組件的識別碼前綴。 */
        const val EXAMPLE_NAMESPACE = "mahjongcraft_example:"
    }
}
