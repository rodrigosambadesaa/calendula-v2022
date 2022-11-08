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
import es.usc.citius.servando.calendula.healthcareprovider.util.ResponseVoUtil
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.InputStreamReader


@RunWith(RobolectricTestRunner::class)
@Config(constants = BuildConfig::class)
class FhirTest {

    @Test
    @Throws(Exception::class)
    fun testParseResponse() {
        val input = javaClass.classLoader?.getResourceAsStream(RESPONSE_FILE)
        val reader = InputStreamReader(input)

        val vo = FHIRUtil.parseResponse(reader)
        // asserts
        ResponseVoUtil.checkResponseVo(vo)
    }

    @Test
    fun testEncodeQueryNoPayload() {

        val query = FhirParseUtil.encodeBundle(FHIRUtil.generateQuery(null))

        Assert.assertNotNull("Query is null", query)
        Assert.assertFalse("Query contains payload!", query.contains("payload"))
    }

    @Test
    fun testEncodeQueryWithPayload() {

        val query = FhirParseUtil.encodeBundle(FHIRUtil.generateQuery("test-payload"))

        Assert.assertNotNull("Query is null", query)
        Assert.assertTrue("Query does not contain payload!", query.contains("payload"))
    }

    companion object {

        private val TAG = "FhirTest"

        private val RESPONSE_FILE = "resposta.json"
    }


}