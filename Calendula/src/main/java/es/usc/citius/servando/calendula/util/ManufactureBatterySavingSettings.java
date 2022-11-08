package es.usc.citius.servando.calendula.util;

import android.content.Intent;

public class ManufactureBatterySavingSettings {
    public String manufacturerName;
    public int minAPIVersion;
    public int maxAPIVersion;
    public Intent intent;

    public ManufactureBatterySavingSettings(String manufacturerName, int minAPIVersion, int maxAPIVersion, Intent intent) {
        this.manufacturerName = manufacturerName;
        this.minAPIVersion = minAPIVersion;
        this.maxAPIVersion = maxAPIVersion;
        this.intent = intent;
    }
}
