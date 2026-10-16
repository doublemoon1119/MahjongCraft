package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiScoreConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import kotlin.test.Test
import kotlin.test.assertEquals

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
        val preparation = current.copy(flowConfig = current.flowConfig.copy(preparationBaseSeconds = Int.MAX_VALUE))

        assertEquals(RoomConfigSubmission.Result.Invalid, review(preparation))
    }

    /** 換成另一個可選規則時，以新規則的預設設定與目前的流程設定為基礎，與房間畫面切換規則相同。 */
    @Test
    fun `switching to another selectable rule starts from its default settings`() {
        val switched = current.copy(ruleConfig = ThreePlayerRiichiRuleConfig())

        assertEquals(RoomConfigSubmission.Result.Accepted(switched), review(switched))
    }

    /** 不可選的規則不能用於房間設定。 */
    @Test
    fun `unselectable rules are unavailable`() {
        assertEquals(RoomConfigSubmission.Result.RuleUnavailable, review(current.copy(ruleConfig = TaiwanRuleConfig())))
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
    private fun review(submitted: GameConfig): RoomConfigSubmission.Result = RoomConfigSubmission.review(
        submitted = submitted,
        current = current,
        presentations = presentations,
        moduleRegistry = modules,
    )

    private companion object {
        /** 內建設定呈現。 */
        val presentations: GameConfigPresentationRegistry = GameConfigPresentationRegistryImpl().apply {
            BundledMinecraftMahjongExtensions.all.forEach { it.registerGameConfigPresentations(this) }
        }

        /** 內建規則模組。 */
        val modules: MahjongModuleRegistry = MahjongModuleRegistryImpl().apply {
            registerBundledRuleModules()
            freeze()
        }
    }
}
