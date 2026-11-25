package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 牌局進行中存檔離開再重新進入。
 *
 * - 確認本機玩家仍坐在牌桌對局中，而且本 mod 會存檔的實體數量不變。
 * - 需要前面步驟開好的牌局。
 */
internal object AutotestSaveStep : AutotestStep {
    override val id: String = "save"

    override suspend fun run(context: AutotestContext) {
        val client = context.client
        if (!context.check("A game is running at the table", context.ensureGameRunning())) return
        val before = modEntityCounts(client)
        context.tableScreenshots("before")
        AutotestWorld.saveAndQuit(client)
        val left = context.waitUntil(1.minutes) { client.level == null && client.singleplayerServer == null }
        if (!context.check("Saved and left the world", left)) return
        AutotestWorld.reenter(client)
        val entered = context.waitUntil(2.minutes) { client.level != null && client.player != null && client.screen == null && client.overlay == null }
        if (!context.check("Entered the world again", entered)) return
        val inGame = context.waitUntil(SYNC_TIMEOUT) { AutotestTable.isSeated(client) }
        if (!context.check("Still seated at the table in a game", inGame)) return
        // 實體在玩家進入後陸續送到用戶端，等數量回到存檔前的樣子。
        val restored = context.waitUntil(SYNC_TIMEOUT) { modEntityCounts(client) == before }
        context.check("Mod entity counts are the same", restored, "before $before, after ${modEntityCounts(client)}")
        if (!context.check("The game is ready again", context.ensureGameRunning())) return
        context.tableScreenshots("after")
    }

    /**
     * 世界中本 mod 各種實體的數量。
     *
     * @param client 目前客戶端。
     * @return 實體類型識別碼對數量。
     */
    private fun modEntityCounts(client: Minecraft): Map<String, Int> = client.level?.entitiesForRendering()?.toList().orEmpty()
        .map { BuiltInRegistries.ENTITY_TYPE.getKey(it.type) }
        .filter { it.namespace == MinecraftModMetadata.MOD_ID && !it.path.endsWith(PRESENTATION_SUFFIX) }
        .groupingBy { it.path }
        .eachCount()
        .toSortedMap()

    /** 演出用實體類型名稱的結尾。這些實體只在演出期間存在，不存檔，比對時略過。 */
    private const val PRESENTATION_SUFFIX = "_presentation"

    /** 等用戶端收到伺服器狀態的最長時間。 */
    private val SYNC_TIMEOUT = 30.seconds
}
