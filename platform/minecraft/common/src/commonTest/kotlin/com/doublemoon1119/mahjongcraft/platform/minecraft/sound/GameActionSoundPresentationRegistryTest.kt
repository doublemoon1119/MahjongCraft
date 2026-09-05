package com.doublemoon1119.mahjongcraft.platform.minecraft.sound

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證規則動作語音的映射、凍結與安全靜音行為。 */
class GameActionSoundPresentationRegistryTest {
    /** 內建日麻的立直與自摸應解析至各自語音。 */
    @Test
    fun `built-in riichi actions resolve their voice sounds`() {
        val registry = GameActionSoundPresentationRegistryImpl().apply {
            registerBuiltInRiichiActionSounds()
            freeze()
        }

        assertEquals(
            BuiltInGameActionVoiceSoundIds.RIICHI,
            registry.find(BuiltInRuleModuleIds.RIICHI, GameAction.Extension(RiichiGameAction.Riichi))?.soundId,
        )
        assertEquals(
            BuiltInGameActionVoiceSoundIds.TSUMO,
            registry.find(BuiltInRuleModuleIds.RIICHI, GameAction.Tsumo)?.soundId,
        )
        assertNull(registry.find(BuiltInRuleModuleIds.TAIWAN, GameAction.Tsumo))
    }

    /** 重複映射與凍結後新增映射都必須立即失敗。 */
    @Test
    fun `duplicate and late registrations fail`() {
        val registry = GameActionSoundPresentationRegistryImpl()
        val definition = GameActionSoundDefinition(
            ruleModuleId = "example:rule",
            actionId = BuiltInGameActionSoundIds.TSUMO,
            presentation = GameActionSoundPresentation("example:voice.tsumo"),
        )
        registry.register(definition)
        assertFailsWith<IllegalArgumentException> { registry.register(definition) }
        registry.freeze()
        assertTrue(registry.isFrozen)
        assertFailsWith<IllegalStateException> {
            registry.register(definition.copy(actionId = BuiltInGameActionSoundIds.RON))
        }
    }
}
