package com.wavexzore.toolbox.core.accessibility.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.wavexzore.toolbox.core.data.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 通知操作按钮：切换引擎总开关（T-009）。 */
@AndroidEntryPoint
class ToggleEngineReceiver : BroadcastReceiver() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_TOGGLE_ENGINE) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val current = settingsRepository.engineEnabled.first()
                settingsRepository.setEngineEnabled(!current)
                Log.i(TAG, "engine toggled: $current -> ${!current}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val TAG = "WaveA11y.KeepAlive"
        const val ACTION_TOGGLE_ENGINE = "com.wavexzore.toolbox.action.TOGGLE_ENGINE"
    }
}
