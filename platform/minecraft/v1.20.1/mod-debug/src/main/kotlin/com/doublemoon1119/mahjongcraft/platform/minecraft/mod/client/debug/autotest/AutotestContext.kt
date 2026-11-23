package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.mojang.blaze3d.platform.NativeImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * 一個步驟執行時使用的環境：記錄檢查、截圖、等待遊戲進展。
 *
 * @property client 目前客戶端。
 * @property outputDirectory 報告所在的資料夾。
 * @property stepId 目前步驟的識別碼，用於截圖檔名。
 */
internal class AutotestContext(
    val client: Minecraft,
    private val outputDirectory: Path,
    private val stepId: String,
) {
    /** 目前步驟的檢查。 */
    val checks = mutableListOf<AutotestCheck>()

    /** 目前步驟的截圖檔名。 */
    val screenshots = mutableListOf<String>()

    /**
     * 記錄一項檢查。
     *
     * @param name 檢查內容。
     * @param passed 是否通過。
     * @param detail 補充說明；沒有時為 null。
     * @return [passed]。
     */
    fun check(name: String, passed: Boolean, detail: String? = null): Boolean {
        checks += AutotestCheck(name, passed, detail)
        return passed
    }

    /**
     * 等待指定的遊戲刻數。
     *
     * @param ticks 遊戲刻數。
     */
    suspend fun waitTicks(ticks: Int) {
        delay(TICK * ticks)
    }

    /**
     * 等到條件成立或逾時。
     *
     * @param timeout 最長等待時間。
     * @param condition 每個遊戲刻檢查一次的條件。
     * @return 條件在逾時前成立時為 true。
     */
    suspend fun waitUntil(timeout: Duration, condition: () -> Boolean): Boolean = withTimeoutOrNull(timeout) {
        while (!condition()) delay(TICK)
        true
    } ?: false

    /**
     * 等畫面畫完下一幀後截圖，存成 PNG。
     *
     * @param name 截圖名稱，會加上步驟識別碼作為檔名。
     */
    suspend fun screenshot(name: String) {
        waitTicks(SCREENSHOT_SETTLE_TICKS)
        val fileName = "screenshots/$stepId-$name.png"
        val file = outputDirectory.resolve(fileName)
        Files.createDirectories(file.parent)
        Screenshot.takeScreenshot(client.mainRenderTarget).use { image: NativeImage -> image.writeToFile(file) }
        screenshots += fileName
    }

    private companion object {
        /** 一個遊戲刻的時間。 */
        val TICK = 50.milliseconds

        /** 截圖前等待畫面穩定的遊戲刻數。 */
        const val SCREENSHOT_SETTLE_TICKS = 2
    }
}
