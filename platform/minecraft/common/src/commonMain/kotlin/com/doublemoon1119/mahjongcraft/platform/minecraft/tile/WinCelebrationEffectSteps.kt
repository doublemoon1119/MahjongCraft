package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.MahjongAnimationSounds

/**
 * 胡牌特效的時間軸，對局中的演出與牌譜回放共用，時間以遊戲 tick 計。
 *
 * 攤牌時特效在手牌全部倒下後停一下才開始；不攤牌時手牌不動，特效在演出開始後停一下就開始。特效打在和牌張上：三叉戟先
 * 落下，落地時有落地聲；接著打下閃電，同時有閃電聲。
 */
object WinCelebrationEffectSteps {
    /** 手牌倒下（不攤牌時為演出開始）到特效開始之間的停頓。 */
    const val START_DELAY_TICKS: Long = MahjongTileTableLayout.WIN_PRE_EFFECT_DELAY_TICKS.toLong()

    /** 特效從開始到結束的長度。 */
    const val DURATION_TICKS: Long = MahjongTileTableLayout.WIN_EFFECT_DURATION_TICKS.toLong()

    /**
     * 特效的兩個聲音：三叉戟落地聲與閃電聲。
     *
     * @param startTick 特效開始的遊戲 tick。
     * @return 依播放時刻排列的聲音步驟。
     */
    fun sounds(startTick: Long): List<AnimationStep.PlaySound> = listOf(
        sound(MahjongAnimationSounds.TRIDENT_HIT_GROUND, volume = 1.0f, tick = startTick + MahjongTileTableLayout.WIN_TRIDENT_FALL_DURATION_TICKS),
        sound(MahjongAnimationSounds.WIN_LIGHTNING, volume = 0.9f, tick = startTick + MahjongTileTableLayout.WIN_LIGHTNING_START_TICK),
    )

    /**
     * 建立一個在指定時刻播放的聲音。
     *
     * @param soundId 聲音識別碼。
     * @param volume 音量。
     * @param tick 播放的遊戲 tick。
     * @return 聲音步驟。
     */
    private fun sound(soundId: String, volume: Float, tick: Long): AnimationStep.PlaySound = AnimationStep.PlaySound(
        soundId = soundId,
        volume = volume,
        pitch = 1.0f,
        playAtGameTime = tick,
        expiresAtGameTime = tick + MahjongAnimationSounds.EVENT_GRACE_TICKS,
    )
}
