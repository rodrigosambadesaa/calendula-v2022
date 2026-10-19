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

package es.usc.citius.servando.calendula.util;

import android.os.Build;
import android.text.Html;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.Locale;

public class Strings {

    private static final String TAG = "Strings";
    private static final NumberFormat doubleFormat = new DecimalFormat("##.##");

    public static String toCamelCase(String s, String spacer) {
        String[] parts = s.split(spacer);
        String camelCaseString = "";
        for (String part : parts) {
            camelCaseString = camelCaseString + spacer + toProperCase(part);
        }
        return camelCaseString.replaceFirst(spacer, "");
    }

    public static String toProperCase(String s) {
        final StringBuilder result = new StringBuilder(s.length());
        String[] words = s.toLowerCase(Locale.ROOT).split("\\s");
        for (int i = 0, l = words.length; i < l; ++i) {
            if (i > 0) result.append(" ");
            String word = words[i];
            if (word.length() > 1)
                result.append(Character.toUpperCase(word.charAt(0))).append(words[i].substring(1));
            else
                result.append(word);

        }
        return result.toString();
    }

    public static String firstPart(String str) {
        try {
            String[] parts = str.split(" ");
            String s = parts[0].toLowerCase(Locale.ROOT);
            if ((s.contains("acido") || s.contains("ácido")) && parts.length > 1) {
                return Strings.toCamelCase(s + " " + parts[1], " ");
            }
            return Strings.toProperCase(s);

        } catch (Exception e) {
            return str;
        }
    }

    public static SpannableStringBuilder getHighlighted(String text, String match, int color) {
        // Normalize before computing indices. Converting names to title case
        // can collapse repeated whitespace, so offsets from the original
        // medication/allergen label may no longer fit the displayed text.
        final SpannableStringBuilder sb = new SpannableStringBuilder(
                text == null ? "" : Strings.toProperCase(text));
        if (match == null || match.isEmpty()) {
            return sb;
        }
        String folded = sb.toString().toLowerCase(Locale.ROOT);
        String query = match.toLowerCase(Locale.ROOT);
        int start = folded.indexOf(query);
        return highlightRange(sb, start, start < 0 ? -1 : start + query.length(), color);
    }

    public static SpannableStringBuilder getHighlighted(final String text, final int start, final int end, final int color) {
        return highlightRange(new SpannableStringBuilder(
                text == null ? "" : Strings.toProperCase(text)), start, end, color);
    }

    private static SpannableStringBuilder highlightRange(
            SpannableStringBuilder sb, int start, int end, int color) {
        // Corrupt/imported medical labels or asynchronous search positions
        // must not crash the search results list. No match is safer than an
        // invalid span, and clinical search content is never logged.
        if (start < 0 || start >= sb.length() || end <= start) {
            return sb;
        }
        int boundedEnd = Math.min(end, sb.length());
        final ForegroundColorSpan fcs = new ForegroundColorSpan(color);
        final StyleSpan bss = new StyleSpan(android.graphics.Typeface.BOLD);
        sb.setSpan(fcs, start, boundedEnd, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        sb.setSpan(bss, start, boundedEnd, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
        return sb;
    }

    /**
     * Calls appropriate version of Html.fromHtml depending on build sdk version
     *
     * @param html the html string
     * @return the spanned
     */
    public static Spanned fromHtmlCompat(String html) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY);
        }
        return Html.fromHtml(html);
    }

    public static Spanned genBulletList(Collection<?> items) {
        StringBuilder sb = new StringBuilder();
        for (Object o : items) {
            sb.append("&#8226; ").append(o.toString()).append("<br/>");
        }
        return fromHtmlCompat(sb.toString());
    }

    /**
     * Returns a {@link String} representing <code>theDouble</code> up to two decimal places, ommitting them if the double is exact.
     *
     * @param theDouble the double to format
     * @return the {@link String} representation of the double
     */
    public static String prettyDouble(final double theDouble) {
        return doubleFormat.format(theDouble);
    }
}
