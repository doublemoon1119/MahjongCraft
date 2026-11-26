package com.doublemoon1119.mahjongcraft.flow.api.event

/** 對局結束的方式。 */
enum class MatchCompletion {
    /** 依規則正常打完。 */
    COMPLETED,

    /** 尚未打完就終止，例如場地在對局中被移除。 */
    ABORTED,
}
