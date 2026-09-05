package com.doublemoon1119.mahjongcraft.platform.fabric.registry

import com.doublemoon1119.mahjongcraft.platform.fabric.entity.MahjongAnimationSounds
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier

/** MahjongCraft 內建聲音事件的註冊入口與可直接播放實例。 */
object ModSounds {
    /** 自由放置麻將牌時播放的落桌聲音。 */
    lateinit var tileDiscardLand: SoundEvent
        private set

    /** 自由放置骰子時播放的落桌聲音。 */
    lateinit var diceLand: SoundEvent
        private set

    /** 自由放置點棒時播放的接觸聲音。 */
    lateinit var scoringStickPlace: SoundEvent
        private set

    /** 註冊全部自訂聲音事件並保存自由物品互動需要的實例。 */
    fun register() {
        tileDiscardLand = register(MahjongAnimationSounds.TILE_DISCARD_LAND)
        register(MahjongAnimationSounds.TILE_MELD_LAND)
        register(MahjongAnimationSounds.DRAW_TILE_LAND)
        register(MahjongAnimationSounds.TILE_HAND_TURN)
        register(MahjongAnimationSounds.WALL_STACK_LAND)
        register(MahjongAnimationSounds.DEAL_BATCH)
        register(MahjongAnimationSounds.DORA_REVEAL)
        diceLand = register(MahjongAnimationSounds.DICE_LAND)
        scoringStickPlace = register(MahjongAnimationSounds.SCORING_STICK_PLACE)
        register(MahjongAnimationSounds.WIN_LIGHTNING, fixedRange = LIGHTNING_RANGE)
    }

    /** 依完整 namespaced ID 建立並註冊聲音事件。 */
    private fun register(soundId: String, fixedRange: Float? = null): SoundEvent {
        val id = Identifier(soundId)
        val soundEvent = fixedRange?.let { SoundEvent.of(id, it) } ?: SoundEvent.of(id)
        return Registry.register(Registries.SOUND_EVENT, id, soundEvent)
    }

    /** 胡牌閃電聲音的最大可聽距離。 */
    private const val LIGHTNING_RANGE: Float = 16.0f
}
