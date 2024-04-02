package cachet.plugins.health;

import org.greenrobot.greendao.annotation.Entity;
import org.greenrobot.greendao.annotation.Id;
import org.greenrobot.greendao.annotation.Generated;

@Entity
public class MinuateAvergageJ {
    @Id(autoincrement = true)
    private Long id;
    private Long timestamp;
    private double average;
    @Generated(hash = 553077035)
    public MinuateAvergageJ(Long id, Long timestamp, double average) {
        this.id = id;
        this.timestamp = timestamp;
        this.average = average;
    }
    @Generated(hash = 1498158604)
    public MinuateAvergageJ() {
    }
    public Long getId() {
        return this.id;
    }
    public void setId(Long id) {
        this.id = id;
    }
    public Long getTimestamp() {
        return this.timestamp;
    }
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }
    public double getAverage() {
        return this.average;
    }
    public void setAverage(double average) {
        this.average = average;
    }
}
