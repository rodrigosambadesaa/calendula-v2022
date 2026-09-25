/*
 *    Calendula - An assistant for personal medication management.
 *    Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
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

package es.usc.citius.servando.calendula.healthcareprovider.util

import org.hl7.fhir.dstu3.model.MessageHeader
import org.joda.time.DateTime

import es.usc.citius.servando.calendula.healthcareprovider.fhir.ResponseVO

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull


object ResponseVoUtil {

    fun checkResponseVo(vo: ResponseVO) {
        // 1. object
        assertNotNull(vo)
        // 2. header
        val expectedTimestamp = DateTime.parse("2017-04-05T11:52:08.996+02:00")
        assertNotNull("Header is null", vo.header)
        assertEquals("Wrong header timestamp", expectedTimestamp.toDate(), vo.header!!.timestamp)
        assertEquals(
            "Wrong header hash",
            "5df9f63916ebf8528697b629022993e8",
            vo.header!!.response.identifier
        )
        assertEquals(
            "Wrong header response type",
            MessageHeader.ResponseType.OK,
            vo.header!!.response.code
        )
        assertEquals("Wrong header focus size", 7, vo.header!!.focus.size.toLong())
        // 3. medicationDispenses
        assertNotNull("MedicationDispenses is null", vo.medicationDispenses)
        assertEquals("Wrong MedicationDispenses size", 7, vo.medicationDispenses!!.size.toLong())
        assertNotNull(
            "Missing a MedicationDispense",
            vo.medicationDispenses!!["MedicationDispense/98218529"]
        )
        assertNotNull(
            "Missing a MedicationDispense",
            vo.medicationDispenses!!["MedicationDispense/10632220"]
        )
        assertNotNull(
            "Missing a MedicationDispense",
            vo.medicationDispenses!!["MedicationDispense/83439294"]
        )
        assertNotNull(
            "Missing a MedicationDispense",
            vo.medicationDispenses!!["MedicationDispense/48432191"]
        )
        assertNotNull(
            "Missing a MedicationDispense",
            vo.medicationDispenses!!["MedicationDispense/37698558"]
        )
        assertNotNull(
            "Missing a MedicationDispense",
            vo.medicationDispenses!!["MedicationDispense/39063934"]
        )
        assertNotNull(
            "Missing a MedicationDispense",
            vo.medicationDispenses!!["MedicationDispense/97372909"]
        )
        // 4. medicationRequests
        assertNotNull("MedicationRequests is null", vo.medicationRequests)
        assertEquals("Wrong MedicationRequests size", 3, vo.medicationRequests!!.size.toLong())
        assertNotNull(
            "Missing a MedicationRequest",
            vo.medicationRequests!!["MedicationRequest/00000003"]
        )
        assertNotNull(
            "Missing a MedicationRequest",
            vo.medicationRequests!!["MedicationRequest/00000002"]
        )
        assertNotNull(
            "Missing a MedicationRequest",
            vo.medicationRequests!!["MedicationRequest/00000001"]
        )
        // 5. medications
        assertNotNull("Medications is null", vo.medications)
        assertEquals("Wrong medications size", 3, vo.medications!!.size.toLong())
        assertNotNull(
            "Missing a Medication",
            vo.medications!!["Medication/2.16.724.4.21.5.15.4.672908"]
        )
        assertNotNull(
            "Missing a Medication",
            vo.medications!!["Medication/2.16.724.4.21.5.15.4.665477"]
        )
        assertNotNull(
            "Missing a Medication",
            vo.medications!!["Medication/2.16.724.4.21.5.15.4.654186"]
        )
        // 6. patient
        assertNotNull("Patient is null", vo.patient)
        assertEquals("Wrong patient ID", "Patient/ID_NUMBER", vo.patient!!.id)
        assertEquals(
            "Wrong patient name",
            "Nombre Apellido1 Apellido2",
            vo.patient!!.nameFirstRep.text
        )
        // 7. organization
        assertNotNull("Organization is null", vo.organization)
        assertEquals("Wrong organization ID", "Organization/2.16.724.4.12", vo.organization!!.id)
        assertEquals("Wrong organization name", "Organization name", vo.organization!!.name)
    }
}
