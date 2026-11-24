package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestCheck
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla.performCommand
import com.mojang.blaze3d.platform.NativeImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
     * 在單人世界的伺服器執行緒上執行一段程式，等它執行完。
     *
     * @param action 要執行的程式。
     * @return [action] 的結果。
     */
    suspend fun <T> onServer(action: (MinecraftServer) -> T): T {
        val server = checkNotNull(client.singleplayerServer) { "No integrated server" }
        val result = CompletableFuture.supplyAsync({ action(server) }, server)
        while (!result.isDone) delay(TICK)
        return result.join()
    }

    /**
     * 以本機玩家的身分在伺服器上執行指令，等伺服器執行完。
     *
     * 指令的回饋不送到聊天欄，記在回傳的結果中；之後才送出的回饋（例如非同步載入的結果）也會記進同一份結果。
     *
     * @param command 不含開頭斜線的指令。
     * @return 指令的結果與回饋。
     */
    suspend fun runCommand(command: String): AutotestCommandFeedback {
        val playerId = checkNotNull(client.player) { "No player to run the command" }.uuid
        val feedback = AutotestCommandFeedback(command)
        val result = onServer { server ->
            val player = checkNotNull(server.playerList.getPlayer(playerId)) { "The player is not on the server" }
            performCommand(server, player.createCommandSourceStack().withSource(feedback), command)
        }
        feedback.result = result
        return feedback
    }

    /**
     * 以本機玩家的身分在伺服器上執行指令，並把指令成功記為一項檢查。
     *
     * @param command 不含開頭斜線的指令。
     * @return 指令成功時為 true。
     */
    suspend fun runCheckedCommand(command: String): Boolean {
        val feedback = runCommand(command)
        return check("Command /$command succeeds", feedback.succeeded, feedback.failures.joinToString().ifEmpty { null })
    }

    /**
     * 執行用戶端指令；用戶端指令在送出時就在用戶端執行完。
     *
     * @param command 不含開頭斜線的指令。
     */
    fun runClientCommand(command: String) {
        checkNotNull(client.player) { "No player to run the command" }.connection.sendCommand(command)
    }

    /**
     * 清空本機玩家的背包，等用戶端收到；背包已經是空的時不執行指令，因為原版對空背包執行清除會回報錯誤。
     *
     * @return 在時限內清空時為 true。
     */
    suspend fun clearInventory(): Boolean {
        val player = checkNotNull(client.player) { "No player to clear" }
        if (!player.inventory.isEmpty && !runCheckedCommand("clear @s")) return false
        return check("Inventory is empty", waitUntil(SYNC_TIMEOUT) { player.inventory.isEmpty })
    }

    /**
     * 把本機玩家傳送到指定位置與方向，等用戶端收到新的位置。
     *
     * @param x 腳下的 x 座標。
     * @param y 腳下的 y 座標。
     * @param z 腳下的 z 座標。
     * @param yaw 水平角度。
     * @param pitch 俯仰角度。
     * @return 在時限內到達時為 true。
     */
    suspend fun teleport(x: Double, y: Double, z: Double, yaw: Float, pitch: Float): Boolean {
        if (!runCheckedCommand("tp @s $x $y $z $yaw $pitch")) return false
        val target = Vec3(x, y, z)
        val arrived = waitUntil(SYNC_TIMEOUT) {
            val player = client.player
            player != null && player.position().distanceToSqr(target) < POSITION_TOLERANCE && player.yRot == yaw && player.xRot == pitch
        }
        return check("Teleported to $x $y $z", arrived)
    }

    /**
     * 等待指定的遊戲刻數，以用戶端世界的遊戲時間計算；不在世界中時以時間換算。
     *
     * 只用在截圖時機，例如等演出播到某個畫面；其他等待一律等明確的條件。
     *
     * @param ticks 遊戲刻數。
     */
    suspend fun waitTicks(ticks: Int) {
        val level = client.level ?: return delay(TICK * ticks)
        val end = level.gameTime + ticks
        while (client.level === level && level.gameTime < end) delay(TICK)
    }

    /**
     * 等到條件成立或逾時。
     *
     * @param timeout 最長等待時間。
     * @param condition 每個遊戲刻檢查一次的條件。
     * @return 條件在逾時前成立時為 true。
     */
    suspend fun waitUntil(timeout: Duration, condition: suspend () -> Boolean): Boolean = withTimeoutOrNull(timeout) {
        while (!condition()) delay(TICK)
        true
    } ?: false

    /**
     * 移除測試世界中玩家與本 mod 實體以外的實體，例如區塊生成時出現的動物與掉落物，讓截圖只留下要看的東西。
     */
    suspend fun removeStrayEntities() {
        onServer { server ->
            server.allLevels.forEach { level ->
                level.allEntities.toList()
                    .filter { it !is Player && BuiltInRegistries.ENTITY_TYPE.getKey(it.type).namespace != MinecraftModMetadata.MOD_ID }
                    .forEach(Entity::discard)
            }
        }
    }

    /**
     * 等畫面畫完下一幀後截圖，存成 PNG。
     *
     * @param name 截圖名稱，會加上步驟識別碼作為檔名。
     */
    suspend fun screenshot(name: String) {
        removeStrayEntities()
        // 聊天訊息與右上角的提示會遮住畫面，截圖前清掉。
        client.gui.chat.clearMessages(false)
        client.toasts.clear()
        waitTicks(SCREENSHOT_SETTLE_TICKS)
        val fileName = "screenshots/$stepId-$name.png"
        val file = outputDirectory.resolve(fileName)
        Files.createDirectories(file.parent)
        Screenshot.takeScreenshot(client.mainRenderTarget).use { image: NativeImage -> image.writeToFile(file) }
        screenshots += fileName
    }

    /**
     * 從指定位置與方向截圖，不移動玩家：暫時把鏡頭交給一個不在世界中的隱形盔甲座，並隱藏介面與手，截完圖再還原。
     *
     * @param name 截圖名稱。
     * @param eye 鏡頭的位置。
     * @param yaw 鏡頭的水平角度，與原版相同：0 朝南（z 增加的方向）。
     * @param pitch 鏡頭的俯仰角度，正值朝下。
     */
    suspend fun screenshotFrom(name: String, eye: Vec3, yaw: Float, pitch: Float) {
        val level = client.level ?: return screenshot(name)
        val camera = ArmorStand(level, eye.x, eye.y, eye.z)
        camera.setPos(eye.x, eye.y - camera.eyeHeight, eye.z)
        camera.isInvisible = true
        camera.setNoGravity(true)
        camera.yRot = yaw
        camera.yRotO = yaw
        camera.yHeadRot = yaw
        camera.yHeadRotO = yaw
        camera.xRot = pitch
        camera.xRotO = pitch
        camera.setOldPosAndRot()
        val previous = client.cameraEntity ?: client.player
        val hideGui = client.options.hideGui
        client.options.hideGui = true
        client.setCameraEntity(camera)
        try {
            screenshot(name)
        } finally {
            previous?.let(client::setCameraEntity)
            client.options.hideGui = hideGui
        }
    }

    private companion object {
        /** 一個遊戲刻的時間。 */
        val TICK = 50.milliseconds

        /** 截圖前等待畫面穩定的遊戲刻數。 */
        const val SCREENSHOT_SETTLE_TICKS = 2

        /** 等用戶端收到伺服器變化的最長時間。 */
        val SYNC_TIMEOUT = 10.seconds

        /** 傳送後位置相同的容許誤差，為距離的平方。 */
        const val POSITION_TOLERANCE = 1.0e-4
    }
}
