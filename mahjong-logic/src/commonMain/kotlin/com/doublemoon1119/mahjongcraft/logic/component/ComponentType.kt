package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId
import kotlin.reflect.KClass

/**
 * 定義一種組件的值型別、驗證與複製方式。
 *
 * 同一種組件在一次執行期間使用同一個定義。複製函式必須複製值中所有可變集合，不能保留來源別名。
 *
 * @param T 組件值的型別。
 * @property id 完整的組件識別碼。
 * @property valueClass 組件值的執行期型別。
 * @property copyValue 建立與來源獨立的組件值。
 * @property validateValue 驗證值是否合法，無效時拋出例外。
 * @property mergeValues 合併低層與高層值；未提供時，不同值視為衝突。
 * @property target 此組件允許附加的對象。
 * @property mergePeerValues 合併同層來源；未提供時只接受相同值，提供時必須與順序無關。
 * @property publicMarkers 明確允許公開的標記，預設不產生任何公開資料。
 */
class ComponentType<T : Any>(
    val id: String,
    val valueClass: KClass<T>,
    private val copyValue: (T) -> T,
    private val validateValue: (T) -> Unit = {},
    private val mergeValues: ((T, T) -> T)? = null,
    val target: ComponentOwnerType<*>,
    private val mergePeerValues: ((T, T) -> T)? = null,
    private val publicMarkers: (T) -> Set<String> = { emptySet() },
) {
    init {
        NamespacedId.requireValid(id) { "Component ID must be namespaced: $id" }
    }

    /**
     * 驗證並複製一個值，副本也必須符合相同型別與限制。
     *
     * @param value 要複製的值。
     * @return 不持有來源可變資料別名的值。
     */
    fun snapshot(value: T): T {
        require(valueClass.isInstance(value)) { "Invalid value type for component $id" }
        validateValue(value)
        return copyValue(value).also {
            require(valueClass.isInstance(it)) { "Invalid copied value type for component $id" }
            validateValue(it)
        }
    }

    /**
     * 依定義合併不同來源，不依登記或觀察者順序覆寫。
     *
     * @param lower 較低層值。
     * @param higher 較高層值。
     * @return 經驗證及獨立複製的合併結果。
     */
    fun merge(lower: T, higher: T): T {
        val first = snapshot(lower)
        val second = snapshot(higher)
        val merged = mergeValues?.invoke(first, second) ?: run {
            require(first == second) { "Conflicting component values: $id" }
            first
        }
        return snapshot(merged)
    }

    /**
     * 合併同層效果，拒絕沒有明確合併規則的衝突。
     *
     * @param first 第一個來源的值。
     * @param second 第二個來源的值。
     * @return 獨立且符合定義的合併值。
     */
    fun mergePeers(first: T, second: T): T {
        val left = snapshot(first)
        val right = snapshot(second)
        if (left == right) return left
        val merge = requireNotNull(mergePeerValues) { "Conflicting component effects: $id" }
        val result = snapshot(merge(snapshot(left), snapshot(right)))
        val reversed = snapshot(merge(snapshot(right), snapshot(left)))
        require(result == reversed) { "Component effect merge depends on source order: $id" }
        return result
    }

    /**
     * 取得定義明確開放的公共標記，不回傳私有組件 ID 或原始值。
     *
     * @param value 要投影的值。
     * @return 已驗證且獨立的標記集合。
     */
    fun publicMarkerIds(value: T): Set<String> = publicMarkers(snapshot(value)).toSet().also { markers ->
        markers.forEach { NamespacedId.requireValid(it) { "Public component marker must be namespaced: $it" } }
    }
}
