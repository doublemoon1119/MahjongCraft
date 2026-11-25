package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.render

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ShadowPassProbe
import org.koin.core.context.GlobalContext

/** 光影模組是否正在畫陰影圖。判斷方式由載入器提供的 [ShadowPassProbe] 決定。 */
internal object ShaderShadowPass {
    /** 載入器提供的判斷方式。第一次查詢時經 Koin 取得。 */
    private val probe: ShadowPassProbe by lazy { GlobalContext.get().get<ShadowPassProbe>() }

    /**
     * 目前是否在畫陰影圖。
     *
     * @return 正在畫陰影圖時為 true。
     */
    fun active(): Boolean = probe.active()
}
