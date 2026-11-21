package com.doublemoon1119.mahjongcraft.platform.fabric.client.tableprop

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.client.character.CharacterPreviewCache
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropKindDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropTarget
import net.fabricmc.api.EnvType
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.world.ClientWorld
import net.minecraft.entity.Entity
import net.minecraft.util.crash.CrashException
import kotlin.coroutines.cancellation.CancellationException

/**
 * 客戶端場景中畫出的桌上物件，例如牌譜回放當時桌上的點棒；工廠只在有界的 tick 工作中執行。
 *
 * @property registry 客戶端自己的桌上物件預覽登記。
 * @property failures 擴充錯誤的隔離規則。
 */
internal class ClientTablePropPreviews(
    private val registry: ClientTablePropPreviewRegistry,
    private val failures: ExtensionFailurePolicy,
) {
    /** 擴充失敗診斷，不含牌局資訊。 */
    private val logger = mahjongCraftLogger(ClientTablePropPreviews::class)

    /** 依物件身分與內容保存的實體，最多 [CAPACITY] 個。 */
    private val models = CharacterPreviewCache<TablePropTarget, PropModel>(CAPACITY)

    /** 建立或繪製失敗的物件下次重試的 tick。 */
    private val retryAt = linkedMapOf<TablePropTarget, Long>()

    /** 一次連線內每種物件只回報一次相同錯誤。 */
    private val reported = mutableMapOf<String, String>()

    /** 目前實體所屬世界；切換時清理全部實體。 */
    private var world: ClientWorld? = null

    /** 本機重試時鐘，不依賴世界遊戲時間。 */
    private var ticks = 0L

    /** 客戶端認得的物件種類，供擺放計算使用。 */
    val definitions: Map<String, TablePropKindDefinition> get() = registry.definitions

    /** 登記有界的實體建立工作與世界切換時的清理。 */
    fun initialize() {
        check(FabricLoader.getInstance().environmentType == EnvType.CLIENT)
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (world !== client.world) clear()
            world = client.world
            ticks++
            val current = world ?: return@register
            models.processOne { key ->
                if (ticks < retryAt.getOrElse(key) { 0L }) return@processOne null
                attempt(key) { registry.find(key.kind)?.provider?.create(current, key)?.let(::PropModel) }.also { model ->
                    if (model == null) postpone(key) else retryAt.remove(key)
                }
            }
        }
    }

    /**
     * 取得可放進場景繪製的實體；建立工作在 tick 中排隊執行，失敗後的等待期間不回傳。
     *
     * 同樣身分與內容的物件共用一個實體，位置與朝向由繪製端決定。
     *
     * @param target 物件的身分、規則與內容。
     * @return 已建立的實體；排隊中、種類沒有登記或等待重試時為 null。
     */
    fun model(target: TablePropTarget): Entity? {
        if (world == null || registry.find(target.kind) == null) return null
        val key = key(target)
        if (ticks < retryAt.getOrElse(key) { 0L }) return null
        return models.request(key)?.entity
    }

    /**
     * 隔離繪製 [model] 實體時種類造成的失敗；同類錯誤僅回報一次，失敗的物件在等待重試期間不再提供實體。
     *
     * @param target 實體所屬的物件。
     * @param draw 繪製工作。
     */
    fun guard(target: TablePropTarget, draw: () -> Unit) {
        attempt(key(target)) { draw() }
    }

    /** 清理實體與失敗記錄，舊世界的實體不能沿用。 */
    fun clear() {
        try {
            models.clear()
        } finally {
            retryAt.clear()
            reported.clear()
            world = null
        }
    }

    /**
     * 去掉位置與朝向的快取鍵。
     *
     * @param target 物件。
     * @return 只保留身分、規則、種類與內容的鍵。
     */
    private fun key(target: TablePropTarget): TablePropTarget = target.copy(x = 0.0, y = 0.0, z = 0.0, yaw = 0f)

    /**
     * 隔離種類的建立或繪製失敗，同類錯誤僅回報一次並延後重試。
     *
     * @param key 物件快取鍵。
     * @param block 建立或繪製工作。
     * @return 工作結果，失敗或等待重試時為 null。
     */
    private fun <T> attempt(key: TablePropTarget, block: () -> T?): T? {
        if (ticks < retryAt.getOrElse(key) { 0L }) return null
        return try {
            block()
        } catch (cause: Throwable) {
            val root = (cause as? CrashException)?.cause ?: cause
            if (root is CancellationException || !failures.isIsolatable(root)) throw root
            val signature = "${cause::class.qualifiedName}:${cause.message}"
            if (reported.put(key.kind, signature) != signature) logger.warn("Table prop preview failed kind={}", key.kind, cause)
            postpone(key)
            null
        }
    }

    /**
     * 暫時失敗等兩秒後重試，並限制記錄數量。
     *
     * @param key 失敗的物件快取鍵。
     */
    private fun postpone(key: TablePropTarget) {
        if (retryAt.size >= CAPACITY && key !in retryAt) retryAt.remove(retryAt.keys.first())
        retryAt[key] = ticks + RETRY_TICKS
    }

    /**
     * 工廠建立的實體；淘汰時移除。
     *
     * @property entity 不在世界中的實體。
     */
    private class PropModel(val entity: Entity) : AutoCloseable {
        /** 移除不在世界中的實體。 */
        override fun close() {
            entity.discard()
        }
    }

    /** 快取與重試的上限。 */
    private companion object {
        /** 同時保留的實體數。 */
        const val CAPACITY = 64

        /** 失敗後等待重試的 tick 數。 */
        const val RETRY_TICKS = 40L
    }
}
