package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.bundled.BuiltInMahjongExtension
import com.doublemoon1119.mahjongcraft.bundled.BundledMahjongExtensions
import com.doublemoon1119.mahjongcraft.extension.CoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationCategory
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationReport
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationReportFormatter
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSource
import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.extension.MahjongExtensionRegistrar
import com.doublemoon1119.mahjongcraft.extension.mergeRegistrationSources
import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.DebugMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.MahjongStoolSeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtensionRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftPresentationRegistries
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RulePresentationRequirements
import net.fabricmc.loader.api.FabricLoader

/** Fabric Loader 用來發現 [MahjongExtension] 的 entrypoint key。 */
const val MAHJONG_EXTENSION_ENTRYPOINT: String = "${MinecraftModMetadata.MOD_ID}:extension"

/**
 * 發現並註冊 Fabric 環境中的第三方 [MahjongExtension]。
 *
 * 規則中立的 [BuiltInMahjongExtension] 先完成登記；接著 [BundledMahjongExtensions] 與第三方 extension 依序經由同一組回呼登記，
 * 取得 runtime 實際使用的同一批 registry；全部成功後由 [MahjongExtensionRegistrar] 凍結 registry。
 * 啟動 log 依來源列出所有啟用的登記：INFO 只列每個來源的筆數，DEBUG 另外列出完整內容。
 */
internal object FabricMahjongExtensions {
    /** Fabric extension discovery 與註冊結果使用的 logger。 */
    private val logger = mahjongCraftLogger(FabricMahjongExtensions::class)

    /**
     * 透過 [FabricLoader] 發現 entrypoint，完成一次 runtime registry 初始化並輸出來源摘要。
     *
     * @param coreRegistries 規則核心 registry 集合。
     * @param presentationRegistries Minecraft 呈現 registry 集合。
     * @param fabricRegistries Fabric 原生整合 registry 集合。
     * @param mahjongStoolSeatProvider 內建麻將凳座位 provider。
     * @param debugWinRoundContinuationState 開發用中途胡牌流程狀態。
     * @param minecraftEnvironment 判斷是否載入開發用 extension 的環境資訊。
     * @param failurePolicy 判斷登記回呼丟出的失敗能否包成帶擴充識別碼的註冊例外。
     */
    fun initialize(
        coreRegistries: CoreExtensionRegistries,
        presentationRegistries: MinecraftPresentationRegistries,
        fabricRegistries: FabricExtensionRegistries,
        mahjongStoolSeatProvider: MahjongStoolSeatProvider,
        debugWinRoundContinuationState: DebugWinRoundContinuationState,
        minecraftEnvironment: MinecraftEnvironment,
        failurePolicy: ExtensionFailurePolicy,
    ) {
        try {
            val extensions = FabricLoader.getInstance()
                .getEntrypoints(MAHJONG_EXTENSION_ENTRYPOINT, MahjongExtension::class.java)
            val result = initialize(
                coreRegistries = coreRegistries,
                presentationRegistries = presentationRegistries,
                fabricRegistries = fabricRegistries,
                mahjongStoolSeatProvider = mahjongStoolSeatProvider,
                debugWinRoundContinuationState = debugWinRoundContinuationState,
                minecraftEnvironment = minecraftEnvironment,
                failurePolicy = failurePolicy,
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
     * 所有相依都必須由呼叫端明確提供，測試與正式啟動共用相同的註冊路徑。
     * 各層 registrar 成功後各自凍結；後續層失敗時中止啟動，不回滾先前層的登記或重試。
     * Minecraft 呈現登記完成後，檢查每個可選規則都能在麻將桌上呈現（[RulePresentationRequirements]），不符合時中止啟動。
     *
     * @return Core、Minecraft presentation 與 Fabric 依來源合併後的所有登記。
     * @param coreRegistries 規則核心 registry 集合。
     * @param presentationRegistries Minecraft 呈現 registry 集合。
     * @param fabricRegistries Fabric 原生整合 registry 集合。
     * @param mahjongStoolSeatProvider 內建麻將凳座位 provider。
     * @param debugWinRoundContinuationState 開發用中途胡牌流程狀態。
     * @param minecraftEnvironment 判斷是否載入開發用 extension 的環境資訊。
     * @param failurePolicy 判斷登記回呼丟出的失敗能否包成帶擴充識別碼的註冊例外。
     * @param extensions 要額外登記的 extension。
     */
    internal fun initialize(
        coreRegistries: CoreExtensionRegistries,
        presentationRegistries: MinecraftPresentationRegistries,
        fabricRegistries: FabricExtensionRegistries,
        mahjongStoolSeatProvider: MahjongStoolSeatProvider,
        debugWinRoundContinuationState: DebugWinRoundContinuationState,
        minecraftEnvironment: MinecraftEnvironment,
        failurePolicy: ExtensionFailurePolicy,
        extensions: Iterable<MahjongExtension>,
    ): FabricExtensionRegistrationResult {
        val discoveredExtensions = extensions.toList()
        // 開發環境限定的 debug 整合排在內建規則之後、第三方之前；正式產物不登記。
        val debugExtensions = if (minecraftEnvironment.isDevelopment) {
            listOf(DebugMahjongExtension(winRoundContinuationState = debugWinRoundContinuationState))
        } else {
            emptyList()
        }
        val coreSources = MahjongExtensionRegistrar.registerAndFreeze(
            extensions = BundledMahjongExtensions.all + debugExtensions + discoveredExtensions,
            registries = coreRegistries,
            builtIn = BuiltInMahjongExtension(
                moduleRegistry = coreRegistries.moduleRegistry,
                extensionActionRegistry = coreRegistries.gameActionAiRegistry,
                opponentModelRegistry = coreRegistries.opponentModelRegistry,
            ),
            failurePolicy = failurePolicy,
        )

        // 同一個第三方類別可同時實作 MahjongExtension 與 MinecraftMahjongExtension，
        // 不需要在 fabric.mod.json 額外宣告第二個 entrypoint。
        val minecraftResult = MinecraftMahjongExtensionRegistrar.registerAndFreeze(
            extensions = BundledMinecraftMahjongExtensions.all + discoveredExtensions.filterIsInstance<MinecraftMahjongExtension>(),
            registries = presentationRegistries,
            failurePolicy = failurePolicy,
        )
        RulePresentationRequirements.verifySelectableRules(
            presentations = presentationRegistries.gameConfigPresentationRegistry,
            moduleRegistry = coreRegistries.moduleRegistry,
        )
        val minecraftSources = minecraftResult.sources.map { source ->
            ExtensionRegistrationSource(
                extensionId = source.extensionId,
                categories = source.categories.map { category ->
                    ExtensionRegistrationCategory(category.id, category.displayName, category.registrationIds)
                },
            )
        }
        val fabricSources = FabricMahjongExtensionRegistrar.registerAndFreeze(
            extensions = discoveredExtensions.filterIsInstance<FabricMahjongExtension>(),
            registries = fabricRegistries,
            builtIn = BuiltInFabricMahjongExtension(mahjongStoolSeatProvider),
            failurePolicy = failurePolicy,
        )
        return FabricExtensionRegistrationResult(
            sources = mergeRegistrationSources(coreSources, minecraftSources, fabricSources),
        )
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
