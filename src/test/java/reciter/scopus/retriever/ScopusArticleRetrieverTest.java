package reciter.scopus.retriever;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for Scopus query construction in {@link ScopusArticleRetriever}:
 * per-identifier URL-encoding (so DOIs with reserved characters cannot corrupt the
 * request) and fixed-size OR-batching.
 */
class ScopusArticleRetrieverTest {

    // ------------------------------------------------------------------
    // Identifier encoding
    // ------------------------------------------------------------------

    @Test
    void encodesReservedCharactersButKeepsSlash() {
        // Slash is kept literal (every DOI has one and it has always worked);
        // the URL-reserved characters that would break the request are encoded.
        assertEquals("10.1002/%28SICI%29", ScopusArticleRetriever.encodeIdentifier("10.1002/(SICI)"));
        assertEquals("a%26b", ScopusArticleRetriever.encodeIdentifier("a&b"));
        assertEquals("frag%23x", ScopusArticleRetriever.encodeIdentifier("frag#x"));
        assertEquals("a+b", ScopusArticleRetriever.encodeIdentifier("a b"));
    }

    @Test
    void numericIdentifierUnchanged() {
        assertEquals("20000000", ScopusArticleRetriever.encodeIdentifier("20000000"));
        assertEquals("12345", ScopusArticleRetriever.encodeIdentifier(12345));
    }

    @Test
    void doiQueryDoesNotContainRawReservedChars() {
        // A DOI carrying & / space / # must not leak those into the query string raw,
        // which would split the URL or start a fragment.
        List<String> q = ScopusArticleRetriever.buildBatchQueries(
                Collections.singletonList("10.1/a&b c#d"), "doi", 25);
        assertEquals(1, q.size());
        String query = q.get(0);
        assertFalse(query.contains("&"), "raw & would start a new URL parameter");
        assertFalse(query.contains(" "), "raw space would break the URL");
        assertFalse(query.contains("#"), "raw # would start a URL fragment");
        assertTrue(query.startsWith("doi(") && query.endsWith(")"));
    }

    // ------------------------------------------------------------------
    // Batching
    // ------------------------------------------------------------------

    @Test
    void emptyInputYieldsNoQueries() {
        assertTrue(ScopusArticleRetriever.buildBatchQueries(new ArrayList<>(), "pmid", 25).isEmpty());
    }

    @Test
    void singleIdentifier() {
        assertEquals(Collections.singletonList("pmid(20000000)"),
                ScopusArticleRetriever.buildBatchQueries(Collections.singletonList("20000000"), "pmid", 25));
    }

    @Test
    void multipleIdentifiersJoinedWithOr() {
        assertEquals(Collections.singletonList("pmid(1)+OR+pmid(2)+OR+pmid(3)"),
                ScopusArticleRetriever.buildBatchQueries(Arrays.asList("1", "2", "3"), "pmid", 25));
    }

    @Test
    void splitsIntoFixedSizeBatches() {
        assertEquals(1, batchCount(25), "25 ids -> 1 batch");
        assertEquals(2, batchCount(26), "26 ids -> 2 batches");
        assertEquals(2, batchCount(50), "50 ids -> 2 batches");
        assertEquals(3, batchCount(51), "51 ids -> 3 batches");

        // Exactly one boundary-spanning case: 26 ids -> [25, 1]
        List<String> q = ScopusArticleRetriever.buildBatchQueries(ids(26), "pmid", 25);
        assertEquals(25, clauseCount(q.get(0)));
        assertEquals(1, clauseCount(q.get(1)));
    }

    private static int batchCount(int n) {
        return ScopusArticleRetriever.buildBatchQueries(ids(n), "pmid", 25).size();
    }

    private static List<Object> ids(int n) {
        List<Object> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(String.valueOf(i));
        }
        return list;
    }

    private static int clauseCount(String query) {
        // Each clause is one "pmid(" occurrence.
        int count = 0;
        int idx = 0;
        while ((idx = query.indexOf("pmid(", idx)) != -1) {
            count++;
            idx += 5;
        }
        return count;
    }
}
