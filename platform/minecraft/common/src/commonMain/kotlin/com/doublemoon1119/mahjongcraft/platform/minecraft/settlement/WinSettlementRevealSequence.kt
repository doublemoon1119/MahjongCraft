package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 模板的時間與音效設定。
 *
 * @property initialFadeTicks 初始淡入時間。
 * @property entryStaggerTicks 條目依序出現的間隔。
 * @property scoreRevealTicks 分數揭曉時間。
 * @property readingTicks 閱讀時間。
 * @property entrySoundId 條目音效 ID。
 * @property scoreSoundId 分數音效 ID。
 * @property soundVolume 音效音量。
 * @property soundPitch 音效音高。
 * @property sounds 宣告式聲音清單。
 */
data class WinSettlementRevealSequence(
    val initialFadeTicks: Int = 16,
    val entryStaggerTicks: Int = 8,
    val scoreRevealTicks: Int = 18,
    val readingTicks: Int = 60,
    val entrySoundId: String? = null,
    val scoreSoundId: String? = null,
    val soundVolume: Float = 0.35f,
    val soundPitch: Float = 1f,
    val sounds: List<PresentationSoundCue> = emptyList(),
) {
    init {
        require(initialFadeTicks >= 0 && entryStaggerTicks >= 0 && scoreRevealTicks >= 0 && readingTicks >= 0)
        require(soundVolume >= 0f && soundPitch > 0f)
        require(sounds.size <= 32)
    }
}
