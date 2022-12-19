package cachet.plugins.health

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.SleepStageRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import cachet.plugins.health.HealthPlugin.Companion.MINUTES
import cachet.plugins.health.HealthPlugin.Companion.UNIT
import cachet.plugins.health.HealthPlugin.Companion.VALUE
import kotlin.reflect.KClass


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

fun SleepStageRecord.toHashMap(): HashMap<String, Any> {
    return hashMapOf(
        VALUE to endTime.toEpochMilli() - startTime.toEpochMilli(),
        HealthPlugin.DATE_FROM to startTime.toEpochMilli(),
        HealthPlugin.DATE_TO to endTime.toEpochMilli(),
        HealthPlugin.SOURCE_NAME to HealthPlugin.HEALTH_CONNECT,
        HealthPlugin.SOURCE_ID to SleepStageRecord::class.java.simpleName,
        UNIT to MINUTES
    )
}

suspend fun <T : Record> HealthConnectClient.readAllRecords(
    recordType: KClass<T>,
    timeRangeFilter: TimeRangeFilter,
    dataOriginFilter: Set<DataOrigin> = emptySet(),
    ascendingOrder: Boolean = true,
): MutableList<T> {
    val firstPage = this.readRecords(
        ReadRecordsRequest(
            recordType,
            timeRangeFilter,
            dataOriginFilter,
            ascendingOrder
        )
    )
    val totalRecords = firstPage.records.toMutableList()
    var nextPageToken = firstPage.pageToken
    while (!nextPageToken.isNullOrEmpty()) {
        val nextPageRecords = this.readRecords(
            ReadRecordsRequest(
                recordType,
                timeRangeFilter,
                pageToken = nextPageToken
            )
        )
        totalRecords.addAll(nextPageRecords.records)
        nextPageToken = nextPageRecords.pageToken
    }

    return totalRecords
}