package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceRollAnimationSpec
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.nbt.NbtCompound
import net.minecraft.util.math.Box
import net.minecraft.world.World
import kotlin.uuid.Uuid

/** 一次正式擲骰的聚合結果舞台；client 依絕對時間與最終點數繪製 3D 結果面板。 */
class DiceRollPresentationEntity(
    type: EntityType<out DiceRollPresentationEntity> = ModEntities.diceRollPresentation,
    world: World,
) : Entity(type, world) {
    /** 所屬麻將桌；未完成設定或資料損壞時為 `null`。 */
    val managedTableId: Uuid?
        get() = dataTracker[TABLE_ID].takeIf(String::isNotBlank)?.let { encoded ->
            runCatching { Uuid.parse(encoded) }.getOrNull()
        }

    /** 本次擲骰的最終點數，依投擲順序排列。 */
    val points: List<Int>
        get() = dataTracker[POINTS].split(",").mapNotNull(String::toIntOrNull).filter { it in VALID_POINT_RANGE }

    /** 最後一顆骰子停穩、結果面板開始揭示的絕對遊戲時間。 */
    val revealGameTime: Long
        get() = dataTracker[REVEAL_GAME_TIME]

    /** 結果面板與桌面骰子共同結束的絕對遊戲時間。 */
    val endGameTime: Long
        get() = dataTracker[END_GAME_TIME]

    init {
        setNoGravity(true)
    }

    /** 生成前設定完整且不可變的擲骰結果時間線。 */
    fun configure(
        tableId: Uuid,
        points: List<Int>,
        revealGameTime: Long,
        endGameTime: Long,
    ) {
        check(!world.isClient) { "Dice roll presentation must be configured by the server" }
        require(points.size in SUPPORTED_DICE_COUNTS) { "Dice roll presentation must contain two or three dice" }
        require(points.all { it in VALID_POINT_RANGE }) { "Dice points must be between one and six" }
        require(endGameTime - revealGameTime == DiceRollAnimationSpec.EXTRA_VIEWING_TICKS.toLong()) {
            "Dice result duration must match the shared presentation duration"
        }
        dataTracker.set(TABLE_ID, tableId.toString())
        dataTracker.set(POINTS, points.joinToString(","))
        dataTracker.set(REVEAL_GAME_TIME, revealGameTime)
        dataTracker.set(END_GAME_TIME, endGameTime)
    }

    /** 取得含 partial tick 的結果階段相對時間。 */
    fun elapsedResultTicks(tickDelta: Float): Double = world.time + tickDelta.toDouble() - revealGameTime

    /** 純視覺舞台不提供碰撞。 */
    override fun isCollidable(): Boolean = false

    /** 純視覺舞台不參與推擠。 */
    override fun isPushable(): Boolean = false

    /** 純視覺舞台不能被準星選取。 */
    override fun canHit(): Boolean = false

    /** 覆蓋完整 billboard 範圍，避免只依小型 entity 錨點進行視錐裁切。 */
    override fun getVisibilityBoundingBox(): Box = Box(
        x - WIDTH / 2.0,
        y - HEIGHT / 2.0,
        z - WIDTH / 2.0,
        x + WIDTH / 2.0,
        y + HEIGHT / 2.0,
        z + WIDTH / 2.0,
    )

    /** 非法或到期的舞台由伺服器自行清除。 */
    override fun tick() {
        super.tick()
        if (world.isClient) return
        val valid = managedTableId != null &&
            points.size in SUPPORTED_DICE_COUNTS &&
            endGameTime - revealGameTime == DiceRollAnimationSpec.EXTRA_VIEWING_TICKS.toLong()
        if (!valid || world.time >= endGameTime) discard()
    }

    /** 初始化 client/server 同步欄位。 */
    override fun initDataTracker() {
        dataTracker.startTracking(TABLE_ID, "")
        dataTracker.startTracking(POINTS, "")
        dataTracker.startTracking(REVEAL_GAME_TIME, 0L)
        dataTracker.startTracking(END_GAME_TIME, 0L)
    }

    /** 從世界存檔還原完整擲骰結果時間線。 */
    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        dataTracker.set(TABLE_ID, nbt.getString(NBT_KEY_TABLE_ID))
        dataTracker.set(POINTS, nbt.getString(NBT_KEY_POINTS))
        dataTracker.set(REVEAL_GAME_TIME, nbt.getLong(NBT_KEY_REVEAL_GAME_TIME))
        dataTracker.set(END_GAME_TIME, nbt.getLong(NBT_KEY_END_GAME_TIME))
    }

    /** 將完整擲骰結果時間線寫入世界存檔。 */
    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putString(NBT_KEY_TABLE_ID, dataTracker[TABLE_ID])
        nbt.putString(NBT_KEY_POINTS, dataTracker[POINTS])
        nbt.putLong(NBT_KEY_REVEAL_GAME_TIME, revealGameTime)
        nbt.putLong(NBT_KEY_END_GAME_TIME, endGameTime)
    }

    companion object {
        /** 足以涵蓋三顆放大骰子結果面板的追蹤寬度。 */
        const val WIDTH: Float = 3.0f

        /** 足以涵蓋結果面板的追蹤高度。 */
        const val HEIGHT: Float = 2.0f

        /** 結果舞台在桌面上方的世界高度。 */
        const val HEIGHT_ABOVE_TABLETOP: Double = 1.15

        /** 面板淡入所需 tick。 */
        const val FADE_IN_TICKS: Double = 5.0

        /** 面板淡出所需 tick。 */
        const val FADE_OUT_TICKS: Double = 7.0

        /** 支援的骰子數量。 */
        private val SUPPORTED_DICE_COUNTS: IntRange = 2..3

        /** 合法骰子點數。 */
        private val VALID_POINT_RANGE: IntRange = 1..6

        /** 世界存檔中的桌子 ID key。 */
        private const val NBT_KEY_TABLE_ID = "TableId"

        /** 世界存檔中的點數序列 key。 */
        private const val NBT_KEY_POINTS = "Points"

        /** 世界存檔中的揭示時間 key。 */
        private const val NBT_KEY_REVEAL_GAME_TIME = "RevealGameTime"

        /** 世界存檔中的結束時間 key。 */
        private const val NBT_KEY_END_GAME_TIME = "EndGameTime"

        /** 同步桌子 ID。 */
        private val TABLE_ID: TrackedData<String> =
            DataTracker.registerData(DiceRollPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 同步逗號分隔的骰子點數。 */
        private val POINTS: TrackedData<String> =
            DataTracker.registerData(DiceRollPresentationEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 同步揭示時間。 */
        private val REVEAL_GAME_TIME: TrackedData<Long> =
            DataTracker.registerData(DiceRollPresentationEntity::class.java, TrackedDataHandlerRegistry.LONG)

        /** 同步結束時間。 */
        private val END_GAME_TIME: TrackedData<Long> =
            DataTracker.registerData(DiceRollPresentationEntity::class.java, TrackedDataHandlerRegistry.LONG)
    }
}
