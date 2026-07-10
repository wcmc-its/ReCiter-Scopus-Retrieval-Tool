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
	private static final int AUTHOR_PAGE_SIZE = 10;
	private static final String DEFAULT_AFFILIATION = "Weill Cornell";

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

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
		String url = SCOPUS_SEARCH + "?query=" + enc(query) + "&count=" + DOCUMENT_PAGE_SIZE + sort;
		return get(url);
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
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(REQUEST_TIMEOUT)
				.header("Accept", "application/json")
				.GET();
		builder.header("X-ELS-Insttoken", INST_TOKEN);
		builder.header("X-ELS-APIKey", API_KEY);
		return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	private static String nullToEmpty(String s) {
		return s == null ? "" : s;
	}
}
