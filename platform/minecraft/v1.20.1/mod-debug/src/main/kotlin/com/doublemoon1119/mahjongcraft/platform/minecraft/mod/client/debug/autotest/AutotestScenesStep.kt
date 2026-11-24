package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.RiichiGameActionVoiceSoundIds
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.Entity
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 在開局的牌桌上依序載入日麻的 debug 場景，以玩家的身分做出場景預期的操作，檢查結果並截圖。
 *
 * 每個場景都等伺服器的對局可以接受操作後才載入，只載入一次；載入被拒即失敗。需要前一個步驟開好的牌局。
 */
internal object AutotestScenesStep : AutotestStep {
    override val id: String = "scenes"

    override suspend fun run(context: AutotestContext) {
        SCENARIOS.forEach { scenario ->
            if (!context.check("A game is ready before scenario ${scenario.name}", context.ensureGameRunning())) return
            if (!loadScenario(context, scenario.name)) return
            scenario.play(context)
        }
    }

    /**
     * 載入一個場景，等它的開局演出播完後截圖。
     *
     * @param context 步驟環境。
     * @param name 場景名稱，不含命名空間與規則前綴。
     * @return 載入成功而且演出播完時為 true。
     */
    private suspend fun loadScenario(context: AutotestContext, name: String): Boolean {
        val feedback = context.runCommand("mahjongcraft debug scenario load mahjongcraft:riichi/debug/$name")
        // 指令只排程載入，結果之後才以回饋送回。
        val answered = context.waitUntil(LOAD_TIMEOUT) { feedback.answered }
        val detail = feedback.failures.joinToString().ifEmpty { if (answered) null else "No result within $LOAD_TIMEOUT" }
        if (!context.check("Scenario $name loads", answered && feedback.succeeded, detail)) return false
        if (!context.check("Scenario $name presentation finishes", context.waitUntilTableIdle())) return false
        context.tableScreenshots("scenario-$name")
        return true
    }

    /**
     * 立直後自摸一發：立直宣言打出西風，其他三家摸切後摸到四萬自摸。
     *
     * @param context 步驟環境。
     */
    private suspend fun playIppatsuTsumo(context: AutotestContext) {
        val riichi = context.waitForAction(RiichiGameAction.Riichi.id)
        if (!context.check("Riichi is offered", riichi != null, context.offeredActions()) || riichi == null) return
        val choices = riichi.action.tileSelection?.eligibleTileIds.orEmpty()
        if (!context.check("Riichi has a single discard choice", choices.size == 1, "${choices.size} choices")) return
        if (!context.check("Riichi is accepted", context.submit(riichi))) return
        context.check("The riichi call is voiced", context.waitForSound(RiichiGameActionVoiceSoundIds.RIICHI))

        val tsumo = context.waitForAction(BuiltInGameActionIds.TSUMO)
        if (!context.check("Tsumo is offered on the ippatsu draw", tsumo != null, context.offeredActions()) || tsumo == null) return
        if (!context.check("Tsumo is accepted", context.submit(tsumo))) return
        context.check("The tsumo call is voiced", context.waitForSound(RiichiGameActionVoiceSoundIds.TSUMO))
        val settled = context.waitUntil(SETTLEMENT_TIMEOUT) { context.client.level?.entitiesForRendering()?.any(::isWinSettlement) == true }
        if (!context.check("The win settlement appears", settled)) return
        context.tableScreenshots("ippatsu-tsumo-settlement")
    }

    /**
     * 包牌前的碰：上家打出的牌可以碰。
     *
     * @param context 步驟環境。
     */
    private suspend fun playBeforePaoPon(context: AutotestContext) {
        val pon = context.waitForAction(BuiltInGameActionIds.PON)
        if (!context.check("Pon is offered", pon != null, context.offeredActions()) || pon == null) return
        if (!context.check("Pon is accepted", context.submit(pon))) return
        context.check("The pon call is voiced", context.waitForSound(RiichiGameActionVoiceSoundIds.PON))
        if (!context.check("The pon presentation finishes", context.waitUntilTableIdle())) return
        context.tableScreenshots("before-pao-pon-called")
    }

    /**
     * 等到指定的聲音被要求播放。
     *
     * @param soundId 聲音識別碼。
     * @return 在時限內被要求播放時為 true。
     */
    private suspend fun AutotestContext.waitForSound(soundId: String): Boolean = waitUntil(SOUND_TIMEOUT) {
        AutotestRuntime.peekSounds().any { it.soundId == soundId }
    }

    /**
     * 實體是否是和牌結算的演出。
     *
     * @param entity 用戶端的實體。
     * @return 是和牌結算演出時為 true。
     */
    private fun isWinSettlement(entity: Entity): Boolean = BuiltInRegistries.ENTITY_TYPE.getKey(entity.type).path == WIN_SETTLEMENT_ENTITY

    /**
     * 一個要載入的場景，以及載入後做的操作。
     *
     * @property name 場景名稱，不含命名空間與規則前綴。
     * @property play 載入後做的操作與檢查。
     */
    private class Scenario(val name: String, val play: suspend (AutotestContext) -> Unit)

    /** 等場景載入結果的最長時間；比載入指令自己等牌桌空下來的上限更長，失敗時收到的是指令回報的原因。 */
    private val LOAD_TIMEOUT = 3.minutes

    /** 等聲音被要求播放的最長時間。 */
    private val SOUND_TIMEOUT = 10.seconds

    /** 和牌後等結算演出出現的最長時間。 */
    private val SETTLEMENT_TIMEOUT = 60.seconds

    /** 和牌結算演出的實體類型名稱。 */
    private const val WIN_SETTLEMENT_ENTITY = "win_settlement_presentation"

    /** 依序載入的日麻場景。 */
    private val SCENARIOS = listOf(
        Scenario(name = "wall_opening") {},
        Scenario(name = "meld_showcase") {},
        Scenario(name = "ippatsu_tsumo", play = ::playIppatsuTsumo),
        Scenario(name = "before_pao_pon", play = ::playBeforePaoPon),
    )
}
