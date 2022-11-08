package es.usc.citius.servando.calendula.healthcareprovider.model;

public class ActiveMedCharacteristic {
    private final ActiveMedCodingSystemType codingSystem;
    private final String value;

    public ActiveMedCharacteristic(String codingSystem, String value) {
        this.codingSystem = ActiveMedCodingSystemType.forCoding(codingSystem);
        this.value = value;
    }

    public ActiveMedCharacteristic(ActiveMedCodingSystemType codingSystem, String value) {
        this.codingSystem = codingSystem;
        this.value = value;
    }

    public ActiveMedCodingSystemType getCodingSystem() {
        return codingSystem;
    }

    public String getValue() {
        return value;
    }
}
