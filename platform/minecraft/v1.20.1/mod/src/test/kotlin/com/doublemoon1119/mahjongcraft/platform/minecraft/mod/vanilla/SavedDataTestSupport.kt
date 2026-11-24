package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.mojang.datafixers.DataFixer
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtIo
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.storage.DimensionDataStorage
import java.io.InputStream
import java.nio.file.Path

/**
 * 建立讀寫指定資料夾的存檔資料管理器。
 *
 * @param directory 存放資料檔案的資料夾。
 * @param fixer 讀取舊資料時使用的資料修正器。
 * @return 存檔資料管理器。
 */
fun dimensionDataStorage(directory: Path, fixer: DataFixer): DimensionDataStorage = DimensionDataStorage(directory.toFile(), fixer)

/**
 * 把存檔資料直接寫進指定檔案。
 *
 * @param file 要寫入的檔案。
 */
fun SavedData.saveToFile(file: Path) {
    save(file.toFile())
}

/**
 * 讀取以 GZIP 壓縮的 NBT，大小不設上限。
 *
 * @param input 壓縮的 NBT 資料。
 * @return 讀到的 NBT。
 */
fun readCompressedNbt(input: InputStream): CompoundTag = NbtIo.readCompressed(input)
