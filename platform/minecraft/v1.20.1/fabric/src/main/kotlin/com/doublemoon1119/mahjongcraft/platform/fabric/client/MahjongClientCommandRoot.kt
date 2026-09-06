package com.doublemoon1119.mahjongcraft.platform.fabric.client

import com.doublemoon1119.mahjongcraft.platform.fabric.server.room.FabricRoomCommand
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/**
 * 純 client-only 指令共用根節點。
 *
 * 不用跟 server 端共用的 `mahjongcraft`（[MinecraftModMetadata.MOD_ID]）：
 * 那個字面值已經被伺服器端的 `/mahjongcraft room|game|config`（見 [FabricRoomCommand] 等）用掉，
 * client 指令樹跟伺服器同步過來的指令樹合併時，同名根節點會被伺服器那棵樹蓋掉，導致這裡的子節點在 Tab 補全跟
 * 指令說明裡完全消失（遊戲內驗證過的現象）。
 *
 * 所有純 client-only 指令都應該掛在這個獨立根節點下面，避免重蹈覆轍。
 */
const val CLIENT_COMMAND_ROOT: String = "mahjongcraft_client"
