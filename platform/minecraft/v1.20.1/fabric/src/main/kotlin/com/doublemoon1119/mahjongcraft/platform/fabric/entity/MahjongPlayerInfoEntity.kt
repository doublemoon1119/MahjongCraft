package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicator
import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicatorValue
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.DistinctFailureGate
import com.doublemoon1119.mahjongcraft.platform.fabric.logging.mahjongCraftLogger
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.minecraft.animation.AnimationStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import kotlin.uuid.Uuid

/**
 * 一桌一個、同步完整公開玩家快照的純視覺 entity。
 *
 * @param type Minecraft entity 類型。
 * @param world 實體所屬世界。
 */
class MahjongPlayerInfoEntity(
    type: EntityType<out MahjongPlayerInfoEntity> = ModEntities.mahjongPlayerInfo,
    world: World,
) : AnimatedMahjongEntity<Nothing>(type, world) {
    /** 同一份解不出來的玩家資料只記錄一次警告。 */
    private val unreadablePlayers = DistinctFailureGate<String>()

    /** 同步的玩家快照；資料解不出來時視為沒有玩家，並對同一份資料記錄一次警告（不含資料內容）。 */
    var players: List<MahjongPlayerInfoEntry>
        get() {
            val raw = dataTracker[PLAYERS]
            val entries = try {
                JSON.decodeFromString<List<PlayerEntryDto>>(raw)
            } catch (error: Exception) {
                if (unreadablePlayers.shouldReport(raw)) {
                    logger.warn("Player info entity {} has unreadable player data ({}); showing no players", uuid, error::class.simpleName)
                }
                return emptyList()
            }
            unreadablePlayers.clear()
            return entries.mapNotNull(PlayerEntryDto::toDomain)
        }
        set(value) = dataTracker.set(PLAYERS, JSON.encodeToString(value.map(PlayerEntryDto::fromDomain)))

    /** 莊家玩家 UUID；資料未設定或損壞時為 `null`。 */
    var dealerPlayerId: Uuid?
        get() = dataTracker[DEALER_PLAYER_ID].takeIf(String::isNotBlank)?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        set(value) = dataTracker.set(DEALER_PLAYER_ID, value?.toString().orEmpty())

    /** 觀看此桌時的座位方向；未知值使用北向預設。 */
    var tableFacing: MahjongTableFacing
        get() = MahjongTableFacing.fromNameOrDefault(dataTracker[TABLE_FACING])
        set(value) = dataTracker.set(TABLE_FACING, value.name)

    /** 所屬麻將桌；尚未指派或資料損壞時為 `null`。 */
    var managedTableId: Uuid?
        get() = dataTracker[MANAGED_TABLE_ID].takeIf(String::isNotBlank)?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        private set(value) = dataTracker.set(MANAGED_TABLE_ID, value?.toString().orEmpty())

    /** 所屬桌子的控制方塊位置，用來定期檢查此資訊 entity 是否仍有效。 */
    private var controllerPos: BlockPos? = null

    /** 目前隱藏租約的絕對結束時間；`Long.MIN_VALUE` 表示沒有隱藏租約。 */
    private var hiddenUntilGameTime: Long = Long.MIN_VALUE

    init {
        setNoGravity(true)
    }

    /**
     * 指派所屬桌子與用來檢查桌子是否仍有效的控制方塊位置。
     *
     * @param tableId 所屬麻將桌 UUID。
     * @param controllerPos 該桌控制方塊位置。
     */
    fun assignToTable(tableId: Uuid, controllerPos: BlockPos) {
        check(!world.isClient) { "Managed player info must be assigned by the server" }
        managedTableId = tableId
        this.controllerPos = controllerPos.toImmutable()
    }

    /**
     * 將玩家公開資訊隱藏至指定絕對遊戲時間。
     *
     * @param gameTime 隱藏租約結束的 server game time；不晚於現有期限時不改變佇列。
     */
    fun hideUntil(gameTime: Long) {
        check(!world.isClient) { "Player info visibility lease must be changed by the server" }
        if (gameTime <= hiddenUntilGameTime) return
        hiddenUntilGameTime = gameTime
        replaceAnimationQueue(
            listOf(AnimationStep.SetInvisible(true), AnimationStep.WaitUntil(gameTime), AnimationStep.SetInvisible(false)),
        )
    }

    /** 將玩家公開資訊隱藏至此 entity 被移除。 */
    fun hideUntilRemoved() {
        check(!world.isClient) { "Player info visibility lease must be changed by the server" }
        hiddenUntilGameTime = Long.MAX_VALUE
        replaceAnimationQueue(listOf(AnimationStep.SetInvisible(true)))
    }

    /** 新局已確定開始時解除所有舊 presentation lease，立即恢復玩家公開資訊。 */
    fun showNow() {
        check(!world.isClient) { "Player info visibility lease must be changed by the server" }
        hiddenUntilGameTime = Long.MIN_VALUE
        replaceAnimationQueue(emptyList())
        setInvisible(false)
    }

    override fun tick() {
        super.tick()
        if (world.isClient || age % ORPHAN_CHECK_INTERVAL_TICKS != 0) return
        val expectedId = managedTableId ?: return discard()
        val table = controllerPos?.let { world.getBlockEntity(it) as? MahjongTableBlockEntity }
        if (table?.tableId != expectedId) discard()
    }

    override fun isCollidable(): Boolean = false
    override fun isPushable(): Boolean = false
    override fun canHit(): Boolean = false
    override fun applyCustomStep(step: Nothing) = error("Player info has no custom animation step")
    override fun applyPlayMotion(step: AnimationStep.PlayMotion, startGameTime: Long) = error("Player info does not support motion")
    override fun serializeCustomStep(step: Nothing, nbt: NbtCompound) = error("Player info has no custom animation step")
    override fun deserializeCustomStep(nbt: NbtCompound): Nothing = error("Player info has no custom animation step")

    override fun initDataTracker() {
        dataTracker.startTracking(PLAYERS, "[]")
        dataTracker.startTracking(DEALER_PLAYER_ID, "")
        dataTracker.startTracking(TABLE_FACING, MahjongTableFacing.NORTH.name)
        dataTracker.startTracking(MANAGED_TABLE_ID, "")
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        dataTracker.set(PLAYERS, nbt.getString(NBT_PLAYERS).ifBlank { "[]" })
        dealerPlayerId = nbt.getString(NBT_DEALER).takeIf(String::isNotBlank)?.let { Uuid.parse(it) }
        dataTracker.set(TABLE_FACING, MahjongTableFacing.fromNameOrDefault(nbt.getString(NBT_FACING)).name)
        managedTableId = nbt.getString(NBT_TABLE_ID).takeIf(String::isNotBlank)?.let { Uuid.parse(it) }
        if (nbt.contains(NBT_CONTROLLER_X)) {
            controllerPos = BlockPos(nbt.getInt(NBT_CONTROLLER_X), nbt.getInt(NBT_CONTROLLER_Y), nbt.getInt(NBT_CONTROLLER_Z))
        }
        hiddenUntilGameTime = nbt.getLong(NBT_HIDDEN_UNTIL).takeIf { it > 0L } ?: Long.MIN_VALUE
        readAnimationQueueFromNbt(nbt)
    }

    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putString(NBT_PLAYERS, dataTracker[PLAYERS])
        dealerPlayerId?.let { nbt.putString(NBT_DEALER, it.toString()) }
        nbt.putString(NBT_FACING, dataTracker[TABLE_FACING])
        managedTableId?.let { nbt.putString(NBT_TABLE_ID, it.toString()) }
        controllerPos?.let {
            nbt.putInt(NBT_CONTROLLER_X, it.x)
            nbt.putInt(NBT_CONTROLLER_Y, it.y)
            nbt.putInt(NBT_CONTROLLER_Z, it.z)
        }
        if (hiddenUntilGameTime != Long.MIN_VALUE) nbt.putLong(NBT_HIDDEN_UNTIL, hiddenUntilGameTime)
        writeAnimationQueueToNbt(nbt)
    }

    companion object {
        const val WIDTH = 8f
        const val HEIGHT = 6f
        private const val ORPHAN_CHECK_INTERVAL_TICKS = 100
        private const val NBT_PLAYERS = "Players"
        private const val NBT_DEALER = "DealerPlayerId"
        private const val NBT_FACING = "TableFacing"
        private const val NBT_TABLE_ID = "ManagedTableId"
        private const val NBT_CONTROLLER_X = "ControllerX"
        private const val NBT_CONTROLLER_Y = "ControllerY"
        private const val NBT_CONTROLLER_Z = "ControllerZ"
        private const val NBT_HIDDEN_UNTIL = "HiddenUntilGameTime"
        private val JSON = Json
        private val logger = mahjongCraftLogger(MahjongPlayerInfoEntity::class)
        private val PLAYERS: TrackedData<String> = DataTracker.registerData(MahjongPlayerInfoEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val DEALER_PLAYER_ID: TrackedData<String> = DataTracker.registerData(MahjongPlayerInfoEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val TABLE_FACING: TrackedData<String> = DataTracker.registerData(MahjongPlayerInfoEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val MANAGED_TABLE_ID: TrackedData<String> = DataTracker.registerData(MahjongPlayerInfoEntity::class.java, TrackedDataHandlerRegistry.STRING)
    }
}

/**
 * 供 tracked data 序列化玩家公開快照的內部 DTO。
 *
 * @property playerId 玩家 UUID 的文字形式。
 * @property playerName 玩家顯示名稱；沒有名稱時為 `null`。
 * @property isAi 玩家是否由 AI 操控。
 * @property seatIndex 玩家座位索引。
 * @property seatWind 座位風的名稱。
 * @property score 玩家目前分數。
 * @property indicators 玩家公開指示器。
 */
@Serializable
internal data class PlayerEntryDto(
    val playerId: String,
    val playerName: String?,
    val isAi: Boolean,
    val seatIndex: Int,
    val seatWind: String,
    val score: Int,
    val indicators: List<IndicatorDto>,
) {
    /**
     * 將此序列化條目還原為領域資料；未知座位風位或 UUID 無效時略過該筆，避免錯誤資料被當成其他玩家。
     *
     * @return 有效的玩家公開資料；資料無效時為 `null`。
     */
    fun toDomain(): MahjongPlayerInfoEntry? {
        val wind = Wind.entries.firstOrNull { it.name == seatWind } ?: return null
        val id = runCatching { Uuid.parse(playerId) }.getOrNull() ?: return null
        return runCatching {
            MahjongPlayerInfoEntry(
                id,
                playerName,
                isAi,
                seatIndex,
                wind,
                score,
                indicators.map(IndicatorDto::toDomain),
            )
        }.getOrNull()
    }

    companion object {
        /**
         * 將領域玩家公開資料轉換為可同步的 DTO。
         *
         * @param value 要轉換的玩家公開資料。
         * @return 可序列化的玩家條目。
         */
        fun fromDomain(value: MahjongPlayerInfoEntry) = PlayerEntryDto(
            value.playerId.toString(),
            value.playerName,
            value.isAi,
            value.seatIndex,
            value.seatWind.name,
            value.score,
            value.indicators.map(IndicatorDto::fromDomain),
        )
    }
}

/**
 * 以穩定文字欄位編碼公開玩家指示器。
 *
 * @property id 指示器識別碼。
 * @property kind 指示器種類標記。
 * @property value 數值或選項內容；marker 類型不帶值。
 */
@Serializable
internal data class IndicatorDto(val id: String, val kind: String, val value: String?) {
    /**
     * 還原領域指示器值；未知種類按無值 marker 處理，count／option 缺值則視為格式錯誤。
     *
     * @return 還原的公開玩家指示器。
     */
    fun toDomain() = PublicPlayerIndicator(
        id,
        when (kind) {
            "count" -> PublicPlayerIndicatorValue.Count(checkNotNull(value).toInt())
            "option" -> PublicPlayerIndicatorValue.Option(checkNotNull(value))
            else -> PublicPlayerIndicatorValue.Marker
        },
    )

    companion object {
        /**
         * 將領域指示器編碼為穩定的種類與值欄位。
         *
         * @param indicator 要轉換的公開玩家指示器。
         * @return 可序列化的指示器 DTO。
         */
        fun fromDomain(indicator: PublicPlayerIndicator): IndicatorDto = when (val indicatorValue = indicator.indicatorValue) {
            PublicPlayerIndicatorValue.Marker -> IndicatorDto(indicator.id, "marker", null)
            is PublicPlayerIndicatorValue.Count -> IndicatorDto(indicator.id, "count", indicatorValue.value.toString())
            is PublicPlayerIndicatorValue.Option -> IndicatorDto(indicator.id, "option", indicatorValue.optionId)
        }
    }
}
