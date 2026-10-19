package es.usc.citius.servando.calendula.util;

import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;

/** Search highlighting must use displayed medical text, not stale source offsets. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class StringsHighlightSafetyTest {
    private static final int HIGHLIGHT = 0xff0088cc;

    @Test
    public void caseFoldChangesDoNotShiftMedicationHighlight() {
        // The dotted uppercase I expands to two code points when lowercased.
        SpannableStringBuilder highlighted =
                Strings.getHighlighted("\u0130NSULINA 500", "500", HIGHLIGHT);
        ForegroundColorSpan[] spans =
                highlighted.getSpans(0, highlighted.length(), ForegroundColorSpan.class);
        assertEquals(1, spans.length);
        assertEquals(highlighted.toString().indexOf("500"),
                highlighted.getSpanStart(spans[0]));
        assertEquals(highlighted.toString().indexOf("500") + 3,
                highlighted.getSpanEnd(spans[0]));
    }

    @Test
    public void nullAndEmptyInputDoNotCreateInvalidSpans() {
        assertEquals(0, Strings.getHighlighted(null, "dose", HIGHLIGHT).length());
        assertEquals(0, Strings.getHighlighted("", "dose", HIGHLIGHT).length());
        SpannableStringBuilder emptyQuery =
                Strings.getHighlighted("PARACETAMOL", "", HIGHLIGHT);
        assertEquals(0, emptyQuery.getSpans(0, emptyQuery.length(), StyleSpan.class).length);
        SpannableStringBuilder missingQuery =
                Strings.getHighlighted("PARACETAMOL", null, HIGHLIGHT);
        assertEquals(0, missingQuery.getSpans(0, missingQuery.length(), ForegroundColorSpan.class).length);
    }

    @Test
    public void restoredOutOfRangeOffsetsRenderTextWithoutCrashing() {
        SpannableStringBuilder beyond = Strings.getHighlighted("IBUPROFENO", 50, 60, HIGHLIGHT);
        assertEquals("Ibuprofeno", beyond.toString());
        assertEquals(0, beyond.getSpans(0, beyond.length(), StyleSpan.class).length);
        SpannableStringBuilder inverted = Strings.getHighlighted("IBUPROFENO", 8, 4, HIGHLIGHT);
        assertEquals(0, inverted.getSpans(0, inverted.length(), StyleSpan.class).length);
        SpannableStringBuilder negative = Strings.getHighlighted("IBUPROFENO", -1, 5, HIGHLIGHT);
        assertEquals(0, negative.getSpans(0, negative.length(), StyleSpan.class).length);
    }

    @Test
    public void oversizedEndIsClampedToVisibleText() {
        SpannableStringBuilder highlighted = Strings.getHighlighted("METFORMINA", 4, 5000, HIGHLIGHT);
        ForegroundColorSpan[] spans =
                highlighted.getSpans(0, highlighted.length(), ForegroundColorSpan.class);
        assertEquals(1, spans.length);
        assertEquals(4, highlighted.getSpanStart(spans[0]));
        assertEquals(highlighted.length(), highlighted.getSpanEnd(spans[0]));
    }
}
