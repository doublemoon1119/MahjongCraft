package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import net.minecraft.client.resources.model.UnbakedModel
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/**
 * 參與每次資源重載的模型載入。
 *
 * 每次重載先以 [prepare] 準備資料，再依這份資料決定要額外載入哪些模型，並為沒有模型檔的 ID 直接提供未烘焙模型。
 *
 * @param T 一次資源重載準備好的資料。
 */
interface ModelLoading<T : Any> {
    /**
     * 準備本次資源重載使用的資料。
     *
     * @param resourceManager 本次資源重載的資源管理器。
     * @param executor 執行準備工作的背景執行器。
     * @return 準備完成的資料。
     */
    fun prepare(resourceManager: ResourceManager, executor: Executor): CompletableFuture<T>

    /**
     * 模型開始載入時取得要額外載入並烘焙的模型。
     *
     * @param data 本次重載準備好的資料。
     * @return 要額外載入的模型 ID。
     */
    fun additionalModels(data: T): Collection<ResourceLocation>

    /**
     * 為沒有模型檔的 ID 提供未烘焙模型。
     *
     * @param data 本次重載準備好的資料。
     * @param id 要載入的模型 ID。
     * @return 這個 ID 的模型；不由這裡提供時為 null，交給其他來源處理。
     */
    fun resolveModel(data: T, id: ResourceLocation): UnbakedModel?
}
