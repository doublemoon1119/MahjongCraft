package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.platform.fabric.item.MahjongTileItem
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModEntities
import com.doublemoon1119.mahjongcraft.platform.fabric.registry.ModItems
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.MahjongTableGameActionService
import com.doublemoon1119.mahjongcraft.platform.fabric.server.tile.FabricMahjongTileWallPresenter
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.DiceAnimationVector
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedbackPublisher
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileDimensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.nextTileAssetKey
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.normalizedTileAssetKey
import kotlinx.coroutines.runBlocking
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityDimensions
import net.minecraft.entity.EntityPose
import net.minecraft.entity.EntityType
import net.minecraft.entity.data.DataTracker
import net.minecraft.entity.data.TrackedData
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.world.World
import org.koin.core.context.GlobalContext
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/**
 * 可自由放置的麻將牌 entity。
 *
 * 正式牌局接線後，權威 [IdentifiedTile] 的 ID 會直接使用此 entity 的 UUID，
 * 不在 entity 內複製麻將規則狀態；牌局管理中的 entity 由 [managedByGame]／[managedTableId] 區分自由放置模式，
 * 比照 [MahjongDiceEntity] 的雙態設計。
 */
class MahjongTileEntity(
    type: EntityType<out MahjongTileEntity> = ModEntities.mahjongTile,
    world: World,
) : Entity(type, world) {
    /**
     * 牌面素材 key；外部輸入會正規化為支援值或 `unknown`。
     *
     * 牌局管理中的 entity（[managedByGame] 為 `true`）恆定為 [UNKNOWN_TILE_ASSET_KEY]，不寫入真正
     * 牌面——tracked data 會廣播給所有 tracking 到這個 entity 的 client，沒辦法只給特定玩家看到真牌
     * 面，真正牌面完全交給 client 端依 `TableStateSnapshot` 的可見性規則另外呈現。
     */
    var tileAssetKey: String
        get() = dataTracker[TILE_ASSET_KEY].normalizedTileAssetKey()
        set(value) {
            val normalized = if (managedByGame) UNKNOWN_TILE_ASSET_KEY else value.normalizedTileAssetKey()
            dataTracker.set(TILE_ASSET_KEY, normalized)
        }

    /** 牌相對於表面的姿態；改變後立即更新 bounding box。 */
    var tilePose: MahjongTilePose
        get() = MahjongTilePose.fromOrdinalOrDefault(dataTracker[TILE_POSE])
        set(value) = dataTracker.set(TILE_POSE, value.ordinal)

    /** 是否阻擋玩家及其他非麻將牌 entity；由目前 server config 同步，不寫入世界存檔。 */
    var physicalCollisionEnabled: Boolean
        get() = dataTracker[PHYSICAL_COLLISION_ENABLED]
        set(value) = dataTracker.set(PHYSICAL_COLLISION_ENABLED, value)

    /** 是否由正式牌局管理；管理中的牌不接受自由放置互動。 */
    var managedByGame: Boolean
        get() = dataTracker[MANAGED_BY_GAME]
        set(value) = dataTracker.set(MANAGED_BY_GAME, value)

    /** 正式牌局所屬麻將桌；自由放置牌為 null。 */
    var managedTableId: Uuid?
        get() = dataTracker[MANAGED_TABLE_ID]
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
        private set(value) = dataTracker.set(MANAGED_TABLE_ID, value?.toString().orEmpty())

    /**
     * 是否正在播放由伺服器啟動的運動動畫（牌牆生成掉落、發牌、摸牌共用同一套機制，見
     * [startMotionAnimation]）。`entity` 本身固定 `refreshPositionAndAngles` 到動畫**終點**，動畫期間
     * 的視覺位移／姿態旋轉完全由 client 端 renderer 依 [animationStartGameTime] 等欄位即時計算插值，
     * 不影響這個欄位代表的實際邏輯位置——比照 [MahjongDiceEntity.rolling] 的既有設計。
     */
    var animating: Boolean
        get() = dataTracker[ANIMATING]
        private set(value) = dataTracker.set(ANIMATING, value)

    /** 動畫開始時的 server game time。 */
    val animationStartGameTime: Long
        get() = dataTracker[ANIMATION_START_GAME_TIME]

    /** 動畫總長度，以 server ticks 表示。 */
    val animationDurationTicks: Int
        get() = dataTracker[ANIMATION_DURATION_TICKS]

    /** 拋物線最高額外高度；`0` 代表純直落，不套用拋物線。 */
    val animationArcHeight: Double
        get() = dataTracker[ANIMATION_ARC_HEIGHT].toDouble()

    /** 動畫起點相對 entity 最終位置的向量。 */
    val animationStartOffset: DiceAnimationVector
        get() = DiceAnimationVector(
            x = dataTracker[ANIMATION_START_OFFSET_X].toDouble(),
            y = dataTracker[ANIMATION_START_OFFSET_Y].toDouble(),
            z = dataTracker[ANIMATION_START_OFFSET_Z].toDouble(),
        )

    /** 動畫起始姿態的局部 X 軸旋轉角。 */
    val animationStartPoseRotationDegrees: Float
        get() = dataTracker[ANIMATION_START_POSE_ROTATION]

    /** 動畫結束姿態的局部 X 軸旋轉角。 */
    val animationEndPoseRotationDegrees: Float
        get() = dataTracker[ANIMATION_END_POSE_ROTATION]

    /** 這個 entity 是否已經做過一次 [validateStillManagedByActiveGame] 檢查；只需要做一次。 */
    private var hasValidatedManagedState = false

    init {
        setNoGravity(true)
    }

    /**
     * 啟動一次由伺服器決定終點的運動動畫；呼叫前 entity 應已經 `refreshPositionAndAngles` 到動畫
     * **終點**座標，[startOffset] 是「起點相對終點」的差值（不是絕對世界座標），理由同
     * [MahjongDiceEntity.startRoll]。
     */
    fun startMotionAnimation(
        startGameTime: Long,
        durationTicks: Int,
        arcHeight: Double,
        startOffset: DiceAnimationVector,
        startPoseRotationDegrees: Float,
        endPoseRotationDegrees: Float,
    ) {
        check(!world.isClient) { "Tile motion animations must be started by the server" }
        dataTracker.set(ANIMATION_START_GAME_TIME, startGameTime)
        dataTracker.set(ANIMATION_DURATION_TICKS, durationTicks)
        dataTracker.set(ANIMATION_ARC_HEIGHT, arcHeight.toFloat())
        dataTracker.set(ANIMATION_START_OFFSET_X, startOffset.x.toFloat())
        dataTracker.set(ANIMATION_START_OFFSET_Y, startOffset.y.toFloat())
        dataTracker.set(ANIMATION_START_OFFSET_Z, startOffset.z.toFloat())
        dataTracker.set(ANIMATION_START_POSE_ROTATION, startPoseRotationDegrees)
        dataTracker.set(ANIMATION_END_POSE_ROTATION, endPoseRotationDegrees)
        animating = true
    }

    /** 將牌標記為指定正式牌局桌子管理；[tileAssetKey] 的 setter 會因此自動鎖定為 [UNKNOWN_TILE_ASSET_KEY]。 */
    fun assignToTable(tableId: Uuid) {
        check(!world.isClient) { "Managed tiles must be assigned by the server" }
        managedByGame = true
        managedTableId = tableId
    }

    /**
     * 只在這個 entity 存在後的第一個 server tick 驗證一次：`managedByGame` 為 `true` 時，
     * [managedTableId] 對應的對局是否還存在——查不到就自我 [discard]。
     *
     * 這是崩潰恢復的安全網，補足「某條清除路徑忘記清 3D entity」這類漏洞（已知一例：
     * `FabricTableLocationValidationService.cleanupMissing`），不是取代既有的明確清除呼叫
     * （`MahjongTileWallPresenter.clear()`／`MahjongHandTilesPresenter.clear()` 等）——那些呼叫仍是
     * 正常流程下的清除方式，這裡只在「不知道為什麼漏清了」的情況下才會真的觸發。
     *
     * 只查「這個 tableId 是否還有對局」，不逐一比對這張牌的 UUID 是否還在牌牆／手牌／牌河／副露的
     * 哪個位置——那需要把整個 `TableState` 攤開搜尋，對一個安全網來說成本太高；只要對局本身還在，
     * 就交給既有的「每局重建時整批清空重新生成」機制（見 [FabricMahjongTileWallPresenter.present] KDoc）
     * 保證舊局的牌會被正確換掉，不需要這裡重複驗證。
     *
     * 只在第一個 tick 檢查一次，不是每個 tick 都查：[AuthoritativeStateStore.getGame] 是 suspend
     * function，要用 [runBlocking] 橋接到 tick 這個同步呼叫點（比照 `FabricTableLifecycleService`
     * 既有處理事件回呼的同一種寫法）；對「一整場遊戲可能有上百張管理中的牌」的規模，每 tick 都做
     * 這件事會是不必要的開銷，只在 entity 剛存在（含世界重新載入後第一次 tick，也就是崩潰重啟後
     * 最需要驗證的時機點）驗證一次就足夠——這個 entity 之後如果真的被清除，一定是走上面說的既有
     * 明確清除路徑，不會是本機制錯過的情況。
     */
    override fun tick() {
        super.tick()
        if (world.isClient) return
        if (animating && world.time - animationStartGameTime >= animationDurationTicks) animating = false
        if (!managedByGame || hasValidatedManagedState) return
        hasValidatedManagedState = true
        validateStillManagedByActiveGame()
    }

    /** 見 [tick] KDoc。 */
    private fun validateStillManagedByActiveGame() {
        val tableId = managedTableId ?: return
        val store = GlobalContext.get().get<AuthoritativeStateStore>()
        val gameStillActive = runBlocking { store.getGame(tableId) } != null
        if (!gameStillActive) discard()
    }

    /** 依 server policy 決定是否提供物理阻擋；raycast 選取能力由 [canHit] 獨立保留。 */
    override fun isCollidable(): Boolean = physicalCollisionEnabled

    /** 麻將牌彼此永遠不產生物理碰撞，其他 entity 則依目前 server policy 與原版規則處理。 */
    override fun collidesWith(other: Entity): Boolean = physicalCollisionEnabled &&
        other !is MahjongTileEntity &&
        super.collidesWith(other)

    /** 麻將牌固定不動，不參與一般 entity 推擠。 */
    override fun isPushable(): Boolean = false

    /** 允許玩家視線 raycast 選取麻將牌，以執行右鍵互動與後續 HUD targeting。 */
    override fun canHit(): Boolean = !isRemoved

    /** 允許麻將牌成為攻擊目標；與控制視線選取的 [canHit] 分開處理。 */
    override fun isAttackable(): Boolean = !isRemoved

    /** 依直立或平放姿態提供實際尺寸。 */
    override fun getDimensions(pose: EntityPose): EntityDimensions = when (tilePose) {
        MahjongTilePose.STANDING -> EntityDimensions.fixed(TILE_WIDTH, TILE_HEIGHT)
        MahjongTilePose.FACE_UP, MahjongTilePose.FACE_DOWN -> EntityDimensions.fixed(TILE_HEIGHT, TILE_DEPTH)
    }

    /**
     * 姿態同步後立即重算 bounding box，並保留切換前的底面高度。
     *
     * [calculateDimensions] 在 entity 由直立縮成平放尺寸時可能調整位置；重新以原本底面定位可避免模型與
     * bounding box 一起浮離所放置的表面。Server 本地更新與 client tracked data 同步都經過此入口。
     */
    override fun onTrackedDataSet(data: TrackedData<*>) {
        super.onTrackedDataSet(data)
        if (data == TILE_POSE) {
            val bottomY = boundingBox.minY
            calculateDimensions()
            setPosition(x, bottomY, z)
        }
    }

    /**
     * 普通右鍵循環牌面；蹲下右鍵循環姿態。牌局管理中的牌改右鍵直接捨牌（見 [interactManaged]），
     * 不區分蹲下——立直宣告改由 GUI／HUD 詢問玩家，不使用蹲下右鍵麻將牌。
     */
    override fun interact(player: PlayerEntity, hand: Hand): ActionResult {
        if (managedByGame) return interactManaged(player)
        if (world.isClient) return ActionResult.SUCCESS

        if (player.isSneaking) {
            tilePose = tilePose.next()
        } else {
            tileAssetKey = tileAssetKey.nextTileAssetKey()
        }
        return ActionResult.CONSUME
    }

    /**
     * 牌局管理中的牌右鍵直接捨牌。
     *
     * 這裡先做一次廉價過濾：這張牌是否確實是互動玩家目前的手牌，過濾不過直接發布拒絕回饋、不送出
     * 指令，避免點別人的手牌、牌牆或牌河都各送一趟權威流程；[MahjongTableGameActionService.discard]
     * 內部仍會經過 `DiscardTileUseCase` 完整驗證（是否輪到這位玩家、振聽等規則限制），這裡的過濾
     * 只是提早擋掉明顯不合理的點擊，不是唯一防線。
     */
    private fun interactManaged(player: PlayerEntity): ActionResult {
        if (world.isClient) return ActionResult.SUCCESS
        val serverPlayer = player as? ServerPlayerEntity ?: return ActionResult.PASS
        val tableId = managedTableId ?: return ActionResult.PASS

        val playerId = player.uuid.toKotlinUuid()
        val tileId = uuid.toKotlinUuid()
        val gameRepository = GlobalContext.get().get<GameRepository>()
        val isOwnHandTile = runBlocking {
            gameRepository.getTableState(tableId)
                ?.players
                ?.firstOrNull { it.id == playerId }
                ?.hand
                ?.standingTiles
                ?.any { it.id == tileId }
        } == true
        if (!isOwnHandTile) {
            GlobalContext.get().get<MinecraftPlayerFeedbackPublisher>()
                .publish(playerId, MinecraftPlayerFeedback.IllegalGameAction)
            return ActionResult.CONSUME
        }

        GlobalContext.get().get<MahjongTableGameActionService>().discard(serverPlayer, tileId)
        return ActionResult.CONSUME
    }

    /** 玩家左鍵攻擊時回收牌張；生存模式掉落保留牌面的物品，創造模式只移除 entity。牌局管理中的牌不能被回收。 */
    override fun handleAttack(attacker: Entity): Boolean {
        if (managedByGame) return false
        val player = attacker as? PlayerEntity ?: return false
        if (!world.isClient) {
            if (!player.abilities.creativeMode) {
                dropStack(asItemStack())
            }
            playSound(SoundEvents.ENTITY_ITEM_FRAME_BREAK, 1.0f, 1.0f)
            discard()
        }
        return true
    }

    /** 建立保留目前牌面 asset key 的麻將牌物品。 */
    private fun asItemStack(): ItemStack = ItemStack(ModItems.MAHJONG_TILE).also {
        MahjongTileItem.writeTileAssetKey(it, tileAssetKey)
    }

    /** 初始化 client/server 同步的牌面、姿態與管理狀態。 */
    override fun initDataTracker() {
        dataTracker.startTracking(TILE_ASSET_KEY, UNKNOWN_TILE_ASSET_KEY)
        dataTracker.startTracking(TILE_POSE, MahjongTilePose.STANDING.ordinal)
        dataTracker.startTracking(PHYSICAL_COLLISION_ENABLED, true)
        dataTracker.startTracking(MANAGED_BY_GAME, false)
        dataTracker.startTracking(MANAGED_TABLE_ID, "")
        dataTracker.startTracking(ANIMATING, false)
        dataTracker.startTracking(ANIMATION_START_GAME_TIME, 0L)
        dataTracker.startTracking(ANIMATION_DURATION_TICKS, 1)
        dataTracker.startTracking(ANIMATION_ARC_HEIGHT, 0.0f)
        dataTracker.startTracking(ANIMATION_START_OFFSET_X, 0.0f)
        dataTracker.startTracking(ANIMATION_START_OFFSET_Y, 0.0f)
        dataTracker.startTracking(ANIMATION_START_OFFSET_Z, 0.0f)
        dataTracker.startTracking(ANIMATION_START_POSE_ROTATION, 0.0f)
        dataTracker.startTracking(ANIMATION_END_POSE_ROTATION, 0.0f)
    }

    /** 從世界存檔還原牌面、姿態與管理狀態，非法值使用安全預設。 */
    override fun readCustomDataFromNbt(nbt: NbtCompound) {
        managedByGame = nbt.getBoolean(NBT_KEY_MANAGED_BY_GAME)
        managedTableId = nbt.getString(NBT_KEY_MANAGED_TABLE_ID)
            .takeIf(String::isNotBlank)
            ?.let { encoded -> runCatching { Uuid.parse(encoded) }.getOrNull() }
        tileAssetKey = nbt.getString(NBT_KEY_TILE)
        tilePose = MahjongTilePose.fromNameOrDefault(nbt.getString(NBT_KEY_POSE))
    }

    /** 將牌面、姿態與管理狀態寫入世界存檔。 */
    override fun writeCustomDataToNbt(nbt: NbtCompound) {
        nbt.putString(NBT_KEY_TILE, tileAssetKey)
        nbt.putString(NBT_KEY_POSE, tilePose.name)
        nbt.putBoolean(NBT_KEY_MANAGED_BY_GAME, managedByGame)
        managedTableId?.let { tableId -> nbt.putString(NBT_KEY_MANAGED_TABLE_ID, tableId.toString()) }
    }

    companion object {
        /** 麻將牌世界寬度；與 [MahjongTileTableLayout] 共用同一組數值來源。 */
        val TILE_WIDTH = MahjongTileDimensions.TILE_WIDTH.toFloat()

        /** 麻將牌世界高度。 */
        val TILE_HEIGHT = MahjongTileDimensions.TILE_HEIGHT.toFloat()

        /** 麻將牌世界深度。 */
        val TILE_DEPTH = MahjongTileDimensions.TILE_DEPTH.toFloat()

        /** 牌面世界存檔 key。 */
        private const val NBT_KEY_TILE = "Tile"

        /** 姿態世界存檔 key。 */
        private const val NBT_KEY_POSE = "Pose"

        /** 正式牌局管理狀態世界存檔 key。 */
        private const val NBT_KEY_MANAGED_BY_GAME = "ManagedByGame"

        /** 正式牌局所屬桌子 UUID 的世界存檔 key。 */
        private const val NBT_KEY_MANAGED_TABLE_ID = "ManagedTableId"

        /** 同步牌面素材 key。 */
        private val TILE_ASSET_KEY: TrackedData<String> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 同步姿態 ordinal；持久化仍使用名稱以避免 enum 重排影響存檔。 */
        private val TILE_POSE: TrackedData<Int> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        /** 同步目前 server policy 決定的物理碰撞開關。 */
        private val PHYSICAL_COLLISION_ENABLED: TrackedData<Boolean> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        /** 同步是否由正式牌局管理。 */
        private val MANAGED_BY_GAME: TrackedData<Boolean> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        /** 同步正式牌局所屬麻將桌 UUID；空字串表示自由放置。 */
        private val MANAGED_TABLE_ID: TrackedData<String> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.STRING)

        /** 同步是否正在播放運動動畫。 */
        private val ANIMATING: TrackedData<Boolean> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.BOOLEAN)

        /** 同步動畫開始的 server game time。 */
        private val ANIMATION_START_GAME_TIME: TrackedData<Long> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.LONG)

        /** 同步動畫總長度（ticks）。 */
        private val ANIMATION_DURATION_TICKS: TrackedData<Int> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.INTEGER)

        /** 同步動畫拋物線最高額外高度。 */
        private val ANIMATION_ARC_HEIGHT: TrackedData<Float> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.FLOAT)

        /** 同步動畫起點相對終點的 X 偏移。 */
        private val ANIMATION_START_OFFSET_X: TrackedData<Float> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.FLOAT)

        /** 同步動畫起點相對終點的 Y 偏移。 */
        private val ANIMATION_START_OFFSET_Y: TrackedData<Float> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.FLOAT)

        /** 同步動畫起點相對終點的 Z 偏移。 */
        private val ANIMATION_START_OFFSET_Z: TrackedData<Float> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.FLOAT)

        /** 同步動畫起始姿態的局部 X 軸旋轉角。 */
        private val ANIMATION_START_POSE_ROTATION: TrackedData<Float> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.FLOAT)

        /** 同步動畫結束姿態的局部 X 軸旋轉角。 */
        private val ANIMATION_END_POSE_ROTATION: TrackedData<Float> =
            DataTracker.registerData(MahjongTileEntity::class.java, TrackedDataHandlerRegistry.FLOAT)
    }
}
