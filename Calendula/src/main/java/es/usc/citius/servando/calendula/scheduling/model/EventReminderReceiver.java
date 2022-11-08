package es.usc.citius.servando.calendula.scheduling.model;

import android.content.Context;



/**
 * Interface for event receiver
 */
public interface EventReminderReceiver {

    void onEvent(Context context, EventReminder e);

    boolean autoRepeat();

}
