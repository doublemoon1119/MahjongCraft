package doublemoon.mahjongcraft.game.mahjong.riichi

/**
 * 所有麻將點棒, 按材質順序排
 * */
enum class ScoringStick(
    val point: Int
) {
    P100(100),
    P1000(1000),
    P5000(5000),
    P10000(10000)
    ;

    val code = this.ordinal
}