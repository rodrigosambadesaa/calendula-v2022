package es.usc.citius.servando.calendula.healthcareprovider.fhir

import android.os.Build
import android.util.ArrayMap
import es.usc.citius.servando.calendula.CalendulaApp
import es.usc.citius.servando.calendula.database.DB
import es.usc.citius.servando.calendula.drugdb.model.persistence.HomogeneousGroup
import es.usc.citius.servando.calendula.healthcareprovider.model.*
import es.usc.citius.servando.calendula.healthcareprovider.model.ActiveMedCharacteristic
import es.usc.citius.servando.calendula.healthcareprovider.persistence.ActiveMedType
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageType
import es.usc.citius.servando.calendula.healthcareprovider.persistence.RepeatType
import es.usc.citius.servando.calendula.healthcareprovider.util.ActiveMedFilter
import es.usc.citius.servando.calendula.healthcareprovider.util.DBUtil
import es.usc.citius.servando.calendula.util.LogUtil
import org.hl7.fhir.dstu3.model.*
import org.hl7.fhir.dstu3.model.codesystems.MessageEvents
import org.hl7.fhir.exceptions.FHIRException
import org.joda.time.DateTime
import org.joda.time.Interval
import org.joda.time.LocalDateTime
import org.joda.time.LocalTime
import org.joda.time.format.DateTimeFormat
import java.io.Reader
import java.util.*

/**
 * Utilities to work with FHIR messages.
 */
object FHIRUtil {

    private val TAG = "FHIRUtil"

    private val QUERY_SOURCE_NAME = "CALENDULA"

    private val DISPENSE_EXTENSION_URL = "http://hl7.org/fhir/StructureDefinition/medicationdispense-validityPeriod"

    /**
     * Gets a [ResponseVO] from a response message.
     *
     * @param reader reader for the message
     * @return the [ResponseVO]
     */
    @JvmStatic
    fun parseResponse(reader: Reader): ResponseVO {

        val bundle = FhirParseUtil.parseBundle(reader)

        val vo = ResponseVO()
        val medicationDispenses: MutableMap<String, MedicationDispense>
        val medicationRequests: MutableMap<String, MedicationRequest>
        val medications: MutableMap<String, Medication>


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            medicationDispenses = ArrayMap()
            medicationRequests = ArrayMap()
            medications = ArrayMap()
        } else {
            medicationDispenses = HashMap()
            medicationRequests = HashMap()
            medications = HashMap()
        }

        for (component in bundle.entry) {
            val resource = component.resource
            val type = resource.resourceType

            when (type) {
                ResourceType.MedicationDispense -> {
                    val md = resource as MedicationDispense
                    medicationDispenses.put(md.id, md)
                }
                ResourceType.MedicationRequest -> {
                    val mr = resource as MedicationRequest
                    medicationRequests.put(mr.id, mr)
                }
                ResourceType.Medication -> {
                    val m = resource as Medication
                    medications.put(m.id, m)
                }
                ResourceType.MessageHeader -> {
                    val mh = resource as MessageHeader
                    retrieveResponseDetailsTarget(mh)
                    if (vo.header == null) {
                        vo.header = mh
                    } else {
                        LogUtil.w(TAG, "parseResponse: More than one MessageHeader in response entries!")
                    }
                }
                ResourceType.Patient -> {
                    val p = resource as Patient
                    if (vo.patient == null) {
                        vo.patient = p
                    } else {
                        LogUtil.w(TAG, "parseResponse: More than one Patient in response entries!")
                    }
                }
                ResourceType.Organization -> {
                    val o = resource as Organization
                    if (vo.organization == null) {
                        vo.organization = o
                    } else {
                        LogUtil.w(TAG, "parseResponse: More than one Organization in response entries!")
                    }
                }
                else -> LogUtil.e(TAG, "parseResponse: Unexpected resource type " + type.toString() + "in response, ignoring")
            }
        }

        vo.medicationDispenses = medicationDispenses
        vo.medicationRequests = medicationRequests
        vo.medications = medications

        return vo
    }

    /**
     * Generates a query for the service, optionally adding the last digest.
     *
     * @param lastDigest
     * @return
     */
    @JvmStatic
    fun generateQuery(lastDigest: String?): Bundle {

        // request
        val request = CommunicationRequest()
        request.status = CommunicationRequest.CommunicationRequestStatus.ACTIVE
        // digest if present
        if (lastDigest != null) {
            val p = CommunicationRequest.CommunicationRequestPayloadComponent()
            p.content = StringType(lastDigest)
            request.addPayload(p)
        }

        // header
        val source = MessageHeader.MessageSourceComponent()
        source.name = QUERY_SOURCE_NAME

        val event = Coding()
        event.code = MessageEvents.COMMUNICATIONREQUEST.toCode()

        val timestamp = InstantType(Date())

        val header = MessageHeader(event, timestamp, source)

        header.addFocus(Reference(request))

        // bundle
        val bundle = Bundle()
        val headerComponent = Bundle.BundleEntryComponent()
        headerComponent.resource = header
        val requestComponent = Bundle.BundleEntryComponent()
        requestComponent.resource = request
        bundle.addEntry(headerComponent)
        bundle.addEntry(requestComponent)

        return bundle
    }

    @JvmStatic
    fun genVOsFromResponse(response: ResponseVO): List<ActiveMedVO> {
        val ret = ArrayList<ActiveMedVO>()
        for (requestKey in response.medicationRequests!!.keys) {
            try {
                val request = response.medicationRequests!![requestKey]
                val reference = request!!.medicationReference.reference
                val m = response.medications!![reference]

                var type: ActiveMedType? = null
                var code: String? = null
                var characteristics = ArrayList<ActiveMedCharacteristic> ()
                for(coding in m!!.code.coding) {
                    when (ActiveMedCodingSystemType.forCoding(coding.system)) {
                        ActiveMedCodingSystemType.DCPF, ActiveMedCodingSystemType.NATIONAL_CODE -> {
                            type = ActiveMedType.forCoding(coding.system)
                            code = coding.code
                        }
                        ActiveMedCodingSystemType.SNOMED_CT, ActiveMedCodingSystemType.FHIR, ActiveMedCodingSystemType.ATC -> {
                            characteristics.add (ActiveMedCharacteristic(coding.system, coding.code))
                        }
                        else -> {}
                    }

                }

                // general info
                val medVO: ActiveMedVO
                when (type) {
                    ActiveMedType.DCPF -> {
                        val dcpfvo = ActiveMedDCPFVO()
                        if (DBUtil.isValidDB()) {
                            val g = DB.drugDB().homogeneousGroups().findOneBy(HomogeneousGroup.COLUMN_HOMOGENEOUS_GROUP_ID, code)
                            if (g != null) {
                                dcpfvo.homogeneousGroup = g
                            } else {
                                LogUtil.e(TAG, "genVOsFromResponse: DCPF not found in database")
                            }
                        } else {
                            LogUtil.e(TAG, "genVOsFromResponse: No valid database!")
                        }
                        medVO = dcpfvo
                    }
                    ActiveMedType.NATIONAL_CODE -> {
                        val cnvo = ActiveMedCNVO()
                        if (DBUtil.isValidDB()) {
                            val p = DB.drugDB().prescriptions().findByCn(code)
                            if (p != null) {
                                cnvo.prescription = p
                            } else {
                                LogUtil.e(TAG, "genVOsFromResponse: Prescription not found in database")
                            }
                        } else {
                            LogUtil.e(TAG, "genVOsFromResponse: No valid database!")
                        }
                        medVO = cnvo
                    }
                    else -> throw IllegalArgumentException("System not recognized!")
                }
                medVO.code = code
                medVO.patient = DB.patients().getActive(CalendulaApp.getContext())
                medVO.defaultDisplay = m.code.codingFirstRep.display
                ActiveMedFilter.filter(medVO,characteristics)

                // validityPeriod
                val dispenseRequest = request.dispenseRequest
                if (dispenseRequest != null) {
                    val validityPeriod = dispenseRequest.validityPeriod
                    if (validityPeriod != null) {
                        if (validityPeriod.hasStart()) {
                            medVO.validityStart = DateTime(validityPeriod.start)
                        }
                        if (validityPeriod.hasEnd()) {
                            medVO.validityEnd = DateTime(validityPeriod.end)
                        }
                    }
                }

                // dispensation info
                val dispensationInfo = ArrayList<DispensationInfoVO>()
                for (dispenseKey in response.medicationDispenses!!.keys) {
                    val dispense = response.medicationDispenses!![dispenseKey]
                    if (dispense!!.authorizingPrescriptionFirstRep.reference == requestKey) {
                        dispensationInfo.add(getDispensationInfoVO(dispense))
                    }
                }

                medVO.dispensationInfo = dispensationInfo
                medVO.dosage = getDosageVO(request, medVO)
                ret.add(medVO)

            } catch (e: Exception) {
                LogUtil.e(TAG, "genVOsFromResponse: ", e)
            }

        }
        return ret
    }


    private fun retrieveResponseDetailsTarget(header: MessageHeader) {
        val response = header.response
        if (response != null && response.details != null) {
            val reference = response.details.reference
            val contained = header.contained
            for (resource in contained) {
                if (resource is OperationOutcome && resource.getId() == reference) {
                    header.response.detailsTarget = resource
                    return
                }
            }

        }
    }

    private fun getDosageVO(request: MedicationRequest, med: ActiveMedVO): DosageVO? {
        try {
            val dosages = request.dosageInstruction
            val dosageCount = dosages.size

            val dosageType = when {
                dosageCount == 0 -> DosageType.NONE
                dosageCount > 1 || getTimingRepeat(dosages[0]).hasWhen() || getTimingRepeat(dosages[0]).hasTimeOfDay() -> DosageType.DETAILED
                dosages[0].hasAsNeededCodeableConcept() -> DosageType.AS_NEEDED
                else -> DosageType.GENERAL
            }

            val dosageBuilder = DosageVO.Builder()
                    .activeMed(med)
                    .type(dosageType)

            val dosage: Dosage
            var quantity: SimpleQuantity
            var repeat: Timing.TimingRepeatComponent
            var entryBuilder: DosageEntryVO.Builder

            when (dosageType) {

            /* DETAILED dosage type */

                DosageType.DETAILED ->
                    // iterate over dosages and build a new entry for each of them
                    for (detailedDosage in dosages) {
                        quantity = getDoseQuantity(detailedDosage)
                        repeat = getTimingRepeat(detailedDosage)

                        entryBuilder = dosageBuilder.newEntry()
                                .quantity(quantity.value.toDouble(), quantity.unit)
                                .instructions(detailedDosage.patientInstruction)
                                .text(detailedDosage.text)

                        when {
                            repeat.hasWhen() -> {
                                val timing = repeat.`when`[0].value
                                val repeatType = RepeatType.fromEventTiming(timing)
                                entryBuilder.repeatType(repeatType)
                            }
                            repeat.hasTimeOfDay() -> {
                                val timeType = repeat.timeOfDay[0]
                                val timeStr = timeType.asStringValue()
                                entryBuilder.repeatAt(parseFhirLocalTime(timeStr))
                            }
                            else -> throw RuntimeException("Detailed dosage instructions must have 'when' or 'timeOfDay'")
                        }
                        entryBuilder.done()
                    }

            /* AS_NEEDED dosage type */

                DosageType.AS_NEEDED -> {
                    dosage = dosages[0]
                    repeat = getTimingRepeat(dosage)
                    if (!repeat.hasDuration()) {
                        throw RuntimeException("As-needed dosages must have a repeat duration")
                    }

                    entryBuilder = dosageBuilder.newEntry()
                            .instructions(dosage.patientInstruction)
                            .repeatType(RepeatType.DURATION)
                            .repeatDuration(repeat.duration.toDouble(), repeat.durationUnit)
                            .text(dosage.text)
                    entryBuilder.done()
                }

            /* GENERAL dosage type */

                DosageType.GENERAL -> {
                    dosage = dosages[0]
                    quantity = getDoseQuantity(dosage)
                    repeat = getTimingRepeat(dosage)
                    if (!repeat.hasPeriod()) {
                        throw RuntimeException("General dosages must have a repeat period")
                    }

                    val value = repeat.period.toDouble()
                    val units = if (repeat.periodUnit != null) repeat.periodUnit else Timing.UnitsOfTime.NULL

                    entryBuilder = dosageBuilder.newEntry()
                            .instructions(dosage.patientInstruction)
                            .quantity(quantity.value.toDouble(), quantity.unit)
                            .repeatPeriod(value, units)
                            .text(dosage.text)
                    entryBuilder.done()
                }

                DosageType.NONE -> LogUtil.d(TAG, "Dosage type is NONE, not creating entries")
            }
            return dosageBuilder.build()

        } catch (e: Exception) {
            LogUtil.d(TAG, "Error while reading dosage information from FHIR request")
            LogUtil.e(TAG, "An error occurred while reading dosage information", e)
            throw RuntimeException(e)
        }

    }

    internal fun parseFhirLocalTime(timeStr: String): LocalTime {
        // FHIR time uses a 00-23 hour. Joda's `kk` pattern uses clock-hour-of-day 1-24,
        // which rejects a valid midnight value such as 00:00. Use `HH` instead.
        val timePattern = if (timeStr.length > 5) "HH:mm:ss" else "HH:mm"
        return LocalTime.parse(timeStr, DateTimeFormat.forPattern(timePattern))
    }

    @Throws(FHIRException::class)
    private fun getDoseQuantity(dosage: Dosage): SimpleQuantity {
        if (dosage.hasDoseSimpleQuantity()) {
            return dosage.doseSimpleQuantity
        }
        throw RuntimeException("Only dosages with SimpleQuantities are supported")
    }


    @Throws(FHIRException::class)
    private fun getTimingRepeat(dosage: Dosage): Timing.TimingRepeatComponent {
        if (dosage.timing.hasRepeat()) {
            return dosage.timing.repeat
        }
        throw RuntimeException("Only dosages with TimingRepeatComponent are supported")
    }


    @Throws(FHIRException::class)
    private fun getDispensationInfoVO(dispense: MedicationDispense): DispensationInfoVO {
        val infoVO = DispensationInfoVO()
        infoVO.isDispensed = !dispense.notDone
        if (infoVO.isDispensed) {
            //if it's dispensed, check for date and dispensed code
            infoVO.dispensedCode = dispense.medicationReference.reference
            val whenHandedOver = dispense.whenHandedOver
            if (whenHandedOver != null) {
                infoVO.dispensedDateTime = LocalDateTime(whenHandedOver)
            }
        }
        val extensionsByUrl = dispense.getExtensionsByUrl(DISPENSE_EXTENSION_URL)
        if (extensionsByUrl.size == 1) {
            val extension = extensionsByUrl[0]
            val valuePeriod = extension.value as Period
            val valuePeriodStart = valuePeriod.start
            val valuePeriodEnd = valuePeriod.end

            if (valuePeriodStart != null && valuePeriodEnd != null) {
                val start = DateTime(valuePeriodStart)
                val end = DateTime(valuePeriodEnd)
                val validityInterval = Interval(start, end)
                infoVO.validityInterval = validityInterval
            } else {
                LogUtil.e(TAG, "getDispensationInfoVO: Missing start or end for validity period")
            }

        } else {
            LogUtil.e(TAG, "genVOsFromResponse: wrong number of extensions for medication dispense: " + extensionsByUrl.size)
        }
        return infoVO
    }
}
