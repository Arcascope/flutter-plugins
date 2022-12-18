package cachet.plugins.health

import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import kotlin.reflect.KClass


// Mapper class that do the basic converting the flutter types.
// todo: clean up and support all the types that available in health connect

var BODY_FAT_PERCENTAGE = "BODY_FAT_PERCENTAGE"
var HEIGHT = "HEIGHT"
var WEIGHT = "WEIGHT"
var STEPS = "STEPS"
var AGGREGATE_STEP_COUNT = "AGGREGATE_STEP_COUNT"
var ACTIVE_ENERGY_BURNED = "ACTIVE_ENERGY_BURNED"
var HEART_RATE = "HEART_RATE"
var BODY_TEMPERATURE = "BODY_TEMPERATURE"
var BLOOD_PRESSURE_SYSTOLIC = "BLOOD_PRESSURE_SYSTOLIC"
var BLOOD_PRESSURE_DIASTOLIC = "BLOOD_PRESSURE_DIASTOLIC"
var BLOOD_OXYGEN = "BLOOD_OXYGEN"
var BLOOD_GLUCOSE = "BLOOD_GLUCOSE"
var MOVE_MINUTES = "MOVE_MINUTES"
var DISTANCE_DELTA = "DISTANCE_DELTA"
var WATER = "WATER"
var SLEEP_ASLEEP = "SLEEP_ASLEEP"
var SLEEP_AWAKE = "SLEEP_AWAKE"
var SLEEP_IN_BED = "SLEEP_IN_BED"
var WORKOUT = "WORKOUT"

fun callToHealthConnectTypes(types: List<String>, permissions: List<Int>): List<HealthPermission> {
    val healthPermissions = mutableListOf<HealthPermission>()

    for ((i, typeKey) in types.withIndex()) {
        val access = permissions[i]
        val dataType = keyToHealthConnectDataType(typeKey)
        when (access) {
            0 -> healthPermissions.add(HealthPermission.createReadPermission(dataType))
            1 -> healthPermissions.add(HealthPermission.createWritePermission(dataType))
            2 -> {
                healthPermissions.add(HealthPermission.createReadPermission(dataType))
                healthPermissions.add(HealthPermission.createWritePermission(dataType))
            }
            else -> throw IllegalArgumentException("Unknown access type $access")
        }
    }

    return healthPermissions
}

fun getFieldHealthConnect(type: String): KClass<out Record> {
    return when (type) {
        STEPS -> StepsRecord::class
        HEART_RATE -> HeartRateRecord::class
//      BODY_TEMPERATURE -> HealthFields.FIELD_BODY_TEMPERATURE
//      BLOOD_PRESSURE_SYSTOLIC -> HealthFields.FIELD_BLOOD_PRESSURE_SYSTOLIC
//      BLOOD_PRESSURE_DIASTOLIC -> HealthFields.FIELD_BLOOD_PRESSURE_DIASTOLIC
//      BLOOD_OXYGEN -> HealthFields.FIELD_OXYGEN_SATURATION
//      BLOOD_GLUCOSE -> HealthFields.FIELD_BLOOD_GLUCOSE_LEVEL
//      MOVE_MINUTES -> Field.FIELD_DURATION
//      DISTANCE_DELTA -> Field.FIELD_DISTANCE
//      WATER -> Field.FIELD_VOLUME
        SLEEP_ASLEEP -> SleepStageRecord::class
        SLEEP_AWAKE -> SleepStageRecord::class
        SLEEP_IN_BED -> SleepStageRecord::class
//      WORKOUT -> Field.FIELD_ACTIVITY
//      BODY_FAT_PERCENTAGE -> Field.FIELD_PERCENTAGE
//      HEIGHT -> Field.FIELD_HEIGHT
//      WEIGHT -> Field.FIELD_WEIGHT
//      ACTIVE_ENERGY_BURNED -> Field.FIELD_CALORIES
        else -> throw IllegalArgumentException("Unsupported dataType: $type")
    }
}

fun keyToHealthConnectDataType(type: String): KClass<out Record> {
    return when (type) {
        STEPS -> StepsRecord::class
        HEART_RATE -> HeartRateRecord::class
//        BODY_FAT_PERCENTAGE -> BodyFatRecord::class
//        HEIGHT -> HeightRecord::class
//        WEIGHT -> WeightRecord::class
//        BODY_TEMPERATURE -> BodyTemperatureRecord::class
//        BLOOD_PRESSURE_SYSTOLIC -> BloodPressureRecord::class
//        BLOOD_PRESSURE_DIASTOLIC -> BloodPressureRecord::class
//        BLOOD_OXYGEN -> OxygenSaturationRecord::class
//        BLOOD_GLUCOSE -> BloodGlucoseRecord::class
//
//        WATER -> HydrationRecord::class
//        AGGREGATE_STEP_COUNT -> DataType.AGGREGATE_STEP_COUNT_DELTA
//        ACTIVE_ENERGY_BURNED -> DataType.TYPE_CALORIES_EXPENDED
//        MOVE_MINUTES -> DataType.TYPE_MOVE_MINUTES
//        DISTANCE_DELTA -> DataType.TYPE_DISTANCE_DELTA
        SLEEP_ASLEEP -> SleepStageRecord::class
        SLEEP_AWAKE -> SleepStageRecord::class
        SLEEP_IN_BED -> SleepStageRecord::class
//        WORKOUT -> DataType.TYPE_ACTIVITY_SEGMENT
        else -> throw IllegalArgumentException("Unsupported dataType: $type")
    }
}