package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicator
import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicatorValue
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.fabric.block.entity.MahjongTableBlockEntity
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

/** 一桌一個、同步完整公開玩家快照的純視覺 entity。 */
class MahjongPlayerInfoEntity(
    type: EntityType<out MahjongPlayerInfoEntity> = ModEntities.mahjongPlayerInfo,
    world: World,
) : AnimatedMahjongEntity<Nothing>(type, world) {
    var players: List<MahjongPlayerInfoEntry>
        get() = runCatching { JSON.decodeFromString<List<PlayerEntryDto>>(dataTracker[PLAYERS]) }
            .getOrDefault(emptyList()).map(PlayerEntryDto::toDomain)
        set(value) = dataTracker.set(PLAYERS, JSON.encodeToString(value.map(PlayerEntryDto::fromDomain)))

    var dealerPlayerId: Uuid?
        get() = dataTracker[DEALER_PLAYER_ID].takeIf(String::isNotBlank)?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        set(value) = dataTracker.set(DEALER_PLAYER_ID, value?.toString().orEmpty())

    var tableFacing: MahjongTableFacing
        get() = MahjongTableFacing.entries.getOrElse(dataTracker[TABLE_FACING]) { MahjongTableFacing.NORTH }
        set(value) = dataTracker.set(TABLE_FACING, value.ordinal)

    var managedTableId: Uuid?
        get() = dataTracker[MANAGED_TABLE_ID].takeIf(String::isNotBlank)?.let { runCatching { Uuid.parse(it) }.getOrNull() }
        private set(value) = dataTracker.set(MANAGED_TABLE_ID, value?.toString().orEmpty())

    private var controllerPos: BlockPos? = null
    private var hiddenUntilGameTime: Long = Long.MIN_VALUE

    init {
        setNoGravity(true)
    }

    fun assignToTable(tableId: Uuid, controllerPos: BlockPos) {
        check(!world.isClient) { "Managed player info must be assigned by the server" }
        managedTableId = tableId
        this.controllerPos = controllerPos.toImmutable()
    }

    fun hideUntil(gameTime: Long) {
        check(!world.isClient) { "Player info visibility lease must be changed by the server" }
        if (gameTime <= hiddenUntilGameTime) return
        hiddenUntilGameTime = gameTime
        replaceAnimationQueue(
            listOf(AnimationStep.SetInvisible(true), AnimationStep.WaitUntil(gameTime), AnimationStep.SetInvisible(false)),
        )
    }

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
        dataTracker.startTracking(TABLE_FACING, MahjongTableFacing.NORTH.ordinal)
        dataTracker.startTracking(MANAGED_TABLE_ID, "")
    }

    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        dataTracker.set(PLAYERS, nbt.getString(NBT_PLAYERS).ifBlank { "[]" })
        dealerPlayerId = nbt.getString(NBT_DEALER).takeIf(String::isNotBlank)?.let { Uuid.parse(it) }
        dataTracker.set(TABLE_FACING, nbt.getInt(NBT_FACING))
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
        nbt.putInt(NBT_FACING, dataTracker[TABLE_FACING])
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
        private val JSON = Json { ignoreUnknownKeys = true }
        private val PLAYERS: TrackedData<String> = DataTracker.registerData(MahjongPlayerInfoEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val DEALER_PLAYER_ID: TrackedData<String> = DataTracker.registerData(MahjongPlayerInfoEntity::class.java, TrackedDataHandlerRegistry.STRING)
        private val TABLE_FACING: TrackedData<Int> = DataTracker.registerData(MahjongPlayerInfoEntity::class.java, TrackedDataHandlerRegistry.INTEGER)
        private val MANAGED_TABLE_ID: TrackedData<String> = DataTracker.registerData(MahjongPlayerInfoEntity::class.java, TrackedDataHandlerRegistry.STRING)
    }
}

@Serializable
private data class PlayerEntryDto(
    val playerId: String,
    val playerName: String,
    val isAi: Boolean,
    val seatIndex: Int,
    val seatWind: Int,
    val score: Int,
    val indicators: List<IndicatorDto>,
) {
    fun toDomain() = MahjongPlayerInfoEntry(
        Uuid.parse(playerId),
        playerName,
        isAi,
        seatIndex,
        Wind.entries[seatWind],
        score,
        indicators.map(IndicatorDto::toDomain),
    )

    companion object {
        fun fromDomain(value: MahjongPlayerInfoEntry) = PlayerEntryDto(
            value.playerId.toString(),
            value.playerName,
            value.isAi,
            value.seatIndex,
            value.seatWind.ordinal,
            value.score,
            value.indicators.map(IndicatorDto::fromDomain),
        )
    }
}

@Serializable
private data class IndicatorDto(val id: String, val kind: String, val value: String = "") {
    fun toDomain() = PublicPlayerIndicator(
        id,
        when (kind) {
            "count" -> PublicPlayerIndicatorValue.Count(value.toInt())
            "option" -> PublicPlayerIndicatorValue.Option(value)
            else -> PublicPlayerIndicatorValue.Marker
        },
    )

    companion object {
        fun fromDomain(indicator: PublicPlayerIndicator): IndicatorDto = when (val indicatorValue = indicator.indicatorValue) {
            PublicPlayerIndicatorValue.Marker -> IndicatorDto(indicator.id, "marker")
            is PublicPlayerIndicatorValue.Count -> IndicatorDto(indicator.id, "count", indicatorValue.value.toString())
            is PublicPlayerIndicatorValue.Option -> IndicatorDto(indicator.id, "option", indicatorValue.optionId)
        }
    }
}
