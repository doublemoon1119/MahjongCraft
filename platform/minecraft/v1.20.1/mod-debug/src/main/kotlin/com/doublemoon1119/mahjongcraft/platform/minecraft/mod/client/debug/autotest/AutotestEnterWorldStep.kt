package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import kotlin.time.Duration.Companion.minutes

/** 建立並進入測試世界；其他步驟都在這個世界中執行，因此一律執行。 */
internal object AutotestEnterWorldStep : AutotestStep {
    override val id: String = "world"

    /** 進入世界的最長等待時間。 */
    private val ENTER_TIMEOUT = 3.minutes

    override suspend fun run(context: AutotestContext) {
        val client = context.client
        AutotestWorld.createAndEnter(client)
        val entered = context.waitUntil(ENTER_TIMEOUT) { client.level != null && client.player != null && client.screen == null && client.overlay == null }
        if (!context.check("Entered the test world", entered, if (entered) null else "Not in the world after $ENTER_TIMEOUT")) return
        context.check("Player is in creative mode", client.gameMode?.playerMode?.isCreative == true)
        context.screenshot("entered")
    }
}
