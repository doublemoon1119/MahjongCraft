package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.vanilla

import com.mojang.datafixers.DataFixer
import net.minecraft.core.RegistryAccess
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.storage.DimensionDataStorage
import java.io.InputStream
import java.nio.file.Path

/**
 * 建立讀寫指定資料夾的存檔資料管理器；本 mod 的資料不引用登記表內容，使用空的登記表。
 *
 * 本 mod 的存檔資料不指定資料修正類型。原版遇到這種資料會出錯，載入器（例如 Fabric API）則直接讀取、不做修正；
 * 這個管理器比照載入器的處理，讓測試反映遊戲中的行為。
 *
 * @param directory 存放資料檔案的資料夾。
 * @param fixer 讀取舊資料時使用的資料修正器。
 * @return 存檔資料管理器。
 */
fun dimensionDataStorage(directory: Path, fixer: DataFixer): DimensionDataStorage = object : DimensionDataStorage(
    directory.toFile(),
    fixer,
    RegistryAccess.EMPTY,
) {
    // 原版的參數標為不可為 null，實際上會收到 null；宣告成可為 null，才不會在進入方法時先被檢查擋下。
    @Suppress("WRONG_TYPE_FOR_JAVA_OVERRIDE")
    override fun readTagFromDisk(name: String, type: DataFixTypes?, version: Int): CompoundTag = if (type == null) {
        NbtIo.readCompressed(directory.resolve("$name$SAVED_DATA_EXTENSION"), NbtAccounter.unlimitedHeap())
    } else {
        super.readTagFromDisk(name, type, version)
    }
}

/** 原版存檔資料檔案的副檔名。 */
private const val SAVED_DATA_EXTENSION = ".dat"

/**
 * 把存檔資料直接寫進指定檔案；本 mod 的資料不引用登記表內容，使用空的登記表。
 *
 * @param file 要寫入的檔案。
 */
fun SavedData.saveToFile(file: Path) {
    save(file.toFile(), RegistryAccess.EMPTY)
}

/**
 * 讀取以 GZIP 壓縮的 NBT，大小不設上限。
 *
 * @param input 壓縮的 NBT 資料。
 * @return 讀到的 NBT。
 */
fun readCompressedNbt(input: InputStream): CompoundTag = NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap())
