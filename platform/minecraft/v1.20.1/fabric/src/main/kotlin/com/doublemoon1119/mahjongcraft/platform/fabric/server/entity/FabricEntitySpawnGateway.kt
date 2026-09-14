package com.doublemoon1119.mahjongcraft.platform.fabric.server.entity

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.minecraft.entity.Entity
import net.minecraft.server.world.ServerWorld
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
import kotlin.uuid.Uuid

/**
 * Minecraft entity 正式生成的單一入口；在觸碰 world 的 entity index 前確認目前位於 server thread，
 * 並在失敗時保留桌面、entity、執行緒與既有 mutation 的診斷上下文。
 */
@Single
class FabricEntitySpawnGateway {
    private val logger = LoggerFactory.getLogger(MinecraftModMetadata.MOD_ID)
    private val activeSpawn = AtomicReference<SpawnContext?>()

    /** 只在 server thread 上檢查 UUID collision，然後原樣回傳或重拋 [ServerWorld.spawnEntity] 結果。 */
    fun spawn(world: ServerWorld, entity: Entity, source: String, tableId: Uuid): Boolean {
        val serverIsOnThread = world.server.isOnThread
        val context = "source=$source tableId=$tableId entity=${entity.javaClass.name} id=${entity.id} " +
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

    /** 一次進行中 spawn mutation 的最小診斷資料。 */
    private data class SpawnContext(
        val source: String,
        val tableId: Uuid,
        val entityId: Int,
        val entityUuid: String,
        val threadName: String,
    )
}
