package cachet.plugins.health

import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.converter.PropertyConverter
import java.time.LocalDateTime
import java.time.ZoneOffset


@Entity
data class MinuteAverage(
    @Id var id: Long = 0,
    @Convert(converter = LocalDateTimeConverter::class, dbType = Long::class)
    val timestamp: LocalDateTime,
    val average: Double

)

class LocalDateTimeConverter : PropertyConverter<LocalDateTime?, Long?> {
    override fun convertToEntityProperty(databaseValue: Long?): LocalDateTime? {
        return if (databaseValue == null) {
            null
        } else {
            LocalDateTime.ofEpochSecond(databaseValue, 0, ZoneOffset.UTC)
        }
    }

    override fun convertToDatabaseValue(entityProperty: LocalDateTime?): Long? {
        return entityProperty?.toEpochSecond(ZoneOffset.UTC)
    }
}