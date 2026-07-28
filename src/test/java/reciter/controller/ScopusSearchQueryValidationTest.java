package reciter.controller;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import reciter.scopus.search.ScopusSearchRequest;
import reciter.scopus.search.ScopusSearchService;

/**
 * Unit tests for {@link ScopusSearchController#validate} — the guard on the verbatim-query
 * endpoint.
 *
 * <p>Every rule pinned here is one where Elsevier's own behaviour would otherwise hand the caller a
 * confidently WRONG answer rather than an error, so a regression would be invisible:
 *
 * <ul>
 *   <li>{@code count=0} is IGNORED by Elsevier, which returns 25 records — so "just give me the
 *       total" must be {@code count=1}, and a zero has to be refused rather than passed on.</li>
 *   <li>{@code view=COMPLETE} caps a page at 25. Both of these were probed against the live API:
 *       {@code count=50&view=COMPLETE} is an HTTP 400, not a short page.</li>
 * </ul>
 */
class ScopusSearchQueryValidationTest {

	private static ScopusSearchRequest req(String query, Integer count, Integer start, String view) {
		return new ScopusSearchRequest(query, count, start, view);
	}

	private static String messageOf(ScopusSearchRequest r) {
		return assertThrows(IllegalArgumentException.class, () -> ScopusSearchController.validate(r)).getMessage();
	}

	@Test
	void acceptsAStrategyWithTopLevelLimits() {
		// The whole reason this endpoint exists: a top-level limit that TITLE-ABS-KEY() cannot hold.
		assertDoesNotThrow(() -> ScopusSearchController.validate(
				req("TITLE-ABS-KEY(probiotics AND depression) AND PUBYEAR > 2020 AND DOCTYPE(ar)", null, null, null)));
	}

	@Test
	void rejectsMissingQuery() {
		assertThrows(IllegalArgumentException.class, () -> ScopusSearchController.validate(null));
		assertThrows(IllegalArgumentException.class, () -> ScopusSearchController.validate(req(null, null, null, null)));
		assertThrows(IllegalArgumentException.class, () -> ScopusSearchController.validate(req("   ", null, null, null)));
	}

	/** count=0 is not "no records" — Elsevier ignores it and sends 25. Refuse it, and say so. */
	@Test
	void rejectsZeroCountAndSaysWhatToUseInstead() {
		String msg = messageOf(req("probiotics", 0, null, null));
		assertTrue(msg.contains("count=1"), "the error must point at the cheap-count idiom, got: " + msg);
	}

	/** The COMPLETE ceiling is 25, and a caller asking for 50 must be TOLD, not quietly given 25. */
	@Test
	void rejectsOversizedCompletePageRatherThanClampingIt() {
		String msg = messageOf(req("probiotics", 50, null, "COMPLETE"));
		assertTrue(msg.contains("25"), "the error must name the COMPLETE ceiling, got: " + msg);
		assertTrue(msg.contains("start"), "the error must say how to get the rest, got: " + msg);
		assertDoesNotThrow(() -> ScopusSearchController.validate(req("probiotics", 25, null, "COMPLETE")));
		// 50 records at COMPLETE is two pages, and both of them are legal.
		assertDoesNotThrow(() -> ScopusSearchController.validate(req("probiotics", 25, 25, "COMPLETE")));
	}

	/** STANDARD is a different ceiling, so the same count is fine there. */
	@Test
	void standardViewAllowsTwoHundred() {
		assertDoesNotThrow(() -> ScopusSearchController.validate(req("probiotics", 200, null, null)));
		assertDoesNotThrow(() -> ScopusSearchController.validate(req("probiotics", 200, null, "STANDARD")));
		assertTrue(messageOf(req("probiotics", 201, null, "STANDARD")).contains("200"));
		assertEquals(200, ScopusSearchService.maxCountFor(null));
		assertEquals(200, ScopusSearchService.maxCountFor("STANDARD"));
		assertEquals(25, ScopusSearchService.maxCountFor("complete"));   // case-insensitive, like Elsevier
	}

	@Test
	void rejectsUnknownViewAndNegativeStart() {
		assertThrows(IllegalArgumentException.class, () -> ScopusSearchController.validate(req("x", null, null, "FULL")));
		assertThrows(IllegalArgumentException.class, () -> ScopusSearchController.validate(req("x", null, -1, null)));
	}

	/** The default page has to be legal in EITHER view, or an unset count breaks COMPLETE. */
	@Test
	void defaultCountIsSafeInBothViews() {
		assertEquals(25, req("x", null, null, null).countOrDefault());
		assertEquals(0, req("x", null, null, null).startOrDefault());
		assertTrue(ScopusSearchRequest.DEFAULT_COUNT <= ScopusSearchService.MAX_COUNT_COMPLETE);
	}
}
