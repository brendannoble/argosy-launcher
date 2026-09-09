package com.nendo.argosy.debugtools

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nendo.argosy.data.preferences.UserPreferencesRepository
import com.nendo.argosy.data.remote.romm.RomMRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Debug-only setup: pass url/token to seed RomM, or --ez skipSetup true for an offline library.
 */
class DebugSeedReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SeedEntryPoint {
        fun userPreferencesRepository(): UserPreferencesRepository
        fun romMRepository(): RomMRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        val skipSetup = intent.getBooleanExtra("skipSetup", false)
        val url = intent.getStringExtra("url")
        val token = intent.getStringExtra("token")
        if (!skipSetup && (url == null || token == null)) return
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            SeedEntryPoint::class.java
        )
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (!skipSetup) {
                    entryPoint.romMRepository().connectWithToken(requireNotNull(url), requireNotNull(token))
                }
                entryPoint.userPreferencesRepository().setFirstRunComplete()
            } finally {
                pending.finish()
            }
        }
    }
}
