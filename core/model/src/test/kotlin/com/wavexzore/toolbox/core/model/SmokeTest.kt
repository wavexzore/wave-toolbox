package com.wavexzore.toolbox.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
private data class Ping(val ok: Boolean)

/**
 * 脚手架冒烟：证明 Kotlin 编译、kotlinx.serialization 插件、kotlin-test 单测通路可用。
 */
class SmokeTest {

    @Test
    fun serializationAndTestToolchainWork() {
        assertEquals("""{"ok":true}""", Json.encodeToString(Ping.serializer(), Ping(true)))
    }
}
