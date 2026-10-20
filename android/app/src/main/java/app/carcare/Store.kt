package app.carcare

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Car(
    val modelId: String,
    val year: Int,
    val km: Int,
    val startedAt: Long,
)

@Serializable
data class Service(
    val km: Int,
    val date: Long,
)

@Serializable
data class Event(
    val partId: String,
    val km: Int,
    val date: Long,
    val cost: Int = 0,
    val type: String = "done",
)

@Serializable
data class AppState(
    val car: Car? = null,
    val services: Map<String, Service> = emptyMap(),
    val events: List<Event> = emptyList(),
)

class Store(context: Context) {
    private val file = File(context.filesDir, "state.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    fun load(): AppState = runCatching {
        if (file.exists()) json.decodeFromString(AppState.serializer(), file.readText()) else AppState()
    }.getOrDefault(AppState())

    fun save(state: AppState) = runCatching { file.writeText(json.encodeToString(AppState.serializer(), state)) }

    fun clear() = runCatching { file.delete() }
}
