package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.vanilla

import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.MinecraftServer
import java.nio.file.Path

/**
 * 以指定的指令來源執行一個伺服器指令，指令可以帶或不帶開頭斜線。需要在伺服器主執行緒上呼叫。
 *
 * @param server 目前伺服器。
 * @param source 指令來源。
 * @param command 指令。
 * @return 指令的回傳值，指令執行失敗時為 0。
 */
fun performCommand(server: MinecraftServer, source: CommandSourceStack, command: String): Int = server.commands.performPrefixedCommand(source, command)

/**
 * 伺服器的執行資料夾，例如世界與設定檔所在的位置。
 *
 * @param server 目前伺服器。
 * @return 資料夾路徑。
 */
fun serverDirectory(server: MinecraftServer): Path = server.serverDirectory.toPath()
