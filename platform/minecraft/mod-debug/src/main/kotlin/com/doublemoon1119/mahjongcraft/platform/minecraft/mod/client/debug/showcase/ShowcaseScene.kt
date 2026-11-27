package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.showcase

import com.doublemoon1119.mahjongcraft.flow.common.game.random.MatchSeed
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.RoomActionDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.block.entity.MahjongTableBlockEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.AutotestContext
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.AutotestTable
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.ensureGameRunning
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.offeredActions
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.submit
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.waitForAction
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest.waitUntilTableIdle
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.room.RoomScreen
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase.ShowcaseColumn
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase.ShowcasePosition
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase.ShowcaseRaise
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase.ShowcaseShot
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase.ShowcaseSite
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase.ShowcaseSiteChooser
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase.ShowcaseSiteSurvey
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.showcase.ShowcaseSunlight
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla.addClientOnlyEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla.hasRenderedAllTerrain
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.MahjongStoolSeatEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.entity.WinCelebrationEffectEntity
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.room.MahjongTableRoomService
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.TableOccupancyDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.seating.MahjongSeatingTableLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongTableSeatLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MahjongTileTableLayout
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.biome.Biomes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraft.world.ticks.LevelChunkTicks
import net.minecraft.world.ticks.TickContainerAccess
import org.koin.core.context.GlobalContext
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * 宣傳截圖的拍攝流程：在櫻花林擺一張牌局打到一半的牌桌，從固定的鏡頭位置截圖。
 *
 * 1. 進入新的一般地形世界，固定在傍晚、晴天。
 * 2. 到固定的牌桌位置：櫻花樹旁一塊天然平坦的空地，不挖地形。位置被樹擋住或桌面在樹影裡時，重新生成世界再試。
 * 3. 四邊座位放上麻將凳，旁邊擺上燈籠與放著蛋糕的木桶；像玩家一樣放下牌桌、開房、加入三個 AI 並開局，
 *    載入打到中盤的 debug 場景。
 *    本機玩家的座位由只在用戶端出現的史蒂夫替身坐著。
 * 4. 從每個鏡頭位置截圖。遠景鏡頭被擋住時繞牌桌換個角度；每張圖都等鏡頭附近的地形畫好、畫面穩定後才拍。
 * 5. 載入同一桌剛摸到和牌張的 debug 場景，以玩家的身分自摸，拍閃電落下時的和牌演出。
 *
 * - 任何一項檢查失敗就停止，之後的截圖不拍。
 *
 * @property context 拍攝使用的環境，提供指令、等待與截圖。
 */
internal class ShowcaseScene(private val context: AutotestContext) {
    /** 擺放牌桌的位置。還沒找到時為 null。 */
    var site: BlockPos? = null
        private set

    /** 已拍的截圖，依拍攝順序。後製產生的檔案由執行器之後補上。 */
    val shots = mutableListOf<ShowcaseShot>()

    /** 依序執行整個拍攝流程。 */
    suspend fun run() {
        val chosen = enterSunlitWorld() ?: return
        val site = BlockPos(chosen.x, chosen.level, chosen.z)
        this.site = site
        if (!prepareGround(site, chosen.raised)) return
        if (!startGame(site)) return
        if (!loadScenario(MIDGAME_SCENARIO)) return
        if (!seatStandIn(site)) return
        val center = Vec3.atBottomCenterOf(site)
        CAMERAS.forEach { camera -> shoot(camera.name, unobstructedEye(camera, site), center.add(0.0, camera.lookHeight, 0.0)) }
        shootWin(site)
    }

    /**
     * 鏡頭的位置，避開擋住牌桌的地形或樹木。
     *
     * - 依序嘗試鏡頭的 [Camera.distances] 與 [Camera.turns]：先在原本的距離繞牌桌轉動，都被擋住才拉近再轉一輪，
     *   取第一個看得到牌桌的位置。
     * - 以方塊被選取時的外框判斷，高草之類的植物也算擋住。
     * - 每個角度都被擋住時記錄失敗，仍從原本的位置拍。
     *
     * @param camera 鏡頭。
     * @param site 牌桌的位置。
     * @return 鏡頭位置。
     */
    private fun unobstructedEye(camera: Camera, site: BlockPos): Vec3 {
        val center = Vec3.atBottomCenterOf(site)
        val seat = seatDirection(center)
        val target = center.add(0.0, camera.lookHeight, 0.0)
        val eyes = camera.distances.flatMap { distance ->
            camera.turns.map { turn -> camera.eye(center, seat.yRot(Math.toRadians(turn).toFloat()), distance) }
        }
        val clear = eyes.firstOrNull { seesTable(it, target, site, camera.clearance) }
        context.check("The ${camera.name} camera sees the table", clear != null)
        return clear ?: eyes.first()
    }

    /**
     * 鏡頭看得到整張牌桌，而且鏡頭旁沒有方塊佔滿畫面。
     *
     * - 鏡頭周圍 [clearance] 格內除了牌桌沒有方塊，例如樹冠。
     * - 鏡頭到目標、以及到牌桌桌面四個角的連線上，除了牌桌本身沒有方塊。
     *
     * @param eye 鏡頭位置。
     * @param target 鏡頭對準的位置。
     * @param site 牌桌的位置。
     * @param clearance 鏡頭周圍要空出的格數。
     * @return 看得到牌桌時為 true。
     */
    private fun seesTable(eye: Vec3, target: Vec3, site: BlockPos, clearance: Int): Boolean {
        val level = checkNotNull(context.client.level)
        val player = checkNotNull(context.client.player)
        val isTable = { pos: BlockPos -> pos.x in site.x - 1..site.x + 1 && pos.z in site.z - 1..site.z + 1 && pos.y in site.y..site.y + 1 }
        val center = BlockPos.containing(eye)
        val around = BlockPos.betweenClosed(center.offset(-clearance, -clearance, -clearance), center.offset(clearance, clearance, clearance))
        if (around.any { !isTable(it) && !level.getBlockState(it).getShape(level, it).isEmpty }) return false
        val top = Vec3.atBottomCenterOf(site).add(0.0, TABLE_TOP_HEIGHT, 0.0)
        val corners = listOf(-1.0 to -1.0, -1.0 to 1.0, 1.0 to -1.0, 1.0 to 1.0).map { (dx, dz) -> top.add(dx * TABLE_HALF_WIDTH, 0.0, dz * TABLE_HALF_WIDTH) }
        return (corners + target).all { point ->
            val hit = level.clip(ClipContext(eye, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player))
            hit.type == HitResult.Type.MISS || isTable(hit.blockPos)
        }
    }

    /**
     * 本機玩家座位相對於牌桌中心的水平方向。鏡頭以它為準擺放，從玩家這一側拍，其他三家的手牌朝外，只露出牌背。
     *
     * @param center 牌桌底部的中心。
     * @return 從牌桌中心指向玩家的單位向量。
     */
    private fun seatDirection(center: Vec3): Vec3 {
        val player = checkNotNull(context.client.player).position()
        return Vec3(player.x - center.x, 0.0, player.z - center.z).normalize()
    }

    /**
     * 建立世界，確認固定的牌桌位置可以用、而且桌面照得到陽光。不符合時離開世界重新生成，最多 [WORLD_ATTEMPTS] 次。
     *
     * - 同一個種子，樹木每次生成的位置可能稍有不同，可能剛好有一棵樹擋住牌桌位置，或把影子投在桌上。
     * - 每一次的結果都記錄下來。只有全部都不符合時才算失敗。
     *
     * @return 牌桌的位置。進不了世界、地形沒有載入，或全部嘗試都不符合時為 null。
     */
    private suspend fun enterSunlitWorld(): ShowcaseSite? {
        var outcome = ""
        for (attempt in 1..WORLD_ATTEMPTS) {
            if (attempt > 1 && !leaveWorld()) return null
            if (!enterWorld()) return null
            val survey = findSite() ?: return null
            val site = survey.site
            val sunlit = site?.let { context.onServer { server -> tableSunlight(server.overworld(), it) } }
            outcome = if (site == null || sunlit == null) {
                "rejected ${survey.rejections}, ${survey.mostBlossoms} blossom columns"
            } else {
                "${site.x}, ${site.level}, ${site.z}: ${site.blossoms} blossom columns, ${site.raised.size} raised, sunlit ${String.format(Locale.ROOT, "%.2f", sunlit)}"
            }
            context.check("World $attempt is checked", passed = true, detail = outcome)
            if (site != null && sunlit != null && sunlit >= MIN_TABLE_SUNLIT) {
                context.check("The showcase spot is open and the table is in sunlight", passed = true, detail = outcome)
                return site
            }
        }
        context.check("The showcase spot is open and the table is in sunlight", passed = false, detail = "after $WORLD_ATTEMPTS worlds: $outcome")
        return null
    }

    /**
     * 離開目前的世界回到主選單，等伺服器完全關閉。
     *
     * @return 已經離開世界時為 true。
     */
    private suspend fun leaveWorld(): Boolean {
        val client = context.client
        ShowcaseWorld.leave(client)
        return context.check("Left the showcase world", context.waitUntil(ENTER_TIMEOUT) { client.level == null && client.singleplayerServer == null })
    }

    /**
     * 牌桌放在 [site] 時，桌面照得到陽光的比例。依世界目前的時間決定太陽的方向。
     *
     * @param level 世界。
     * @param site 牌桌的位置。
     * @return 照得到陽光的比例，0 到 1。
     */
    private fun tableSunlight(level: ServerLevel, site: ShowcaseSite): Double {
        val pos = BlockPos.MutableBlockPos()
        return ShowcaseSunlight.litFraction(site.x, site.level, site.z, ShowcaseSunlight.sunDirection(level.getTimeOfDay(1f))) { x, y, z ->
            pos.set(x, y, z)
            !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty
        }
    }

    /**
     * 建立並進入世界，固定時間與天氣。
     *
     * @return 進入世界而且用戶端已經顯示固定的時間與天氣時為 true。
     */
    private suspend fun enterWorld(): Boolean {
        val client = context.client
        ShowcaseWorld.createAndEnter(client)
        val entered = context.waitUntil(ENTER_TIMEOUT) { client.level != null && client.player != null && client.screen == null && client.overlay == null }
        if (!context.check("Entered the showcase world", entered)) return false
        if (!SETUP_COMMANDS.all { context.runCheckedCommand(it) }) return false
        val level = checkNotNull(client.level)
        return context.check("The client shows the fixed time and clear weather", context.waitUntil(SYNC_TIMEOUT) { level.dayTime == FIXED_TIME && !level.isRaining })
    }

    /**
     * 飛到離出生點最近的櫻花林上空等周圍的區塊載入，確認固定的牌桌位置仍適合擺牌桌。
     *
     * - 世界種子與牌桌位置都固定，每次拍出相同的構圖與光線。
     * - 樹木的生成會受區塊生成的先後影響，所以每次都從同一個地點、載入同樣的範圍，讓區塊以相同的順序生成。
     * - 以 [ShowcaseSiteChooser] 確認這個位置符合條件，由呼叫端決定是否重新生成世界，不改用其他位置。
     * - 區塊由玩家到達後照常載入，不在伺服器上另外強制生成，關閉世界時才不會留下未完成的區塊工作。
     * - 玩家維持飛行，傳送後不會因為下方的地形還沒載入而落下或被推開。
     * - 等範圍內地表的水不再流動、方塊不再更新，連續 [STABLE_TICKS] 個遊戲刻都沒有排定的更新才檢查。
     *   地底洞穴裡的水和岩漿可能流動很久，不影響畫面，不必等。
     *
     * @return 對固定位置的檢查結果。區塊沒有載入或地形一直在變化時為 null。
     */
    private suspend fun findSite(): ShowcaseSiteSurvey? {
        val found = context.onServer { server ->
            val level = server.overworld()
            level.findClosestBiome3d({ it.`is`(Biomes.CHERRY_GROVE) }, level.sharedSpawnPos, SEARCH_RADIUS, SEARCH_HORIZONTAL_STEP, SEARCH_VERTICAL_STEP)?.first
        }
        if (!context.check("A cherry grove is found near the spawn", found != null, found?.toShortString()) || found == null) return null
        if (!startFlying(found)) return null
        val minChunkX = (found.x - LOAD_RADIUS) shr CHUNK_SHIFT
        val maxChunkX = (found.x + LOAD_RADIUS) shr CHUNK_SHIFT
        val minChunkZ = (found.z - LOAD_RADIUS) shr CHUNK_SHIFT
        val maxChunkZ = (found.z + LOAD_RADIUS) shr CHUNK_SHIFT
        val loaded = context.waitUntil(TERRAIN_TIMEOUT) {
            context.onServer { server ->
                val level = server.overworld()
                (minChunkX..maxChunkX).all { x -> (minChunkZ..maxChunkZ).all { z -> level.hasChunk(x, z) } }
            }
        }
        if (!context.check("The chunks around the cherry grove are loaded", loaded)) return null
        // 新生成的地形裡，泉水還會往下流、樹葉還會更新，等地表停下來再檢查位置。
        var settledTicks = 0
        val settled = context.waitUntil(TERRAIN_TIMEOUT) {
            val quiet = context.onServer { server ->
                val level = server.overworld()
                (minChunkX..maxChunkX).all { x ->
                    (minChunkZ..maxChunkZ).all { z ->
                        val chunk = level.chunkSource.getChunkNow(x, z)
                        chunk != null && !hasSurfaceUpdates(level, chunk.fluidTicks) && !hasSurfaceUpdates(level, chunk.blockTicks)
                    }
                }
            }
            settledTicks = if (quiet) settledTicks + 1 else 0
            settledTicks >= STABLE_TICKS
        }
        if (!context.check("Water and blocks around the cherry grove stop changing", settled)) return null
        val survey = context.onServer { server ->
            val level = server.overworld()
            ShowcaseSiteChooser.survey(originX = SITE_X, originZ = SITE_Z, searchRadius = 0, step = 1) { x, z -> surveyColumn(level, x, z) }
        }
        return survey
    }

    /**
     * 一個區塊在地表附近還有排定的更新，例如流動中的水或要更新的樹葉。
     *
     * @param level 世界。
     * @param ticks 區塊排定的方塊或液體更新。
     * @return 有任何更新位於地面往下 [SURFACE_DEPTH] 格以上時為 true。
     */
    private fun hasSurfaceUpdates(level: ServerLevel, ticks: TickContainerAccess<*>): Boolean {
        val scheduled = (ticks as? LevelChunkTicks<*>)?.getAll() ?: return ticks.count() > 0
        return scheduled.anyMatch { tick ->
            val pos = tick.pos()
            pos.y >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.x, pos.z) - SURFACE_DEPTH
        }
    }

    /**
     * 讀取一柱地形。只讀已經載入的區塊。
     *
     * @param level 世界。
     * @param x x 座標。
     * @param z z 座標。
     * @return 這一柱的地形。
     */
    private fun surveyColumn(level: ServerLevel, x: Int, z: Int): ShowcaseColumn {
        val ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
        val top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
        val pos = BlockPos.MutableBlockPos()
        val obstruction = (ground until ground + ShowcaseSiteChooser.SCAN_HEIGHT).firstOrNull { y ->
            pos.set(x, y, z)
            !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty
        }
        return ShowcaseColumn(
            ground = ground,
            obstruction = obstruction,
            water = !level.getFluidState(pos.set(x, ground - 1, z)).isEmpty,
            blossom = level.getBlockState(pos.set(x, top - 1, z)).`is`(Blocks.CHERRY_LEAVES),
        )
    }

    /**
     * 傳送到指定位置的上空，像創造模式玩家按兩下跳躍一樣開始飛行。
     *
     * - 站在地面上時用戶端會自動取消飛行，因此先到空中才開始。
     * - 用戶端把飛行狀態送給伺服器，與玩家自己開始飛行相同。
     *
     * @param target 要到達上空的位置，只用水平座標。
     * @return 到達空中而且伺服器也記錄玩家在飛行時為 true。
     */
    private suspend fun startFlying(target: BlockPos): Boolean {
        val player = checkNotNull(context.client.player)
        if (!context.runCheckedCommand("tp @s ${target.x + 0.5} $SURVEY_HEIGHT ${target.z + PLAYER_DISTANCE}")) return false
        if (!context.check("Arrived above the cherry grove", context.waitUntil(SYNC_TIMEOUT) { player.y > SURVEY_HEIGHT - SURVEY_TOLERANCE })) return false
        player.abilities.flying = true
        player.onUpdateAbilities()
        // 傳送後下落的速度在開始飛行後仍會延續一段距離，停下來讓之後的傳送停在指定的位置。
        player.deltaMovement = Vec3.ZERO
        val playerId = player.uuid
        val flying = context.waitUntil(SYNC_TIMEOUT) { context.onServer { server -> server.playerList.getPlayer(playerId)?.abilities?.flying == true } }
        return context.check("The player is flying", flying)
    }

    /**
     * 準備牌桌的地面，再降落到牌桌旁。不挖地形，周圍的地面與樹木保持原樣。
     *
     * - 比牌桌低一格的地面以草方塊補到同高。
     * - 清掉牌桌佔用的格子裡的花草，讓牌桌放得下。挑位置時已經確認那裡只有花草。
     * - 四邊座位各放一張木製麻將凳，開局時玩家與 AI 會坐上去。
     * - 玩家右手邊那張凳子的外側放一個上面擺著蛋糕的木桶，像打牌時放點心的小桌，不貼著牌桌。
     * - 牌桌遠端（離玩家較遠的那一側）的兩個角落外各立一根放著燈籠的柵欄。
     * - 玩家固定坐在 [PLAYER_SIDE]，道具依這個方向擺放。
     * - 道具放在當地的地面上，地面是水時不放。
     * - 道具都是方塊：截圖前會清掉模組以外的 entity。
     *
     * @param site 牌桌的位置。
     * @param raised 要墊高的柱。
     * @return 全部指令都成功、而且玩家降落在牌桌旁時為 true。
     */
    private suspend fun prepareGround(site: BlockPos, raised: List<ShowcaseRaise>): Boolean {
        val (x, y, z) = Triple(site.x, site.y, site.z)
        val footprint = BlockPos(x - 1, y, z - 1) to BlockPos(x + 1, y + 1, z + 1)
        // 指令沒有改到任何方塊時會回報失敗，因此只在牌桌的位置真的有花草時才清。
        val hasPlants = context.onServer { server -> BlockPos.betweenClosed(footprint.first, footprint.second).any { !server.overworld().getBlockState(it).isAir } }
        val commands = raised.map { "fill ${it.x} ${it.ground} ${it.z} ${it.x} ${y - 1} ${it.z} grass_block" } +
            listOfNotNull(if (hasPlants) "fill ${x - 1} $y ${z - 1} ${x + 1} ${y + 1} ${z + 1} air" else null)
        val stools = Direction.Plane.HORIZONTAL.map { side ->
            val stool = site.relative(side, SEAT_DISTANCE)
            "setblock ${stool.x} ${stool.y} ${stool.z} $STOOL_BLOCK[facing=${side.serializedName}]"
        }
        val props = context.onServer { server ->
            val level = server.overworld()
            val placements = LANTERN_OFFSETS.map { it to listOf("oak_fence", "lantern") } + (SNACK_TABLE_OFFSET to listOf("barrel[facing=up]", "cake"))
            placements.mapNotNull { (offset, blocks) ->
                val (dx, dz) = worldOffset(offset)
                val ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz)
                // 地面是水時不放，避免道具立在水面上。
                if (!level.getFluidState(BlockPos(x + dx, ground - 1, z + dz)).isEmpty) return@mapNotNull null
                blocks.mapIndexed { index, block -> "setblock ${x + dx} ${ground + index} ${z + dz} $block" }
            }.flatten()
        }
        val prepared = (commands + stools + props).all { context.runCheckedCommand(it) }
        context.client.player?.deltaMovement = Vec3.ZERO
        // 站在南邊麻將凳的右側，不與凳子重疊。
        return prepared && context.teleport(x = x + 1.5, y = y.toDouble(), z = z + PLAYER_DISTANCE, yaw = FACING_NORTH, pitch = 30f)
    }

    /**
     * 像玩家一樣放下牌桌、開房、加入三個 AI 並開局，等發牌演出結束。
     *
     * @param site 牌桌的位置。
     * @return 開局而且本機玩家坐在牌桌時為 true。
     */
    private suspend fun startGame(site: BlockPos): Boolean {
        val client = context.client
        val player = checkNotNull(client.player)
        player.inventory.selected = 0
        if (!context.clearInventory()) return false
        if (!context.runCheckedCommand("give @s $TABLE_ITEM")) return false
        val holding = context.waitUntil(SYNC_TIMEOUT) { BuiltInRegistries.ITEM.getKey(player.mainHandItem.item).toString() == TABLE_ITEM }
        if (!context.check("Holding a wooden table", holding)) return false
        val below = site.below()
        client.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, BlockHitResult(Vec3.atCenterOf(below).add(0.0, 0.5, 0.0), Direction.UP, below, false))
        val placed = context.waitUntil(SYNC_TIMEOUT) { BuiltInRegistries.BLOCK.getKey(client.level!!.getBlockState(site).block).toString() == TABLE_ITEM }
        if (!context.check("Placed a wooden table", placed)) return false
        if (!context.clearInventory()) return false
        client.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, BlockHitResult(Vec3.atCenterOf(site), Direction.UP, site, false))
        val opened = context.waitUntil(SYNC_TIMEOUT) { client.screen is RoomScreen }
        if (!opened) {
            val serverBlock = context.onServer { server ->
                val level = server.overworld()
                val parts = BlockPos.betweenClosed(site.offset(-1, 0, -1), site.offset(1, 1, 1))
                    .count { BuiltInRegistries.BLOCK.getKey(level.getBlockState(it).block).toString() == TABLE_ITEM }
                "${BuiltInRegistries.BLOCK.getKey(level.getBlockState(site).block)} ($parts table parts, block entity ${level.getBlockEntity(site) != null})"
            }
            val serverPlayer = context.onServer { server -> server.playerList.getPlayer(player.uuid)?.position()?.toString() }
            val detail = "server block $serverBlock, server player $serverPlayer, screen ${client.screen?.javaClass?.simpleName}"
            context.check("Right-clicking the table opens the room screen", passed = false, detail = detail)
            return false
        }
        val tableId = (client.screen as? RoomScreen)?.tableId ?: error("The room screen closed before its table ID was read")
        AutotestTable.tableId = tableId

        val koin = GlobalContext.get()
        val json = koin.get<NetworkJson>()
        val states = koin.get<ClientMahjongStateStore>()
        val venueId = tableId.toString()
        MahjongChannels.roomAction.sendToServer(json, RoomActionDto.Create(venueId))
        if (!context.check("Created a room", context.waitUntil(SYNC_TIMEOUT) { states.roomSnapshot(tableId)?.isInRoom == true })) return false
        for (index in 1..AI_COUNT) {
            MahjongChannels.roomAction.sendToServer(json, RoomActionDto.AddAi(venueId))
            if (!context.check("Added AI player $index", context.waitUntil(SYNC_TIMEOUT) { states.roomSnapshot(tableId)?.playerIds?.size == index + 1 })) return false
        }
        if (!startWithFixedSeat(site, tableId, checkNotNull(states.roomSnapshot(tableId)).playerIds)) return false
        val started = context.waitUntil(SYNC_TIMEOUT) { states.tableOccupancy(tableId)?.occupancy == TableOccupancyDto.GAME }
        if (!context.check("Started a game", started)) return false
        client.setScreen(null)
        if (!context.check("The deal finishes", context.ensureGameRunning())) return false
        val level = checkNotNull(client.level)
        val stools = Direction.Plane.HORIZONTAL.map { site.relative(it, SEAT_DISTANCE) }
        val occupied = { stools.count { stool -> MahjongStoolSeatEntity.seatsAt(level, stool).any { it.isVehicle } } }
        val seated = context.waitUntil(SYNC_TIMEOUT) { occupied() == stools.size }
        return context.check("Everyone sits on a stool", seated, "${occupied()} of ${stools.size} stools occupied")
    }

    /**
     * 以固定的座位順序與種子開局，讓本機玩家坐在牌桌的 [PLAYER_SIDE]。
     *
     * - 一般開局會隨機決定座位，玩家坐哪一邊、鏡頭朝哪個方向就每次不同，夕陽照過來的方向也跟著改變。
     * - 這裡改由伺服器以指定的座位順序開局，與伺服器端自動測試的做法相同。座位順序中的位置依牌桌朝向對應到固定的一邊，
     *   所以先算出哪個位置在那一邊，再把本機玩家排在那裡。
     *
     * @param site 牌桌的位置，也就是牌桌的控制方塊。
     * @param tableId 牌桌 ID。
     * @param members 房間成員，依加入順序。
     * @return 伺服器收到開局要求時為 true。之後是否真的開局由呼叫端等待。
     */
    private suspend fun startWithFixedSeat(site: BlockPos, tableId: Uuid, members: List<Uuid>): Boolean {
        val me = checkNotNull(context.client.player).uuid.toKotlinUuid()
        val center = Vec3.atBottomCenterOf(site)
        val seatIndex = context.onServer { server ->
            val table = server.overworld().getBlockEntity(site) as? MahjongTableBlockEntity
            val seats = table?.let {
                MahjongSeatingTableLayout.seatPlacements(
                    layout = MahjongTableSeatLayout.forPlayerCount(members.size),
                    controllerX = site.x,
                    controllerY = site.y,
                    controllerZ = site.z,
                    tableFacing = it.mahjongTableFacing,
                )
            }
            seats?.indexOfFirst { seat ->
                (seat.x - center.x) * PLAYER_SIDE.stepX + (seat.z - center.z) * PLAYER_SIDE.stepZ > SEAT_SIDE_MIN_DISTANCE
            } ?: -1
        }
        if (!context.check("A seat on the ${PLAYER_SIDE.serializedName} side of the table exists", seatIndex >= 0, "seat index $seatIndex")) return false
        val others = members - me
        val seatOrder = others.take(seatIndex) + me + others.drop(seatIndex)
        val requested = context.onServer { server ->
            val serverPlayer = server.playerList.getPlayer(me.toJavaUuid())
            serverPlayer?.let { GlobalContext.get().get<MahjongTableRoomService>().start(player = it, seatOrder = seatOrder, matchSeed = SHOWCASE_MATCH_SEED) } != null
        }
        return context.check("The server receives the fixed-seat start", requested)
    }

    /**
     * 把以玩家為準的道具位置換成世界座標的位移。
     *
     * @param offset 道具的位置（往玩家右手邊幾格, 往牌桌遠端幾格），玩家坐在 [PLAYER_SIDE] 面向牌桌。
     * @return 相對於牌桌中心的世界座標位移（x, z）。
     */
    private fun worldOffset(offset: Pair<Int, Int>): Pair<Int, Int> {
        val (right, far) = offset
        val away = PLAYER_SIDE.opposite
        val rightHand = away.clockWise
        return (rightHand.stepX * right + away.stepX * far) to (rightHand.stepZ * right + away.stepZ * far)
    }

    /**
     * 在本機玩家的座位放一位史蒂夫外觀的替身，坐在麻將凳上面向牌桌。
     *
     * - 鏡頭不在本機玩家身上時，原版不畫本機玩家，座位看起來是空的，所以由替身代替。
     * - 替身與它坐的座位只存在於用戶端，不加入牌局，伺服器也不知道它們。
     * - 截圖時隱藏介面，替身頭上不顯示名字。
     * - 外觀與動作見 [ShowcaseStandIn]。
     *
     * @param site 牌桌的位置。
     * @return 替身坐在本機玩家的座位上時為 true。
     */
    private fun seatStandIn(site: BlockPos): Boolean {
        val level = checkNotNull(context.client.level)
        val seat = checkNotNull(context.client.player).vehicle as? MahjongStoolSeatEntity
        if (!context.check("The player sits on a stool", seat != null) || seat == null) return false
        val center = Vec3.atBottomCenterOf(site)
        val yaw = Math.toDegrees(atan2(-(center.x - seat.x), center.z - seat.z)).toFloat()
        val standInSeat = MahjongStoolSeatEntity(world = level).apply { moveTo(seat.x, seat.y, seat.z, yaw, 0f) }
        addClientOnlyEntity(level, standInSeat)
        val standIn = ShowcaseStandIn(level).apply {
            moveTo(seat.x, seat.y, seat.z, yaw, STAND_IN_PITCH)
            yHeadRot = yaw
            yHeadRotO = yaw
            yBodyRot = yaw
            yBodyRotO = yaw
        }
        addClientOnlyEntity(level, standIn)
        return context.check("The stand-in sits at the player's seat", standIn.startRiding(standInSeat, true))
    }

    /**
     * 等對局可以接受操作後載入一個日麻的 debug 場景，等它的演出播完。
     *
     * @param name 場景名稱，不含命名空間與規則前綴。
     * @return 載入成功而且演出播完時為 true。
     */
    private suspend fun loadScenario(name: String): Boolean {
        if (!context.check("A game is ready before scenario $name", context.ensureGameRunning())) return false
        val feedback = context.runCommand("mahjongcraft debug scenario load mahjongcraft:riichi/debug/$name")
        // 指令只排程載入，結果之後才以回饋送回。
        val answered = context.waitUntil(LOAD_TIMEOUT) { feedback.answered }
        val detail = feedback.failures.joinToString().ifEmpty { if (answered) null else "No result within $LOAD_TIMEOUT" }
        if (!context.check("Scenario $name loads", answered && feedback.succeeded, detail)) return false
        return context.check("Scenario $name presentation finishes", context.waitUntilTableIdle())
    }

    /**
     * 從指定位置對準目標截圖，並記錄鏡頭。
     *
     * @param name 截圖名稱。
     * @param eye 鏡頭位置。
     * @param target 鏡頭對準的位置。
     * @param beforeCapture 畫面準備好之後、截圖之前執行，例如送出和牌的操作並等演出播到想拍的畫面。
     */
    private suspend fun shoot(name: String, eye: Vec3, target: Vec3, beforeCapture: suspend () -> Unit = {}) {
        val delta = target.subtract(eye)
        val yaw = Math.toDegrees(atan2(-delta.x, delta.z)).toFloat()
        val pitch = -Math.toDegrees(atan2(delta.y, sqrt(delta.x * delta.x + delta.z * delta.z))).toFloat()
        context.screenshotFrom(name, eye, yaw, pitch) {
            waitUntilSceneReady(eye, name)
            beforeCapture()
        }
        shots += ShowcaseShot(
            name = name,
            file = context.screenshots.last(),
            eye = ShowcasePosition(eye.x, eye.y, eye.z),
            yaw = yaw,
            pitch = pitch,
            outputs = emptyList(),
        )
    }

    /**
     * 等鏡頭附近的地形載入並畫好，再等光影的畫面穩定。
     *
     * - 鏡頭周圍 [TERRAIN_RADIUS] 個區塊都已經在用戶端載入。
     * - 鏡頭看得到的地形連續 [STABLE_TICKS] 個遊戲刻都沒有等待建立的網格。
     * - 最後等 [SETTLE_TICKS] 個遊戲刻，讓光影的時間累積效果（例如自動曝光與抗鋸齒）收斂，只用來決定截圖時機。
     *
     * @param eye 鏡頭位置。
     * @param name 截圖名稱，用於檢查的說明。
     */
    private suspend fun waitUntilSceneReady(eye: Vec3, name: String) {
        val client = context.client
        val chunkX = floor(eye.x / CHUNK_SIZE).toInt()
        val chunkZ = floor(eye.z / CHUNK_SIZE).toInt()
        val loaded = context.waitUntil(TERRAIN_TIMEOUT) {
            val level = client.level ?: return@waitUntil false
            (-TERRAIN_RADIUS..TERRAIN_RADIUS).all { dx -> (-TERRAIN_RADIUS..TERRAIN_RADIUS).all { dz -> level.chunkSource.hasChunk(chunkX + dx, chunkZ + dz) } }
        }
        context.check("Chunks around the $name camera are loaded", loaded)
        var stableTicks = 0
        val built = context.waitUntil(TERRAIN_TIMEOUT) {
            stableTicks = if (hasRenderedAllTerrain(client)) stableTicks + 1 else 0
            stableTicks >= STABLE_TICKS
        }
        context.check("Terrain in view of the $name camera is built", built)
        // 有等玩家操作時，操作畫面會自己打開；它是畫面而不是介面，隱藏介面時不會一起隱藏。
        client.setScreen(null)
        context.waitTicks(SETTLE_TICKS)
    }

    /**
     * 載入同一桌剛摸到和牌張的場景，從主要鏡頭拍自摸的和牌演出。
     *
     * - 鏡頭就位後才自摸。
     * - 等和牌特效出現後，在閃電落下、電流往外擴散時截圖。
     *
     * @param site 牌桌的位置。
     */
    private suspend fun shootWin(site: BlockPos) {
        val center = Vec3.atBottomCenterOf(site)
        if (!loadScenario(WIN_SCENARIO)) return
        val tsumo = context.waitForAction(BuiltInGameActionIds.TSUMO)
        if (!context.check("Tsumo is offered on the drawn tile", tsumo != null, context.offeredActions()) || tsumo == null) return
        val hero = CAMERAS.first()
        shoot(WIN_SHOT, unobstructedEye(hero, site), center.add(0.0, hero.lookHeight, 0.0)) {
            if (!context.check("Tsumo is accepted", context.submit(tsumo))) return@shoot
            val level = checkNotNull(context.client.level)
            val struck = context.waitUntil(WIN_EFFECT_TIMEOUT) {
                val effect = level.entitiesForRendering().filterIsInstance<WinCelebrationEffectEntity>().maxByOrNull { it.startGameTime }
                effect != null && level.gameTime - effect.startGameTime >= WIN_CAPTURE_TICK
            }
            context.check("The win effect reaches the lightning", struck)
        }
    }

    /**
     * 一個鏡頭位置，以本機玩家座位的方向為準。
     *
     * @property name 截圖名稱。
     * @property back 鏡頭從牌桌中心往玩家方向多遠。
     * @property side 鏡頭往玩家右手邊多遠，負值往左。
     * @property up 鏡頭比牌桌底部高多少。
     * @property lookHeight 鏡頭對準牌桌中心上方多高。
     * @property turns 被擋住時繞牌桌轉動的角度，依嘗試順序，單位為度。
     *   座位附近的鏡頭不轉：挑位置時已經確認那裡沒有遮擋，轉動反而可能拍到其他家的牌面。
     * @property clearance 鏡頭周圍要空出的格數。座位附近的鏡頭貼近地面與牌桌，不要求。
     * @property distances 被擋住時依序嘗試的距離比例，1 是原本的位置，較小的值把鏡頭往牌桌拉近。
     */
    private class Camera(
        val name: String,
        val back: Double,
        val side: Double,
        val up: Double,
        val lookHeight: Double,
        val turns: List<Double> = listOf(0.0),
        val clearance: Int = 0,
        val distances: List<Double> = listOf(1.0),
    ) {
        /**
         * 鏡頭在世界中的位置。
         *
         * @param center 牌桌底部的中心。
         * @param seat 從牌桌中心指向玩家的單位向量。
         * @param distance 水平距離的比例，高度不變。
         * @return 鏡頭位置。
         */
        fun eye(center: Vec3, seat: Vec3, distance: Double = 1.0): Vec3 {
            // 面向牌桌時的右手邊：玩家方向順時針轉 90 度。
            val right = Vec3(seat.z, 0.0, -seat.x)
            return center.add(seat.scale(back * distance)).add(right.scale(side * distance)).add(0.0, up, 0.0)
        }
    }

    private companion object {
        /** 進入世界的最長等待時間。一般地形的生成比超平坦久。 */
        val ENTER_TIMEOUT = 5.minutes

        /** 等用戶端收到伺服器變化的最長時間。 */
        val SYNC_TIMEOUT = 10.seconds

        /** 等場景載入結果的最長時間。 */
        val LOAD_TIMEOUT = 3.minutes

        /** 等地形載入與畫好的最長時間。 */
        val TERRAIN_TIMEOUT = 3.minutes

        /** 固定的時間，接近日落的傍晚。 */
        const val FIXED_TIME = 12000L

        /** 進入世界後設定拍攝環境的指令，與自動測試相同，只有時間不同。 */
        val SETUP_COMMANDS = listOf(
            "gamerule sendCommandFeedback false",
            "gamerule announceAdvancements false",
            "gamerule doDaylightCycle false",
            "gamerule doWeatherCycle false",
            "gamerule doMobSpawning false",
            "gamerule randomTickSpeed 0",
            "gamerule doFireTick false",
            "time set $FIXED_TIME",
            "weather clear",
        )

        /** 等櫻花林的區塊載入時，玩家在上空的高度。 */
        const val SURVEY_HEIGHT = 200.0

        /** 判斷已經到達上空時容許的高度誤差。傳送後還沒開始飛行前會稍微下落。 */
        const val SURVEY_TOLERANCE = 5.0

        /** 方塊座標換算成區塊座標要右移的位數。 */
        const val CHUNK_SHIFT = 4

        /** 等地形穩定時只看地面往下幾格內的更新，更深的地底不影響畫面。 */
        const val SURFACE_DEPTH = 2

        /** 牌桌位置不符合或桌面被樹影遮住時，最多生成幾次世界。 */
        const val WORLD_ATTEMPTS = 5

        /** 桌面至少要有多少比例照得到陽光。 */
        const val MIN_TABLE_SUNLIT = 0.9

        /** 找櫻花林的最大水平距離，單位為方塊。 */
        const val SEARCH_RADIUS = 6400

        /** 找櫻花林時水平取樣的間隔，單位為方塊。 */
        const val SEARCH_HORIZONTAL_STEP = 32

        /** 找櫻花林時垂直取樣的間隔，單位為方塊。 */
        const val SEARCH_VERTICAL_STEP = 64

        /** 在櫻花林上空等待載入的範圍：找到的位置周圍多少格，涵蓋牌桌位置與它周圍的樹。仍在用戶端的顯示距離內。 */
        const val LOAD_RADIUS = 108

        /** 牌桌中心的 x 座標：[ShowcaseWorld] 的世界裡，離出生點最近的櫻花林旁的一塊平地。 */
        const val SITE_X = -1424

        /** 牌桌中心的 z 座標。 */
        const val SITE_Z = -481

        /** 遠景鏡頭被擋住時繞牌桌轉動的角度，依嘗試順序，單位為度。 */
        val SCENERY_TURNS = listOf(0.0, 15.0, -15.0, 30.0, -30.0, 45.0, -45.0, 60.0, -60.0, 75.0, -75.0, 90.0, -90.0)

        /** 遠景鏡頭每個角度都被擋住時，依序拉近到原本距離的幾倍。 */
        val SCENERY_DISTANCES = listOf(1.0, 0.8, 0.65, 0.5)

        /** 遠景鏡頭周圍要空出的格數，避免樹冠之類的方塊貼在鏡頭前。 */
        const val SCENERY_CLEARANCE = 2

        /** 牌桌桌面比牌桌底部高多少，用來判斷看不看得到桌面的四個角。 */
        const val TABLE_TOP_HEIGHT = 1.0

        /** 桌面中心到邊緣的水平距離。 */
        const val TABLE_HALF_WIDTH = 1.4

        /** 朝北的水平角度。原版把角度換算到 -180 到 180 之間（不含 180），寫成 -180 才與用戶端收到的值相同。 */
        const val FACING_NORTH = -180f

        /** 玩家站在牌桌南方多遠，伸手可及。 */
        const val PLAYER_DISTANCE = 2.5

        /** 加入的 AI 數量。 */
        const val AI_COUNT = 3

        /**
         * 本機玩家固定坐的那一邊。
         *
         * - 鏡頭在玩家背後，所以這也決定了鏡頭的方向。
         * - 夕陽在西邊，玩家坐北邊、鏡頭朝南拍時，陽光從畫面右側斜照，穿過櫻花樹在桌面與地上留下光影。
         */
        val PLAYER_SIDE: Direction = Direction.NORTH

        /** 座位中心往 [PLAYER_SIDE] 方向離牌桌中心超過多遠，才算在那一邊。座位離中心 [SEAT_DISTANCE] 格，其他三邊都不超過 0。 */
        const val SEAT_SIDE_MIN_DISTANCE = 1.5

        /** 宣傳截圖開局使用的固定種子，搭配固定的座位順序，每次開局的結果相同。 */
        val SHOWCASE_MATCH_SEED: MatchSeed = MatchSeed.fromByteArray(MessageDigest.getInstance("SHA-256").digest("mahjongcraft:showcase".encodeToByteArray()))

        /** 座位離牌桌中心的距離，單位為方塊。麻將凳放在這裡。 */
        const val SEAT_DISTANCE = 2

        /** 放點心的木桶的位置（往玩家右手邊幾格, 往牌桌遠端幾格），在右手邊那張凳子外側、靠遠端的斜後方。 */
        val SNACK_TABLE_OFFSET = 3 to 1

        /** 燈籠的位置（往玩家右手邊幾格, 往牌桌遠端幾格），在牌桌遠端的兩個角落外，鏡頭不會被擋住。 */
        val LANTERN_OFFSETS = listOf(-3 to 3, 3 to 3)

        /** 座位上的麻將凳。 */
        const val STOOL_BLOCK = "${MinecraftModMetadata.MOD_ID}:wooden_mahjong_stool"

        /** 替身低頭看手牌的角度。 */
        const val STAND_IN_PITCH = 35f

        /** 放下的麻將桌物品。 */
        const val TABLE_ITEM = "${MinecraftModMetadata.MOD_ID}:wooden_mahjong_table"

        /** 打到中盤、牌河有牌、對家立直的場景。 */
        const val MIDGAME_SCENARIO = "showcase_midgame"

        /** 立直後一發自摸的場景。 */
        const val WIN_SCENARIO = "showcase_tsumo"

        /** 和牌演出的截圖名稱。 */
        const val WIN_SHOT = "win-tsumo"

        /**
         * 閃電落下後再等幾個遊戲刻才截圖。
         *
         * - 閃電只在落下後的前幾個遊戲刻閃動，截圖本身還會再等畫面畫好，所以提早一點，讓截到的那一幀落在閃電最亮的時候。
         * - 這時往外擴散的電流已經出現。
         */
        const val LIGHTNING_CAPTURE_DELAY_TICKS = 2

        /** 和牌特效開始後第幾個遊戲刻截圖，對應閃電落下、電流往外擴散的畫面。 */
        const val WIN_CAPTURE_TICK = MahjongTileTableLayout.WIN_LIGHTNING_START_TICK + LIGHTNING_CAPTURE_DELAY_TICKS

        /** 等和牌特效出現並播到閃電的最長時間。 */
        val WIN_EFFECT_TIMEOUT = 30.seconds

        /** 一個區塊的寬度，單位為方塊。 */
        const val CHUNK_SIZE = 16.0

        /** 截圖前要在用戶端載入的範圍：鏡頭周圍多少個區塊。 */
        const val TERRAIN_RADIUS = 4

        /** 地形連續畫好多少個遊戲刻才算穩定。 */
        const val STABLE_TICKS = 20

        /** 地形畫好後再等多少個遊戲刻才截圖。 */
        const val SETTLE_TICKS = 60

        /** 拍攝牌桌的鏡頭，第一個是主要鏡頭，和牌演出也從它拍。 */
        val CAMERAS = listOf(
            Camera(name = "seat-view", back = 3.0, side = 0.8, up = 3.1, lookHeight = 0.9),
            Camera(name = "seat-high", back = 3.8, side = 1.2, up = 3.8, lookHeight = 0.9),
            Camera(name = "seat-low", back = 3.6, side = -1.5, up = 1.9, lookHeight = 1.0),
            Camera(
                name = "scenery-wide",
                back = 8.5,
                side = -5.0,
                up = 4.0,
                lookHeight = 1.5,
                turns = SCENERY_TURNS,
                clearance = SCENERY_CLEARANCE,
                distances = SCENERY_DISTANCES,
            ),
        )
    }
}
