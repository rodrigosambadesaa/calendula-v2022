package es.usc.citius.servando.calendula.scheduling.model;


import android.os.Bundle;
import androidx.annotation.Nullable;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import org.joda.time.DateTime;

import es.usc.citius.servando.calendula.persistence.Patient;
import es.usc.citius.servando.calendula.persistence.typeSerializers.BundlePersister;
import es.usc.citius.servando.calendula.persistence.typeSerializers.DateTimePersister;


@DatabaseTable(tableName = "EventInstances")
public class EventInstance {

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_REF = "Ref";
    public static final String COLUMN_PATIENT = "Patient";
    public static final String COLUMN_DATE_TIME = "DateTime";
    public static final String COLUMN_EVENT_TYPE = "EventType";
    public static final String COLUMN_COMPLETED = "Completed";
    public static final String COLUMN_CANCELLED = "Cancelled";
    public static final String COLUMN_COMPLETED_DATETIME = "CompletedDateTime";
    public static final String COLUMN_OFFSET = "Offset";
    public static final String COLUMN_PARAMS = "Params";

    public static final String PARAM_DOSE = "Dose";

    @DatabaseField(columnName = COLUMN_ID, generatedId = true)
    private Long id;

    @DatabaseField(columnName = COLUMN_REF)
    private Long ref;

    @DatabaseField(columnName = COLUMN_PATIENT, foreign = true, foreignAutoRefresh = true)
    private Patient patient;

    @DatabaseField(columnName = COLUMN_EVENT_TYPE)
    private EventType type;

    @DatabaseField(columnName = COLUMN_DATE_TIME, persisterClass = DateTimePersister.class)
    private DateTime time;

    @DatabaseField(columnName = COLUMN_PARAMS, persisterClass = BundlePersister.class)
    private Bundle params;

    @DatabaseField(columnName = COLUMN_COMPLETED_DATETIME)
    private DateTime completedAt;

    @DatabaseField(columnName = COLUMN_COMPLETED)
    private boolean completed;

    @DatabaseField(columnName = COLUMN_CANCELLED)
    private boolean cancelled;

    @DatabaseField(columnName = COLUMN_OFFSET)
    private EventOffset offset;

    public EventInstance(){}

    // other properties
    public EventInstance(DateTime time, EventType type) {
        this.time = time;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRef() {
        return ref;
    }

    public void setRef(Long ref) {
        this.ref = ref;
    }

    public DateTime getTime() {
        return time;
    }

    public void setTime(DateTime time) {
        this.time = time;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public boolean completed() {
        return completed;
    }

    public void setCompleted(Boolean completed) {
        this.completed = completed;
    }

    public Patient getPatient() {
        return patient;
    }

    public void setPatient(Patient patient) {
        this.patient = patient;
    }

    public DateTime completedAt() {
        return completedAt;
    }

    public void setCompletedAt(DateTime completedDatetime) {
        this.completedAt= completedDatetime;
    }

    public boolean cancelled() {
        return cancelled;
    }

    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public EventOffset getOffset() {
        return offset;
    }

    public void setOffset(EventOffset offset) {
        this.offset = offset;
    }

    public Bundle getParams() { return params; }

    // params
    public void removeParam(String key) {
        initBundle();
        params.remove(key);
    }

    public void addParam(String key, String value) {
        initBundle();
        params.putString(key,value);
    }

    public void addParam(String key, Long value) {
        initBundle();
        params.putString(key,String.valueOf(value));
    }

    public void addParam(String key, Double value) {
        initBundle();
        params.putString(key,String.valueOf(value));
    }

    public boolean getBooleanParam(String key) {
        return Boolean.valueOf(params.getString(key));
    }

    public long getLongParam(String key) {
        return Long.valueOf(params.getString(key));
    }

    public double getDoubleParam(String key) {
        return Double.valueOf(params.getString(key));
    }

    public String getStringParam(@Nullable String key) {
        return params.getString(key);
    }

    private void initBundle(){
        if(params==null){
            params = new Bundle();
        }
    }

    public enum EventOffset {
        NONE,
        AFTER,
        BEFORE
    }

    @Override
    public String toString() {
        return "EventInstance{" +
                "id=" + id +
                ", ref=" + ref +
                ", patient=" + patient.getName() +
                ", type=" + type +
                ", time=" + time +
                ", completedAt=" + completedAt +
                ", completed=" + completed +
                ", params=" + params.toString() +
                '}';
    }
}
