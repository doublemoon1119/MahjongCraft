package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import kotlin.time.Duration.Companion.minutes

/** 建立並進入測試世界；其他步驟都在這個世界中執行，因此一律執行。 */
internal object AutotestEnterWorldStep : AutotestStep {
    override val id: String = "world"

    /** 進入世界的最長等待時間。 */
    private val ENTER_TIMEOUT = 3.minutes

    /** 進入世界後設定測試環境的指令。 */
    private val SETUP_COMMANDS = listOf(
        "gamerule sendCommandFeedback false",
        "gamerule announceAdvancements false",
        "gamerule doDaylightCycle false",
        "gamerule doWeatherCycle false",
        "time set 6000",
        "weather clear",
    )

    override suspend fun run(context: AutotestContext) {
        val client = context.client
        AutotestWorld.createAndEnter(client)
        val entered = context.waitUntil(ENTER_TIMEOUT) { client.level != null && client.player != null && client.screen == null && client.overlay == null }
        if (!context.check("Entered the test world", entered, if (entered) null else "Not in the world after $ENTER_TIMEOUT")) return
        context.check("Player is in creative mode", client.gameMode?.playerMode?.isCreative == true)
        // 固定的白天與天氣讓每次截圖的光線相同；關掉指令回饋與進度公告，避免聊天訊息遮住畫面。
        SETUP_COMMANDS.forEach { context.runCommand(it) }
        context.screenshot("entered")
    }
}
