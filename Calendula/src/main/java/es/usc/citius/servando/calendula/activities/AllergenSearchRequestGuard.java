/*
 * Calendula - An assistant for personal medication management.
 * Distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.activities;

import java.util.Objects;

/** UI-independent generation guard for background catalog search results. */
final class AllergenSearchRequestGuard {
    private AllergenSearchRequestGuard() { }

    static boolean isCurrent(int requestedGeneration, int activeGeneration,
                             String requestedQuery, String visibleQuery) {
        return requestedGeneration == activeGeneration
                && requestedQuery != null && requestedQuery.length() >= 3
                && Objects.equals(requestedQuery, visibleQuery);
    }
}
