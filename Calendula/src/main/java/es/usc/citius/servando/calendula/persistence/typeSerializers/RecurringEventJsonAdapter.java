/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2016 CITIUS - USC
 *
 *    Calendula is free software; you can redistribute it and/or modify
 *    it under the terms of the GNU General Public License as published by
 *    the Free Software Foundation; either version 3 of the License, or
 *    (at your option) any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *    GNU General Public License for more details.
 *
 *    You should have received a copy of the GNU General Public License
 *    along with this software.  If not, see <http://www.gnu.org/licenses/>.
 */

package es.usc.citius.servando.calendula.persistence.typeSerializers;


import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException;
import org.dmfs.rfc5545.recur.RecurrenceRule;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.io.IOException;

import es.usc.citius.servando.calendula.scheduling.model.EventInstance;
import es.usc.citius.servando.calendula.scheduling.model.recur.DailyFixedTime;
import es.usc.citius.servando.calendula.scheduling.model.recur.RecurringEvent;


public class RecurringEventJsonAdapter extends TypeAdapter<RecurringEvent> {

    private static DateTimeFormatter timeFormat = DateTimeFormat.forPattern("HH:mm");
    private static DateTimeFormatter dateFormat = ISODateTimeFormat.dateTimeNoMillis();

    @Override
    public void write(JsonWriter out, RecurringEvent evt) throws IOException {
        out.beginObject();
        // write rule
        out.name("rule").value(evt.recurrenceRule().toString());
        // start and end
        if (evt.hasStart()) {
            out.name("start").value(evt.getStartDateTime().toString(dateFormat));
        }
        if (evt.hasEnd()) {
            out.name("end").value(evt.getEndDateTime().toString(dateFormat));
        }
        if (evt.isCyclic()) {
            out.name("cycleActive").value(evt.getCycleActiveDays());
            out.name("cycleInactive").value(evt.getCycleInactiveDays());
        }
        if (evt.hasDailyFixedTimes()) {
            out.name("dailyTimes").beginArray();

            for (DailyFixedTime d : evt.getDailyFixedTimes()) {
                out.beginObject();
                out.name("refType").value(d.getReferenceType().toString());
                if (d.getReference() != null) {
                    out.name("ref").value(d.getReference());
                } else {
                    out.name("time").value(d.getTime().toString(timeFormat));
                }
                if (d.offset() != null) {
                    out.name("offset").value(d.offset().name());
                }
                out.endObject();
            }
            out.endArray();
        }
        out.endObject();
    }

    @Override
    public RecurringEvent read(JsonReader in) throws IOException {
        RecurringEvent event = null;
        try {
            event = new RecurringEvent();
            in.beginObject();
            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "rule":
                        event.setRecurrence(new RecurrenceRule(in.nextString()));
                        break;
                    case "start":
                        event.setStartDateTime(dateFormat.parseDateTime(in.nextString()));
                        break;
                    case "end":
                        event.setEndDateTime(dateFormat.parseDateTime(in.nextString()));
                        break;
                    case "cycleActive":
                        event.setCycleActiveDays(in.nextInt());
                        break;
                    case "cycleInactive":
                        event.setCycleInactiveDays(in.nextInt());
                        break;
                    case "dailyTimes":
                        in.beginArray();
                        while (in.hasNext()) {
                            DailyFixedTime t = new DailyFixedTime();
                            in.beginObject();
                            while (in.hasNext()) {
                                switch (in.nextName()) {
                                    case "refType":
                                        t.setReferenceType(DailyFixedTime.ReferenceType.valueOf(in.nextString()));
                                        break;
                                    case "ref":
                                        t.setReference(in.nextLong());
                                        break;
                                    case "time":
                                        t.setTime(timeFormat.parseLocalTime(in.nextString()));
                                        break;
                                    case "offset":
                                        t.setOffset(EventInstance.EventOffset.valueOf(in.nextString()));
                                        break;
                                    default:
                                        in.skipValue();
                                        break;
                                }
                            }
                            in.endObject();
                            event.addDailyFixedTime(t);
                        }
                        in.endArray();
                        break;
                    default:
                        in.skipValue();
                        break;
                }
            }
            in.endObject();
        } catch (InvalidRecurrenceRuleException e) {
            e.printStackTrace();
        }
        return event;
    }
}
