package com.doublemoon1119.mahjongcraft.platform.fabric.server.history

import net.minecraft.server.MinecraftServer
import net.minecraft.util.WorldSavePath
import java.nio.file.Path

/**
 * 由目前伺服器存檔根目錄決定唯一的歷史資料庫位置。
 *
 * 例如，單人世界可存於 `.minecraft/saves/MahjongWorld/mahjongcraft/history.sqlite`；
 * 專用伺服器可存於 `server/world/mahjongcraft/history.sqlite`。實際前綴取決於遊戲目錄與世界存檔名稱。
 * 同一存檔的所有維度共用這份資料庫，不會依玩家或維度另外建立檔案。
 */
object FabricHistoryDatabasePath {
    /** 資料庫路徑只依存檔決定，與維度及伺服器設定檔位置無關。 */
    fun resolve(server: MinecraftServer): Path = resolve(server.getSavePath(WorldSavePath.ROOT))

    /** 將已確認的存檔根目錄轉為模組專用資料庫路徑。 */
    internal fun resolve(saveRoot: Path): Path = saveRoot.resolve("mahjongcraft").resolve("history.sqlite")
}
