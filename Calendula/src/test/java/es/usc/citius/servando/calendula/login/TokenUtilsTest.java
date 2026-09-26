/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.login;

import com.nimbusds.jwt.JWTClaimsSet;

import org.junit.Test;

import java.util.Arrays;
import java.util.Date;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TokenUtilsTest {

    private static final String CLIENT_ID = "calendula-client";
    private static final long NOW_MS = 1_700_000_000_000L;
    private static final Date NOW = new Date(NOW_MS);

    @Test
    public void validAudienceAndTemporalClaimsAreAccepted() throws Exception {
        JWTClaimsSet claims = baseClaims()
                .build();

        assertTrue(TokenUtils.validateIdTokenClaims(claims, CLIENT_ID, NOW));
    }

    @Test
    public void missingExpirationIsRejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience(CLIENT_ID)
                .issueTime(new Date(NOW_MS - 1_000L))
                .build();

        assertFalse(TokenUtils.validateIdTokenClaims(claims, CLIENT_ID, NOW));
    }

    @Test
    public void expiredTokenBeyondClockSkewIsRejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience(CLIENT_ID)
                .expirationTime(new Date(NOW_MS - TokenUtils.ID_TOKEN_CLOCK_SKEW_MS - 1L))
                .issueTime(new Date(NOW_MS - 120_000L))
                .build();

        assertFalse(TokenUtils.validateIdTokenClaims(claims, CLIENT_ID, NOW));
    }

    @Test
    public void expirationWithinClockSkewIsAccepted() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience(CLIENT_ID)
                .expirationTime(new Date(NOW_MS - TokenUtils.ID_TOKEN_CLOCK_SKEW_MS + 1L))
                .issueTime(new Date(NOW_MS - 120_000L))
                .build();

        assertTrue(TokenUtils.validateIdTokenClaims(claims, CLIENT_ID, NOW));
    }

    @Test
    public void wrongAudienceIsRejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience("different-client")
                .expirationTime(new Date(NOW_MS + 300_000L))
                .issueTime(new Date(NOW_MS - 1_000L))
                .build();

        assertFalse(TokenUtils.validateIdTokenClaims(claims, CLIENT_ID, NOW));
    }

    @Test
    public void multipleAudiencesRequireMatchingAuthorizedParty() throws Exception {
        JWTClaimsSet missingAzp = new JWTClaimsSet.Builder()
                .audience(Arrays.asList(CLIENT_ID, "another-client"))
                .expirationTime(new Date(NOW_MS + 300_000L))
                .issueTime(new Date(NOW_MS - 1_000L))
                .build();
        JWTClaimsSet matchingAzp = new JWTClaimsSet.Builder()
                .audience(Arrays.asList(CLIENT_ID, "another-client"))
                .claim("azp", CLIENT_ID)
                .expirationTime(new Date(NOW_MS + 300_000L))
                .issueTime(new Date(NOW_MS - 1_000L))
                .build();

        assertFalse(TokenUtils.validateIdTokenClaims(missingAzp, CLIENT_ID, NOW));
        assertTrue(TokenUtils.validateIdTokenClaims(matchingAzp, CLIENT_ID, NOW));
    }

    @Test
    public void notBeforeTooFarInFutureIsRejected() throws Exception {
        JWTClaimsSet claims = baseClaims()
                .notBeforeTime(new Date(NOW_MS + TokenUtils.ID_TOKEN_CLOCK_SKEW_MS + 1L))
                .build();

        assertFalse(TokenUtils.validateIdTokenClaims(claims, CLIENT_ID, NOW));
    }

    @Test
    public void issuedAtTooFarInFutureIsRejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .audience(CLIENT_ID)
                .expirationTime(new Date(NOW_MS + 300_000L))
                .issueTime(new Date(NOW_MS + TokenUtils.ID_TOKEN_CLOCK_SKEW_MS + 1L))
                .build();

        assertFalse(TokenUtils.validateIdTokenClaims(claims, CLIENT_ID, NOW));
    }

    private JWTClaimsSet.Builder baseClaims() {
        return new JWTClaimsSet.Builder()
                .audience(CLIENT_ID)
                .expirationTime(new Date(NOW_MS + 300_000L))
                .issueTime(new Date(NOW_MS - 1_000L));
    }
}
