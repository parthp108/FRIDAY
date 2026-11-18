package com.health.friday.desktop.di

import com.health.friday.desktop.daemon.HeadlessDaemon
import com.health.friday.desktop.features.command.CommandSurfaceEngine
import com.health.friday.desktop.features.hud.HudScreenModel
import com.health.friday.desktop.features.layout.AdaptiveLayoutState
import com.health.friday.desktop.features.intelligence.ContextEngine
import com.health.friday.desktop.features.sensory.ProceduralAudioSynthesizer
import com.health.friday.desktop.features.sensory.SensoryEngine
import com.health.friday.desktop.features.windows.WindowManager
import com.health.friday.desktop.repository.DesktopRepository
import com.health.friday.desktop.tools.CopyClipboardTool
import com.health.friday.desktop.tools.OpenWebPageTool
import com.health.friday.shared.ai.SharedAiOrchestrator
import org.koin.dsl.module

val appModule = module {
    single {
        SharedAiOrchestrator().apply {
            registerTool(OpenWebPageTool())
            registerTool(CopyClipboardTool())
        }
    }

    single { DesktopRepository(get()) }

    single { WindowManager() }

    single { ContextEngine() }

    single { AdaptiveLayoutState() }

    single { CommandSurfaceEngine(get()) }

    single { ProceduralAudioSynthesizer() }

    single { SensoryEngine(get()) }

    single { HeadlessDaemon().apply { start() } }

    factory { HudScreenModel(get()) }
}
