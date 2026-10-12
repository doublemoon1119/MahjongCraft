package com.doublemoon1119.mahjongcraft.platform.minecraft.animation

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftResourceIds

/**
 * 描述動畫佇列裡的一個步驟。[WaitUntil]／[Teleport]／[SetInvisible]／[PlayMotion] 是所有動畫實體共用的
 * 最小集合；[Custom] 讓個別實體類型（例如麻將牌的姿態切換）附加自己專屬的瞬間動作，用型別 [C]
 * 承載，不透過 lambda 表達——lambda 無法序列化進世界存檔，而佇列本身要能完整持久化／還原正是這個
 * 型別存在的前提，理由見驅動佇列的抽象基底類別 KDoc。
 *
 * [WaitUntil]／[Teleport]／[SetInvisible]／[PlayMotion] 本身不需要 [C]，用 [Nothing] 實作
 * [AnimationStep]（`out C` 讓 [AnimationStep]<[Nothing]> 依協變規則相容於任何 `AnimationStep<C>`），
 * 只有 [Custom] 真正攜帶實體專屬的資料。
 *
 * @param C 實體專屬自訂步驟的資料型別。
 */
sealed interface AnimationStep<out C> {
    /**
     * 等待絕對遊戲時間 [gameTime]，到期後即可繼續處理後續步驟。
     *
     * 使用絕對時間，讓多個實體共用同一目標時刻；前面的動畫延遲不會重新起算等待時間。
     *
     * @property gameTime 等待到的絕對遊戲時間。
     */
    data class WaitUntil(val gameTime: Long) : AnimationStep<Nothing>

    /**
     * 立即修改真實／持久化座標與朝向（瞬間動作，等同直接呼叫 `refreshPositionAndAngles`）。
     *
     * @property x 目標 X 座標。
     * @property y 目標 Y 座標。
     * @property z 目標 Z 座標。
     * @property yaw 目標水平朝向角度。
     */
    data class Teleport(val x: Double, val y: Double, val z: Double, val yaw: Float) : AnimationStep<Nothing>

    /**
     * 立即切換 vanilla 隱形旗標（瞬間動作）。
     *
     * @property invisible 是否設為隱形。
     */
    data class SetInvisible(val invisible: Boolean) : AnimationStep<Nothing>

    /**
     * 在 entity 目前的世界座標播放一次聲音。[soundId] 必須是完整 namespaced ID；
     * [playAtGameTime] 是預定播放的絕對 server game time，[expiresAtGameTime] 是允許播放的最後時間，
     * 避免 chunk 長時間卸載後補播已過期事件。聲音 cue 不阻塞同一佇列中的運動 step。
     *
     * @property soundId 完整 namespaced 聲音 ID。
     * @property volume 播放音量，必須是有限的非負值。
     * @property pitch 播放音高，必須是有限的正值。
     * @property playAtGameTime 預定播放的絕對伺服器遊戲時間。
     * @property expiresAtGameTime 允許播放的最後絕對遊戲時間，不得早於播放時間。
     */
    data class PlaySound(
        val soundId: String,
        val volume: Float,
        val pitch: Float,
        val playAtGameTime: Long,
        val expiresAtGameTime: Long,
    ) : AnimationStep<Nothing> {
        init {
            MinecraftResourceIds.requireValid(soundId) {
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
     * [easeRotation] 為 false 時姿態旋轉採線性內插；為 true 時採用與位移相同的漸慢曲線。
     * [startYawOffsetDegrees] 是畫面起點相對 entity 真實終點 yaw 的角差；它與位移一起套用 ease-out 並
     * 收斂到零。預設為零，因此既有不需要水平轉向的動畫完全不受影響。
     *
     * @property durationTicks 運動動畫持續的 tick 數。
     * @property arcHeight 位移弧線的高度。
     * @property startOffsetX 起始畫面位置相對實體位置的 X 偏移。
     * @property startOffsetY 起始畫面位置相對實體位置的 Y 偏移。
     * @property startOffsetZ 起始畫面位置相對實體位置的 Z 偏移。
     * @property startPoseRotationDegrees 起始姿態旋轉角度。
     * @property endPoseRotationDegrees 結束姿態旋轉角度。
     * @property easeRotation 是否讓姿態旋轉套用 ease-out；預設為 false。
     * @property startYawOffsetDegrees 起始畫面朝向相對實體終點朝向的角差。
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
        val startYawOffsetDegrees: Float = 0.0f,
    ) : AnimationStep<Nothing>

    /**
     * 實體專屬的瞬間動作（例如麻將牌的姿態切換），交給該實體類型自己解讀與套用。
     *
     * @param C 實體專屬自訂步驟的資料型別。
     * @property step 要交由實體處理的自訂步驟資料。
     */
    data class Custom<C>(val step: C) : AnimationStep<C>
}
