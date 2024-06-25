package doublemoon.mahjongcraft.network

enum class ChannelType(val s2c: Boolean, val c2s: Boolean) {
    S2C(true, false),
    C2S(false, true),
    Both(true, true);
}