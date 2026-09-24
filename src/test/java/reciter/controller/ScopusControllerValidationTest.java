package reciter.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import reciter.model.scopus.ScopusQuery;

/**
 * Unit tests for {@link ScopusController#validate} — the request guard that turns malformed
 * input into a 400 instead of an NPE/500 or a raw type concatenated into the Scopus query.
 */
class ScopusControllerValidationTest {

    private static ScopusQuery query(List<Object> query, String type) {
        return new ScopusQuery(query, type);
    }

    @Test
    void acceptsSupportedTypesCaseInsensitively() {
        assertDoesNotThrow(() -> {
            ScopusController.validate(query(Collections.singletonList("123"), "pmid"));
            ScopusController.validate(query(Collections.singletonList("123"), "PMID"));
            ScopusController.validate(query(Collections.singletonList("10.1/x"), "doi"));
            ScopusController.validate(query(Collections.singletonList("10.1/x"), "DOI"));
            ScopusController.validate(query(Collections.singletonList("85"), "scopus-id"));
            ScopusController.validate(query(Collections.singletonList("60"), "AF-ID"));
        });
    }

    @Test
    void rejectsNullBody() {
        assertThrows(IllegalArgumentException.class, () -> ScopusController.validate(null));
    }

    @Test
    void rejectsNullQuery() {
        assertThrows(IllegalArgumentException.class,
                () -> ScopusController.validate(query(null, "pmid")));
    }

    @Test
    void rejectsEmptyQuery() {
        assertThrows(IllegalArgumentException.class,
                () -> ScopusController.validate(query(Collections.emptyList(), "pmid")));
    }

    @Test
    void rejectsNullType() {
        assertThrows(IllegalArgumentException.class,
                () -> ScopusController.validate(query(Collections.singletonList("123"), null)));
    }

    @Test
    void rejectsUnsupportedType() {
        assertThrows(IllegalArgumentException.class,
                () -> ScopusController.validate(query(Collections.singletonList("123"), "eid")));
        assertThrows(IllegalArgumentException.class,
                () -> ScopusController.validate(query(Collections.singletonList("123"), "")));
    }
}
