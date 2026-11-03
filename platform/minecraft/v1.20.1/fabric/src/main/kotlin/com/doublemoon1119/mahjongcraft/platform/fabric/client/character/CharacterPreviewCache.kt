package com.doublemoon1119.mahjongcraft.platform.fabric.client.character

/**
 * 單執行緒、有界且可清理的資源快取；缺少值只排隊，不在繪製中執行模型工廠。
 *
 * @param K 包含完整外觀與資源世代的鍵。
 * @param V 可釋放的資源。
 * @property capacity 資源與待處理鍵各自的上限。
 */
internal class CharacterPreviewCache<K, V : AutoCloseable>(private val capacity: Int) {
    /** 讀取後移至尾端，讓最久未使用的資源先釋放。 */
    private val images = linkedMapOf<K, V>()

    /** 清理世代，防止載入中重入 clear 後放回舊資源。 */
    private var generation = 0L

    /** 每個鍵最多排隊一次。 */
    private val pending = linkedSetOf<K>()

    init {
        require(capacity > 0)
    }

    /**
     * 取得資源，沒有時排入有界佇列。
     *
     * @param key 完整鍵。
     * @return 已有資源，否則為 null。
     */
    fun request(key: K): V? {
        val image = images.remove(key)
        if (image != null) {
            images[key] = image
        } else if (pending.size < capacity) {
            pending.add(key)
        }
        return image
    }

    /**
     * 一次只處理一個待載入鍵；載入失敗不留下半成品。
     *
     * @param load 由呼叫端處理例外與備援的載入函式。
     */
    fun processOne(load: (K) -> V?) {
        val key = pending.firstOrNull() ?: return
        pending.remove(key)
        val expectedGeneration = generation
        val image = load(key) ?: return
        if (generation != expectedGeneration) {
            image.close()
            return
        }
        val replaced = images.put(key, image)
        replaced?.close()
        if (images.size > capacity) images.remove(images.keys.first())?.close()
    }

    /** 清除待處理工作，並釋放每張已建立資源。 */
    fun clear() {
        generation++
        pending.clear()
        val previous = images.values.toList()
        images.clear()
        var failure: Throwable? = null
        previous.forEach { image ->
            try {
                image.close()
            } catch (cause: Throwable) {
                val first = failure
                if (first == null) {
                    failure = cause
                } else if (first !== cause) {
                    first.addSuppressed(cause)
                }
            }
        }
        failure?.let { throw it }
    }
}
