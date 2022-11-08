package es.usc.citius.servando.calendula.scheduling.model;


public enum EventType {

    MEDICATION_INTAKE("MEDICATION_INTAKE"),
    PHARMACY_REMINDER("PHARMACY_REMINDER"),
    STOCK_REMINDER("STOCK_REMINDER");

    public final String name;

    EventType(String name) {
        this.name = name;
    }

}
