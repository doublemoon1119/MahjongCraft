package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.showcase

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config.MahjongClientConfigStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config.MahjongClientConfigUpdateResult
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.config.MahjongPresentationVisibilityConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.AutotestContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestCheck
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestReportRetention
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestStatus
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase.ShowcaseImages
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase.ShowcaseManifest
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase.ShowcaseMode
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase.ShowcasePosition
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.tutorial.TutorialSteps
import org.koin.core.context.GlobalContext
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.util.concurrent.Executor
import javax.imageio.ImageIO
import kotlin.time.TimeSource

/**
 * 宣傳截圖的執行者：遊戲到主選單後拍攝宣傳截圖（[ShowcaseScene]），後製並寫出結果，再關閉遊戲。
 *
 * - 結果寫在遊戲資料夾的 `showcase/<開始時間>/`：原始截圖、後製的圖片與 `showcase.json`。
 * - 只保留最近幾次的結果，與自動測試的報告相同（[AutotestReportRetention]）。
 * - 開始前關掉本 mod 全部可關閉的面板與提示，原版介面在截圖時由鏡頭隱藏。
 *
 * @property mode 宣傳截圖模式的設定。
 */
internal class ShowcaseClientRunner(private val mode: ShowcaseMode) {
    /** 記錄宣傳截圖進度的 logger。 */
    private val logger = mahjongCraftLogger(ShowcaseClientRunner::class)

    /** 是否已經開始。 */
    private var started = false

    /** 開始時間。 */
    private val startedAt = LocalDateTime.now()

    /** 開始時的單調時間。 */
    private val startMark = TimeSource.Monotonic.markNow()

    /**
     * 掛上用戶端事件。遊戲第一次到主選單、而且啟動時的資源載入已經結束時開始。
     *
     * @param events 登記用戶端事件用的事件來源。
     */
    fun register(events: ClientEvents) {
        events.onClientTickEnd { client ->
            // 與自動測試相同：不經過 Gradle 啟動時，第一次啟動的無障礙設定引導仍會出現，略過它並記下已完成。
            if (!started && client.screen is AccessibilityOnboardingScreen && client.overlay == null) {
                client.options.onboardAccessibility = false
                client.options.save()
                client.setScreen(TitleScreen())
            }
            if (!started && client.screen is TitleScreen && client.overlay == null) {
                started = true
                start(client)
            }
        }
    }

    /**
     * 準備輸出資料夾與畫面設定，在用戶端主執行緒上拍攝、後製並寫出結果。
     *
     * @param client 目前客戶端。
     */
    private fun start(client: Minecraft) {
        applyDisplayOptions(client)
        hidePresentations()
        val root = client.gameDirectory.toPath().resolve(OUTPUT_FOLDER)
        val outputDirectory = root.resolve(startedAt.format(AutotestReportRetention.FOLDER_FORMAT)).toAbsolutePath().normalize()
        Files.createDirectories(outputDirectory)
        AutotestReportRetention.prune(root, AutotestReportRetention.keepCount(null))
        logger.info("Showcase started; output folder {}", outputDirectory)
        CoroutineScope(SupervisorJob() + (client as Executor).asCoroutineDispatcher()).launch {
            val context = AutotestContext(client, outputDirectory, STEP_ID)
            val scene = ShowcaseScene(context)
            val error = try {
                scene.run()
                null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                failure
            }
            val shots = scene.shots.map { shot -> shot.copy(outputs = postProcess(outputDirectory.resolve(shot.file), outputDirectory)) }
            val passed = error == null && context.checks.all(AutotestCheck::passed)
            val manifest = ShowcaseManifest(
                startedAt = startedAt.toString(),
                durationMillis = startMark.elapsedNow().inWholeMilliseconds,
                status = if (passed) AutotestStatus.PASSED else AutotestStatus.FAILED,
                site = scene.site?.let { ShowcasePosition(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) },
                shots = shots,
                checks = context.checks.toList(),
                error = error?.stackTraceToString(),
            )
            Files.writeString(outputDirectory.resolve(MANIFEST_FILE), JSON.encodeToString(ShowcaseManifest.serializer(), manifest))
            logger.info("Showcase {} with {} screenshots; closing the game", if (passed) "finished" else "failed", shots.size)
            client.stop()
        }
    }

    /**
     * 後製一張截圖。失敗時記錄並略過，不影響其他截圖。
     *
     * @param source 原始截圖。
     * @param outputDirectory 輸出資料夾。
     * @return 產生的檔案，相對於輸出資料夾。
     */
    private fun postProcess(source: Path, outputDirectory: Path): List<String> = try {
        val title = mode.titleImage?.takeIf(Files::isRegularFile)?.let { ImageIO.read(it.toFile()) }
        val folder = outputDirectory.relativize(source.parent).toString().replace('\\', '/')
        ShowcaseImages.process(source, title).map { if (folder.isEmpty()) it else "$folder/$it" }
    } catch (failure: Exception) {
        logger.warn("Showcase could not post-process {}; the original screenshot is kept", source, failure)
        emptyList()
    }

    /**
     * 固定影響截圖的畫面設定：視野角度，並關掉視角搖晃與新手教學提示。設定存在宣傳截圖獨立的執行目錄。
     *
     * @param client 目前客戶端。
     */
    private fun applyDisplayOptions(client: Minecraft) {
        val options = client.options
        options.fov().set(FOV)
        options.bobView().set(false)
        options.tutorialStep = TutorialSteps.NONE
        client.tutorial.setStep(TutorialSteps.NONE)
        options.save()
    }

    /** 關掉本 mod 全部可由玩家關閉的面板、高亮與提示，並關掉牌面角落的輔助標籤。 */
    private fun hidePresentations() {
        val store = GlobalContext.get().get<MahjongClientConfigStore>()
        val hidden = MahjongPresentationVisibilityConfig(
            roundInfoEnabled = false,
            playerInfoEnabled = false,
            lobbyInfoEnabled = false,
            diceResultEnabled = false,
            compactPromptEnabled = false,
            discardAnalysisEnabled = false,
            winSettlementEnabled = false,
            drawSettlementEnabled = false,
            matchSettlementEnabled = false,
            matchingTileHighlightEnabled = false,
            latestDiscardHighlightEnabled = false,
            discardSourceEnabled = false,
            discardPopupEnabled = false,
            meldPopupEnabled = false,
            automaticControlStatusEnabled = false,
        )
        val result = store.save(store.current.copy(tileLabelsEnabled = false, presentationVisibility = hidden))
        if (result is MahjongClientConfigUpdateResult.Failure) logger.error("Showcase could not hide the presentations: {}", result.message)
    }

    private companion object {
        /** 結果在遊戲資料夾中的資料夾名稱。 */
        const val OUTPUT_FOLDER = "showcase"

        /** 截圖檔名的前綴。 */
        const val STEP_ID = "showcase"

        /** 結果檔名。 */
        const val MANIFEST_FILE = "showcase.json"

        /** 截圖時的視野角度。 */
        const val FOV = 70

        /** 結果的 JSON 格式。 */
        val JSON = Json { prettyPrint = true }
    }
}
