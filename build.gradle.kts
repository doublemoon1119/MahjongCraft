plugins {
    base
    alias(libs.plugins.mahjongcraft.target.management)
    alias(libs.plugins.mahjongcraft.repository.verification)
}

group = "com.doublemoon1119.mahjongcraft"
version = "0.0.0-dev"

val projectId = "mahjongcraft"
val projectDisplayName = "MahjongCraft"
extra["projectId"] = projectId
extra["projectDisplayName"] = projectDisplayName
