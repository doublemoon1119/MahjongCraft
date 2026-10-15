package com.doublemoon1119.mahjongcraft.platform.minecraft.di

import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.room.repository.RoomSnapshotRepositoryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistry
import com.doublemoon1119.mahjongcraft.logic.tile.TileTypeRegistryImpl
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/**
 * 平台為 `:mahjong-flow-common` 組裝的共用流程服務與由擴充登記流程初始化的 registry。
 *
 * 規則模組與牌種 registry 的實作屬於框架無關的 `:mahjong-logic`，在這裡明確建立。平台啟動 Koin 後必須先取得這兩個 single，
 * 交給 extension bootstrap 完成內建與第三方登記並凍結，才能解析依賴它們的遊戲流程服務；network、persistence 與規則流程
 * 後續都使用同一個實例。
 */
val flowCommonModule = module {
    single<MahjongModuleRegistry> { MahjongModuleRegistryImpl() }
    single<TileTypeRegistry> { TileTypeRegistryImpl() }

    single<GameSnapshotRepositoryImpl>() bind GameSnapshotRepository::class
    single<WinCelebrationCueResolverRegistryImpl>() bind WinCelebrationCueResolverRegistry::class

    single<RoomSnapshotRepositoryImpl>() bind RoomSnapshotRepository::class
}
