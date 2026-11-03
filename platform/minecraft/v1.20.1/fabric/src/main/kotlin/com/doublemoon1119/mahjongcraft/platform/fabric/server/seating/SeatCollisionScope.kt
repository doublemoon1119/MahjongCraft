package com.doublemoon1119.mahjongcraft.platform.fabric.server.seating

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatRequest
import net.minecraft.entity.Entity
import net.minecraft.util.math.Box
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

/** 同步整桌換位時辨識已安排離開的成員，不略過外部實體或互相重疊的目的位置。 */
internal object SeatCollisionScope {
    /** 只在目前同步呼叫所在執行緒保存本批目的位置，不跨協程等待。 */
    private val targets = ThreadLocal<Map<Uuid, Box>?>()

    /**
     * 在同步整桌安排期間提供目的位置，巢狀呼叫結束後恢復上一層。
     *
     * @param riders 已授權的本批乘坐者。
     * @param requests 依同一順序排列的正式席位。
     * @param action 不可暫停的原生入座操作。
     * @return 原生入座操作的結果。
     */
    fun <T> with(
        riders: List<Entity?>,
        requests: List<SeatRequest>,
        action: () -> T,
    ): T {
        require(riders.size == requests.size)
        val plans = riders.mapIndexedNotNull { index, rider ->
            rider?.let {
                val target = requests[index].seat
                it.uuid.toKotlinUuid() to it.boundingBox.offset(target.x - it.x, target.y - it.y, target.z - it.z)
            }
        }.toMap()
        require(plans.size == riders.count { it != null }) { "Batch riders must be unique" }
        return withPlans(plans, action)
    }

    /**
     * 將已計算的目的範圍限定在同步操作內，結束或失敗都清除。
     *
     * @param plans 本批成員與目的範圍。
     * @param action 不可暫停的入座操作。
     * @return 入座操作的結果。
     */
    fun <T> withPlans(plans: Map<Uuid, Box>, action: () -> T): T {
        val previous = targets.get()
        targets.set(plans.toMap())
        return try {
            action()
        } finally {
            if (previous == null) targets.remove() else targets.set(previous)
        }
    }

    /**
     * 判斷另一位本批成員是否會離開此位置，目的範圍仍重疊時不忽略。
     *
     * @param subject 正在尋找安全位置的成員。
     * @param other 本次碰撞查詢遇到的實體。
     * @return 兩者都在本批且目的範圍不重疊時為 true。
     */
    fun ignores(subject: Entity, other: Entity): Boolean = ignores(subject.uuid.toKotlinUuid(), other.uuid.toKotlinUuid())

    /**
     * 只略過本批中目的範圍不重疊的另一位成員。
     *
     * @param subject 本次查詢的成員。
     * @param other 查詢遇到的另一個主體。
     * @return 兩者皆在本批且目的範圍不相交時為 true。
     */
    fun ignores(subject: Uuid, other: Uuid): Boolean {
        if (subject == other) return false
        val current = targets.get() ?: return false
        val first = current[subject] ?: return false
        val second = current[other] ?: return false
        return !first.intersects(second)
    }
}
