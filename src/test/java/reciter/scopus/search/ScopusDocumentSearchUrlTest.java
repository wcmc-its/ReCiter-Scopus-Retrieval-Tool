package reciter.scopus.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Pins the document-search URL, whose two halves each fail SILENTLY if broken.
 *
 * <p>A document search that drops {@code author} from the field whitelist still returns HTTP 200
 * and a full page of documents — only every author list collapses to the first name, which is what
 * put a three-author book chapter on screen as "Proal A.D.". Conversely, reaching for
 * {@code view=COMPLETE} to get those authors caps a page at 25 and refuses {@code count=200} with
 * an HTTP 400. Neither shows up as a test failure anywhere else.
 */
class ScopusDocumentSearchUrlTest {

	@Test
	void requestsTheAuthorArray() {
		// The bug this fixes: without an explicit `author` field the response carries only dc:creator.
		String url = ScopusSearchService.buildDocumentSearchUrl("author", "23493733900");
		assertTrue(url.contains("field="), "document search must restrict fields, or `author` is absent");
		assertTrue(fieldList(url).contains("author"), "field whitelist must request the full author array");
	}

	@Test
	void keepsEveryFieldTheCallerReads() {
		// `field` is a whitelist: anything missing here is absent from the response. PM drops any
		// entry without dc:identifier, so an omission here empties the results tab.
		String fields = fieldList(ScopusSearchService.buildDocumentSearchUrl("author", "23493733900"));
		for (String required : new String[] { "dc:identifier", "dc:title", "dc:creator", "author",
				"prism:doi", "prism:coverDate", "prism:publicationName", "pubmed-id", "subtypeDescription" }) {
			assertTrue(fields.contains(required), "field whitelist is missing " + required);
		}
	}

	@Test
	void doesNotPayForAuthorsWithTheCompleteViewCeiling() {
		// view=COMPLETE would return authors too, but caps a page at 25 and 400s on count=200 —
		// trading missing authors for missing documents. Probed live; see ScopusSearchService.
		String url = ScopusSearchService.buildDocumentSearchUrl("author", "23493733900");
		assertFalse(url.contains("view=COMPLETE"), "COMPLETE view refuses count=200");
		assertTrue(url.contains("count=200"), "a prolific author's full result set must fit one call");
	}

	@Test
	void startZeroIsByteIdenticalToTheTwoArgCall() {
		// Paging must not change a single byte of the URL that existing callers (and every other
		// test in this class) already depend on.
		String unpaged = ScopusSearchService.buildDocumentSearchUrl("author", "23493733900");
		String pagedAtZero = ScopusSearchService.buildDocumentSearchUrl("author", "23493733900", 0);
		assertEquals(unpaged, pagedAtZero, "start=0 must produce the exact same URL as the 2-arg call");
		assertFalse(pagedAtZero.contains("start="), "start=0 must be omitted, not sent as start=0");
	}

	@Test
	void startAboveZeroAppendsTheOffsetAndKeepsEverythingElse() {
		String page1 = ScopusSearchService.buildDocumentSearchUrl("author", "23493733900");
		String page2 = ScopusSearchService.buildDocumentSearchUrl("author", "23493733900", 200);
		assertTrue(page2.contains("&start=200"), "start=200 must be appended once start > 0");
		assertTrue(page2.contains("count=200"), "page size must stay 200 regardless of start");
		assertTrue(page2.contains("&sort=-coverDate"), "start must not disturb the author-search sort");
		assertEquals(fieldList(page1), fieldList(page2), "start must not disturb the field whitelist");
	}

	@Test
	void startCarriesThroughTheKeywordAndDoiShapes() {
		String keyword = ScopusSearchService.buildDocumentSearchUrl("keyword", "vitamin d", 200);
		assertTrue(keyword.contains("TITLE-ABS-KEY%28"), "keyword search must still use TITLE-ABS-KEY()");
		assertTrue(keyword.contains("&start=200"), "keyword search must carry start when > 0");

		String doi = ScopusSearchService.buildDocumentSearchUrl("doi", "10.1016/j.autrev.2009.02.011", 200);
		assertTrue(doi.contains("DOI%28"), "doi search must still use the DOI() field");
		assertTrue(doi.contains("&start=200"), "doi search must carry start when > 0");
	}

	@Test
	void stillBuildsTheThreeQueryShapes() {
		assertTrue(ScopusSearchService.buildDocumentSearchUrl("author", "AUTHOR_ID:23493733900")
				.contains("AU-ID%2823493733900%29"), "author search must strip the AUTHOR_ID: prefix");
		assertTrue(ScopusSearchService.buildDocumentSearchUrl("doi", "10.1016/j.autrev.2009.02.011")
				.contains("DOI%28"), "doi search must use the DOI() field");
		String keyword = ScopusSearchService.buildDocumentSearchUrl("keyword", "vitamin d");
		assertTrue(keyword.contains("TITLE-ABS-KEY%28"), "keyword search must use TITLE-ABS-KEY()");
		assertFalse(keyword.contains("sort="), "keyword search stays in relevance order");
	}

	/** The decoded value of the field= parameter, stopping before any parameter that follows it. */
	private static String fieldList(String url) {
		int i = url.indexOf("field=");
		if (i < 0) {
			return "";
		}
		String rest = url.substring(i + "field=".length());
		int amp = rest.indexOf('&');
		String raw = amp < 0 ? rest : rest.substring(0, amp);
		return raw.replace("%3A", ":").replace("%2C", ",");
	}
}
