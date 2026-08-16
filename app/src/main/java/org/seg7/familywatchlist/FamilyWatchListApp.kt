package org.seg7.familywatchlist

import android.app.Application
import org.seg7.familywatchlist.di.AppContainer

/**
 * Application entry point. Owns the single [AppContainer] (PLAN.md §1: manual DI, no Hilt).
 */
class FamilyWatchListApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
