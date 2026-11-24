package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.client.debug.autotest

import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * 自動測試期間 mixin 查詢與回報的共用狀態：是否處於自動測試模式，以及記錄下來的聲音播放要求。
 *
 * 自動測試模式從遊戲啟動起就生效，直到遊戲關閉。
 */
object AutotestRuntime {
    /** 自動測試開始的時間，用來記錄聲音要求的相對時間。 */
    private val startedAt = TimeSource.Monotonic.markNow()

    /** 被攔下的聲音播放要求，依發生順序。 */
    private val sounds = CopyOnWriteArrayList<AutotestSoundRequest>()

    /**
     * 是否處於自動測試模式；此時忽略滑鼠鍵盤輸入、不鎖定游標、失去焦點不暫停，聲音只記錄不播放。
     *
     * @return 處於自動測試模式時為 true。
     */
    @JvmStatic
    fun isActive(): Boolean = AutotestSelection.current != null

    /**
     * 記錄一次被攔下的聲音播放要求。
     *
     * @param soundId 聲音事件的識別碼。
     */
    @JvmStatic
    fun recordSound(soundId: String) {
        sounds += AutotestSoundRequest(soundId, startedAt.elapsedNow())
    }

    /**
     * 取出從上次取出後記錄的聲音播放要求。
     *
     * @return 依發生順序的聲音播放要求。
     */
    internal fun drainSounds(): List<AutotestSoundRequest> = buildList {
        while (sounds.isNotEmpty()) add(sounds.removeAt(0))
    }

    /**
     * 從上次取出後記錄的聲音播放要求，不取出。
     *
     * @return 依發生順序的聲音播放要求。
     */
    internal fun peekSounds(): List<AutotestSoundRequest> = sounds.toList()

    /** 自動測試開始至今的時間。 */
    internal fun elapsed(): Duration = startedAt.elapsedNow()
}
