package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.ai.registerBuiltInAiStrategies
import com.doublemoon1119.mahjongcraft.bundled.BundledMahjongExtensions
import com.doublemoon1119.mahjongcraft.extension.CoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationCategory
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationReport
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationReportFormatter
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSource
import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.extension.MahjongExtensionRegistrar
import com.doublemoon1119.mahjongcraft.extension.mergeRegistrationSources
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.decision.DebugRoundPreparationResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.registerDebugWinRoundContinuationResolvers
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario.registerDebugScriptedAiStrategies
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.prop.FabricTablePropKindRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtensionRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftPresentationRegistries
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.fabricmc.loader.api.FabricLoader

/** Fabric Loader 用來發現 [MahjongExtension] 的 entrypoint key。 */
const val MAHJONG_EXTENSION_ENTRYPOINT: String = "${MinecraftModMetadata.MOD_ID}:extension"

/**
 * 發現並註冊 Fabric 環境中的第三方 [MahjongExtension]。
 *
 * 規則中立的內建項目（例如內建 AI 策略）先完成註冊；接著 [BundledMahjongExtensions] 與第三方 extension 依序經由同一組回呼登記，
 * 取得 runtime 實際使用的同一批 registry；全部成功後由 [MahjongExtensionRegistrar] 凍結 registry。
 * 啟動 log 依來源列出所有啟用的登記：INFO 只列每個來源的筆數，DEBUG 另外列出完整內容。
 */
object FabricMahjongExtensions {
    /** Fabric extension discovery 與註冊結果使用的 logger。 */
    private val logger = mahjongCraftLogger(FabricMahjongExtensions::class)

    /** 透過 [FabricLoader] 發現 entrypoint，完成一次 runtime registry 初始化。 */
    fun initialize(
        coreRegistries: CoreExtensionRegistries,
        presentationRegistries: MinecraftPresentationRegistries,
        tablePropKindRegistry: FabricTablePropKindRegistry,
        debugWinRoundContinuationState: DebugWinRoundContinuationState,
        minecraftEnvironment: MinecraftEnvironment,
    ) {
        try {
            val extensions = FabricLoader.getInstance()
                .getEntrypoints(MAHJONG_EXTENSION_ENTRYPOINT, MahjongExtension::class.java)
            val result = initialize(
                coreRegistries = coreRegistries,
                presentationRegistries = presentationRegistries,
                tablePropKindRegistry = tablePropKindRegistry,
                debugWinRoundContinuationState = debugWinRoundContinuationState,
                minecraftEnvironment = minecraftEnvironment,
                extensions = extensions,
            )
            val report = ExtensionRegistrationReport(result.sources)
            logger.info(ExtensionRegistrationReportFormatter.formatSummary(report))
            if (logger.isDebugEnabled) {
                logger.debug(ExtensionRegistrationReportFormatter.formatDetails(report))
            }
        } catch (cause: Exception) {
            logger.error("Failed to initialize Mahjong extensions", cause)
            throw cause
        }
    }

    /**
     * 使用明確提供的 [extensions] 初始化，供平台測試驗證組裝順序。
     *
     * @return Core、Minecraft presentation 與 Fabric 依來源合併後的所有登記。
     */
    internal fun initialize(
        coreRegistries: CoreExtensionRegistries,
        presentationRegistries: MinecraftPresentationRegistries,
        tablePropKindRegistry: FabricTablePropKindRegistry,
        debugWinRoundContinuationState: DebugWinRoundContinuationState = DebugWinRoundContinuationState(),
        // 預設不註冊開發用的中途胡牌 resolver：這個多載的其他測試呼叫端只關心依賴圖，正式呼叫端
        // （MahjongCraftMod）會傳入真正的 MinecraftEnvironment。
        minecraftEnvironment: MinecraftEnvironment = NonDevelopmentEnvironment,
        extensions: Iterable<MahjongExtension>,
    ): FabricExtensionRegistrationResult {
        coreRegistries.aiStrategyRegistry.registerBuiltInAiStrategies(
            moduleRegistry = coreRegistries.moduleRegistry,
            extensionActionRegistry = coreRegistries.gameActionAiRegistry,
            opponentModelRegistry = coreRegistries.opponentModelRegistry,
        )
        // 開發環境限定：讓「胡牌後本局繼續」這條路徑在還沒有任何規則支援它時就能進遊戲驗證，
        // 比照 FabricDebugCommand 的 gating——正式產物裡根本沒註冊過。預設 inert。
        if (minecraftEnvironment.isDevelopment) {
            listOf(BuiltInRuleModuleIds.RIICHI, BuiltInRuleModuleIds.TAIWAN).forEach { ruleModuleId ->
                if (coreRegistries.roundPreparationResolverRegistry.find(ruleModuleId) == null) {
                    coreRegistries.roundPreparationResolverRegistry.register(DebugRoundPreparationResolver(ruleModuleId))
                }
            }
            coreRegistries.winRoundContinuationResolverRegistry.registerDebugWinRoundContinuationResolvers(
                state = debugWinRoundContinuationState,
            )
            // debug 情境的對手使用腳本 AI
            coreRegistries.aiStrategyRegistry.registerDebugScriptedAiStrategies()
        }

        val coreSources = MahjongExtensionRegistrar.registerAndFreeze(
            extensions = BundledMahjongExtensions.all + extensions,
            registries = coreRegistries,
        )

        // 同一個第三方類別可同時實作 MahjongExtension 與 MinecraftMahjongExtension，
        // 不需要在 fabric.mod.json 額外宣告第二個 entrypoint。
        val minecraftResult = MinecraftMahjongExtensionRegistrar.registerAndFreeze(
            extensions = BundledMinecraftMahjongExtensions.all + extensions.filterIsInstance<MinecraftMahjongExtension>(),
            registries = presentationRegistries,
        )
        val minecraftSources = minecraftResult.sources.map { source ->
            ExtensionRegistrationSource(
                extensionId = source.extensionId,
                categories = source.categories.map { category ->
                    ExtensionRegistrationCategory(category.id, category.displayName, category.registrationKeys.sorted())
                },
            )
        }
        val tablePropKindSources = tablePropKindRegistry.registerAndFreeze(
            extensions = extensions.filterIsInstance<FabricMahjongExtension>(),
        )
        return FabricExtensionRegistrationResult(mergeRegistrationSources(coreSources, minecraftSources, tablePropKindSources))
    }

    /** [initialize] 預設使用的環境查詢：一律回報非開發環境，見該參數上方註解。 */
    private object NonDevelopmentEnvironment : MinecraftEnvironment {
        override val isDevelopment: Boolean = false
    }
}

/**
 * Fabric extension 初始化完成後，核心、Minecraft 呈現與 Fabric 依來源合併的所有登記。
 *
 * @property sources 內建登記在前，接著依登記順序排列每個 extension。
 */
internal data class FabricExtensionRegistrationResult(
    val sources: List<ExtensionRegistrationSource>,
)
