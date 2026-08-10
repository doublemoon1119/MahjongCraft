package com.doublemoon1119.mahjongcraft.platform.fabric.command

import com.doublemoon1119.mahjongcraft.flow.common.concurrency.AppCoroutineScope
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.StartGameUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.AddAiPlayerUseCase
import com.doublemoon1119.mahjongcraft.flow.server.room.usecase.CreateRoomUseCase
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import kotlinx.coroutines.launch
import net.minecraft.server.command.CommandManager
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.Text
import org.koin.core.Koin
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

private val logger = LoggerFactory.getLogger("mahjongcraft")

/**
 * 除錯用指令：讓執行者自己加 3 個 AI 玩家湊成東風戰開局，藉此在子項 3（封包層）就能肉眼＋log 確認
 * 封包真的有收發，不用等到子項 4（房間生命週期正式入口）才能測。子項 4 完成後應直接刪掉這個指令，
 * 不是正式玩法入口。
 *
 * 建立房間/加 AI/開局都走 [koin.get] 拿到的 use case，跟正式入口（未來的方塊/物品操作）用的是
 * 同一套 use case，唯一差別是這裡繞過了「真正的操作介面」直接呼叫。
 */
fun registerMahjongTestCommand(dispatcher: CommandDispatcher<ServerCommandSource>, koin: Koin) {
    dispatcher.register(
        CommandManager.literal("mahjongtest").executes { context ->
            val player = context.source.player
            if (player == null) {
                context.source.sendError(Text.literal("必須由玩家執行"))
                return@executes 0
            }
            player.sendMessage(Text.literal("[MahjongCraft] 正在建立測試對局……"))

            val server = context.source.server
            val hostId = player.uuid.toKotlinUuid()
            koin.get<AppCoroutineScope>().launch {
                val roomId = Uuid.random()
                val result = runCatching {
                    val createOutcome = koin.get<CreateRoomUseCase>().invoke(roomId, hostId, RiichiRuleConfig())
                    check(createOutcome is Outcome.Success) { "建立房間失敗：$createOutcome" }
                    repeat(3) { koin.get<AddAiPlayerUseCase>().invoke(roomId, hostId) }
                    val startOutcome = koin.get<StartGameUseCase>().invoke(roomId, hostId)
                    check(startOutcome is Outcome.Success) { "開局失敗：$startOutcome" }
                }
                server.execute {
                    result.fold(
                        onSuccess = {
                            logger.info("mahjongtest: 已為 {} 建立測試對局 {}", player.name.string, roomId)
                            player.sendMessage(Text.literal("[MahjongCraft] 測試對局已開始（$roomId）"))
                        },
                        onFailure = { error ->
                            logger.error("mahjongtest 失敗", error)
                            player.sendMessage(Text.literal("[MahjongCraft] 建立測試對局失敗：${error.message}"))
                        },
                    )
                }
            }
            Command.SINGLE_SUCCESS
        },
    )
}
