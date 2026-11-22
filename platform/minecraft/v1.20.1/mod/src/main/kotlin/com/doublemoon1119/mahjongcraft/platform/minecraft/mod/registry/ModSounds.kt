package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.registry

import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.MahjongAnimationSounds
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.RegisteredEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.RiichiGameActionVoiceSoundIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.TaiwanGameActionVoiceSoundIds
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent

/** MahjongCraft 內建聲音事件的集中登記點；可直接播放的實例在 [register] 登記完成後才能讀取。 */
object ModSounds {
    private lateinit var tileDiscardLandEntry: RegisteredEntry<SoundEvent>
    private lateinit var diceLandEntry: RegisteredEntry<SoundEvent>
    private lateinit var scoringStickPlaceEntry: RegisteredEntry<SoundEvent>
    private lateinit var rulerPlaceEntry: RegisteredEntry<SoundEvent>

    /** 自由放置麻將牌時播放的落桌聲音。 */
    val tileDiscardLand: SoundEvent
        get() = tileDiscardLandEntry.get()

    /** 自由放置骰子時播放的落桌聲音。 */
    val diceLand: SoundEvent
        get() = diceLandEntry.get()

    /** 自由放置點棒時播放的接觸聲音。 */
    val scoringStickPlace: SoundEvent
        get() = scoringStickPlaceEntry.get()

    /** 放下牌尺時播放的接觸聲音。 */
    val rulerPlace: SoundEvent
        get() = rulerPlaceEntry.get()

    /**
     * 登記全部自訂聲音事件並保存自由物品互動需要的實例。
     *
     * @param registrar 寫入登記表的方式。
     */
    fun register(registrar: ModRegistrar) {
        tileDiscardLandEntry = registrar.registerSound(MahjongAnimationSounds.TILE_DISCARD_LAND)
        registrar.registerSound(MahjongAnimationSounds.TILE_MELD_LAND)
        registrar.registerSound(MahjongAnimationSounds.DRAW_TILE_LAND)
        registrar.registerSound(MahjongAnimationSounds.TILE_HAND_TURN)
        registrar.registerSound(MahjongAnimationSounds.WALL_STACK_LAND)
        registrar.registerSound(MahjongAnimationSounds.DEAL_BATCH)
        registrar.registerSound(MahjongAnimationSounds.WALL_REVEAL)
        diceLandEntry = registrar.registerSound(MahjongAnimationSounds.DICE_LAND)
        scoringStickPlaceEntry = registrar.registerSound(MahjongAnimationSounds.SCORING_STICK_PLACE)
        rulerPlaceEntry = registrar.registerSound(MahjongAnimationSounds.RULER_PLACE)
        registrar.registerSound(MahjongAnimationSounds.WIN_LIGHTNING, fixedRange = LIGHTNING_RANGE)
        RiichiGameActionVoiceSoundIds.ALL.forEach { registrar.registerSound(it) }
        TaiwanGameActionVoiceSoundIds.ALL.forEach { registrar.registerSound(it) }
    }

    /**
     * 依完整 namespaced ID 建立並登記聲音事件。
     *
     * @param soundId 要登記的聲音事件完整 ID。
     * @param fixedRange 指定時使用的固定最大可聽距離；省略時使用一般聲音事件範圍。
     * @return 登記完成後可讀取事件實例的項目。
     */
    private fun ModRegistrar.registerSound(soundId: String, fixedRange: Float? = null): RegisteredEntry<SoundEvent> {
        val id = ResourceLocation(soundId)
        return register(registry = Registries.SOUND_EVENT, id = id) {
            fixedRange?.let { SoundEvent.createFixedRangeEvent(id, it) } ?: SoundEvent.createVariableRangeEvent(id)
        }
    }

    /** 胡牌閃電聲音的最大可聽距離。 */
    private const val LIGHTNING_RANGE: Float = 16.0f
}
