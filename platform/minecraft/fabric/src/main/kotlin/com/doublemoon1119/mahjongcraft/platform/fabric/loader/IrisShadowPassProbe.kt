package com.doublemoon1119.mahjongcraft.platform.fabric.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.LoaderPlatform
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ShadowPassProbe
import net.irisshaders.iris.api.v0.IrisApi

/**
 * 以 Iris 的 API 實作 [ShadowPassProbe]。只對 Iris 的 API 編譯、不打包，沒有安裝 Iris 時一律為否。
 *
 * @property platform 判斷是否安裝了 Iris。
 */
class IrisShadowPassProbe(private val platform: LoaderPlatform) : ShadowPassProbe {
    /** 遊戲中是否安裝了 Iris。第一次查詢時向載入器確認。 */
    private val irisLoaded: Boolean by lazy { platform.isModLoaded(IRIS_MOD_ID) }

    override fun active(): Boolean = irisLoaded && IrisShadowPass.active()

    private companion object {
        /** Iris 的模組 ID。 */
        const val IRIS_MOD_ID = "iris"
    }
}

/** 只在安裝了 Iris 時才會載入的 Iris API 查詢，沒有 Iris 時不會碰到 Iris 的類別。 */
private object IrisShadowPass {
    /**
     * Iris 是否正在畫陰影圖。
     *
     * @return 正在畫陰影圖時為 true。
     */
    fun active(): Boolean = IrisApi.getInstance().isRenderingShadowPass
}
