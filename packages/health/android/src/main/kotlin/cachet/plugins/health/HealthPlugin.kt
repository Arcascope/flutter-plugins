package cachet.plugins.health

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.util.Log
import androidx.annotation.NonNull
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.platform.client.proto.DataProto
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.ActivityResultListener
import io.flutter.plugin.common.PluginRegistry.Registrar
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.*
import java.util.concurrent.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataPoint
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.data.HealthDataTypes
import com.google.android.gms.fitness.data.HealthFields
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.request.SessionReadRequest
import com.google.android.gms.fitness.result.DataReadResponse
import com.google.android.gms.fitness.result.SessionReadResponse
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener


class HealthPlugin(private var channel: MethodChannel? = null) : MethodCallHandler,
    ActivityResultListener, Result, ActivityAware, FlutterPlugin {
    private var result: Result? = null
    private var handler: Handler? = null
    private var activity: Activity? = null
    private var threadPoolExecutor: ExecutorService? = null
    private val mainScope = MainScope()

    private var useHealthConnect = false

    // keep the result temporary
    private var _result: Result? = null

    val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1111
    val MMOLL_2_MGDL = 18.0 // 1 mmoll= 18 mgdl

    override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, CHANNEL_NAME)
        channel?.setMethodCallHandler(this)
        threadPoolExecutor = Executors.newFixedThreadPool(4)
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel = null
        activity = null
        threadPoolExecutor!!.shutdown()
        threadPoolExecutor = null
        mainScope.cancel()
    }

    override fun success(p0: Any?) {
        handler?.post { result?.success(p0) }
    }

    override fun notImplemented() {
        handler?.post { result?.notImplemented() }
    }

    override fun error(
        errorCode: String, errorMessage: String?, errorDetails: Any?
    ) {
        handler?.post { result?.error(errorCode, errorMessage, errorDetails) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {

        if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                Log.i("FLUTTER_HEALTH", "Access Granted!")
                _result?.success(true)
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Log.i("FLUTTER_HEALTH", "Access Denied!")
                _result?.success(false)
            }
        }

        if (requestCode == HealthConnectPermissionActivity.RESULT) {
            if (resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "Access Granted!")
                _result?.success(true)
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Log.d(TAG, "Access Denied!")
                _result?.success(false)
            }
        }

        return false
    }

    private fun writeData(call: MethodCall, result: Result) {
        if (activity == null) {
            result.success(false)
            return
        }

        val type = call.argument<String>(DATA_TYPE_KEY)!!
        val startTime = call.argument<Long>(START_TIME)!!
        val endTime = call.argument<Long>(END_TIME)!!
        val value = call.argument<Float>(VALUE)!!

        // Look up data type and unit for the type key
        val dataType = keyToHealthConnectDataType(type)
        val field = getFieldHealthConnect(type)

        // todo write data to health connect
    }

    private fun getData(call: MethodCall, result: Result) {
        if (activity == null) {
            result.success(null)
            return
        }

        val type = call.argument<String>(DATA_TYPE_KEY)!!
        val startTime = call.argument<Long>(START_TIME)!!
        val endTime = call.argument<Long>(END_TIME)!!

        if(!useHealthConnect) {
            val dataType = keyToHealthDataType(type)
            val field = getField(type)

            val typesBuilder = FitnessOptions.builder()
            typesBuilder.addDataType(dataType)

            if (dataType == DataType.TYPE_SLEEP_SEGMENT) {
                typesBuilder.accessSleepSessions(FitnessOptions.ACCESS_READ)
            } else if (dataType == DataType.TYPE_ACTIVITY_SEGMENT) {
                typesBuilder.accessActivitySessions(FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
                    .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
            }

            val fitnessOptions = typesBuilder.build()
            val googleSignInAccount =
                GoogleSignIn.getAccountForExtension(activity!!.applicationContext, fitnessOptions)
            // Handle data types
            when (dataType) {
                DataType.TYPE_SLEEP_SEGMENT -> {
                    // request to the sessions for sleep data
                    val request = SessionReadRequest.Builder()
                        .setTimeInterval(startTime, endTime, TimeUnit.MILLISECONDS)
                        .enableServerQueries()
                        .readSessionsFromAllApps()
                        .includeSleepSessions()
                        .build()
                    Fitness.getSessionsClient(activity!!.applicationContext, googleSignInAccount)
                        .readSession(request)
                        .addOnSuccessListener(threadPoolExecutor!!, sleepDataHandler(type, result))
                        .addOnFailureListener(
                            errHandler(
                                result,
                                "There was an error getting the sleeping data!",
                            ),
                        )
                }
                else -> {
                    Fitness.getHistoryClient(activity!!.applicationContext, googleSignInAccount)
                        .readData(
                            DataReadRequest.Builder()
                                .read(dataType)
                                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                                .build(),
                        )
                        .addOnSuccessListener(
                            threadPoolExecutor!!,
                            dataHandler(dataType, field, result),
                        )
                        .addOnFailureListener(
                            errHandler(
                                result,
                                "There was an error getting the data!",
                            ),
                        )
                }
            }

            return
        }


        // Look up data type and unit for the type key
        // probably we need this later on with more types going to integrated
        val dataType = keyToHealthConnectDataType(type)
        val field = getFieldHealthConnect(type)

        val timeRangeFilter = TimeRangeFilter.between(
            Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime)
        )

        mainScope.launch {
            try {
                val healthConnectClient = HealthConnectClient.getOrCreate(activity!!)

                val resultInHash = if (field == SleepStageRecord::class) {
                    val sleepStageRecords = healthConnectClient.readAllRecords(
                        SleepStageRecord::class, timeRangeFilter
                    )

                    val sleepSessionRecords =
                        healthConnectClient.readAllRecords(field, timeRangeFilter)

                    handleSleepData(
                        type, sleepSessionRecords, sleepStageRecords
                    )
                } else {
                    dataHandlerHealthConnect(
                        healthConnectClient.readAllRecords(field, timeRangeFilter)
                    )
                }

                activity!!.runOnUiThread {
                    result.success(resultInHash)
                }
            } catch(e:Exception) {
                // send an empty list.
                result.success(listOf<HashMap<String,Any>>());
            }
        }
    }

    private fun getHealthDataValue(dataPoint: DataPoint, field: Field): Any {
        val value = dataPoint.getValue(field)
        // Conversion is needed because glucose is stored as mmoll in Google Fit;
        // while mgdl is used for glucose in this plugin.
        val isGlucose = field == HealthFields.FIELD_BLOOD_GLUCOSE_LEVEL
        return when (value.format) {
            Field.FORMAT_FLOAT -> if (!isGlucose) value.asFloat() else value.asFloat() * MMOLL_2_MGDL
            Field.FORMAT_INT32 -> value.asInt()
            Field.FORMAT_STRING -> value.asString()
            else -> Log.e("Unsupported format:", value.format.toString())
        }
    }

    private fun dataHandler(dataType: DataType, field: Field, result: Result) =
        OnSuccessListener { response: DataReadResponse ->
            // / Fetch all data points for the specified DataType
            val dataSet = response.getDataSet(dataType)
            // / For each data point, extract the contents and send them to Flutter, along with date and unit.
            val healthData = dataSet.dataPoints.mapIndexed { _, dataPoint ->
                return@mapIndexed hashMapOf(
                    "value" to getHealthDataValue(dataPoint, field),
                    "date_from" to dataPoint.getStartTime(TimeUnit.MILLISECONDS),
                    "date_to" to dataPoint.getEndTime(TimeUnit.MILLISECONDS),
                    "source_name" to (
                            dataPoint.originalDataSource.appPackageName
                                ?: (
                                        dataPoint.originalDataSource.device?.model
                                            ?: ""
                                        )
                            ),
                    "source_id" to dataPoint.originalDataSource.streamIdentifier,
                )
            }
            Handler(activity!!.mainLooper).run { result.success(healthData) }
        }

    private fun errHandler(result: Result, addMessage: String) = OnFailureListener { exception ->
        Handler(activity!!.mainLooper).run { result.success(null) }
        Log.w("FLUTTER_HEALTH::ERROR", addMessage)
        Log.w("FLUTTER_HEALTH::ERROR", exception.message ?: "unknown error")
        Log.w("FLUTTER_HEALTH::ERROR", exception.stackTrace.toString())
    }

    private fun sleepDataHandler(type: String, result: Result) =
        OnSuccessListener { response: SessionReadResponse ->
            val healthData: MutableList<Map<String, Any?>> = mutableListOf()
            for (session in response.sessions) {
                // Return sleep time in Minutes if requested ASLEEP data
                if (type == SLEEP_ASLEEP) {
                    healthData.add(
                        hashMapOf(
                            "value" to session.getEndTime(TimeUnit.MINUTES) - session.getStartTime(
                                TimeUnit.MINUTES,
                            ),
                            "date_from" to session.getStartTime(TimeUnit.MILLISECONDS),
                            "date_to" to session.getEndTime(TimeUnit.MILLISECONDS),
                            "unit" to "MINUTES",
                            "source_name" to session.appPackageName,
                            "source_id" to session.identifier,
                        ),
                    )
                }

                if (type == SLEEP_IN_BED) {
                    val dataSets = response.getDataSet(session)

                    // If the sleep session has finer granularity sub-components, extract them:
                    if (dataSets.isNotEmpty()) {
                        for (dataSet in dataSets) {
                            for (dataPoint in dataSet.dataPoints) {
                                // searching OUT OF BED data
                                if (dataPoint.getValue(Field.FIELD_SLEEP_SEGMENT_TYPE)
                                        .asInt() != 3
                                ) {
                                    healthData.add(
                                        hashMapOf(
                                            "value" to dataPoint.getEndTime(TimeUnit.MINUTES) - dataPoint.getStartTime(
                                                TimeUnit.MINUTES,
                                            ),
                                            "date_from" to dataPoint.getStartTime(TimeUnit.MILLISECONDS),
                                            "date_to" to dataPoint.getEndTime(TimeUnit.MILLISECONDS),
                                            "unit" to "MINUTES",
                                            "source_name" to (
                                                    dataPoint.originalDataSource.appPackageName
                                                        ?: (
                                                                dataPoint.originalDataSource.device?.model
                                                                    ?: "unknown"
                                                                )
                                                    ),
                                            "source_id" to dataPoint.originalDataSource.streamIdentifier,
                                        ),
                                    )
                                }
                            }
                        }
                    } else {
                        healthData.add(
                            hashMapOf(
                                "value" to session.getEndTime(TimeUnit.MINUTES) - session.getStartTime(
                                    TimeUnit.MINUTES,
                                ),
                                "date_from" to session.getStartTime(TimeUnit.MILLISECONDS),
                                "date_to" to session.getEndTime(TimeUnit.MILLISECONDS),
                                "unit" to "MINUTES",
                                "source_name" to session.appPackageName,
                                "source_id" to session.identifier,
                            ),
                        )
                    }
                }

                if (type == SLEEP_AWAKE) {
                    val dataSets = response.getDataSet(session)
                    for (dataSet in dataSets) {
                        for (dataPoint in dataSet.dataPoints) {
                            // searching SLEEP AWAKE data
                            if (dataPoint.getValue(Field.FIELD_SLEEP_SEGMENT_TYPE).asInt() == 1) {
                                healthData.add(
                                    hashMapOf(
                                        "value" to dataPoint.getEndTime(TimeUnit.MINUTES) - dataPoint.getStartTime(
                                            TimeUnit.MINUTES,
                                        ),
                                        "date_from" to dataPoint.getStartTime(TimeUnit.MILLISECONDS),
                                        "date_to" to dataPoint.getEndTime(TimeUnit.MILLISECONDS),
                                        "unit" to "MINUTES",
                                        "source_name" to (
                                                dataPoint.originalDataSource.appPackageName
                                                    ?: (
                                                            dataPoint.originalDataSource.device?.model
                                                                ?: "unknown"
                                                            )
                                                ),
                                        "source_id" to dataPoint.originalDataSource.streamIdentifier,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            Handler(activity!!.mainLooper).run { result.success(healthData) }
        }

    private fun handleSleepData(
        type: String,
        // todo: do more research on sleepSessionRecords
        // in my understanding sleepSession includes all the stages of sleeps
        sleepSessionRecords: MutableList<out Record>,
        sleepStageRecords: MutableList<SleepStageRecord>
    ): List<HashMap<String, Any>> {
        when (type) {
            SLEEP_ASLEEP -> {
                return sleepStageRecords.mapNotNull {
                    if (it.stage == SleepStageRecord.STAGE_TYPE_DEEP) {
                        it.toHashMap()
                    } else {
                        null

                    }
                }
            }
            SLEEP_AWAKE -> {
                return sleepStageRecords.mapNotNull {
                    if (it.stage == SleepStageRecord.STAGE_TYPE_OUT_OF_BED) {
                        it.toHashMap()
                    } else {
                        null

                    }
                }
            }
            SLEEP_IN_BED -> {
                return sleepStageRecords.mapNotNull {
                    if (it.stage == SleepStageRecord.STAGE_TYPE_SLEEPING) {
                        it.toHashMap()
                    } else {
                        null

                    }
                }
            }
            else -> return emptyList()
        }

    }

    private fun dataHandlerHealthConnect(
        result: MutableList<out Record>
    ): List<java.util.HashMap<String, Any>> {
        return result.map {
            when (it) {
                is StepsRecord -> it.toHashMap()
                is HeartRateRecord -> it.toHashMap()
                is SleepStageRecord -> it.toHashMap()
                else -> throw IllegalArgumentException("Unsupported dataType: $result")
            }
        }
    }

    private fun parseCallArguments(call: MethodCall): Pair<List<String>, List<Int>> {
        val args = call.arguments as HashMap<*, *>
        val types = (args["types"] as? ArrayList<*>)?.filterIsInstance<String>()
        val permissions = (args["permissions"] as? ArrayList<*>)?.filterIsInstance<Int>()

        assert(types != null)
        assert(permissions != null)
        assert(types!!.count() == permissions!!.count())

        return Pair(types, permissions)
    }

    private fun hasPermissions(call: MethodCall, result: Result) {
        val arguments = parseCallArguments(call)

        if (activity == null) {
            result.success(false)
            return
        }

        mainScope.launch {
            val hasPermission = HealthConnectPermissionActivity.hasAllRequiredPermissions(
                activity!!,
                arguments
            )

            if(hasPermission) {
                result.success(true)
            } else {
                result.success(false)
            }
        }
    }

    private fun callToHealthTypes(call: MethodCall): FitnessOptions {
        val typesBuilder = FitnessOptions.builder()
        val args = call.arguments as HashMap<*, *>
        val types = (args["types"] as? ArrayList<*>)?.filterIsInstance<String>()
        val permissions = (args["permissions"] as? ArrayList<*>)?.filterIsInstance<Int>()

        assert(types != null)
        assert(permissions != null)
        assert(types!!.count() == permissions!!.count())

        for ((i, typeKey) in types.withIndex()) {
            val access = permissions[i]
            val dataType = keyToHealthDataType(typeKey)
            when (access) {
                0 -> typesBuilder.addDataType(dataType, FitnessOptions.ACCESS_READ)
                1 -> typesBuilder.addDataType(dataType, FitnessOptions.ACCESS_WRITE)
                2 -> {
                    typesBuilder.addDataType(dataType, FitnessOptions.ACCESS_READ)
                    typesBuilder.addDataType(dataType, FitnessOptions.ACCESS_WRITE)
                }
                else -> throw IllegalArgumentException("Unknown access type $access")
            }
            if (typeKey == SLEEP_ASLEEP || typeKey == SLEEP_AWAKE || typeKey == SLEEP_IN_BED) {
                typesBuilder.accessSleepSessions(FitnessOptions.ACCESS_READ)
                when (access) {
                    0 -> typesBuilder.accessSleepSessions(FitnessOptions.ACCESS_READ)
                    1 -> typesBuilder.accessSleepSessions(FitnessOptions.ACCESS_WRITE)
                    2 -> {
                        typesBuilder.accessSleepSessions(FitnessOptions.ACCESS_READ)
                        typesBuilder.accessSleepSessions(FitnessOptions.ACCESS_WRITE)
                    }
                    else -> throw IllegalArgumentException("Unknown access type $access")
                }
            }
            if (typeKey == WORKOUT) {
                when (access) {
                    0 -> typesBuilder.accessActivitySessions(FitnessOptions.ACCESS_READ)
                    1 -> typesBuilder.accessActivitySessions(FitnessOptions.ACCESS_WRITE)
                    2 -> {
                        typesBuilder.accessActivitySessions(FitnessOptions.ACCESS_READ)
                        typesBuilder.accessActivitySessions(FitnessOptions.ACCESS_WRITE)
                    }
                    else -> throw IllegalArgumentException("Unknown access type $access")
                }
            }
        }
        return typesBuilder.build()
    }

    private fun getField(type: String): Field {
        return when (type) {
            BODY_FAT_PERCENTAGE -> Field.FIELD_PERCENTAGE
            HEIGHT -> Field.FIELD_HEIGHT
            WEIGHT -> Field.FIELD_WEIGHT
            STEPS -> Field.FIELD_STEPS
            ACTIVE_ENERGY_BURNED -> Field.FIELD_CALORIES
            HEART_RATE -> Field.FIELD_BPM
            BODY_TEMPERATURE -> HealthFields.FIELD_BODY_TEMPERATURE
            BLOOD_PRESSURE_SYSTOLIC -> HealthFields.FIELD_BLOOD_PRESSURE_SYSTOLIC
            BLOOD_PRESSURE_DIASTOLIC -> HealthFields.FIELD_BLOOD_PRESSURE_DIASTOLIC
            BLOOD_OXYGEN -> HealthFields.FIELD_OXYGEN_SATURATION
            BLOOD_GLUCOSE -> HealthFields.FIELD_BLOOD_GLUCOSE_LEVEL
            MOVE_MINUTES -> Field.FIELD_DURATION
            DISTANCE_DELTA -> Field.FIELD_DISTANCE
            WATER -> Field.FIELD_VOLUME
            SLEEP_ASLEEP -> Field.FIELD_SLEEP_SEGMENT_TYPE
            SLEEP_AWAKE -> Field.FIELD_SLEEP_SEGMENT_TYPE
            SLEEP_IN_BED -> Field.FIELD_SLEEP_SEGMENT_TYPE
            WORKOUT -> Field.FIELD_ACTIVITY
            else -> throw IllegalArgumentException("Unsupported dataType: $type")
        }
    }

    private fun keyToHealthDataType(type: String): DataType {
        return when (type) {
            BODY_FAT_PERCENTAGE -> DataType.TYPE_BODY_FAT_PERCENTAGE
            HEIGHT -> DataType.TYPE_HEIGHT
            WEIGHT -> DataType.TYPE_WEIGHT
            STEPS -> DataType.TYPE_STEP_COUNT_DELTA
            AGGREGATE_STEP_COUNT -> DataType.AGGREGATE_STEP_COUNT_DELTA
            ACTIVE_ENERGY_BURNED -> DataType.TYPE_CALORIES_EXPENDED
            HEART_RATE -> DataType.TYPE_HEART_RATE_BPM
            BODY_TEMPERATURE -> HealthDataTypes.TYPE_BODY_TEMPERATURE
            BLOOD_PRESSURE_SYSTOLIC -> HealthDataTypes.TYPE_BLOOD_PRESSURE
            BLOOD_PRESSURE_DIASTOLIC -> HealthDataTypes.TYPE_BLOOD_PRESSURE
            BLOOD_OXYGEN -> HealthDataTypes.TYPE_OXYGEN_SATURATION
            BLOOD_GLUCOSE -> HealthDataTypes.TYPE_BLOOD_GLUCOSE
            MOVE_MINUTES -> DataType.TYPE_MOVE_MINUTES
            DISTANCE_DELTA -> DataType.TYPE_DISTANCE_DELTA
            WATER -> DataType.TYPE_HYDRATION
            SLEEP_ASLEEP -> DataType.TYPE_SLEEP_SEGMENT
            SLEEP_AWAKE -> DataType.TYPE_SLEEP_SEGMENT
            SLEEP_IN_BED -> DataType.TYPE_SLEEP_SEGMENT
            WORKOUT -> DataType.TYPE_ACTIVITY_SEGMENT
            else -> throw IllegalArgumentException("Unsupported dataType: $type")
        }
    }

    /// Called when the "requestAuthorization" is invoked from Flutter
    private fun requestAuthorization(call: MethodCall, result: Result) {

//        Fitness.getConfigClient(activity!!, GoogleSignIn.getLastSignedInAccount(activity!!)!!)
//            .disableFit()
//            .addOnSuccessListener {
//                Log.i("Health", "Disabled Google Fit")
//                result.success(true)
//            }
//            .addOnFailureListener { e ->
//                Log.w("Health", "There was an error disabling Google Fit", e)
//                result.success(false)
//            }
//        return

        if (activity == null) {
            result.success(false)
            return
        }

        val arguments = parseCallArguments(call)

        _result = result

        if(!useHealthConnect) {
            val optionsToRegister = callToHealthTypes(call)

            val isGranted = false

            if (!isGranted && activity != null) {
                GoogleSignIn.requestPermissions(
                    activity!!,
                    GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                    GoogleSignIn.getLastSignedInAccount(activity!!.applicationContext),
                    optionsToRegister,
                )
            } else { // / Permission already granted
                result?.success(true)
            }
            return
        }

        activity?.run {
            val appContext = this.applicationContext

            mainScope.launch {
                if (HealthConnectPermissionActivity.hasAllRequiredPermissions(
                        appContext,
                        arguments
                    )
                ) {
                    result.success(true)
                } else {
                    startActivityForResult(
                        HealthConnectPermissionActivity.open(
                            appContext, ArrayList(arguments.first), ArrayList(arguments.second)
                        ), HealthConnectPermissionActivity.RESULT
                    )
                }
            }
        }
    }

    private fun getTotalStepsInInterval(call: MethodCall, result: Result) {
        val startTime = call.argument<Long>(START_TIME)!!
        val endTime = call.argument<Long>(END_TIME)!!

        val activity = activity ?: return

        val field = getFieldHealthConnect(STEPS)

        val timeRangeFilter = TimeRangeFilter.between(
            Instant.ofEpochMilli(startTime), Instant.ofEpochMilli(endTime)
        )

        mainScope.launch {
            val healthConnectClient = HealthConnectClient.getOrCreate(activity)
            val stepRecords =
                healthConnectClient.readRecords(ReadRecordsRequest(field, timeRangeFilter))

            var totalCount = 0L
            stepRecords.records.forEach {
                if (it is StepsRecord) {
                    totalCount += it.count
                }
            }

            activity.runOnUiThread { result.success(totalCount) }
        }
    }

    private fun isHealthConnectExist(call: MethodCall, result: Result) {
        activity ?: result.success(false)
        val availabilityStatus = HealthConnectClient.getSdkStatus(activity!!)
        result.success(availabilityStatus == HealthConnectClient.SDK_AVAILABLE)
    }

    private fun useHealthConnect(call: MethodCall, result: Result) {
        useHealthConnect = true
        result.success(true)
    }

    private fun useGoogleFit(call: MethodCall, result: Result) {
        useHealthConnect = false
        result.success(true)
    }

    // Handle calls from the MethodChannel
    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "useHealthConnect" -> useHealthConnect(call, result)
            "useGoogleFit" -> useGoogleFit(call, result)
            "requestAuthorization" -> requestAuthorization(call, result)
            "getData" -> getData(call, result)
            "hasPermissions" -> hasPermissions(call, result)
            "getTotalStepsInInterval" -> getTotalStepsInInterval(call, result)
            "healthConnectExist" -> isHealthConnectExist(call, result)
//            todo: implement the rest of the functions
//            "writeData" -> writeData(call, result)
//            "writeWorkoutData" -> writeWorkoutData(call, result)
            else -> result.notImplemented()
        }
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        if (channel == null) {
            return
        }
        binding.addActivityResultListener(this)
        activity = binding.activity
    }

    override fun onDetachedFromActivityForConfigChanges() {
        onDetachedFromActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        if (channel == null) {
            return
        }
        activity = null
    }

    // This static function is optional and equivalent to onAttachedToEngine. It supports the old
    // pre-Flutter-1.12 Android projects. You are encouraged to continue supporting
    // plugin registration via this function while apps migrate to use the new Android APIs
    // post-flutter-1.12 via https://flutter.dev/go/android-project-migration.
    //
    // It is encouraged to share logic between onAttachedToEngine and registerWith to keep
    // them functionally equivalent. Only one of onAttachedToEngine or registerWith will be called
    // depending on the user's project. onAttachedToEngine or registerWith must both be defined
    // in the same class.
    companion object {
        @Suppress("unused")
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), CHANNEL_NAME)
            val plugin = HealthPlugin(channel)
            registrar.addActivityResultListener(plugin)
            channel.setMethodCallHandler(plugin)
        }

        const val DATA_TYPE_KEY = "dataTypeKey"
        const val START_TIME = "startTime"
        const val END_TIME = "endTime"
        const val VALUE = "value"
        const val MINUTES = "MINUTES"

        const val DATE_FROM = "date_from"
        const val DATE_TO = "date_to"
        const val SOURCE_NAME = "source_name"
        const val SOURCE_ID = "source_id"
        const val UNIT = "unit"

        const val HEALTH_CONNECT = "HealthConnect"

        private const val TAG = "FLUTTER_HEALTH"
        private const val SUCCESS_TAG = "$TAG::SUCCESS"
        private const val ERROR_TAG = "$TAG::ERROR"

        const val CHANNEL_NAME = "flutter_health"
    }
}
