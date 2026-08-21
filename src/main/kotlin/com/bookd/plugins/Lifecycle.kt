package com.bookd.plugins

import com.bookd.infrastructure.lifecycle.BackendLifecycleService
import io.ktor.server.application.*
import org.koin.ktor.ext.inject

fun Application.configureLifecycleCleanup() {
    val lifecycleService by inject<BackendLifecycleService>()

    monitor.subscribe(ApplicationStopping) {
        lifecycleService.close()
    }
}
