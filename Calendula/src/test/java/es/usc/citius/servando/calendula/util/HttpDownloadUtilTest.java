/*
 * Calendula - An assistant for personal medication management.
 * Copyright (C) 2014-2018 CiTIUS - University of Santiago de Compostela
 *
 * This file is distributed under the GNU General Public License v3.0 or later.
 */
package es.usc.citius.servando.calendula.util;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.Assert.assertEquals;

public class HttpDownloadUtilTest {

    @Test
    public void readLimitedTextReturnsContentWithinLimit() throws Exception {
        assertEquals(
                "{\"AEMPS\":{}}",
                HttpDownloadUtil.readLimitedText(
                        new StringReader("{\"AEMPS\":{}}"),
                        64));
    }

    @Test(expected = IOException.class)
    public void readLimitedTextRejectsContentPastLimit() throws Exception {
        HttpDownloadUtil.readLimitedText(new StringReader("123456"), 5);
    }

    @Test
    public void readLimitedTextAllowsExactLimit() throws Exception {
        assertEquals("12345", HttpDownloadUtil.readLimitedText(new StringReader("12345"), 5));
    }

    @Test(expected = IllegalArgumentException.class)
    public void readLimitedTextRejectsNegativeLimit() throws Exception {
        HttpDownloadUtil.readLimitedText(new StringReader(""), -1);
    }
}
