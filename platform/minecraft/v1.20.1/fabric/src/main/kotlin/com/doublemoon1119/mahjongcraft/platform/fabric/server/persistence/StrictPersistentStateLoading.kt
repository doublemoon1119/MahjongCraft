package com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence

import net.minecraft.nbt.NbtCompound
import net.minecraft.server.MinecraftServer
import net.minecraft.util.WorldSavePath
import net.minecraft.world.PersistentState
import net.minecraft.world.PersistentStateManager
import java.nio.file.Files
import java.nio.file.Path

/**
 * 嚴格載入 overworld 的持久化資料，只在確認檔案不存在時建立空資料。
 *
 * 原版會捕捉讀取及解碼例外，再呼叫建立函式。本入口在建立函式再次檢查檔案與解碼結果，
 * 避免原版把損毀或不支援的既有存檔當成新世界。
 *
 * @param S 持久化資料型別。
 * @param server 擁有 overworld 存檔管理器的伺服器。
 * @param key 存檔管理器的固定資料名稱。
 * @param decode 從既有 NBT 還原完整資料，不可自行退回空資料。
 * @param create 檔案確定不存在時建立的新資料。
 * @return 原管理器快取或成功載入的資料。
 */
internal fun <S : PersistentState> loadPersistentStateStrict(
    server: MinecraftServer,
    key: String,
    decode: (NbtCompound) -> S,
    create: () -> S,
): S = loadPersistentStateStrict(
    manager = server.overworld.persistentStateManager,
    dataFile = server.getSavePath(WorldSavePath.ROOT).resolve(PersistentStateStoragePaths.DIRECTORY).resolve(key + PersistentStateStoragePaths.EXTENSION),
    key = key,
    decode = decode,
    create = create,
)

/**
 * 在原生管理器建立新資料前阻止讀取失敗被當成檔案不存在。
 *
 * 使用 notExists 而不是 exists 的反值：權限不足或無法判斷是否存在時也必須拒絕建立。
 * 原生管理器仍持有成功載入的資料，保存與快取行為不變。
 *
 * @param S 持久化資料型別。
 * @param manager 原生持久化管理器。
 * @param dataFile 此 key 對應的實際檔案。
 * @param key 固定資料名稱。
 * @param decode 還原既有 NBT 的函式。
 * @param create 確認無既有檔案時建立資料的函式。
 * @return 管理器持有的有效資料。
 */
internal fun <S : PersistentState> loadPersistentStateStrict(
    manager: PersistentStateManager,
    dataFile: Path,
    key: String,
    decode: (NbtCompound) -> S,
    create: () -> S,
): S {
    var decodeFailure: Exception? = null
    return manager.getOrCreate(
        { nbt ->
            try {
                decode(nbt)
            } catch (failure: Exception) {
                decodeFailure = failure
                throw failure
            }
        },
        {
            decodeFailure?.let { throw it }
            check(Files.notExists(dataFile)) { "Existing saved state could not be read: $key" }
            create()
        },
        key,
    )
}

/**
 * 取得持久化資料相對於世界存檔目錄的檔案路徑，供提示玩家與記錄使用。
 *
 * @param key 存檔管理器的固定資料名稱。
 * @return 以 `/` 分隔的相對路徑，例如 `data/<key>.dat`。
 */
internal fun persistentStateDisplayPath(key: String): String = PersistentStateStoragePaths.DIRECTORY + "/" + key + PersistentStateStoragePaths.EXTENSION

/** Minecraft 1.20.1 overworld 持久化管理器使用的檔案位置。 */
private object PersistentStateStoragePaths {
    /** 世界根目錄內的持久化資料目錄。 */
    const val DIRECTORY = "data"

    /** 原生管理器附加在資料名稱後的副檔名。 */
    const val EXTENSION = ".dat"
}
