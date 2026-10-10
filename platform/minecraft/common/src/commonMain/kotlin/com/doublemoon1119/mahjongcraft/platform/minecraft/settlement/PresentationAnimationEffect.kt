package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/** renderer 支援的受控動畫效果；第三方不需要也不能提供任意 callback。 */
sealed interface PresentationAnimationEffect {
    /**
     * 透明度淡入淡出。
     *
     * @property fromAlpha 起始透明度。
     * @property toAlpha 結束透明度。
     */
    data class Fade(
        val fromAlpha: Float = 0f,
        val toAlpha: Float = 1f,
    ) : PresentationAnimationEffect {
        init {
            require(fromAlpha in 0f..1f && toAlpha in 0f..1f)
        }
    }

    /**
     * 水平與垂直位移。
     *
     * @property fromX 起始 X 位移。
     * @property fromY 起始 Y 位移。
     */
    data class Slide(
        val fromX: Float = 0f,
        val fromY: Float = 0f,
    ) : PresentationAnimationEffect {
        init {
            require(fromX in -320f..320f && fromY in -240f..240f)
        }
    }

    /**
     * 依進度線性插值的縮放關鍵影格。
     *
     * @property keyframes 依進度排列的關鍵影格。
     */
    data class ScaleKeyframes(
        val keyframes: List<ScaleKeyframe>,
    ) : PresentationAnimationEffect {
        init {
            require(keyframes.size in 2..8)
            require(keyframes.first().progress == 0f && keyframes.last().progress == 1f)
            require(keyframes.zipWithNext().all { (left, right) -> left.progress < right.progress })
        }
    }

    /**
     * 在節點背景上疊加一次透明度脈衝。
     *
     * @property argb 脈衝顏色。
     */
    data class BackgroundPulse(
        val argb: Int,
    ) : PresentationAnimationEffect

    /**
     * 由左至右掃過節點的高光。
     *
     * @property argb 高光顏色。
     * @property width 高光寬度。
     */
    data class HighlightSweep(
        val argb: Int = 0x66FFFFFF,
        val width: Float = 18f,
    ) : PresentationAnimationEffect {
        init {
            require(width in 1f..160f)
        }
    }

    /**
     * 以水平縮放模擬由中心揭曉，避免暴露底層裁切 API。
     *
     * @property fromScale 起始縮放值。
     */
    data class HorizontalReveal(
        val fromScale: Float = 0f,
    ) : PresentationAnimationEffect {
        init {
            require(fromScale in 0f..1f)
        }
    }
}
