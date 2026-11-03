package com.doublemoon1119.mahjongcraft.example.fabric.character

/**
 * 讓一次借用只恢復自己的原始狀態，舊 handle 不會釋放後來的接管。
 *
 * @param S 不可變的原始狀態。
 * @property capture 唯讀擷取接管前狀態，不在此回呼改動角色。
 * @property restore 恢復已擷取的狀態，不刪除角色。
 */
internal class ExampleActorControl<S : Any>(
    private val capture: () -> S,
    private val restore: (S) -> Unit,
) {
    /** 目前持有接管權的物件身分，不保存或同步。 */
    private var token: Any? = null

    /** 接管前的原始狀態，保存人偶時仍寫入這份狀態。 */
    var savedState: S? = null
        private set

    /** 角色目前是否由某個 handle 接管。 */
    val isActive: Boolean get() = token != null

    /**
     * 先保存狀態並認領，再由呼叫端改動角色。
     *
     * @param owner 本次 handle 的唯一物件。
     * @return 沒有其他接管者時為 true。
     */
    fun acquire(owner: Any): Boolean {
        if (token != null) return false
        val state = capture()
        savedState = state
        token = owner
        return true
    }

    /**
     * 只恢復相同 handle 的狀態；重複或過期釋放不影響別人。
     *
     * @param owner 要結束的 handle。
     */
    fun release(owner: Any) {
        if (token !== owner) return
        val state = savedState
        token = null
        savedState = null
        if (state != null) restore(state)
    }
}
