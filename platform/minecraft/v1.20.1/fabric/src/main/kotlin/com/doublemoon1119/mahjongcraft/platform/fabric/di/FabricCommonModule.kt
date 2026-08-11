package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.buildMahjongDtoSerializersModule
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.registry.buildBuiltInPersistenceRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.state.AuthoritativeStatePersistenceCodec
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Fabric client 與 server graph 共用的序列化及 DTO registry 定義。
 *
 * 此 module 不掃描 Fabric adapter，也不持有 Minecraft client 或 server session 狀態；兩種執行環境因此能
 * 使用相同的 registry、codec 與 [Json] single。
 */
@Module
class FabricCommonModule {
    /** 建立供 extension 註冊與 persistence adapter 共用的 runtime registry single。 */
    @Single
    fun providePersistenceRegistries(): PersistenceRegistries = buildBuiltInPersistenceRegistries()

    /** 建立使用 runtime persistence registries 的權威狀態 codec。 */
    @Single
    fun provideAuthoritativeStatePersistenceCodec(
        registries: PersistenceRegistries,
    ): AuthoritativeStatePersistenceCodec = AuthoritativeStatePersistenceCodec(registries)

    /** 建立供 extension 註冊、DTO 轉換與 network Json 共用的 runtime registry single。 */
    @Single
    fun provideNetworkDtoRegistries(): NetworkDtoRegistries = DefaultNetworkDtoRegistries()

    /**
     * 建立 network 與 snapshot 共用的 [Json]。
     *
     * [registries] 必須先由 Fabric extension bootstrap 完成註冊；Koin single 採延遲建立，因此平台只要在
     * 第一次解析 [Json] 前完成 bootstrap 即可。
     */
    @Single
    fun provideJson(registries: NetworkDtoRegistries): Json = Json {
        serializersModule = buildMahjongDtoSerializersModule(registries)
    }
}
