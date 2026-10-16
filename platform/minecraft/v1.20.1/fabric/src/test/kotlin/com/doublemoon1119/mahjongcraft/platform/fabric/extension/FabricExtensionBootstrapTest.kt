package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.ai.MahjongAiStrategyDefinition
import com.doublemoon1119.mahjongcraft.extension.CoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.persistence.format.registry.PersistenceRegistries
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.platform.fabric.api.seating.FabricSeatProviderRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.di.fabricServerModule
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.MahjongStoolSeatProvider
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftPresentationRegistries
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** 驗證 loader 組裝的跨層順序、單次 discovery 與失敗後停止契約。 */
class FabricExtensionBootstrapTest {
    /** 每個測試結束後釋放正式 Koin graph。 */
    @AfterTest
    fun tearDown() = stopKoin()

    /** 同一批 discovery 只讀一次；三層依序回呼，無新增登記的來源只合併為一筆空分類。 */
    @Test
    fun `bootstrap materializes discovery once and merges no op sources across layers`() {
        val koin = startKoin {
            allowOverride(false)
            modules(fabricServerModule)
        }.koin
        val calls = mutableListOf<String>()
        val extension = RecordingExtension(calls)
        var iterations = 0
        val discovered = Iterable<MahjongExtension> {
            check(iterations++ == 0) { "Discovery must only be enumerated once" }
            listOf(extension).iterator()
        }

        val result = initialize(koin, discovered)

        assertEquals(1, iterations)
        assertEquals(listOf("core", "minecraft", "fabric_table", "fabric_seat"), calls)
        assertEquals(emptyList(), result.sources.single { it.extensionId == extension.id }.categories)
        assertEquals(1, result.sources.count { it.extensionId == extension.id })
        val registries = koin.get<FabricExtensionRegistries>()
        assertTrue(registries.tablePropKindRegistry.isFrozen)
        assertTrue(registries.seatProviderRegistry.isFrozen)
    }

    /** Fabric 回呼失敗會中止；先前層已凍結但 Fabric 不凍結，不承諾回滾或重新初始化。 */
    @Test
    fun `later layer failure aborts bootstrap without rolling back earlier layers`() {
        val koin = startKoin {
            allowOverride(false)
            modules(fabricServerModule)
        }.koin
        val calls = mutableListOf<String>()
        val cause = IllegalStateException("Seat registration failed")
        val extension = RecordingExtension(calls, cause)

        val failure = assertFailsWith<FabricMahjongExtensionRegistrationException> {
            initialize(koin, listOf(extension))
        }

        assertSame(cause, failure.cause)
        assertTrue(failure.message.orEmpty().contains(extension.id))
        assertEquals(listOf("core", "minecraft", "fabric_table", "fabric_seat"), calls)
        val core = koin.get<CoreExtensionRegistries>()
        assertFailsWith<IllegalStateException> {
            core.aiStrategyRegistry.register(MahjongAiStrategyDefinition("example:late") { error("Factory must not run") })
        }
        assertTrue(koin.get<MinecraftPresentationRegistries>().ruleCatalogueRegistry.isFrozen)
        assertFalse(koin.get<FabricExtensionRegistries>().tablePropKindRegistry.isFrozen)
        assertFalse(koin.get<FabricExtensionRegistries>().seatProviderRegistry.isFrozen)
    }

    /** 擴充登記的可選規則無法在麻將桌上呈現時，Minecraft 呈現登記完成後中止啟動，Fabric 層不再登記。 */
    @Test
    fun `selectable rule that cannot be presented aborts bootstrap`() {
        val koin = startKoin {
            allowOverride(false)
            modules(fabricServerModule)
        }.koin

        val failure = assertFailsWith<IllegalStateException> {
            initialize(koin, listOf(MismatchedRulePresentationExtension()))
        }

        assertTrue(failure.message.orEmpty().contains(MismatchedRulePresentationExtension.RULE_ID))
        assertFalse(koin.get<FabricExtensionRegistries>().seatProviderRegistry.isFrozen)
    }

    /**
     * 使用正式 graph 的共用相依執行 production 初始化路徑。
     *
     * @param koin 已組裝的伺服器依賴容器。
     * @param extensions 本次 discovery 的第三方擴充。
     * @return 三層合併的登記結果。
     */
    private fun initialize(koin: Koin, extensions: Iterable<MahjongExtension>): FabricExtensionRegistrationResult = FabricMahjongExtensions.initialize(
        coreRegistries = koin.get<CoreExtensionRegistries>(),
        presentationRegistries = koin.get<MinecraftPresentationRegistries>(),
        fabricRegistries = koin.get<FabricExtensionRegistries>(),
        mahjongStoolSeatProvider = koin.get<MahjongStoolSeatProvider>(),
        debugWinRoundContinuationState = koin.get<DebugWinRoundContinuationState>(),
        minecraftEnvironment = object : MinecraftEnvironment {
            /** 此案例固定驗證非開發環境的登記順序。 */
            override val isDevelopment: Boolean = false
        },
        failurePolicy = koin.get<ExtensionFailurePolicy>(),
        extensions = extensions,
    )

    /**
     * 同時參與三層、但不新增項目的測試擴充。
     *
     * @property calls 依執行順序記錄回呼。
     * @property seatFailure 座位回呼應拋出的例外；為 null 時正常完成。
     */
    private class RecordingExtension(
        private val calls: MutableList<String>,
        private val seatFailure: Exception? = null,
    ) : MahjongExtension,
        MinecraftMahjongExtension,
        FabricMahjongExtension {
        override val id: String = "example:recording"

        override fun registerRuleModules(registry: MahjongModuleRegistry) {
            calls += "core"
        }

        override fun registerNetworkDtos(registries: NetworkDtoRegistries) = Unit

        override fun registerPersistenceDtos(registries: PersistenceRegistries) = Unit

        override fun registerTileAssets(registry: MinecraftTileAssetRegistry) {
            calls += "minecraft"
        }

        override fun registerTablePropKinds(registry: FabricTablePropKindRegistry) {
            calls += "fabric_table"
        }

        override fun registerSeatProviders(registry: FabricSeatProviderRegistry) {
            calls += "fabric_seat"
            seatFailure?.let { throw it }
        }
    }

    /** 登記一個可選設定呈現，但其預設設定建立的是另一個規則的測試擴充。 */
    private class MismatchedRulePresentationExtension :
        MahjongExtension,
        MinecraftMahjongExtension {
        override val id: String = RULE_ID

        override fun registerRuleModules(registry: MahjongModuleRegistry) = Unit

        override fun registerNetworkDtos(registries: NetworkDtoRegistries) = Unit

        override fun registerPersistenceDtos(registries: PersistenceRegistries) = Unit

        override fun registerGameConfigPresentations(registry: GameConfigPresentationRegistry) {
            registry.register(
                GameConfigPresentationDefinition(
                    ruleModuleId = RULE_ID,
                    descriptionTranslationKey = "example.rule.description",
                    selectable = true,
                    defaultRuleConfig = ::TaiwanRuleConfig,
                    categories = emptyList(),
                    fields = emptyList(),
                ),
            )
        }

        companion object {
            /** 設定呈現宣稱的規則 ID。 */
            const val RULE_ID: String = "example:mismatched_rule"
        }
    }
}
