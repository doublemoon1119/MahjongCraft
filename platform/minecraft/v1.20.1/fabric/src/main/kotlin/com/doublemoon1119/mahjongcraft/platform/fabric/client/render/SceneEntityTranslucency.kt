package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

/**
 * 讓原版生物繪製器以指定不透明度畫出一個不在世界中的生物，例如牌譜回放場景中擋住視線的人物。
 *
 * 只在 [render] 的工作期間生效：生物改用原版畫「看得見的隱形生物」的半透明圖層，不透明度換成指定值，
 * 不畫盔甲、手持物品等附加圖層。世界中的實體不受影響。
 */
object SceneEntityTranslucency {
    /** 目前生效的不透明度；沒有生效時為 null。 */
    private var activeAlpha: Float? = null

    /**
     * 以指定不透明度執行一次生物繪製。
     *
     * @param alpha 0 到 1 之間的不透明度。
     * @param draw 繪製工作。
     */
    fun render(alpha: Float, draw: () -> Unit) {
        val previous = activeAlpha
        activeAlpha = alpha.coerceIn(0f, 1f)
        try {
            draw()
        } finally {
            activeAlpha = previous
        }
    }

    /**
     * 是否正以半透明方式繪製。
     *
     * @return 生效中時為 true。
     */
    @JvmStatic
    fun isActive(): Boolean = activeAlpha != null

    /**
     * 套用到生物模型的不透明度。
     *
     * @param original 原版計算的不透明度。
     * @return 生效中時為指定值，否則為原版的值。
     */
    @JvmStatic
    fun alpha(original: Float): Float = activeAlpha ?: original
}
