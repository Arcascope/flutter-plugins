package cachet.plugins.health

import org.greenrobot.greendao.annotation.Entity
import org.greenrobot.greendao.annotation.Id
import org.greenrobot.greendao.annotation.Unique
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Entity
class MinuteAverage {
    @Id(autoincrement = true)
    var timestamp: Long? = 0
    var average: Double = 0.0
}

class LocalDateTimeConverter {
     fun convertToDatabaseValue(localDateTime: LocalDateTime?): Long? {
        return localDateTime!!.atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
    }

     fun convertToEntityProperty(databaseValue: Long?): LocalDateTime? {
        return if (databaseValue == null) {
            null
        } else LocalDateTime.ofInstant(Instant.ofEpochMilli(databaseValue), ZoneId.systemDefault())
    }
}