package com.doublemoon1119.mahjongcraft.platform.fabric.extension

import com.doublemoon1119.mahjongcraft.bundled.BuiltInMahjongExtension
import com.doublemoon1119.mahjongcraft.bundled.BundledMahjongExtensions
import com.doublemoon1119.mahjongcraft.extension.CoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationCategory
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationReport
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationReportFormatter
import com.doublemoon1119.mahjongcraft.extension.ExtensionRegistrationSource
import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.extension.MahjongExtensionBootstrap
import com.doublemoon1119.mahjongcraft.extension.mergeRegistrationSources
import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.server.seating.MahjongStoolSeatProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftMahjongExtensionBootstrap
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.MinecraftPresentationRegistries
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RulePresentationRequirements

/**
 * 依序登記規則、Minecraft 呈現與原生整合三層擴充，與載入器無關；第三方擴充由載入器找出後傳入。
 *
 * 規則中立的 [BuiltInMahjongExtension] 先完成登記；接著 [BundledMahjongExtensions] 與第三方 extension 依序經由同一組回呼登記，
 * 取得 runtime 實際使用的同一批 registry；全部成功後由 [MahjongExtensionBootstrap] 凍結 registry。
 * 啟動 log 依來源列出所有啟用的登記：INFO 只列每個來源的筆數，DEBUG 另外列出完整內容。
 */
internal object MahjongCraftExtensions {
    /** 擴充登記結果使用的 logger。 */
    private val logger = mahjongCraftLogger(MahjongCraftExtensions::class)

    /**
     * 完成一次 runtime registry 初始化並輸出來源摘要。
     *
     * @param coreRegistries 規則核心 registry 集合。
     * @param presentationRegistries Minecraft 呈現 registry 集合。
     * @param nativeRegistries 原生整合 registry 集合。
     * @param mahjongStoolSeatProvider 內建麻將凳座位 provider。
     * @param failurePolicy 判斷登記回呼丟出的失敗能否包成帶擴充識別碼的註冊例外。
     * @param extensions 載入器找出的第三方擴充。
     */
    fun initializeAndReport(
        coreRegistries: CoreExtensionRegistries,
        presentationRegistries: MinecraftPresentationRegistries,
        nativeRegistries: NativeExtensionRegistries,
        mahjongStoolSeatProvider: MahjongStoolSeatProvider,
        failurePolicy: ExtensionFailurePolicy,
        extensions: Iterable<MahjongExtension>,
    ) {
        try {
            val result = initialize(
                coreRegistries = coreRegistries,
                presentationRegistries = presentationRegistries,
                nativeRegistries = nativeRegistries,
                mahjongStoolSeatProvider = mahjongStoolSeatProvider,
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
     * 各層啟動流程成功後各自凍結；後續層失敗時中止啟動，不回滾先前層的登記或重試。
     * Minecraft 呈現登記完成後，檢查每個可選規則都能在麻將桌上呈現（[RulePresentationRequirements]），不符合時中止啟動。
     *
     * @return 規則、Minecraft 呈現與原生整合依來源合併後的所有登記。
     * @param coreRegistries 規則核心 registry 集合。
     * @param presentationRegistries Minecraft 呈現 registry 集合。
     * @param nativeRegistries 原生整合 registry 集合。
     * @param mahjongStoolSeatProvider 內建麻將凳座位 provider。
     * @param failurePolicy 判斷登記回呼丟出的失敗能否包成帶擴充識別碼的註冊例外。
     * @param extensions 要額外登記的 extension。
     */
    internal fun initialize(
        coreRegistries: CoreExtensionRegistries,
        presentationRegistries: MinecraftPresentationRegistries,
        nativeRegistries: NativeExtensionRegistries,
        mahjongStoolSeatProvider: MahjongStoolSeatProvider,
        failurePolicy: ExtensionFailurePolicy,
        extensions: Iterable<MahjongExtension>,
    ): MahjongCraftExtensionRegistrationResult {
        val discoveredExtensions = extensions.toList()
        val coreSources = MahjongExtensionBootstrap.registerAndFreeze(
            extensions = buildList {
                addAll(BundledMahjongExtensions.all)
                addAll(discoveredExtensions)
            },
            registries = coreRegistries,
            builtIn = BuiltInMahjongExtension(
                moduleRegistry = coreRegistries.moduleRegistry,
                extensionActionRegistry = coreRegistries.gameActionAiRegistry,
                opponentModelRegistry = coreRegistries.opponentModelRegistry,
            ),
            failurePolicy = failurePolicy,
        )

        // 同一個第三方類別可同時實作 MahjongExtension 與 MinecraftMahjongExtension，
        // 載入器不需要另外找第二種擴充。
        val minecraftResult = MinecraftMahjongExtensionBootstrap.registerAndFreeze(
            extensions = buildList {
                addAll(BundledMinecraftMahjongExtensions.all)
                discoveredExtensions.filterIsInstanceTo<MinecraftMahjongExtension, _>(this)
            },
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
        val nativeSources = NativeMahjongExtensionBootstrap.registerAndFreeze(
            extensions = discoveredExtensions.filterIsInstance<NativeMahjongExtension>(),
            registries = nativeRegistries,
            builtIn = BuiltInNativeMahjongExtension(
                seatProvider = mahjongStoolSeatProvider,
                tileAssets = presentationRegistries.tileAssetRegistry,
                componentViews = coreRegistries.persistenceRegistries.componentViews,
            ),
            failurePolicy = failurePolicy,
        )
        return MahjongCraftExtensionRegistrationResult(
            sources = mergeRegistrationSources(coreSources, minecraftSources, nativeSources),
        )
    }
}

/**
 * 擴充初始化完成後，規則、Minecraft 呈現與原生整合依來源合併的所有登記。
 *
 * @property sources 內建登記在前，接著依登記順序排列每個 extension。
 */
internal data class MahjongCraftExtensionRegistrationResult(
    val sources: List<ExtensionRegistrationSource>,
)
