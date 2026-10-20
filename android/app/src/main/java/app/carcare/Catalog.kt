package app.carcare

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class Part(
    val id: String,
    val name: String,
    val km: Int = 0,
    val months: Int = 12,
    val hint: String? = null,
    val safety: Boolean = false,
    val beltOnly: Boolean = false,
)

@Serializable
data class ModelSpec(
    val id: String,
    val brand: String,
    val name: String,
    val chain: Boolean = false,
)

@Serializable
data class Catalog(
    val models: List<ModelSpec>,
    val parts: List<Part>,
) {
    fun model(id: String?): ModelSpec? = models.firstOrNull { it.id == id }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(context: Context): Catalog {
            val text = context.assets.open("catalog.json").bufferedReader().use { it.readText() }
            return json.decodeFromString(Catalog.serializer(), text)
        }
    }
}
