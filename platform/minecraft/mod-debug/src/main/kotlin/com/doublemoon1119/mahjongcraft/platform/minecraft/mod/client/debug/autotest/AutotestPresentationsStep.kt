package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 依序觸發和牌、結算等演出的 debug 預覽並截圖。
 *
 * - 播到固定的畫面時，從上方對準預覽截圖。
 * - 等每個預覽播完、清掉自己的實體後，才觸發下一個。
 * - 預覽不屬於任何牌局，在還沒有牌局的世界中執行，所以世界中本 mod 的實體只會因為預覽而增減。
 */
internal object AutotestPresentationsStep : AutotestStep {
    override val id: String = "presentations"

    override suspend fun run(context: AutotestContext) {
        if (!context.teleport(x = 0.5, y = -60.0, z = 1.5, yaw = 0f, pitch = 35f)) return
        PRESENTATIONS.forEach { presentation -> if (!runPresentation(context, presentation)) return }
    }

    /**
     * 觸發一個預覽，截圖後等它播完。
     *
     * @param context 步驟環境。
     * @param presentation 要觸發的預覽。
     * @return 預覽正常播完、清掉自己的實體時為 true。
     */
    private suspend fun runPresentation(context: AutotestContext, presentation: Presentation): Boolean {
        val before = context.modEntityPositions().keys
        if (!context.runCheckedCommand(presentation.command)) return false
        val spawned = context.waitUntil(SPAWN_TIMEOUT) { (context.modEntityPositions().keys - before).isNotEmpty() }
        if (!context.check("Presentation ${presentation.name} spawns its entities", spawned)) return false
        context.waitTicks(presentation.captureTicks)
        val positions = context.modEntityPositions().filterKeys { it !in before }.values
        if (!context.check("Presentation ${presentation.name} is still showing at the capture time", positions.isNotEmpty())) return false
        // 預覽依玩家的位置擺放，鏡頭對準預覽實體範圍的中心。
        val center = Vec3(positions.map { it.x }.average(), positions.minOf { it.y }, positions.map { it.z }.average())
        context.overheadScreenshot("presentation-${presentation.name}", center)
        val finished = context.waitUntil(FINISH_TIMEOUT) { (context.modEntityPositions().keys - before).isEmpty() }
        return context.check("Presentation ${presentation.name} finishes and removes its entities", finished)
    }

    /**
     * 伺服器上本 mod 所有實體的位置。
     *
     * @return 實體識別碼對位置。
     */
    private suspend fun AutotestContext.modEntityPositions(): Map<UUID, Vec3> = onServer { server ->
        server.allLevels.flatMap { level ->
            level.allEntities.filter { BuiltInRegistries.ENTITY_TYPE.getKey(it.type).namespace == MinecraftModMetadata.MOD_ID }.map { it.uuid to it.position() }
        }.toMap()
    }

    /**
     * 一個要觸發的預覽。
     *
     * @property name 截圖與檢查使用的名稱。
     * @property command 不含開頭斜線的指令。
     * @property captureTicks 觸發後到截圖的遊戲刻數，對應想看的畫面。
     */
    private data class Presentation(val name: String, val command: String, val captureTicks: Int)

    /** 等預覽生成實體的最長時間。 */
    private val SPAWN_TIMEOUT = 10.seconds

    /** 等預覽播完的最長時間。 */
    private val FINISH_TIMEOUT = 2.minutes

    /** 依序觸發的預覽。 */
    private val PRESENTATIONS = listOf(
        Presentation(name = "win-tsumo", command = "mahjongcraft debug win tsumo", captureTicks = 50),
        Presentation(name = "showcase-ron", command = "mahjongcraft debug showcase ron", captureTicks = 160),
        Presentation(name = "win-settlement", command = "mahjongcraft debug win_settlement riichi tsumo", captureTicks = 160),
        Presentation(name = "exhaustive-draw", command = "mahjongcraft debug exhaustive_draw_settlement riichi normal 2", captureTicks = 160),
        Presentation(name = "match-settlement", command = "mahjongcraft debug match_settlement", captureTicks = 160),
    )
}
