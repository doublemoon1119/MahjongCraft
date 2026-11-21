package com.doublemoon1119.mahjongcraft.flow.common.concurrent

import java.util.concurrent.ConcurrentHashMap

actual fun <K : Any, V : Any> concurrentMutableMap(): MutableMap<K, V> = ConcurrentHashMap()
