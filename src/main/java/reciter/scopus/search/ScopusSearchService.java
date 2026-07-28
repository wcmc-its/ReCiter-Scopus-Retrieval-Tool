package reciter.scopus.search;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Authenticated proxy for Elsevier's Scopus <em>search</em> APIs (document search and
 * author search), which the article-retrieval path ({@link reciter.scopus.retriever.ScopusArticleRetriever})
 * does not cover. The Publication Manager used to call Elsevier directly and therefore
 * needed its own API key; routing through here keeps the Elsevier credentials in one place.
 *
 * <p>This is a thin proxy: it builds the Scopus query, calls Elsevier with the tool's
 * credentials, and returns the Elsevier search JSON verbatim (status and body passed
 * through). The caller parses {@code search-results} as before.
 */
@Service
public class ScopusSearchService {

	private static final Logger slf4jLogger = LoggerFactory.getLogger(ScopusSearchService.class);

	private static final String SCOPUS_SEARCH = "https://api.elsevier.com/content/search/scopus";
	private static final String AUTHOR_SEARCH = "https://api.elsevier.com/content/search/author";

	/** Scopus returns at most this many documents per request; the JSON's total reports the real count. */
	private static final int DOCUMENT_PAGE_SIZE = 200;

	/**
	 * Fields requested for a document search. {@code field} is a WHITELIST — anything omitted here
	 * is absent from the response — so this list must cover everything a caller reads.
	 *
	 * <p>Its real job is {@code author}. The default view populates only {@code dc:creator}, the
	 * FIRST author, so a three-author chapter came back as one name. The obvious fix,
	 * {@code view=COMPLETE}, is a trap: it caps a page at {@link #MAX_COUNT_COMPLETE} and REFUSES
	 * {@code count=200} with HTTP 400 (see below), so it would trade missing authors for missing
	 * documents, or force paging. Asking for {@code author} by field returns the full author array
	 * at {@code count=200} in a single call — probed live: 117/117 entries carried it.
	 */
	private static final String DOCUMENT_FIELDS = String.join(",",
			"dc:identifier", "dc:title", "dc:creator", "author", "prism:doi",
			"prism:coverDate", "prism:publicationName", "pubmed-id", "subtypeDescription");
	private static final int AUTHOR_PAGE_SIZE = 10;
	private static final String DEFAULT_AFFILIATION = "Weill Cornell";

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

	// Elsevier intermittently resets the connection under load; retry the transient I/O a
	// few times (short backoff) so a reset surfaces as a result rather than a 502. The
	// article-retrieval path retries similarly (via guava-retrying); this is the lightweight
	// equivalent for a single synchronous search request.
	private static final int MAX_ATTEMPTS = 3;
	private static final long RETRY_BACKOFF_MS = 250L;

	// Same credentials the retrieval path uses (injected into the deployment as env/secret).
	private static final String INST_TOKEN = System.getenv("SCOPUS_INST_TOKEN");
	private static final String API_KEY = System.getenv("SCOPUS_API_KEY");

	private final HttpClient httpClient;

	public ScopusSearchService() {
		this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
		if (API_KEY == null || INST_TOKEN == null) {
			slf4jLogger.warn("SCOPUS_API_KEY and/or SCOPUS_INST_TOKEN are not set; "
					+ "Scopus search requests will be rejected until they are configured.");
		}
	}

	/** True once both Elsevier credentials are present; the controller returns 503 otherwise. */
	public boolean isConfigured() {
		return API_KEY != null && INST_TOKEN != null;
	}

	/**
	 * Search Scopus documents. {@code by} selects the query field:
	 * {@code keyword} → TITLE-ABS-KEY (relevance order), {@code doi} → DOI, anything else
	 * → AU-ID (author id, newest first). Returns Elsevier's {@code search-results} JSON.
	 */
	public HttpResponse<String> searchDocuments(String by, String term)
			throws IOException, InterruptedException {
		return get(buildDocumentSearchUrl(by, term));
	}

	/** Package-private so the field whitelist and the page size can be pinned by a test. */
	static String buildDocumentSearchUrl(String by, String term) {
		String t = term == null ? "" : term.trim();
		String query;
		String sort = "&sort=-coverDate";
		if ("keyword".equals(by)) {
			query = "TITLE-ABS-KEY(" + t + ")";
			sort = ""; // relevance order
		} else if ("doi".equals(by)) {
			query = "DOI(" + t + ")";
		} else {
			query = "AU-ID(" + t.replaceFirst("(?i)^AUTHOR_ID:", "") + ")";
		}
		return SCOPUS_SEARCH + "?query=" + enc(query) + "&count=" + DOCUMENT_PAGE_SIZE + sort
				+ "&field=" + enc(DOCUMENT_FIELDS);
	}

	/**
	 * Elsevier's page-size ceilings, which differ BY VIEW. Probed against the live API rather than
	 * read off a page: with {@code view=COMPLETE}, {@code count=50} and {@code count=200} both come
	 * back <strong>HTTP 400 INVALID_INPUT</strong> — not a truncated page, a refusal. So a caller
	 * who wants more than 25 COMPLETE records must PAGE with {@code start}; there is no single call
	 * that will do it, and pretending otherwise is how you end up printing "top 50" over 25 records.
	 */
	public static final int MAX_COUNT_COMPLETE = 25;
	public static final int MAX_COUNT_STANDARD = 200;

	/** The ceiling that applies to a given view. */
	public static int maxCountFor(String view) {
		return view != null && "COMPLETE".equalsIgnoreCase(view.trim()) ? MAX_COUNT_COMPLETE : MAX_COUNT_STANDARD;
	}

	/**
	 * Search Scopus with the query passed through <strong>VERBATIM</strong>.
	 *
	 * <p>This is the whole point of this method, and the difference between it and
	 * {@link #searchDocuments}: that one force-wraps the caller's terms in
	 * {@code TITLE-ABS-KEY(...)}, so a caller can never express a TOP-LEVEL limit. Nest
	 * {@code PUBYEAR > 2020} inside {@code TITLE-ABS-KEY()} and Elsevier answers
	 * {@code HTTP 400 "Error translating query"} — loudly, which is the good news, but it means the
	 * wrapped endpoint simply cannot run a real search strategy. Verified against the live API:
	 * passed raw, a query narrows exactly as a librarian expects
	 * (2,900 → {@code AND PUBYEAR > 2020} → 1,958 → {@code AND DOCTYPE(ar)} → 1,376).
	 *
	 * <p>The caller writes native Scopus. NOTHING here translates a query from another database's
	 * syntax, and nothing ever should: Cochrane and PRESS expect a bespoke, separately peer-reviewed
	 * strategy per database, and a mechanical MeSH → Scopus transliteration is exactly the artifact
	 * a librarian would reject at review.
	 *
	 * <p>To COUNT without retrieving, ask for {@code count=1} and read
	 * {@code search-results.opensearch:totalResults}. Not {@code count=0} — Elsevier ignores that
	 * and silently returns 25 records.
	 */
	public HttpResponse<String> searchRaw(String query, int count, int start, String view)
			throws IOException, InterruptedException {
		StringBuilder url = new StringBuilder(SCOPUS_SEARCH)
				.append("?query=").append(enc(query.trim()))
				.append("&count=").append(count)
				.append("&start=").append(start);
		if (!nullToEmpty(view).trim().isEmpty()) {
			url.append("&view=").append(enc(view.trim().toUpperCase()));
		}
		return get(url.toString());
	}

	/**
	 * Search Scopus authors by name, scoped to an affiliation (defaults to Weill Cornell).
	 * Returns Elsevier's {@code search-results} JSON of author profiles.
	 */
	public HttpResponse<String> searchAuthors(String lastName, String firstName, String affiliation)
			throws IOException, InterruptedException {
		StringBuilder q = new StringBuilder("authlast(").append(nullToEmpty(lastName).trim()).append(")");
		String first = nullToEmpty(firstName).trim();
		if (!first.isEmpty()) {
			q.append(" AND authfirst(").append(first).append(")");
		}
		String affil = nullToEmpty(affiliation).trim();
		q.append(" AND affil(").append(affil.isEmpty() ? DEFAULT_AFFILIATION : affil).append(")");
		String url = AUTHOR_SEARCH + "?query=" + enc(q.toString()) + "&count=" + AUTHOR_PAGE_SIZE;
		return get(url);
	}

	private HttpResponse<String> get(String url) throws IOException, InterruptedException {
		slf4jLogger.info("Scopus search: {}", url);
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(REQUEST_TIMEOUT)
				.header("Accept", "application/json")
				.header("X-ELS-Insttoken", INST_TOKEN)
				.header("X-ELS-APIKey", API_KEY)
				.GET()
				.build();
		IOException last = null;
		for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
			try {
				return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			} catch (IOException e) {
				// Transient (connection reset, timeout) — retry; a permanent failure still
				// throws after the last attempt and the controller maps it to 502.
				last = e;
				slf4jLogger.warn("Scopus search attempt {}/{} failed ({}); retrying", attempt, MAX_ATTEMPTS, e.toString());
				if (attempt < MAX_ATTEMPTS) {
					Thread.sleep(RETRY_BACKOFF_MS * attempt);
				}
			}
		}
		throw last;
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String nullToEmpty(String s) {
		return s == null ? "" : s;
	}
}
