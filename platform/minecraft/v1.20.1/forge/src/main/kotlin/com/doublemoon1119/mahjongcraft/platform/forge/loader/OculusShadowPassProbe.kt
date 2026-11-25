package com.doublemoon1119.mahjongcraft.platform.forge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.LoaderPlatform
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ShadowPassProbe
import net.irisshaders.iris.api.v0.IrisApi

/**
 * 以 Oculus 的 API 實作 [ShadowPassProbe]。Oculus 是 Forge 上的光影模組，API 與 Iris 相同。只對 API 編譯、不打包，
 * 沒有安裝 Oculus 時一律為否。
 *
 * @property platform 判斷是否安裝了 Oculus。
 */
class OculusShadowPassProbe(private val platform: LoaderPlatform) : ShadowPassProbe {
    /** 遊戲中是否安裝了 Oculus。第一次查詢時向載入器確認。 */
    private val oculusLoaded: Boolean by lazy { platform.isModLoaded(OCULUS_MOD_ID) }

    override fun active(): Boolean = oculusLoaded && OculusShadowPass.active()

    private companion object {
        /** Oculus 的模組 ID。 */
        const val OCULUS_MOD_ID = "oculus"
    }
}

/** 只在安裝了 Oculus 時才會載入的 API 查詢，沒有 Oculus 時不會碰到 Oculus 的類別。 */
private object OculusShadowPass {
    /**
     * Oculus 是否正在畫陰影圖。
     *
     * @return 正在畫陰影圖時為 true。
     */
    fun active(): Boolean = IrisApi.getInstance().isRenderingShadowPass
}
