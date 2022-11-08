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

package es.usc.citius.servando.calendula.util.security.certificatePinning

import android.net.http.X509TrustManagerExtensions
import android.util.Base64
import com.google.gson.Gson
import es.usc.citius.servando.calendula.BuildConfig
import es.usc.citius.servando.calendula.CalendulaApp
import es.usc.citius.servando.calendula.util.CloseableUtil
import es.usc.citius.servando.calendula.util.LogUtil
import net.openid.appauth.AppAuthConfiguration
import okhttp3.CertificatePinner
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.*
import kotlin.collections.HashSet

/**
 * Provides some utilities for performing certificate pinning with OkHttp
 * ClientBuilder and AppAuthConfiguration
 *
 * Based on: https://medium.com/@appmattus/android-security-ssl-pinning-1db8acb6621e
 */
object CertificatePinningUtils {

    private const val TAG = "AppAuthPinnerUtils"

    /**
     * File with the valid certificate pins
     */
    private const val VALID_PINS_FILENAME = "certificate_pins.json"

    /**
     * Set with valid certificate pins from BuildConfig
     */
    private val validPins: Set<String> = getCertificatePins().toSet()

    /**
     * Create an OkHttp CertificatePinner with the options specified in the BuildConfig.
     * Should be used when creating OkHttp clients with certificate pinning enabled
     */
    @JvmStatic
    fun createOkHttpPinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()
        for (validPin in validPins) {
            builder.add(BuildConfig.CERTIFICATE_PIN_SERVER, "sha256/$validPin")
        }
        return builder.build()
    }

    /**
     * Returns an AppAuthConfiguration object with a custom connection builder that implements
     * certificate pinning for ssl connections, with the options specified in the BuildConfig
     */
    @JvmStatic
    fun pinnedAppAuthConfiguration(): AppAuthConfiguration.Builder {
        return AppAuthConfiguration.Builder()
            .setConnectionBuilder { uri ->
                val url = URL(uri.toString())
                val connection = url.openConnection() as HttpURLConnection
                if (connection is HttpsURLConnection) {
                    // set our custom hostname verifier for certificate pinning
                    connection.hostnameVerifier = PinningHostVerifier(connection.hostnameVerifier)
                }
                connection
            }
    }

    /**
     * Custom hostname verifier that performs certificate pinning
     */
    private class PinningHostVerifier constructor(private val dlg: HostnameVerifier) :
        HostnameVerifier {
        override fun verify(host: String, sslSession: SSLSession): Boolean {
            if (dlg.verify(host, sslSession)) {
                try {
                    val certificates = sslSession.peerCertificates
                    validatePinning(certificates, host, x509TrustManagerExt(), validPins)
                    return true
                } catch (e: SSLException) {
                    throw RuntimeException(e)
                }
            }
            return false
        }
    }

    @Throws(SSLException::class)
    private fun validatePinning(
        certs: Array<Certificate>, host: String,
        trustMgrExt: X509TrustManagerExtensions,
        validPins: Set<String>
    ) {
        var certChainMsg = ""
        try {
            val md = MessageDigest.getInstance("SHA-256")
            val trustedChain = verifyCertificateChain(certs, host, trustMgrExt)
            trustedChain.forEach {
                val publicKey = it.publicKey.encoded
                md.update(publicKey, 0, publicKey.size)
                val pin = Base64.encodeToString(md.digest(), Base64.NO_WRAP)
                certChainMsg += "    sha256/$pin : ${it.subjectDN}"
                if (validPins.contains(pin)) {
                    return
                }
            }
        } catch (e: NoSuchAlgorithmException) {
            throw SSLException(e)
        }
        throw SSLPeerUnverifiedException(
            "Certificate pinning  failure. Peer certificate chain:\n$certChainMsg"
        )
    }

    @Throws(SSLException::class)
    private fun verifyCertificateChain(
        certs: Array<Certificate>, host: String,
        trustMgrExt: X509TrustManagerExtensions
    ): List<X509Certificate> {
        val untrusted = Arrays.copyOf(certs, certs.size, Array<X509Certificate>::class.java)
        try {
            return trustMgrExt.checkServerTrusted(untrusted, "RSA", host)
        } catch (e: CertificateException) {
            throw SSLException(e)
        }
    }

    @Throws(SSLException::class)
    private fun x509TrustManagerExt(): X509TrustManagerExtensions {
        try {
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(null as KeyStore?)
            val x509TMgr = tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
            return X509TrustManagerExtensions(x509TMgr)
        } catch (e: NoSuchAlgorithmException) {
            throw SSLException(e)
        } catch (e: KeyStoreException) {
            throw SSLException(e)
        }
    }

    /**
     * Read certificate pins from assets
     */
    private fun getCertificatePins(): List<String> {
        var stream: InputStream? = null
        try {
            stream = CalendulaApp.getContext().assets.open(VALID_PINS_FILENAME)
            return Gson().fromJson(InputStreamReader(stream), Array<String>::class.java).asList()
        } catch (e: IOException) {
            LogUtil.e(TAG, "getPublicKeys: ", e)
            throw RuntimeException(e)
        } finally {
            CloseableUtil.closeQuietly(stream)
        }
    }

}
