package com.doublemoon1119.mahjongcraft.platform.fabric.client.character

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.api.character.AiCharacterPreviewHandle
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.character.CharacterPreviewCache
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.character.CharacterPreviewImage
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.character.RoomFigureArea
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ClientEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.LoaderPlatform
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSource
import net.minecraft.ReportedException
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import kotlin.coroutines.cancellation.CancellationException
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid

/**
 * 客戶端直接模型預覽與小型頭像圖片，工廠只在有界 tick 工作中執行。
 *
 * @property registry 客戶端自己的能力登記。
 * @property renderer 獨立模型建立與頭像擷取。
 * @property failures 擴充錯誤的隔離規則。
 * @property platform 確認只在用戶端初始化的載入器資訊。
 */
internal class ClientAiCharacterPreviews(
    private val registry: ClientAiCharacterPreviewRegistry,
    private val renderer: NativeCharacterPreviewRenderer,
    private val failures: ExtensionFailurePolicy,
    private val platform: LoaderPlatform,
) {
    /** 擴充失敗診斷，不含牌局資訊。 */
    private val logger = mahjongCraftLogger(ClientAiCharacterPreviews::class)

    /** 最多 64 張 128×128 圖片，不保存模型實體。 */
    private val cache = CharacterPreviewCache<AiCharacterDefinition, CharacterPreviewImage>(64)

    /** 備援模型使用同一套有界資源快取，工廠不在繪製回呼中執行。 */
    private val models = CharacterPreviewCache<AiCharacterDefinition, PreviewModel>(64)

    /** 本人繪製失敗後暫停重試，不妨礙提供者備援。 */
    private val liveRetryAt = linkedMapOf<Uuid, Long>()

    /** 目前預覽資源所屬世界；切換時清理全部資源。 */
    private var world: ClientLevel? = null

    /** 模型重試與頭像截圖分開，頭像失敗不阻止直接模型。 */
    private val modelRetryAt = linkedMapOf<AiCharacterDefinition, Long>()

    /** 頭像工廠失敗的下次重試 tick。 */
    private val retryAt = linkedMapOf<AiCharacterDefinition, Long>()

    /** 一次連線內每種能力只回報一次相同錯誤。 */
    private val reported = mutableMapOf<String, String>()

    /** 本機重試時鐘，不依賴世界遊戲時間。 */
    private var ticks = 0L

    /**
     * 登記有界的模型／頭像建立工作與資源清理。
     *
     * @param events 登記用戶端事件用的事件來源。
     */
    fun initialize(events: ClientEvents) {
        check(platform.isClient)
        events.onClientTickEnd { client ->
            if (world !== client.level) clear()
            world = client.level
            ticks++
            val current = world ?: return@onClientTickEnd
            models.processOne { character ->
                if (ticks < modelRetryAt.getOrElse(character) { 0L }) return@processOne null
                attempt(character) { renderer.create(current, character)?.let { PreviewModel(it) } }.also { model ->
                    if (model == null) postpone(character, modelRetryAt) else modelRetryAt.remove(character)
                }
            }
            cache.processOne { character ->
                if (ticks < retryAt.getOrElse(character) { 0L }) return@processOne null
                try {
                    renderer.capture(current, character).also { image ->
                        if (image == null) postpone(character) else retryAt.remove(character)
                    }
                } catch (cause: Throwable) {
                    val root = (cause as? ReportedException)?.cause ?: cause
                    if (root is CancellationException || !failures.isIsolatable(root)) throw root
                    val id = character.preview?.capabilityId ?: character.id
                    val signature = "${cause::class.qualifiedName}:${cause.message}"
                    if (reported.put(id, signature) != signature) logger.warn("Character preview failed capabilityId={}", id, cause)
                    postpone(character)
                    null
                }
            }
        }
        events.onClientResourceReload(ResourceLocation("mahjongcraft", "character_previews")) {
            Minecraft.getInstance().execute { clear() }
        }
    }

    /**
     * 唯讀確認能力是否存在，不建立任何模型。
     *
     * @param id 完整能力識別碼。
     * @return 已登記時為 true。
     */
    fun hasFactory(id: String): Boolean = registry.find(id) != null

    /** 清理圖片與失敗記錄，舊連線的描述不能沿用到新世界。 */
    fun clear() {
        try {
            models.clear()
        } finally {
            clearImages()
        }
    }

    /** 清理圖片與錯誤重試狀態。 */
    private fun clearImages() {
        try {
            cache.clear()
        } finally {
            modelRetryAt.clear()
            liveRetryAt.clear()
            retryAt.clear()
            reported.clear()
            world = null
        }
    }

    /**
     * 頭像缺少明確 FACE 或該 FACE 失敗時，取得模型圖片的頭像區域。
     *
     * @param snapshot 已授權或歷史保存的完整外觀。
     * @return 已生成圖片；排隊中或能力缺失時為 null。
     */
    fun portrait(snapshot: AiCharacterSnapshot?): PlayerPortraitSource? = image(snapshot)?.portrait

    /**
     * 直接繪製已授權且已載入的本人，缺少時使用提供者的獨立備援。
     *
     * @param snapshot 當場描述；歷史僅使用這份資料。
     * @param context GUI 繪製上下文。
     * @param area 卡片上畫人物的範圍與放大倍數。
     * @param mouseX 游標水平座標。
     * @param mouseY 游標垂直座標。
     * @param worldEntityId 目前已授權的即時參照；歷史必須為 null。
     * @return 成功繪製模型時為 true，否則由頭像備援。
     */
    fun renderModel(snapshot: AiCharacterSnapshot?, context: GuiGraphics, area: RoomFigureArea, mouseX: Int, mouseY: Int, worldEntityId: Uuid? = null): Boolean {
        val current = Minecraft.getInstance().level ?: return false
        if (world !== current) return false
        val character = snapshot?.definition
        val live = worldEntityId?.let { id -> current.entitiesForRendering().firstOrNull { it.uuid == id.toJavaUuid() && !it.isRemoved } }
        if (live != null && ticks >= liveRetryAt.getOrElse(worldEntityId) { 0L }) {
            if (attempt(null) { GuiEntityModelRenderer.render(live, context, area, mouseX, mouseY) } == true) return true
            if (liveRetryAt.size >= 64 && worldEntityId !in liveRetryAt) liveRetryAt.remove(liveRetryAt.keys.first())
            liveRetryAt[worldEntityId] = ticks + 40L
        }
        character ?: return false
        if (!hasFactory(character.preview?.capabilityId ?: return false)) return false
        val model = models.request(character) ?: return false
        return attempt(character) { GuiEntityModelRenderer.render(model.handle.entity, context, area, mouseX, mouseY) } == true
    }

    /**
     * 取得可放進場景直接繪製的獨立模型；建立工作在 tick 中排隊執行，繪製失敗後的等待期間不回傳。
     *
     * 模型與卡片預覽共用同一份快取，呼叫端畫完後須讓它離開座位等暫時狀態。
     *
     * @param snapshot 歷史保存的完整外觀。
     * @return 已建立的模型；排隊中、能力缺失或等待重試時為 null。
     */
    fun sceneModel(snapshot: AiCharacterSnapshot?): Entity? {
        if (world !== Minecraft.getInstance().level) return null
        val character = snapshot?.definition ?: return null
        if (!hasFactory(character.preview?.capabilityId ?: return null)) return null
        if (ticks < modelRetryAt.getOrElse(character) { 0L }) return null
        return models.request(character)?.handle?.entity
    }

    /**
     * 隔離繪製 [sceneModel] 模型時提供者造成的失敗；同類錯誤僅回報一次，失敗的外觀在等待重試期間不再提供模型。
     *
     * @param snapshot 模型所屬的外觀。
     * @param draw 繪製工作。
     */
    fun guardSceneModel(snapshot: AiCharacterSnapshot, draw: () -> Unit) {
        attempt(snapshot.definition) { draw() }
    }

    /**
     * 隔離提供者的建立或繪製失敗，同類錯誤僅回報一次並延後重試。
     *
     * @param character 此次預覽描述。
     * @param block 建立或繪製工作。
     * @return 工作結果，暫時失敗時為 null。
     */
    private fun <T> attempt(character: AiCharacterDefinition?, block: () -> T?): T? {
        if (character != null && ticks < modelRetryAt.getOrElse(character) { 0L }) return null
        return try {
            block()
        } catch (cause: Throwable) {
            val root = (cause as? ReportedException)?.cause ?: cause
            if (root is CancellationException || !failures.isIsolatable(root)) throw root
            val id = character?.preview?.capabilityId ?: character?.id ?: "loaded_entity"
            val signature = "${cause::class.qualifiedName}:${cause.message}"
            if (reported.put(id, signature) != signature) logger.warn("Character model preview failed capabilityId={}", id, cause)
            character?.let { postpone(it, modelRetryAt) }
            null
        }
    }

    /**
     * 只擁有工廠新建的模型，淘汰時釋放，不接觸世界本人。
     *
     * @property handle 已驗證的獨立預覽資源。
     */
    private inner class PreviewModel(val handle: AiCharacterPreviewHandle) : AutoCloseable {
        /** 隔離提供者清理失敗，其他快取項目仍能釋放。 */
        override fun close() {
            attempt(null) { handle.close() }
        }
    }

    /**
     * 只排入當前世界的完整描述，不在 render 回呼執行工廠。
     *
     * @param snapshot 已授權外觀。
     * @return 已有圖片，或 null。
     */
    private fun image(snapshot: AiCharacterSnapshot?): CharacterPreviewImage? {
        if (world !== Minecraft.getInstance().level) return null
        val character = snapshot?.definition ?: return null
        val capability = character.preview?.capabilityId ?: return null
        if (registry.find(capability) == null) return null
        return cache.request(character)
    }

    /**
     * 暫時失敗等兩秒後重試，並限制記錄數量。
     *
     * @param character 失敗的完整描述。
     * @param retries 模型或頭像各自的重試記錄。
     */
    private fun postpone(character: AiCharacterDefinition, retries: MutableMap<AiCharacterDefinition, Long> = retryAt) {
        if (retries.size >= 64 && character !in retries) retries.remove(retries.keys.first())
        retries[character] = ticks + 40
    }
}
