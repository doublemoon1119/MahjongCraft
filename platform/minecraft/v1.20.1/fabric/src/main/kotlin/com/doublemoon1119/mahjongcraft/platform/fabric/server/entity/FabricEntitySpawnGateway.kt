package com.doublemoon1119.mahjongcraft.platform.fabric.server.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import java.util.concurrent.atomic.AtomicReference
import kotlin.uuid.Uuid

/**
 * Minecraft entity 正式生成的單一入口；在觸碰 world 的 entity index 前確認目前位於 server thread，
 * 並在失敗時保留桌面、entity、執行緒與既有 mutation 的診斷上下文。
 */
class FabricEntitySpawnGateway {
    /** 記錄生成失敗與識別碼衝突的診斷。 */
    private val logger = mahjongCraftLogger(FabricEntitySpawnGateway::class)

    /** 目前正在生成的實體上下文；為 null 時可開始下一次生成。 */
    private val activeSpawn = AtomicReference<SpawnContext?>()

    /**
     * 只在 server thread 上檢查 UUID collision，然後原樣回傳或重拋 [ServerWorld.spawnEntity] 結果。
     *
     * @param world 目標 server 世界。
     * @param entity 要生成的 entity。
     * @param source 供失敗診斷使用的生成來源識別。
     * @param tableId 所屬麻將桌；不屬於任何麻將桌的 entity（例如麻將凳的座位）為 null。
     * @return 世界生成實體的結果；失敗回傳 false 時會記錄警告。
     * @throws IllegalStateException 不在伺服器主執行緒，或存在並行／重入的生成工作。
     */
    fun spawn(world: ServerWorld, entity: Entity, source: String, tableId: Uuid?): Boolean {
        val serverIsOnThread = world.server.isOnThread
        val context = "source=$source tableId=${tableId ?: "none"} entity=${entity.javaClass.name} id=${entity.id} " +
            "uuid=${entity.uuid} thread=${Thread.currentThread().name} serverIsOnThread=$serverIsOnThread " +
            "world=${world.registryKey.value}"
        check(serverIsOnThread) { "Entity spawn attempted off server thread: $context" }
        val spawnContext = SpawnContext(source, tableId, entity.id, entity.uuid.toString(), Thread.currentThread().name)
        val existingSpawn = activeSpawn.get()
        check(activeSpawn.compareAndSet(null, spawnContext)) {
            "Concurrent or reentrant entity spawn detected: requested=[$context] active=[$existingSpawn]"
        }
        return try {
            val collision = world.getEntity(entity.uuid)
            if (collision != null) {
                logger.warn("Entity UUID collision before spawn: {} existing={} existingId={}", context, collision.javaClass.name, collision.id)
            }
            val spawned = world.spawnEntity(entity)
            if (!spawned) logger.warn("Entity spawn returned false: {}", context)
            spawned
        } catch (throwable: Throwable) {
            logger.error("Entity spawn threw: {}", context, throwable)
            throw throwable
        } finally {
            if (!activeSpawn.compareAndSet(spawnContext, null)) {
                logger.error(
                    "Entity spawn diagnostic state changed unexpectedly: completed={} active={}",
                    spawnContext,
                    activeSpawn.get(),
                )
            }
        }
    }

    /**
     * 一次進行中實體生成的診斷資料。
     *
     * @property source 生成來源識別碼。
     * @property tableId 所屬麻將桌；非牌桌實體為 null。
     * @property entityId 實體的執行期數字識別碼。
     * @property entityUuid 實體 UUID 字串。
     * @property threadName 發起生成的執行緒名稱。
     */
    private data class SpawnContext(
        val source: String,
        val tableId: Uuid?,
        val entityId: Int,
        val entityUuid: String,
        val threadName: String,
    )
}
