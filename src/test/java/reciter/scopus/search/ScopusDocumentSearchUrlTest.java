package reciter.scopus.search;

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
	void stillBuildsTheThreeQueryShapes() {
		assertTrue(ScopusSearchService.buildDocumentSearchUrl("author", "AUTHOR_ID:23493733900")
				.contains("AU-ID%2823493733900%29"), "author search must strip the AUTHOR_ID: prefix");
		assertTrue(ScopusSearchService.buildDocumentSearchUrl("doi", "10.1016/j.autrev.2009.02.011")
				.contains("DOI%28"), "doi search must use the DOI() field");
		String keyword = ScopusSearchService.buildDocumentSearchUrl("keyword", "vitamin d");
		assertTrue(keyword.contains("TITLE-ABS-KEY%28"), "keyword search must use TITLE-ABS-KEY()");
		assertFalse(keyword.contains("sort="), "keyword search stays in relevance order");
	}

	/** The decoded value of the field= parameter. */
	private static String fieldList(String url) {
		int i = url.indexOf("field=");
		return i < 0 ? "" : url.substring(i + "field=".length()).replace("%3A", ":").replace("%2C", ",");
	}
}
