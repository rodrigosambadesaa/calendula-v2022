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

        val baseBuilder = when {
            BuildConfig.ENABLE_CERTIFICATE_PINNING ->
                CertificatePinningUtils.pinnedAppAuthConfiguration()
            BuildConfig.OAUTH_ALLOW_INSECURE -> {
                val warning = """
                ================================= WARNING =================================
                Allowing HTTP connections!
                HTTP connections defeat the purpose of OAUTH. Don't use this in production!
                ===========================================================================
                """.trimIndent()
                LogUtil.w(TAG, warning)
                AppAuthConfiguration.Builder()
                    .setConnectionBuilder(TestingConnectionBuilder.getInstance())
            }
            else -> AppAuthConfiguration.Builder()
        }

        val baseConfiguration = baseBuilder.build()
        val configuration = AppAuthConfiguration.Builder()
            .setBrowserMatcher(baseConfiguration.browserMatcher)
            .setConnectionBuilder(
                PreflightConnectionBuilder(
                    ctx.applicationContext ?: ctx,
                    baseConfiguration.connectionBuilder
                )
            )
            .build()

        return AuthorizationService(ctx, configuration)
    }
}
