package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.logic.module.RoundInfoLine
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.math.Box
import net.minecraft.world.World
import kotlin.uuid.Uuid

/**
 * 桌面中央局況顯示用的純視覺 entity——只同步組成畫面內容需要的原始數值（[lines]，實際顯示什麼完全由
 * 規則模組決定），不同步已經翻譯好的文字——翻譯必須在 client 端依各自語系解析，不能在 server 端就
 * 烘焙成固定語言的字串，見 `FabricMahjongRoundInfoPresenter`／`MahjongRoundInfoEntityRenderer` 的
 * 分工：server 只決定「數值」，client renderer 才決定「怎麼翻譯成文字」。
 *
 * 每張桌子固定只有一個，不像牌／點棒有多個 UUID 各自代表獨立的牌局物件，因此不需要
 * `managedByGame`／`managedTableId` 以外的識別欄位——找既有 entity 時直接依 [managedTableId] 搜尋
 * 即可，見 `FabricMahjongRoundInfoPresenter`。
 */
class MahjongRoundInfoEntity(
    type: EntityType<out MahjongRoundInfoEntity> = ModEntities.mahjongRoundInfo,
    world: World,
) : AnimatedMahjongEntity<Nothing>(type, world) {
    /**
     * 要顯示的完整內容，依序顯示——`DataTracker` 只能同步基本型別，這裡編碼成
     * `"key1:1,2;key2:5"` 存進一個 `TrackedData<String>`（`:` 分隔 key 與參數列表，`,` 分隔參數
     * 列表本身，沒有任何內容時為空字串），client 端 renderer 解碼後才依認得的 key 翻譯成文字，見
     * [MahjongRoundInfoEntity] KDoc 同一套分工。
     */
    var lines: List<RoundInfoLine>
        get() = dataTracker[LINES].takeIf(String::isNotBlank)?.split(";")?.mapNotNull { entry ->
            val parts = entry.split(":", limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val args = parts[1].takeIf(String::isNotEmpty)?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
            RoundInfoLine(parts[0], args)
        } ?: emptyList()
        set(value) = dataTracker.set(LINES, value.joinToString(";") { "${it.key}:${it.args.joinToString(",")}" })

    /** 正式牌局所屬麻將桌；未指派時為 `null`。 */
    var managedTableId: Uuid?
        get() = dataTracker[MANAGED_TABLE_ID]
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
        private set(value) = dataTracker.set(MANAGED_TABLE_ID, value?.toString().orEmpty())

    init {
        setNoGravity(true)
    }

    /**
     * 這個 entity 物件在記憶體裡第一次被 tick 到的 `world.time`；只在記憶體內，不寫進存檔，理由跟
     * [MahjongScoringStickEntity.firstTickWorldTime] 完全一致（避免世界重新載入一次補跑大量 tick 時
     * 誤判成早就該消失了）。
     */
    private var firstTickWorldTime: Long = Long.MIN_VALUE

    /** 目前局況顯示隱形 lease 的絕對截止時間；沒有 lease 時為 [Long.MIN_VALUE]。 */
    private var hiddenUntilGameTime: Long = Long.MIN_VALUE

    /**
     * fallback 自動清除——**不是主要清除路徑**。正常生命週期是跟牌牆同時生成、換局時由
     * `FabricMahjongRoundInfoPresenter.present()` 找到既有 entity 就地更新內容（不重新生成），或由
     * `FabricGamePresentationPublisher.clearPlayerAreas()`（回房間等情境）顯式清除；這裡只是意外情境
     * （伺服器崩潰、對局非正常結束導致沒有機會走到正常清除流程）的保險，門檻抓得遠大於正常一局遊戲
     * 時長，理由同 [MahjongScoringStickEntity.tick] 的同款設計——這個 entity 沒有自由放置模式（一律
     * 由牌局管理），不需要像點棒那樣額外判斷 `managedByGame`。
     */
    override fun tick() {
        super.tick()
        if (world.isClient) return
        if (firstTickWorldTime == Long.MIN_VALUE) firstTickWorldTime = world.time
        if (world.time - firstTickWorldTime >= FALLBACK_DESPAWN_AFTER_TICKS) {
            discard()
        }
    }

    /** 純視覺物件，不提供物理阻擋、不參與推擠、不能被互動選取。 */
    override fun isCollidable(): Boolean = false

    override fun isPushable(): Boolean = false

    override fun canHit(): Boolean = false

    /** 覆蓋 billboard 的實際顯示範圍，避免攝影機只依 entity 錨點附近的小範圍進行視錐裁切。 */
    override fun getVisibilityBoundingBox(): Box = Box(
        x - WIDTH / 2.0,
        y - HEIGHT / 2.0,
        z - WIDTH / 2.0,
        x + WIDTH / 2.0,
        y + HEIGHT / 2.0,
        z + WIDTH / 2.0,
    )

    /** 將 entity 標記為指定正式牌局桌子管理。 */
    fun assignToTable(tableId: Uuid) {
        check(!world.isClient) { "Managed round info display must be assigned by the server" }
        managedTableId = tableId
    }

    /**
     * 隱形到 [gameTime]；重複呼叫只能延長期限，並以單一可持久化動畫序列取代舊期限。
     */
    fun hideUntil(gameTime: Long) {
        check(!world.isClient) { "Round info visibility lease must be changed by the server" }
        if (gameTime <= hiddenUntilGameTime) return
        hiddenUntilGameTime = gameTime
        replaceAnimationQueue(
            listOf(
                AnimationStep.SetInvisible(true),
                AnimationStep.WaitUntil(gameTime),
                AnimationStep.SetInvisible(false),
            ),
        )
    }

    /**
     * 保持隱形直到管理此顯示的牌局清除 entity。
     *
     * 終局面板結束後會立即返回房間，不應先恢復局況顯示再清除；此方法以可持久化動畫佇列保存該語意。
     */
    fun hideUntilRemoved() {
        check(!world.isClient) { "Round info visibility lease must be changed by the server" }
        hiddenUntilGameTime = Long.MAX_VALUE
        replaceAnimationQueue(listOf(AnimationStep.SetInvisible(true)))
    }

    /** 新局已確定開始時解除所有舊 presentation lease，立即恢復局況顯示。 */
    fun showNow() {
        check(!world.isClient) { "Round info visibility lease must be changed by the server" }
        hiddenUntilGameTime = Long.MIN_VALUE
        replaceAnimationQueue(emptyList())
        setInvisible(false)
    }

    /** 局況顯示沒有專屬瞬間動畫。 */
    override fun applyCustomStep(step: Nothing) = error("Round info display has no custom animation step")

    /** 局況顯示不支援位移動畫。 */
    override fun applyPlayMotion(step: AnimationStep.PlayMotion, startGameTime: Long) = error("Round info display does not support motion animation")

    /** 局況顯示沒有可序列化的專屬動畫。 */
    override fun serializeCustomStep(step: Nothing, nbt: NbtCompound) = error("Round info display has no custom animation step")

    /** 局況顯示沒有可還原的專屬動畫。 */
    override fun deserializeCustomStep(nbt: NbtCompound): Nothing = error("Round info display has no custom animation step")

    override fun initDataTracker() {
        dataTracker.startTracking(LINES, "")
        dataTracker.startTracking(MANAGED_TABLE_ID, "")
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        dataTracker.set(LINES, nbt.getString(NBT_KEY_LINES))
        managedTableId = nbt.getString(NBT_KEY_MANAGED_TABLE_ID)
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
        hiddenUntilGameTime = nbt.getLong(NBT_KEY_HIDDEN_UNTIL_GAME_TIME).takeIf { it > 0L } ?: Long.MIN_VALUE
        readAnimationQueueFromNbt(nbt)
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putString(NBT_KEY_LINES, dataTracker[LINES])
        managedTableId?.let { tableId -> nbt.putString(NBT_KEY_MANAGED_TABLE_ID, tableId.toString()) }
        if (hiddenUntilGameTime != Long.MIN_VALUE) nbt.putLong(NBT_KEY_HIDDEN_UNTIL_GAME_TIME, hiddenUntilGameTime)
        writeAnimationQueueToNbt(nbt)
    }

    companion object {
        /** 足以涵蓋目前三行本地化文字面板的水平 debug／追蹤寬度。 */
        const val WIDTH: Float = 3.0f

        /** 足以涵蓋目前三行本地化文字面板的垂直 debug／追蹤高度。 */
        const val HEIGHT: Float = 1.5f

        private const val NBT_KEY_LINES = "Lines"
        private const val NBT_KEY_MANAGED_TABLE_ID = "ManagedTableId"
        private const val NBT_KEY_HIDDEN_UNTIL_GAME_TIME = "HiddenUntilGameTime"

        /**
         * fallback 自動清除門檻（見 [tick]）——1 小時份的 tick 數，遠大於正常一局遊戲時長，只用來
         * 兜底意外情境，不影響正常對局的局況顯示。
         */
        private const val FALLBACK_DESPAWN_AFTER_TICKS = 20L * 60L * 60L

        /** 同步要顯示的完整內容，編碼格式見 [lines]。 */
        private val LINES: TrackedData<String> =
            DataTracker.registerData(MahjongRoundInfoEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 同步所屬麻將桌 UUID；空字串表示尚未指派。 */
        private val MANAGED_TABLE_ID: TrackedData<String> =
            DataTracker.registerData(MahjongRoundInfoEntity::class.java, TrackedDataHandlerRegistry.STRING)
    }
}
