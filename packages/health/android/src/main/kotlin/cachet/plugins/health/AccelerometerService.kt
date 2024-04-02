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
import java.time.LocalDateTime
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


        val helper = DaoMaster.DevOpenHelper(this, "12test2231", null)
        val db = helper.writableDatabase
        daoMaster = DaoMaster(db)

//        val helper: DaoMaster.DevOpenHelper = DevOpenHelper(this, "mydatabase", null)
//
//        db = helper.getWritableDatabase()
//        val daoMaster = DaoMaster(db)
//        val daoSession: DaoSession = daoMaster.newSession()
//
//        Realm.init(this)
//        val config: RealmConfiguration = RealmConfiguration.Builder().name("sampler") .schemaVersion(1)
//            .allowQueriesOnUiThread(true)
//            .allowWritesOnUiThread(true)
//            .build()
//        Realm.setDefaultConfiguration(config)

//        boxStore = MyObjectBox.builder().androidContext(this).build()

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

    var gravSensorVals: FloatArray? = null

    private val gravity = FloatArray(3)
    private val linear_acceleration = FloatArray(3)

    protected fun lowPass(input: FloatArray, output: FloatArray?): FloatArray? {
        if (output == null) return input
        for (i in input.indices) {
            output[i] = output[i] + 0.25f * (input[i] - output[i])
        }
        return output
    }

    override fun onSensorChanged(event: SensorEvent) {

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {

            val alpha = 0.8f

            gravity[0] = alpha * gravity[0] + (1 - alpha) * event.values[0]
            gravity[1] = alpha * gravity[1] + (1 - alpha) * event.values[1]
            gravity[2] = alpha * gravity[2] + (1 - alpha) * event.values[2]

            linear_acceleration[0] = event.values[0] - gravity[0]
            linear_acceleration[1] = event.values[1] - gravity[1]
            linear_acceleration[2] = event.values[2] - gravity[2]

            gravSensorVals = lowPass(event.values.clone(), gravSensorVals);

//            val x = gravSensorVals?.get(0);
//            val y = gravSensorVals?.get(1);
//            val z = gravSensorVals?.get(2);

//            val x = linear_acceleration?.get(0);
//            val y = linear_acceleration?.get(1);
//            val z = linear_acceleration?.get(2);

            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            val timestamp = LocalDateTime.now()
            val result = calculateR(x!!,y!!,z!!)

            dataStore.add(CalculationResult(timestamp, result))

            val currentMinute = timestamp.minute
            if (currentMinute != lastMinute) {
                val minuteData = dataStore.filter { it.timestamp.minute == currentMinute }.map { it.result }
                dataStore.clear()
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


//        val minuteAverageBox = boxStore.boxFor(MinuteAverage::class.java)
//

        val oneMonthAgo = LocalDateTimeConverter().convertToDatabaseValue(LocalDateTime.now().minusHours(1))
//
//        Realm.getDefaultInstance().executeTransaction(Realm.Transaction {
//            it.where(MinuteAverageRealm::class.java).lessThan("MinuteAverageRealm",oneMonthAgo).findAll().deleteAllFromRealm()
//        }).

//        minuteAverageBox.query().less(MinuteAverage_.timestamp, oneMonthAgo).build().remove()

//        val minuteAverageRealm = MinuteAverageRealm()
//        minuteAverageRealm.average = average;
//        minuteAverageRealm.timestamp =  LocalDateTimeConverter().convertToDatabaseValue(timestamp);
//        Realm.getDefaultInstance().insert(minuteAverageRealm)
//        minuteAverageBox.put(minuteAverage)

        val daoSession: DaoSession = daoMaster.newSession()

        val minuteAverage = MinuateAvergageJ()
        minuteAverage.average = average
        minuteAverage.timestamp = LocalDateTimeConverter().convertToDatabaseValue(timestamp)

        daoSession.minuateAvergageJDao.insert(minuteAverage)
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
        lateinit var daoMaster:DaoMaster;
    }
}