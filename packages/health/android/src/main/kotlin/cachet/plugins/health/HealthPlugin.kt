package cachet.plugins.health

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.util.Log
import androidx.annotation.NonNull
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ReadRecordsResponse
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
            val healthConnectClient = HealthConnectClient.getOrCreate(activity!!)

            val serializedResult = dataHandlerHealthConnect(
                healthConnectClient.readRecords(ReadRecordsRequest(field, timeRangeFilter))
            )

            activity!!.runOnUiThread { result.success(serializedResult) }
        }
    }


    private fun dataHandlerHealthConnect(
        result: ReadRecordsResponse<out Record>
    ): List<java.util.HashMap<String, Any>> {
       return result.records.map {
            when (it) {
                is StepsRecord -> it.toHashMap()
                is HeartRateRecord -> it.toHashMap()
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
        requestAuthorization(call, result)
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
            startActivityForResult(
                HealthConnectPermissionActivity.open(
                    this, ArrayList(arguments.first), ArrayList(arguments.second)
                ), HealthConnectPermissionActivity.RESULT
            )
        }
    }

    private fun getTotalStepsInInterval(call: MethodCall, result: Result) {
        val start = call.argument<Long>(START_TIME)!!
        val end = call.argument<Long>(END_TIME)!!

        val activity = activity ?: return

        val stepsDataType = keyToHealthConnectDataType(STEPS)
        val aggregatedDataType = keyToHealthConnectDataType(AGGREGATE_STEP_COUNT)

        // todo implement getTotalStepsInInterval
    }

    /// Handle calls from the MethodChannel
    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "requestAuthorization" -> requestAuthorization(call, result)
            "getData" -> getData(call, result)
            "hasPermissions" -> hasPermissions(call, result)
//            todo: implement the rest of the functions
//            "writeData" -> writeData(call, result)
//            "getTotalStepsInInterval" -> getTotalStepsInInterval(call, result)
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
