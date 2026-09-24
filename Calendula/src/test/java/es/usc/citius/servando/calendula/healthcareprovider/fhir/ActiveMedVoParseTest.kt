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

package es.usc.citius.servando.calendula.healthcareprovider.fhir

import es.usc.citius.servando.calendula.BuildConfig
import es.usc.citius.servando.calendula.TestUtils
import es.usc.citius.servando.calendula.healthcareprovider.model.DosageEntryVO
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageEntryEntity
import es.usc.citius.servando.calendula.healthcareprovider.persistence.DosageType
import es.usc.citius.servando.calendula.healthcareprovider.persistence.RepeatType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStreamReader
import java.util.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class ActiveMedVoParseTest {


    @Before
    fun setUp() {
        TestUtils.resetDatabase()
    }

    @Test
    @Throws(Exception::class)
    fun testParseResponse() {
        val input = javaClass.classLoader?.getResourceAsStream(RESPONSE_FILE)
        val reader = InputStreamReader(input)

        val vo = FHIRUtil.parseResponse(reader)

        // TODO: 31/05/17 do some more checks
        val vos = FHIRUtil.genVOsFromResponse(vo)
        assertEquals("Wrong number of VOs", 3, vos.size.toLong())

        //check no repetitions
        val repeated = vos.size > vos.toSet().size
        assertFalse("Repeated VOs!", repeated)

        // do some checks on dosages

        for (v in vos) {
            val dosage = v.dosage
            val entries = dosage.entries

            val entry: DosageEntryVO
            val entity: DosageEntryEntity

            when (dosage.type) {
                DosageType.GENERAL -> {
                    entry = entries[0]
                    entity = entry.entity()
                    assertEquals(entity.repeatType, RepeatType.PERIOD)
                    assertEquals(1, entries.size.toLong())
                    assertEquals(dosageText[dosage.activeMed.code], entry.text)
                    assertEquals(PAT_INSTRUC_TEXT, entry.patientInstruction)
                }

                DosageType.AS_NEEDED -> {
                    entry = entries[0]
                    entity = entry.entity()
                    assertEquals(entity.repeatType, RepeatType.DURATION)
                    assertEquals(1, entries.size.toLong())
                    assertEquals(dosageText[dosage.activeMed.code], entry.text)
                    assertEquals(PAT_INSTRUC_TEXT, entry.patientInstruction)
                }
                DosageType.DETAILED -> assertEquals(4, entries.size.toLong())
                else -> fail("Unexpected dosage type ${dosage.type}")
            }


        }
    }

    companion object {

        private val RESPONSE_FILE = "resposta.json"
        private val PAT_INSTRUC_TEXT = "No consumir bebidas alcohólicas"
        private val dosageText: MutableMap<String, String>

        init {
            dosageText = HashMap()
            dosageText["672908"] = "2.5 COMPRIMIDOS CADA 24 HORAS"
            dosageText["665477"] = "1 SOBRES CADA 12 HORAS"
            dosageText["654186"] = "DURANTE 3 MESES. Según control analítico."
        }
    }

}