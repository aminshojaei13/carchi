package app.carchi

import java.time.Instant
import java.time.ZoneId

enum class Level { OVER, NEAR, OK }

data class Row(
    val part: Part,
    val remKm: Int?,
    val remDays: Long,
    val level: Level,
    val baseKm: Int,
    val baseMs: Long,
    val dueKm: Int?,
    val dueMs: Long,
    val overridden: Boolean = false,
)

private const val DAY_MS = 86_400_000L

fun rowsFor(state: AppState, catalog: Catalog): List<Row> {
    val car = state.car ?: return emptyList()
    val chain = catalog.model(car.modelId)?.chain == true
    val now = System.currentTimeMillis()

    return catalog.effectiveParts(car.modelId, state.overrides)
        .filter { !(it.beltOnly && chain) }
        .map { part ->
            val service = state.services[part.id]
            val baseKm = service?.km ?: car.km
            val baseMs = service?.date ?: car.startedAt
            val baseDate = Instant.ofEpochMilli(baseMs).atZone(ZoneId.systemDefault()).toLocalDate()
            val dueDate = baseDate.plusMonths(part.months.toLong())
            val dueMs = dueDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val remKm = if (part.km > 0) baseKm + part.km - car.km else null
            val remDays = (dueMs - now) / DAY_MS

            val over = (remKm != null && remKm <= 0) || remDays <= 0
            val near = !over && ((remKm != null && remKm <= 1000) || remDays <= 30)

            Row(
                part = part,
                remKm = remKm,
                remDays = remDays,
                level = if (over) Level.OVER else if (near) Level.NEAR else Level.OK,
                baseKm = baseKm,
                baseMs = baseMs,
                dueKm = if (part.km > 0) baseKm + part.km else null,
                dueMs = dueMs,
                overridden = state.overrides.containsKey(Catalog.key(car.modelId, part.id)),
            )
        }
        .sortedWith(compareBy<Row>({ it.level.ordinal }, { it.remKm ?: Int.MAX_VALUE }))
}

fun Row.remainingText(): String = when {
    remKm != null && remKm > 0 && remDays > 0 -> "${remKm.fa()} کیلومتر یا ${remDays.fa()} روز مانده"
    remKm != null && remKm <= 0 && remDays > 0 -> "${(-remKm).fa()} کیلومتر گذشته"
    remDays <= 0 -> "${(-remDays).fa()} روز گذشته"
    remKm != null -> "${remKm.fa()} کیلومتر مانده"
    remDays > 0 -> "${remDays.fa()} روز مانده"
    else -> "—"
}

/** متن کوتاه برای اعلان. */
fun Row.shortText(): String = when {
    remKm != null && remKm <= 0 -> "${(-remKm).fa()} کیلومتر گذشته"
    remDays <= 0 -> "${(-remDays).fa()} روز گذشته"
    remKm != null -> "${remKm.fa()} کیلومتر مانده"
    else -> "${remDays.fa()} روز مانده"
}
