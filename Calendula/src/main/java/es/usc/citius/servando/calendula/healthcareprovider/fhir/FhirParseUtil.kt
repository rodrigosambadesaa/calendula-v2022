package es.usc.citius.servando.calendula.healthcareprovider.fhir

import ca.uhn.fhir.context.FhirContext
import es.usc.citius.servando.calendula.util.LogUtil
import org.hl7.fhir.dstu3.model.Bundle
import java.io.IOException
import java.io.Reader
import java.io.StringReader
import java.io.Writer

/**
 * Parses FHIR-JSON messages from Healthcare provider services.
 */
object FhirParseUtil {

    private val TAG = "FhirParseUtil"

    private val context: FhirContext = FhirContext.forDstu3()

    init {
        LogUtil.d(TAG, "Context initialized (DSTU3)")
    }

    @JvmStatic
    fun parseBundle(message: String): Bundle {
        val reader = StringReader(message)
        return parseBundle(reader)
    }

    @JvmStatic
    fun parseBundle(reader: Reader): Bundle {
        val parser = context.newJsonParser()
        return parser.parseResource(Bundle::class.java, reader)
    }

    @JvmStatic
    fun encodeBundle(bundle: Bundle): String {
        val parser = context.newJsonParser()
        return parser.encodeResourceToString(bundle)
    }

    @Throws(IOException::class)
    @JvmStatic
    fun encodeBundle(bundle: Bundle, writer: Writer) {
        val parser = context.newJsonParser()
        parser.encodeResourceToWriter(bundle, writer)
    }

}
