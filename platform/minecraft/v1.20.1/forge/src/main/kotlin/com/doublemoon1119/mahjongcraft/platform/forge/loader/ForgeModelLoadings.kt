package com.doublemoon1119.mahjongcraft.platform.forge.loader

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ModelLoading
import net.minecraft.client.resources.model.UnbakedModel
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor

/**
 * 在 Forge 載入模型時套用登記的 [ModelLoading]。
 *
 * Forge 登記額外模型時呼叫 [prepare] 準備本次重載的資料。原版讀取模型時，Forge 專用的 mixin 先以 [resolve] 詢問有沒有
 * 程式提供的模型，沒有才照原版讀取模型檔。
 */
object ForgeModelLoadings {
    /** 登記的模型載入。 */
    private val loadings = CopyOnWriteArrayList<ModelLoading<*>>()

    /** 本次資源重載準備好的資料。 */
    @Volatile
    private var prepared: List<PreparedModelLoading<*>> = emptyList()

    /**
     * 登記一個模型載入。
     *
     * @param loading 參與每次資源重載的模型載入。
     */
    fun add(loading: ModelLoading<*>) {
        loadings += loading
    }

    /**
     * 為本次資源重載準備資料。
     *
     * Forge 在模型載入的工作執行緒上發出登記額外模型的事件，這裡在同一條執行緒上直接完成準備，不排入其他執行緒等待。
     *
     * @param resourceManager 本次重載的資源。
     * @return 要額外載入並烘焙的模型。
     */
    fun prepare(resourceManager: ResourceManager): List<ResourceLocation> {
        prepared = loadings.map { prepareLoading(it, resourceManager) }
        return prepared.flatMap(PreparedModelLoading<*>::additionalModels)
    }

    /**
     * 取得程式為某個 ID 提供的未烘焙模型。只由 Forge 專用的 mixin 在原版讀取模型時呼叫。
     *
     * @param id 模型 ID。
     * @return 程式提供的模型。沒有時為 null，照原版讀取模型檔。
     */
    @JvmStatic
    fun resolve(id: ResourceLocation): UnbakedModel? = prepared.firstNotNullOfOrNull { it.resolve(id) }

    /**
     * 準備一個模型載入的資料。
     *
     * @param T 準備資料的型別。
     * @param loading 模型載入。
     * @param resourceManager 本次重載的資源。
     * @return 準備好的資料。
     */
    private fun <T : Any> prepareLoading(loading: ModelLoading<T>, resourceManager: ResourceManager): PreparedModelLoading<T> = PreparedModelLoading(loading, loading.prepare(resourceManager, CURRENT_THREAD).join())

    /** 在呼叫的執行緒上直接執行工作。 */
    private val CURRENT_THREAD = Executor(Runnable::run)
}

/**
 * 一個模型載入與它在本次重載準備好的資料。
 *
 * @param T 準備資料的型別。
 * @property loading 模型載入。
 * @property data 本次重載準備好的資料。
 */
private class PreparedModelLoading<T : Any>(private val loading: ModelLoading<T>, private val data: T) {
    /** 要額外載入並烘焙的模型。 */
    val additionalModels: Collection<ResourceLocation>
        get() = loading.additionalModels(data)

    /**
     * 取得程式為某個 ID 提供的未烘焙模型。
     *
     * @param id 模型 ID。
     * @return 程式提供的模型。沒有時為 null。
     */
    fun resolve(id: ResourceLocation): UnbakedModel? = loading.resolveModel(data, id)
}
