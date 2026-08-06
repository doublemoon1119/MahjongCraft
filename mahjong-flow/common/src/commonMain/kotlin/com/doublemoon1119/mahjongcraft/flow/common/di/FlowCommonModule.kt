package com.doublemoon1119.mahjongcraft.flow.common.di

import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

@Module
@ComponentScan("com.doublemoon1119.mahjongcraft.flow.common")
class FlowCommonModule {
    /**
     * 綁定 [MahjongModuleRegistry] 介面到其預設實作，並註冊內建規則模組。
     *
     * `:mahjong-logic` 不依賴 Koin，故無法直接為 [MahjongModuleRegistryImpl] 標註 `@Single`，
     * 綁定交由此處負責。放在 `FlowCommonModule` 而非 `FlowServerModule`，讓 `:mahjong-flow-client`
     * 之後若要接 Koin，可直接 include 取得同一份綁定。
     */
    @Single
    fun provideMahjongModuleRegistry(): MahjongModuleRegistry =
        MahjongModuleRegistryImpl().apply { registerBuiltInRuleModules() }
}
