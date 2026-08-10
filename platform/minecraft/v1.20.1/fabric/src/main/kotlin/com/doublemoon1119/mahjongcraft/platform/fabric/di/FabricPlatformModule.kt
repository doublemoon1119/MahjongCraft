package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.flow.common.di.FlowCommonModule
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.network.dto.buildMahjongDtoSerializersModule
import com.doublemoon1119.mahjongcraft.flow.server.di.FlowServerModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import kotlinx.serialization.json.Json
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * `:mahjong-flow-common`/`:mahjong-flow-server` 刻意不綁定的幾個介面（[GameEventPublisher]、
 * [RoomEventPublisher]、[CoroutineDispatchers]、[AppCoroutineScope]）由平台層供應，這裡是 Fabric
 * 平台的供應點。跟 `FlowCommonModule`/`FlowServerModule` 同一套 Koin Annotations 慣例：
 * [ComponentScan] 掃這個套件底下標了 `@Single` 的類別（`FabricServerHolder`、
 * `GameEventPublisherImpl`、`RoomEventPublisherImpl`、`FabricCoroutineDispatchers`、
 * `FabricAppCoroutineScope`）；其中 [AppCoroutineScope] 使用可隨 server session 重建 context 的平台實作。
 * [Json] 是第三方型別，維持手動 `@Single` 工廠函式。
 *
 * `includes = [FlowCommonModule::class]`（比照 `FlowServerModule` 的既有寫法）讓 Koin 編譯器外掛的
 * 靜態依賴檢查知道 `GameEventPublisherImpl`/`RoomEventPublisherImpl` 建構子要的
 * `GameSnapshotRepository`/`RoomSnapshotRepository` 是從哪裡來的——這兩個介面實際上是
 * `FlowCommonModule` 綁定的，不是這裡；沒有這個 `includes`，靜態分析看不到跨模組的綁定關係會誤判
 * 缺少依賴（實際執行時因為 `MahjongCraftMod` 把 `FlowServerModule().module()` 也一併傳給
 * `startKoin`，並不會真的缺）。
 */
@Module(includes = [FlowCommonModule::class, FlowServerModule::class])
@ComponentScan("com.doublemoon1119.mahjongcraft.platform.fabric")
class FabricPlatformModule {
    /** 提供目前使用預設值的伺服器政策；後續由磁碟 config adapter 取代。 */
    @Single
    fun provideMinecraftServerConfig(): MinecraftServerConfig = MinecraftServerConfig()

    /**
     * [buildMahjongDtoSerializersModule] 讀到的 `MahjongRuleDtoRegistries` 必須已經在
     * [com.doublemoon1119.mahjongcraft.platform.fabric.MahjongCraftMod.onInitialize] 呼叫過
     * `registerBuiltInRuleConfigDtos()`——Koin `single` 是 lazy 的，只要在第一次 `get<Json>()`
     * 之前完成註冊即可。
     */
    @Single
    fun provideJson(): Json = Json { serializersModule = buildMahjongDtoSerializersModule() }
}
