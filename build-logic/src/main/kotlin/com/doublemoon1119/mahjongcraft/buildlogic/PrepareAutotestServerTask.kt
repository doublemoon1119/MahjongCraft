package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * 伺服器自動測試第一段開機前，準備測試專用的執行目錄：刪掉上次的測試世界，寫入固定的伺服器設定，並代執行者同意 Minecraft
 * EULA。
 *
 * 只在帶 `mahjongcraftAutotest` 的伺服器自動測試前執行，只動測試專用的執行目錄；平常的 `run/server` 不受影響。寫入 EULA
 * 同意時一律在建置輸出印出警告，讓每位執行測試的人都知道這件事。
 *
 * @property runDirectory 伺服器自動測試的執行目錄。
 * @property port 伺服器使用的連接埠。
 */
@DisableCachingByDefault(because = "Deletes and rewrites a local run directory")
abstract class PrepareAutotestServerTask : DefaultTask() {
    @get:Internal
    abstract val runDirectory: DirectoryProperty

    @get:Internal
    abstract val port: Property<Int>

    /** 刪掉上次的測試世界，寫入伺服器設定與 EULA 同意。 */
    @TaskAction
    fun prepare() {
        val directory = runDirectory.get().asFile
        directory.mkdirs()
        directory.resolve(LEVEL_NAME).deleteRecursively()
        directory.resolve("server.properties").writeText(
            SERVER_PROPERTIES.entries.joinToString(separator = "\n", postfix = "\n") { (key, value) -> "$key=$value" } +
                "server-port=${port.get()}\n",
        )
        directory.resolve("eula.txt").writeText(
            "# Written by the MahjongCraft automated server test, which runs only with -PmahjongcraftAutotest.\n" +
                "# https://aka.ms/MinecraftEULA\neula=true\n",
        )
        logger.warn(
            "WARNING: The automated server test accepted the Minecraft EULA (https://aka.ms/MinecraftEULA) on your behalf " +
                "in {}. Do not run it unless you agree to the EULA.",
            directory.resolve("eula.txt"),
        )
    }

    /** 測試世界與伺服器設定。 */
    companion object {
        /** 測試世界的資料夾名稱，與用戶端自動測試的世界同名。 */
        const val LEVEL_NAME: String = "mahjongcraft-autotest"

        /** 寫入 `server.properties` 的固定設定：固定種子的超平坦世界、離線驗證、和平難度，並降低視距讓開機與存檔快一些。 */
        private val SERVER_PROPERTIES = linkedMapOf(
            "level-name" to LEVEL_NAME,
            "level-seed" to "1119",
            "level-type" to "minecraft\\:flat",
            "generate-structures" to "false",
            "online-mode" to "false",
            "gamemode" to "creative",
            "difficulty" to "peaceful",
            "spawn-monsters" to "false",
            "spawn-animals" to "false",
            "spawn-npcs" to "false",
            "spawn-protection" to "0",
            "view-distance" to "4",
            "simulation-distance" to "4",
            "motd" to "MahjongCraft automated test",
        )
    }
}
