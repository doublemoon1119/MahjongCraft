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
     * 建立由 Koin 管理的 runtime [MahjongModuleRegistry]。
     *
     * 平台啟動 Koin 後必須先取得此 single，完成內建規則與第三方 extension 註冊並凍結，才能解析
     * 依賴此 registry 的遊戲流程服務。
     */
    @Single
    fun provideMahjongModuleRegistry(): MahjongModuleRegistry = MahjongModuleRegistryImpl()
}
