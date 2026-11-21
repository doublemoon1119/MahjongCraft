package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import net.fabricmc.loader.api.FabricLoader
import net.irisshaders.iris.api.v0.IrisApi

/**
 * 光影模組是否正在畫陰影圖：開了光影包時，光影模組每一幀會先從光源方向把世界畫一次，得出哪裡被擋住。
 *
 * 目前認得 Iris；只對 Iris 的 API 編譯、不打包，沒有安裝 Iris 時一律為否。
 */
internal object ShaderShadowPass {
    /** 遊戲中是否安裝了 Iris。 */
    private val irisLoaded: Boolean = FabricLoader.getInstance().isModLoaded(IRIS_MOD_ID)

    /**
     * 目前是否在畫陰影圖。
     *
     * @return 正在畫陰影圖時為 true。
     */
    fun active(): Boolean = irisLoaded && IrisShadowPass.active()

    /** Iris 的模組 ID。 */
    private const val IRIS_MOD_ID = "iris"
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
