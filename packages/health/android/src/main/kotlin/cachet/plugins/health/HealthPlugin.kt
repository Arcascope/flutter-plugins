package cachet.plugins.health

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.util.Log
import androidx.annotation.NonNull
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
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

class HealthPlugin(private var channel: MethodChannel? = null) : MethodCallHandler,
    ActivityResultListener, Result, ActivityAware, FlutterPlugin {
    private var result: Result? = null
    private var handler: Handler? = null
    private var activity: Activity? = null
    private var threadPoolExecutor: ExecutorService? = null
    private val mainScope = MainScope()

    // keep the result temporary
    private var _result: Result? = null

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
                    if (it.stage == SleepStageRecord.StageType.DEEP) {
                        it.toHashMap()
                    } else {
                        null

                    }
                }
            }
            SLEEP_AWAKE -> {
                return sleepStageRecords.mapNotNull {
                    if (it.stage == SleepStageRecord.StageType.OUT_OF_BED) {
                        it.toHashMap()
                    } else {
                        null

                    }
                }
            }
            SLEEP_IN_BED -> {
                return sleepStageRecords.mapNotNull {
                    if (it.stage == SleepStageRecord.StageType.SLEEPING) {
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

    /// Called when the "requestAuthorization" is invoked from Flutter
    private fun requestAuthorization(call: MethodCall, result: Result) {
        if (activity == null) {
            result.success(false)
            return
        }

        val arguments = parseCallArguments(call)

        _result = result

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
        result.success(HealthConnectClient.isAvailable(activity!!))
    }

    // Handle calls from the MethodChannel
    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
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
