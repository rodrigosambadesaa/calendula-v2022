package es.usc.citius.servando.calendula.healthcareprovider.persistence;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.*;

/** Defensive model hashing uses synthetic incomplete provider data only. */
public class DosageEntityNullHashTest {
    @Test
    public void missingDosageTypeCanStillBeSafelyHashed() {
        DosageEntity incomplete = new DosageEntity();
        int first = incomplete.hashCode();
        assertEquals(first, incomplete.hashCode());
        assertEquals("Same object remains equal even when legacy fields are absent",
                incomplete, incomplete);
        HashSet<DosageEntity> visited = new HashSet<>();
        assertTrue(visited.add(incomplete));
        assertTrue(visited.contains(incomplete));
    }

    @Test
    public void nullTypeWithEmptyCollectionIsHashableWithoutModifyingEntries() {
        DosageEntity incomplete = new DosageEntity();
        incomplete.setEntries(new ArrayList<>());
        int hash = incomplete.hashCode();
        assertEquals(hash, incomplete.hashCode());
        assertTrue(incomplete.getEntries().isEmpty());

        DosageEntity typed = new DosageEntity();
        typed.setType(DosageType.GENERAL);
        typed.setEntries(Collections.emptyList());
        assertEquals(typed.hashCode(), typed.hashCode());
    }
}
