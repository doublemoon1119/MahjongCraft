package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import net.irisshaders.iris.api.v0.IrisApi

/**
 * 以 Iris 的 API 實作 [ShadowPassProbe]。
 *
 * - 光影模組是 Iris，或 Forge 上 API 與 Iris 相同的 Oculus。
 * - 只對 Iris 的 API 編譯、不打包。兩者都沒有安裝時一律為否。
 *
 * @property platform 判斷是否安裝了光影模組。
 */
class IrisShadowPassProbe(private val platform: LoaderPlatform) : ShadowPassProbe {
    /** 遊戲中是否安裝了提供 Iris API 的光影模組。第一次查詢時向載入器確認。 */
    private val installed: Boolean by lazy { SHADER_MOD_IDS.any(platform::isModLoaded) }

    override fun active(): Boolean = installed && IrisShadowPass.active()

    private companion object {
        /** 提供 Iris API 的光影模組 ID：Iris 與 Oculus。 */
        val SHADER_MOD_IDS = listOf("iris", "oculus")
    }
}

/** 只在安裝了光影模組時才會載入的 Iris API 查詢，沒有安裝時不會碰到 Iris 的類別。 */
private object IrisShadowPass {
    /**
     * 光影模組是否正在畫陰影圖。
     *
     * @return 正在畫陰影圖時為 true。
     */
    fun active(): Boolean = IrisApi.getInstance().isRenderingShadowPass
}
