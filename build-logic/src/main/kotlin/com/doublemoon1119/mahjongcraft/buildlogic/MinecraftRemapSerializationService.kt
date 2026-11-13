package com.doublemoon1119.mahjongcraft.buildlogic

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * 整個建置共用的排隊名額，讓 Minecraft loader 模組的重新映射任務一次只執行一個。
 *
 * 同一個 Gradle daemon 裡同時執行兩個 Fabric Loom 重新映射任務時，其中一個可能在讀取 classpath JAR 時遇到已被關閉的
 * 檔案系統（`ClosedFileSystemException`）。名額上限由 [MahjongCraftMinecraftLoaderPlugin] 登記時設為 1；
 * 本服務不保存任何狀態。
 */
abstract class MinecraftRemapSerializationService : BuildService<BuildServiceParameters.None>
