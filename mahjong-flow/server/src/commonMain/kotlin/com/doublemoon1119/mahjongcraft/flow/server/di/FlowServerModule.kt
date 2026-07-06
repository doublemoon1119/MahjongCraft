package com.doublemoon1119.mahjongcraft.flow.server.di

import com.doublemoon1119.mahjongcraft.flow.common.di.FlowCommonModule
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module

@Module(includes = [FlowCommonModule::class])
@ComponentScan("com.doublemoon1119.mahjongcraft.flow.server")
class FlowServerModule
