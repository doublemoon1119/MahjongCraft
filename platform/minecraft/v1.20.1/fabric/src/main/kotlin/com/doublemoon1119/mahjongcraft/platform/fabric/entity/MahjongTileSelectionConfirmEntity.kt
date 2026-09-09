package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.world.World
import kotlin.uuid.Uuid

/**
 * 多選 preparation／動作選牌（`maxCount > 1`）期間，決策玩家手牌上方的可互動確認面板——右鍵即送出
 * 目前已選取的牌。純粹的互動目標，本身不攜帶選取內容（選取狀態只存在決策玩家自己 client 本機），
 * 伺服器端互動不需要任何邏輯，實際送出仍走既有的 `decisionSelection` channel，見
 * `PlayerDecisionHudController` 對這個 entity 類型的 client 端攔截處理。
 *
 * 跟 [MahjongTileEntity] 一樣有真正的碰撞箱、可被右鍵互動（覆寫 [canHit] 為預設可互動），不像
 * [MahjongRoundInfoEntity] 刻意設計成純視覺、不可互動。
 *
 * **一個確認面板由多個這個類別的 instance 拼湊而成**（見 `FabricMahjongTileSelectionConfirmPresenter`
 * 一次生成 `MahjongTileTableLayout.TILE_SELECTION_CONFIRM_SEGMENT_COUNT` 個、沿面板文字方向並排、
 * 位置固定不變）。原版碰撞箱系統的寬度值（[WIDTH]）同時套用在水平兩軸，無法只在文字方向寬、法線方向
 * 薄；用多個 [WIDTH] 很小的 instance 沿寬度方向拼起來，單一 instance 往碰撞箱法線方向多凸出的範圍
 * 維持在小範圍。只有 [isPrimary] 那個 instance 會實際畫出文字／背景（見
 * `MahjongTileSelectionConfirmEntityRenderer`），其餘 instance 純粹提供額外可點擊範圍；每個 instance
 * 是否真的回應右鍵由 [isActive] 決定，每幀依目前實際渲染出的文字寬度重新計算，讓可點擊範圍精確貼合
 * 當下顯示的文字，而不是整組 instance 涵蓋的固定最大寬度。
 */
class MahjongTileSelectionConfirmEntity(
    type: EntityType<out MahjongTileSelectionConfirmEntity> = ModEntities.mahjongTileSelectionConfirm,
    world: World,
) : AnimatedMahjongEntity<Nothing>(type, world) {
    /**
     * 這個確認面板屬於哪位玩家。透過原版 entity tracking 廣播給範圍內所有玩家（Minecraft 沒有內建的
     * per-player 專屬同步機制），只有本人的 client 才會實際畫出來——其他人的 client 什麼都不畫，等於
     * 視覺上對他們不存在，見 `MahjongTileSelectionConfirmEntityRenderer`。
     *
     * **已知取捨**：改過的客戶端仍讀得到這個 entity 的存在與位置，等於能推測出「這位玩家目前正在
     * 選牌」；自己回合的決策（目前唯一的使用場景）本來就公開輪到誰，影響有限，若未來用在反應類
     * （碰/吃/槓/榮和資格詢問）這種刻意隱藏決策對象的情境，才需要重新評估要不要改用只送給本人連線的
     * per-player 封包。
     */
    var holderId: Uuid?
        get() = dataTracker[HOLDER_ID].takeIf(String::isNotBlank)?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
        private set(value) = dataTracker.set(HOLDER_ID, value?.toString().orEmpty())

    /** 正式牌局所屬麻將桌；未指派時為 `null`，供伺服器端依 [MahjongRoundInfoEntity.managedTableId] 同款模式查找既有 entity。 */
    var managedTableId: Uuid?
        get() = dataTracker[MANAGED_TABLE_ID].takeIf(String::isNotBlank)?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
        private set(value) = dataTracker.set(MANAGED_TABLE_ID, value?.toString().orEmpty())

    /** 這一組拼湊面板中唯一負責實際畫出文字／背景的 instance；其餘只提供碰撞箱，見類別 KDoc。 */
    var isPrimary: Boolean
        get() = dataTracker[IS_PRIMARY]
        private set(value) = dataTracker.set(IS_PRIMARY, value)

    /**
     * 這個 instance 在整組拼湊面板中沿排列方向的位置索引（`0` 到
     * `MahjongTileTableLayout.TILE_SELECTION_CONFIRM_SEGMENT_COUNT` - 1），供
     * `MahjongTileSelectionConfirmEntityRenderer` 換算成 [MahjongTileTableLayout.tileSelectionConfirmSegmentAlongOffset]
     * 後跟目前實際文字寬度比對，決定 [isActive]。
     */
    var segmentIndex: Int
        get() = dataTracker[SEGMENT_INDEX]
        private set(value) = dataTracker.set(SEGMENT_INDEX, value)

    /**
     * 這個 instance 目前是否落在目前實際文字寬度範圍內、要回應右鍵——僅供本地 client 使用，不追蹤也不
     * 持久化，跟 [MahjongTileEntity] 的描邊提示欄位一樣是「純本地 client、不同步」的欄位模式。
     * `MahjongTileSelectionConfirmEntityRenderer` 每幀依目前實際渲染出的文字寬度重新計算並寫入這個值；
     * 伺服器與尚未收到任何一幀計算結果的 client（例如剛生成的那一刻）落回預設 `true`。
     */
    var isActive: Boolean = true

    init {
        setNoGravity(true)
    }

    /**
     * 這個 entity 物件在記憶體裡第一次被 tick 到的 `world.time`；只在記憶體內，不寫進存檔，理由同
     * [MahjongRoundInfoEntity.firstTickWorldTime]。
     */
    private var firstTickWorldTime: Long = Long.MIN_VALUE

    /**
     * fallback 自動清除——**不是主要清除路徑**。正常生命週期是選牌送出／取消後由
     * `MahjongTableGameActionService` 立即清除；這裡只是意外情境（伺服器崩潰、玩家斷線後選牌狀態卡死）
     * 的保險，門檻抓得遠小於局況顯示那種整局都要存在的 entity，理由同
     * [MahjongRoundInfoEntity.tick] 的同款設計。
     */
    override fun tick() {
        super.tick()
        if (world.isClient) return
        if (firstTickWorldTime == Long.MIN_VALUE) firstTickWorldTime = world.time
        if (world.time - firstTickWorldTime >= FALLBACK_DESPAWN_AFTER_TICKS) {
            discard()
        }
    }

    /** 純互動用途，不提供物理阻擋、不參與推擠。 */
    override fun isCollidable(): Boolean = false

    override fun isPushable(): Boolean = false

    /** 允許玩家視線 raycast 選取，以執行右鍵確認送出；見 [isActive]。 */
    override fun canHit(): Boolean = !isInvisible && !isRemoved && isActive

    /**
     * 指派這個確認面板屬於哪位玩家、哪張桌子、在整組拼湊面板中的位置索引（見 [segmentIndex]），並標記
     * 是否為負責顯示的那個 instance（見 [isPrimary]）。
     */
    fun assignToHolder(tableId: Uuid, playerId: Uuid, segmentIndex: Int, isPrimary: Boolean) {
        check(!world.isClient) { "Confirm panel holder must be assigned by the server" }
        managedTableId = tableId
        holderId = playerId
        this.segmentIndex = segmentIndex
        this.isPrimary = isPrimary
    }

    /** 確認面板沒有專屬瞬間動畫。 */
    override fun applyCustomStep(step: Nothing) = error("Tile selection confirm panel has no custom animation step")

    /** 確認面板不支援位移動畫。 */
    override fun applyPlayMotion(step: AnimationStep.PlayMotion, startGameTime: Long) = error("Tile selection confirm panel does not support motion animation")

    /** 確認面板沒有可序列化的專屬動畫。 */
    override fun serializeCustomStep(step: Nothing, nbt: NbtCompound) = error("Tile selection confirm panel has no custom animation step")

    /** 確認面板沒有可還原的專屬動畫。 */
    override fun deserializeCustomStep(nbt: NbtCompound): Nothing = error("Tile selection confirm panel has no custom animation step")

    override fun initDataTracker() {
        dataTracker.startTracking(HOLDER_ID, "")
        dataTracker.startTracking(MANAGED_TABLE_ID, "")
        dataTracker.startTracking(IS_PRIMARY, true)
        dataTracker.startTracking(SEGMENT_INDEX, 0)
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        holderId = nbt.getString(NBT_KEY_HOLDER_ID)
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
        managedTableId = nbt.getString(NBT_KEY_MANAGED_TABLE_ID)
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
        isPrimary = nbt.getBoolean(NBT_KEY_IS_PRIMARY)
        segmentIndex = nbt.getInt(NBT_KEY_SEGMENT_INDEX)
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        holderId?.let { nbt.putString(NBT_KEY_HOLDER_ID, it.toString()) }
        managedTableId?.let { nbt.putString(NBT_KEY_MANAGED_TABLE_ID, it.toString()) }
        nbt.putBoolean(NBT_KEY_IS_PRIMARY, isPrimary)
        nbt.putInt(NBT_KEY_SEGMENT_INDEX, segmentIndex)
    }

    companion object {
        /**
         * 碰撞箱寬度（同時套用在水平兩軸，見 [EntityType] 註冊處的 `EntityDimensions.fixed`），固定值、
         * 刻意很小——這是拼湊面板單一 instance 的大小，見類別 KDoc「多個 instance 拼湊」的設計；跟
         * `MahjongTileTableLayout.TILE_SELECTION_CONFIRM_SEGMENT_SPACING` 數值必須一致，否則拼起來的
         * instance 之間會有沒有碰撞箱涵蓋的縫隙。
         */
        const val WIDTH: Float = 0.3f

        /** 面板高度，固定值，理由同 [WIDTH]。 */
        const val HEIGHT: Float = 0.5f

        private const val NBT_KEY_HOLDER_ID = "HolderId"
        private const val NBT_KEY_MANAGED_TABLE_ID = "ManagedTableId"
        private const val NBT_KEY_IS_PRIMARY = "IsPrimary"
        private const val NBT_KEY_SEGMENT_INDEX = "SegmentIndex"

        /** fallback 自動清除門檻（見 [tick]）——5 分鐘，遠大於正常選牌所需時間，只用來兜底意外情境。 */
        private const val FALLBACK_DESPAWN_AFTER_TICKS = 20L * 60L * 5L

        /** 同步所屬玩家 UUID；空字串表示尚未指派。 */
        private val HOLDER_ID: TrackedData<String> =
            DataTracker.registerData(MahjongTileSelectionConfirmEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 同步這個 instance 是否為負責顯示的那一個；見 [isPrimary]。 */
        private val IS_PRIMARY: TrackedData<Boolean> =
            DataTracker.registerData(MahjongTileSelectionConfirmEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        /** 同步所屬麻將桌 UUID；空字串表示尚未指派。 */
        private val MANAGED_TABLE_ID: TrackedData<String> =
            DataTracker.registerData(MahjongTileSelectionConfirmEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 同步這個 instance 在整組拼湊面板中的位置索引；見 [segmentIndex]。 */
        private val SEGMENT_INDEX: TrackedData<Int> =
            DataTracker.registerData(MahjongTileSelectionConfirmEntity::class.java, TrackedDataHandlerRegistry.INTEGER)
    }
}
