package com.doublemoon1119.mahjongcraft.platform.minecraft.animation

/**
 * 描述動畫佇列裡的一個步驟。[WaitUntil]／[Teleport]／[SetInvisible]／[PlayMotion] 是所有動畫實體共用的
 * 最小集合；[Custom] 讓個別實體類型（例如麻將牌的姿態切換）附加自己專屬的瞬間動作，用型別 [C]
 * 承載，不透過 lambda 表達——lambda 無法序列化進世界存檔，而佇列本身要能完整持久化／還原正是這個
 * 型別存在的前提，理由見驅動佇列的抽象基底類別 KDoc。
 *
 * [WaitUntil]／[Teleport]／[SetInvisible]／[PlayMotion] 本身不需要 [C]，用 [Nothing] 實作
 * [AnimationStep]（`out C` 讓 [AnimationStep]<[Nothing]> 依協變規則相容於任何 `AnimationStep<C>`），
 * 只有 [Custom] 真正攜帶實體專屬的資料。
 */
sealed interface AnimationStep<out C> {
    /**
     * 等到絕對 game time [gameTime] 為止，不做任何其他動作。刻意不提供「等待 N 個相對 tick」的表達
     * 方式：這個 step 可能被疊加到一個還沒清空的既有佇列後面（例如發牌佇列疊加在牌牆掉落動畫還沒
     * 播完的佇列後面），相對等待要等真正輪到才起算，殘留卡得越久就拖得越晚，導致本該同時收斂到同一
     * 絕對時刻的多個 entity（例如「同一次抓的牌一起起飛」）各自延遲不同而分散開——這是遊戲內實際
     * 踩過的問題。[WaitUntil] 不管佇列殘留多久才輪到，一旦輪到就直接比較寫死的目標值，不會被拖慢。
     */
    data class WaitUntil(val gameTime: Long) : AnimationStep<Nothing>

    /** 立即修改真實／持久化座標與朝向（瞬間動作，等同直接呼叫 `refreshPositionAndAngles`）。 */
    data class Teleport(val x: Double, val y: Double, val z: Double, val yaw: Float) : AnimationStep<Nothing>

    /** 立即切換 vanilla 隱形旗標（瞬間動作）。 */
    data class SetInvisible(val invisible: Boolean) : AnimationStep<Nothing>

    /**
     * 在 entity 目前的世界座標播放一次聲音。[soundId] 必須是完整 namespaced ID；
     * [playAtGameTime] 是預定播放的絕對 server game time，[expiresAtGameTime] 是允許播放的最後時間，
     * 避免 chunk 長時間卸載後補播已過期事件。聲音 cue 不阻塞同一佇列中的運動 step。
     */
    data class PlaySound(
        val soundId: String,
        val volume: Float,
        val pitch: Float,
        val playAtGameTime: Long,
        val expiresAtGameTime: Long,
    ) : AnimationStep<Nothing> {
        init {
            require(soundId.substringBefore(':', "").isNotBlank() && soundId.substringAfter(':', "").isNotBlank()) {
                "Animation sound ID must be namespaced: $soundId"
            }
            require(volume.isFinite() && volume >= 0.0f) { "Animation sound volume must be finite and non-negative" }
            require(pitch.isFinite() && pitch > 0.0f) { "Animation sound pitch must be finite and positive" }
            require(playAtGameTime >= 0L) { "Animation sound time must not be negative" }
            require(expiresAtGameTime >= playAtGameTime) { "Animation sound expiry must not precede its play time" }
        }
    }

    /**
     * 播放一段既有的運動動畫（起飛／落下等視覺位移，對應各平台既有 `startMotionAnimation` 的參數
     * 語意）：真實座標不受影響，只驅動 render 端的位移／姿態內插；[durationTicks] 到期前這個 step
     * 會讓佇列停在原地，不處理後續 step。
     *
     * [easeRotation] 預設 `false`：姿態旋轉角內插維持純線性，這是開局發牌翻牌／摸牌換面等既有呼叫點
     * 已經驗證過手感的既有行為，不因為新增這個參數而改變。只有明確需要旋轉本身也先快後慢（跟位移的
     * ease-out 曲線一致，銜接下一個 step 時才不會有旋轉忽然停止的生硬感）的呼叫點才傳 `true`。
     */
    data class PlayMotion(
        val durationTicks: Int,
        val arcHeight: Double,
        val startOffsetX: Double,
        val startOffsetY: Double,
        val startOffsetZ: Double,
        val startPoseRotationDegrees: Float,
        val endPoseRotationDegrees: Float,
        val easeRotation: Boolean = false,
    ) : AnimationStep<Nothing>

    /** 實體專屬的瞬間動作（例如麻將牌的姿態切換），交給該實體類型自己解讀與套用。 */
    data class Custom<C>(val step: C) : AnimationStep<C>
}
