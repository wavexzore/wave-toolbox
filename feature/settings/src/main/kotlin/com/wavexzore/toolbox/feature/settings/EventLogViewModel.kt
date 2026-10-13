package com.wavexzore.toolbox.feature.settings

import androidx.lifecycle.ViewModel
import com.wavexzore.toolbox.core.accessibility.pipeline.EventPipeline
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class EventLogViewModel @Inject constructor(
    private val pipeline: EventPipeline,
) : ViewModel() {

    val recent: StateFlow<List<EventPipeline.RecentEvent>> = pipeline.recent

    val stats: StateFlow<EventPipeline.Stats> = pipeline.stats

    fun clear() {
        pipeline.clearLog()
    }
}
