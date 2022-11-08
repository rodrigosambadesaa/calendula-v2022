package es.usc.citius.servando.calendula.scheduling.model;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;

import java.util.Collection;


/**
 * Interface for event
 */
public interface EventProvider {

    EventType getEventType();

    Collection<EventInstance> getEventsBetween(DateTime start, DateTime end);

    Boolean hasEventsAt(LocalDate date);
}
