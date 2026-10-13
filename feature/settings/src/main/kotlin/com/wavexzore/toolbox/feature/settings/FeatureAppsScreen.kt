package com.wavexzore.toolbox.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.wavexzore.toolbox.core.accessibility.feature.FeatureRegistry
import com.wavexzore.toolbox.core.design.components.WaveScaffold
import com.wavexzore.toolbox.core.design.components.WaveTopBar
import com.wavexzore.toolbox.core.design.theme.Wave
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** 二级页宿主：把整屏交给特性自渲染（SPI [com.wavexzore.toolbox.core.feature.api.ToolFeature.AppsScreen]）。 */
@Composable
fun FeatureAppsScreen(
    onBack: () -> Unit,
    viewModel: FeatureAppsViewModel = hiltViewModel(),
) {
    val feature = viewModel.feature
    if (feature == null) {
        WaveScaffold(topBar = { WaveTopBar("按应用控制", onBack = onBack) }) { padding ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("该特性不存在", color = Wave.Ink2, fontSize = 13.5.sp, modifier = Modifier.padding(padding))
            }
        }
    } else {
        feature.AppsScreen(onBack)
    }
}

@HiltViewModel
class FeatureAppsViewModel @Inject constructor(
    registry: FeatureRegistry,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val feature = registry.byId(savedStateHandle["featureId"] ?: "")
}
