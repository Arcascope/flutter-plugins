package cachet.plugins.health

import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import cachet.plugins.health.HealthPlugin.Companion.VALUE


fun StepsRecord.toHashMap(): HashMap<String, Any> {
    return hashMapOf(
        VALUE to count,
        HealthPlugin.DATE_FROM to startTime.toEpochMilli(),
        HealthPlugin.DATE_TO to endTime.toEpochMilli(),
        HealthPlugin.SOURCE_NAME to HealthPlugin.HEALTH_CONNECT,
        HealthPlugin.SOURCE_ID to StepsRecord::class.java.simpleName
    )
}

fun HeartRateRecord.toHashMap(): HashMap<String, Any> {
    return hashMapOf(
        VALUE to samples.last().beatsPerMinute,
        HealthPlugin.DATE_FROM to startTime.toEpochMilli(),
        HealthPlugin.DATE_TO to endTime.toEpochMilli(),
        HealthPlugin.SOURCE_NAME to HealthPlugin.HEALTH_CONNECT,
        HealthPlugin.SOURCE_ID to HeartRateRecord::class.java.simpleName
    )
}