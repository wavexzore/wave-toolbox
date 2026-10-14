package com.wavexzore.toolbox.feature.settings.update

/** 可注入时钟（测试缝；T-306）。 */
fun interface Clock {
    fun nowMs(): Long
}
