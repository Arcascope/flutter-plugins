package cachet.plugins.health

import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.converter.PropertyConverter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId


@Entity
data class MinuteAverage(
    @Id var id: Long = 0,
    @Convert(converter = LocalDateTimeConverter::class, dbType = Long::class)
    val timestamp: LocalDateTime,
    val average: Double

)

class LocalDateTimeConverter : PropertyConverter<LocalDateTime?, Long?> {
    override fun convertToDatabaseValue(localDateTime: LocalDateTime?): Long? {
        return localDateTime!!.atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli();
    }

    override fun convertToEntityProperty(databaseValue: Long?): LocalDateTime? {
        return if (databaseValue == null) {
            null
        } else LocalDateTime.ofInstant(Instant.ofEpochMilli(databaseValue), ZoneId.systemDefault())
    }
}