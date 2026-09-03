package reciter.controller;

import java.net.http.HttpResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reciter.scopus.search.ScopusSearchRequest;
import reciter.scopus.search.ScopusSearchService;

/**
 * Scopus <em>search</em> endpoints (distinct from the id → article retrieval on
 * {@link ScopusController}). Both proxy Elsevier with the tool's credentials and return
 * the Elsevier {@code search-results} JSON verbatim, so the caller keeps its own parsing.
 *
 *   GET  /scopus/search/documents?by={author|keyword|doi}&term=...&start=...
 *   GET  /scopus/search/authors?lastName=...&firstName=...&affiliation=...
 *   POST /scopus/search/query    {query, count, start, view}   — query passed through VERBATIM
 */
@RestController
@RequestMapping("/scopus/search")
public class ScopusSearchController {

	private static final Logger slf4jLogger = LoggerFactory.getLogger(ScopusSearchController.class);

	private final ScopusSearchService searchService;

	public ScopusSearchController(ScopusSearchService searchService) {
		this.searchService = searchService;
	}

	@GetMapping(value = "/documents", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> documents(
			@RequestParam(name = "by", required = false) String by,
			@RequestParam(name = "term") String term,
			@RequestParam(name = "start", required = false) Integer start) {
		if (isBlank(term)) {
			return ResponseEntity.badRequest().body("{\"error\":\"term is required\"}");
		}
		if (start != null && start < 0) {
			return ResponseEntity.badRequest().body("{\"error\":\"start must be >= 0\"}");
		}
		if (!searchService.isConfigured()) {
			return credentialsMissing();
		}
		try {
			return passthrough(searchService.searchDocuments(by, term, start == null ? 0 : start));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return upstreamFailure(e);
		} catch (Exception e) {
			return upstreamFailure(e);
		}
	}

	@GetMapping(value = "/authors", produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> authors(
			@RequestParam(name = "lastName") String lastName,
			@RequestParam(name = "firstName", required = false) String firstName,
			@RequestParam(name = "affiliation", required = false) String affiliation) {
		if (isBlank(lastName)) {
			return ResponseEntity.badRequest().body("{\"error\":\"lastName is required\"}");
		}
		if (!searchService.isConfigured()) {
			return credentialsMissing();
		}
		try {
			return passthrough(searchService.searchAuthors(lastName, firstName, affiliation));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return upstreamFailure(e);
		} catch (Exception e) {
			return upstreamFailure(e);
		}
	}

	/**
	 * Run a search strategy: the query goes to Elsevier <strong>verbatim</strong>, and {@code count},
	 * {@code start} and {@code view} are the caller's to set.
	 *
	 * <p>{@code /documents} cannot do this. It force-wraps every term in {@code TITLE-ABS-KEY(...)},
	 * which makes a top-level limit ({@code AND PUBYEAR > 2020}) impossible to express — nested
	 * inside the wrap, Elsevier rejects it outright. That wrap is right for a keyword lookup and
	 * fatal for a peer-reviewable strategy, so this is a second endpoint rather than a flag on the
	 * first: the two have genuinely different contracts for what {@code query} means.
	 *
	 * <p>POST, not GET, because a real strategy is thousands of characters once URL-encoded.
	 */
	@PostMapping(value = "/query",
			consumes = MediaType.APPLICATION_JSON_VALUE,
			produces = MediaType.APPLICATION_JSON_VALUE)
	public ResponseEntity<String> query(@RequestBody ScopusSearchRequest request) {
		validate(request);                       // IllegalArgumentException -> 400 (GlobalExceptionHandler)
		if (!searchService.isConfigured()) {
			return credentialsMissing();
		}
		try {
			return passthrough(searchService.searchRaw(
					request.query(), request.countOrDefault(), request.startOrDefault(), request.view()));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return upstreamFailure(e);
		} catch (Exception e) {
			return upstreamFailure(e);
		}
	}

	/**
	 * The guard, and it exists to FAIL LOUDLY rather than to be helpful.
	 *
	 * <p>Every rule here is one that Elsevier would otherwise answer with a silently wrong page
	 * instead of an error, or with an error the caller could not act on:
	 *
	 * <ul>
	 *   <li><b>count &gt; the view's ceiling</b> is rejected here rather than CLAMPED. Clamping is
	 *       the tempting move — but a caller who asked for 50 COMPLETE records and got 25 without
	 *       being told would render "top 50" over half a page. Elsevier itself answers a bare 400
	 *       {@code INVALID_INPUT}; this at least says which knob to turn.</li>
	 *   <li><b>count = 0</b> is rejected because Elsevier IGNORES it and returns 25 records. A
	 *       caller who wants only the total asks for {@code count=1} and reads
	 *       {@code opensearch:totalResults}.</li>
	 * </ul>
	 */
	static void validate(ScopusSearchRequest r) {
		if (r == null || isBlank(r.query())) {
			throw new IllegalArgumentException("query is required");
		}
		String view = r.view() == null ? "" : r.view().trim();
		if (!view.isEmpty() && !view.equalsIgnoreCase("STANDARD") && !view.equalsIgnoreCase("COMPLETE")) {
			throw new IllegalArgumentException("view must be STANDARD or COMPLETE");
		}
		if (r.count() != null) {
			int max = ScopusSearchService.maxCountFor(view);
			if (r.count() < 1) {
				throw new IllegalArgumentException(
						"count must be at least 1; Scopus ignores count=0 and silently returns 25 records. "
								+ "For a count without records, use count=1 and read opensearch:totalResults.");
			}
			if (r.count() > max) {
				throw new IllegalArgumentException("count must be at most " + max
						+ (r.isCompleteView()
								? " when view=COMPLETE; page through the rest with start."
								: "; page through the rest with start."));
			}
		}
		if (r.start() != null && r.start() < 0) {
			throw new IllegalArgumentException("start must be zero or greater");
		}
	}

	// Pass Elsevier's status and JSON body straight through to the caller.
	private ResponseEntity<String> passthrough(HttpResponse<String> resp) {
		return ResponseEntity.status(resp.statusCode())
				.contentType(MediaType.APPLICATION_JSON)
				.body(resp.body());
	}

	private ResponseEntity<String> credentialsMissing() {
		return ResponseEntity.status(503)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"Scopus credentials are not configured on this server.\"}");
	}

	private ResponseEntity<String> upstreamFailure(Exception e) {
		slf4jLogger.error("Scopus search failed", e);
		return ResponseEntity.status(502)
				.contentType(MediaType.APPLICATION_JSON)
				.body("{\"error\":\"Scopus search failed\"}");
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}
}
