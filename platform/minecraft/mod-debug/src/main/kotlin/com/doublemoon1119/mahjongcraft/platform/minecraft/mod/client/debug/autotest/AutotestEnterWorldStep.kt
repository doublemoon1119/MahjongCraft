package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** 建立並進入測試世界；其他步驟都在這個世界中執行，因此一律執行。 */
internal object AutotestEnterWorldStep : AutotestStep {
    override val id: String = "world"

    /** 進入世界的最長等待時間。 */
    private val ENTER_TIMEOUT = 3.minutes

    /** 等用戶端收到伺服器變化的最長時間。 */
    private val SYNC_TIMEOUT = 10.seconds

    /** 固定的時間，白天正午。 */
    private const val FIXED_TIME = 6000L

    /** 進入世界後設定測試環境的指令。 */
    private val SETUP_COMMANDS = listOf(
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

    override suspend fun run(context: AutotestContext) {
        val client = context.client
        AutotestWorld.createAndEnter(client)
        val entered = context.waitUntil(ENTER_TIMEOUT) { client.level != null && client.player != null && client.screen == null && client.overlay == null }
        if (!context.check("Entered the test world", entered, if (entered) null else "Not in the world after $ENTER_TIMEOUT")) return
        context.check("Player is in creative mode", client.gameMode?.playerMode?.isCreative == true)
        // 固定的白天與天氣讓每次截圖的光線相同；不讓生物生成、方塊隨機變化或火焰蔓延改變畫面；關掉指令回饋與進度公告，避免
        // 聊天訊息遮住畫面。
        if (!SETUP_COMMANDS.all { context.runCheckedCommand(it) }) return
        val level = checkNotNull(client.level)
        val synced = context.waitUntil(SYNC_TIMEOUT) { level.dayTime == FIXED_TIME && !level.isRaining }
        if (!context.check("The client shows the fixed time and clear weather", synced)) return
        context.screenshot("entered")
    }
}
