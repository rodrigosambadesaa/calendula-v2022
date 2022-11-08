package es.usc.citius.servando.calendula.login

import android.content.Context
import es.usc.citius.servando.calendula.BuildConfig
import es.usc.citius.servando.calendula.util.LogUtil
import es.usc.citius.servando.calendula.util.security.certificatePinning.CertificatePinningUtils
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationService


object AuthorizationServiceHelper {

    private const val TAG = "AuthServiceHelper"

    @JvmStatic
    fun createAuthorizationService(ctx: Context): AuthorizationService {
        LogUtil.d(TAG, "Creating authorization service")

        val builder: AppAuthConfiguration.Builder

        if (BuildConfig.ENABLE_CERTIFICATE_PINNING) {
            builder = CertificatePinningUtils.pinnedAppAuthConfiguration()
        } else {
            builder = AppAuthConfiguration.Builder()
            if (BuildConfig.OAUTH_ALLOW_INSECURE) {
                val warning = """
                ================================= WARNING =================================
                Allowing HTTP connections!
                HTTP connections defeat the purpose of OAUTH. Don't use this in production!
                ===========================================================================
                """
                LogUtil.w(TAG, warning)
                builder.setConnectionBuilder(TestingConnectionBuilder.getInstance())
            }
        }
        return AuthorizationService(ctx, builder.build())
    }
}
