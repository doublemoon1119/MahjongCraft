package com.doublemoon1119.mahjongcraft.platform.neoforge.debug

import net.neoforged.fml.common.Mod

/**
 * NeoForge 開發執行載入的 debug 工具。
 *
 * 伺服器端的指令與自動測試由 `neoforge.mods.toml` 宣告的開發進入點與擴充接上本體，這裡不需要額外的初始化。
 */
@Mod(DEBUG_MOD_ID)
object MahjongCraftDebugNeoForge

/** debug 工具的 mod ID。 */
private const val DEBUG_MOD_ID = "mahjongcraft_debug"
