package es.usc.citius.servando.calendula.healthcareprovider.fhir

import org.hl7.fhir.dstu3.model.*

/**
 * Utility class to pass around the content of the response in a more accessible manner.
 *
 *
 * @param code Error code, if there was any.
 * @param header Header of the response.
 * @param medicationDispenses Map of [MedicationDispense]s in the response, indexed by id.
 * @param medicationRequests Map of [MedicationRequest]s in the response, indexed by id.
 * @param medications Map of [Medication]s in the response, indexed by id.
 * @param patient Patient in the response.
 * @param organization Organization in the response.
 *
 */
data class ResponseVO(var code: Int,
                      var header: MessageHeader?,
                      var medicationDispenses: Map<String, MedicationDispense>?,
                      var medicationRequests: Map<String, MedicationRequest>?,
                      var medications: Map<String, Medication>?,
                      var patient: Patient?,
                      var organization: Organization?) {
    constructor() : this(-1, null, null, null, null, null, null)
}