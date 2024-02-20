package cachet.plugins.health


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.annotation.RequiresApi
import io.objectbox.BoxStore
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.sqrt

class AccelerometerService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var accelerometer: Sensor
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    fun calculateR(x: Float, y: Float, z: Float): Double {
        val intermediateResult = x * x + y * y + z * z
        return sqrt(intermediateResult.toDouble())
    }

    override fun onCreate() {
        super.onCreate()

        boxStore = MyObjectBox.builder().androidContext(this).build()

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)!!

        // Acquire a partial wake lock if needed
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
            "MyApp::AccelerometerWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundService()

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
        wakeLock?.release()
    }

    data class CalculationResult(val timestamp: LocalDateTime, val result: Double)

    private val dataStore = mutableListOf<CalculationResult>()
    private var lastMinute = -1

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val timestamp = LocalDateTime.now()
            val result = calculateR(x,y,z)

            dataStore.add(CalculationResult(timestamp, result))

            val currentMinute = timestamp.minute
            if (currentMinute != lastMinute) {
                val minuteData = dataStore.filter { it.timestamp.minute == currentMinute }.map { it.result }
                if (minuteData.isNotEmpty()) {
                    val average = minuteData.average()
                    storeAndCalculateAverage(average, timestamp)
                    Log.d("ARCA", "Average for minute ${timestamp.format(DateTimeFormatter.ofPattern("HH:mm"))}: $average");
                }
                lastMinute = currentMinute
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Handle accuracy changes if needed
    }

    private fun storeAndCalculateAverage(average: Double, timestamp: LocalDateTime) {
        val minuteAverageBox = boxStore.boxFor(MinuteAverage::class.java)

        val oneMonthAgo = LocalDateTime.now().minusMonths(1).atOffset(ZoneOffset.UTC).toEpochSecond()
        minuteAverageBox.query().less(MinuteAverage_.timestamp, oneMonthAgo).build().remove()

        val minuteAverage = MinuteAverage(average = average, timestamp = timestamp);
        minuteAverageBox.put(minuteAverage)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun startForegroundService() {
        val channelId = "my_foreground_service_channel"
        createNotificationChannel(channelId)

        val notification = Notification.Builder(this, channelId)
            .setContentTitle("Accelerometer Service")
            .setContentText("Collecting accelerometer data...")
            .build()

        startForeground(1, notification)
    }

    private fun createNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Accelerometer Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }


    companion object {
        lateinit var boxStore: BoxStore
    }
}