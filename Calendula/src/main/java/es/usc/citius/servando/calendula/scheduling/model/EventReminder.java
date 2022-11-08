package es.usc.citius.servando.calendula.scheduling.model;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.joda.time.DateTime;

import es.usc.citius.servando.calendula.persistence.Patient;

/**
 * Reminder for an event or a group of events of the same type
 * that occur at the same time (Medications at breakfast)
 */
@DatabaseTable(tableName = "EventReminders")
public class EventReminder {

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_DATE_TIME = "DateTime";
    public static final String COLUMN_EVENT_TYPE = "EventType";
    public static final String COLUMN_PATIENT = "Patient";
    //public static final String COLUMN_CANCELLED = "Cancelled";
    public static final String COLUMN_AUTO_REPEAT = "AutoRepeat";
    public static final String COLUMN_NEXT_TIME = "NextTime";

    @DatabaseField(columnName = COLUMN_ID, generatedId = true)
    private Long id;

    @DatabaseField(columnName = COLUMN_DATE_TIME)
    private DateTime dateTime;

    @DatabaseField(columnName = COLUMN_NEXT_TIME)
    private DateTime nextTime;

    @DatabaseField(columnName = COLUMN_EVENT_TYPE)
    private EventType eventType;

//    @DatabaseField(columnName = COLUMN_CANCELLED)
//    private boolean cancelled;

    @DatabaseField(columnName =COLUMN_AUTO_REPEAT)
    private boolean autoRepeat;

    @DatabaseField(columnName = COLUMN_PATIENT, foreign = true, foreignAutoRefresh = true)
    private Patient patient;

    public EventReminder(){}

    public EventReminder(DateTime dateTime, EventType eventType) {
        this.dateTime = dateTime;
        this.eventType = eventType;
    }

    public DateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(DateTime dateTime) {
        this.dateTime = dateTime;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public boolean autoRepeat() {
        return autoRepeat;
    }

    public void setAutoRepeat(boolean autoRepeat) {
        this.autoRepeat = autoRepeat;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DateTime getNextTime() {
        return nextTime;
    }

    public void setNextTime(DateTime nextTime) {
        this.nextTime = nextTime;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EventReminder reminder = (EventReminder) o;

        if (autoRepeat != reminder.autoRepeat) return false;
        if (id != null ? !id.equals(reminder.id) : reminder.id != null) return false;
        if (dateTime != null ? !dateTime.equals(reminder.dateTime) : reminder.dateTime != null)
            return false;
        if (eventType != reminder.eventType) return false;
        return patient != null ? patient.equals(reminder.patient) : reminder.patient == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (dateTime != null ? dateTime.hashCode() : 0);
        result = 31 * result + (eventType != null ? eventType.hashCode() : 0);
        result = 31 * result + (autoRepeat ? 1 : 0);
        result = 31 * result + (patient != null ? patient.hashCode() : 0);
        return result;
    }
}
