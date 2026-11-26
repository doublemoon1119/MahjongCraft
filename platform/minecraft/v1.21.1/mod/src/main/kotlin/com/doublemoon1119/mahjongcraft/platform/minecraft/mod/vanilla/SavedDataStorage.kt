package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.storage.DimensionDataStorage

/**
 * 取得存在世界中的資料。
 *
 * - 管理器已經載入過就直接回傳，否則讀取檔案，檔案不存在時建立新的資料。
 * - 資料不經過原版的資料修正，Fabric 與 NeoForge 都接受不指定修正類型。
 *
 * @param S 資料型別。
 * @param key 資料的固定名稱，也是檔名。
 * @param decode 從讀到的內容還原資料。
 * @param create 沒有檔案時建立新的資料。
 * @return 管理器持有的資料。
 */
@Suppress("TYPE_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
fun <S : SavedData> DimensionDataStorage.computeSavedData(key: String, decode: (CompoundTag) -> S, create: () -> S): S = computeIfAbsent(
    SavedData.Factory(create, { nbt, _ -> decode(nbt) }, null),
    key,
)
