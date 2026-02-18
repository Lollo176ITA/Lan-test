package com.lanshare.core.network

import com.lanshare.core.api.model.LanEvent
import com.lanshare.core.api.model.LanEventType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

class EventHub(
    @PublishedApi
    internal val json: Json
) {
    private val mutableEvents = MutableSharedFlow<LanEvent>(
        replay = 0,
        extraBufferCapacity = 256
    )

    val events: SharedFlow<LanEvent> = mutableEvents

    suspend fun emit(type: LanEventType, payload: JsonElement) {
        mutableEvents.emit(
            LanEvent(
                type = type,
                payload = payload,
                timestampEpochMillis = System.currentTimeMillis()
            )
        )
    }

    suspend inline fun <reified T> emit(type: LanEventType, payload: T) {
        emit(type, json.encodeToJsonElement(payload))
    }

    fun serialize(event: LanEvent): String = json.encodeToString(event)
}
