/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.drugdb.download;

/**
 * Release gate for the historical remote SQL medicine catalog.
 *
 * The former /calendula/dbs/ endpoint is retired and its unsigned archives
 * were never authenticated. Merely switching HTTP to HTTPS or trusting a
 * successful ZIP checksum is insufficient when downloaded bytes can contain
 * executable SQL. This gate must not be opened until a reviewed source and
 * independent authenticity/rollback verification are implemented and tested.
 *
 * Existing databases and explicitly selected local files are not removed.
 * Read-only official CIMA REST queries are an independent code path.
 */
public final class LegacyRemoteArchivePolicy {

    private LegacyRemoteArchivePolicy() {
    }

    public static boolean permitsRemoteSqlInstallation() {
        return false;
    }
}
