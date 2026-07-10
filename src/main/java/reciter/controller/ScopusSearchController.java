package reciter.controller;

import java.net.http.HttpResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reciter.scopus.search.ScopusSearchService;

/**
 * Scopus <em>search</em> endpoints (distinct from the id → article retrieval on
 * {@link ScopusController}). Both proxy Elsevier with the tool's credentials and return
 * the Elsevier {@code search-results} JSON verbatim, so the caller keeps its own parsing.
 *
 *   GET /scopus/search/documents?by={author|keyword|doi}&term=...
 *   GET /scopus/search/authors?lastName=...&firstName=...&affiliation=...
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
			@RequestParam(name = "term") String term) {
		if (isBlank(term)) {
			return ResponseEntity.badRequest().body("{\"error\":\"term is required\"}");
		}
		if (!searchService.isConfigured()) {
			return credentialsMissing();
		}
		try {
			return passthrough(searchService.searchDocuments(by, term));
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
