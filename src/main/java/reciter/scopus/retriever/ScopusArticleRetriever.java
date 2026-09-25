package reciter.scopus.retriever;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.base.Predicates;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import reciter.model.scopus.ScopusArticle;
import reciter.scopus.querybuilder.ScopusXmlQuery;
import reciter.scopus.xmlparser.ScopusXmlHandler;

/**
 * Retrieves Scopus articles for a list of identifiers.
 *
 * <p>This is a Spring singleton that owns the heavyweight, reusable resources — a pooled
 * {@link HttpClient}, a virtual-thread-per-task {@link ExecutorService}, and an XXE-hardened
 * {@link SAXParserFactory} — instead of allocating a thread pool and HTTP connection per
 * request. Each request's identifiers are split into batches; batches are fetched and parsed
 * concurrently on virtual threads (in-flight requests bounded by a {@link Semaphore}, since an
 * unbounded virtual-thread executor would otherwise fire every batch at once), with transient
 * I/O retried. A batch that ultimately fails is logged and skipped so the caller still receives
 * the results that did succeed.</p>
 */
@Service
public class ScopusArticleRetriever {

	private static final Logger log = LoggerFactory.getLogger(ScopusArticleRetriever.class);

	/**
	 * Maximum number of identifiers per Scopus request. Each identifier (PMID/DOI/Scopus-ID)
	 * matches at most one article, so this also bounds the result count requested per batch.
	 */
	protected static final int SCOPUS_BATCH_SIZE = 25;

	/**
	 * Upper bound on Scopus requests in flight at once (mild throttle against the API).
	 * Virtual threads are cheap and unbounded by default, so this is enforced explicitly via
	 * {@link #concurrencyLimiter} rather than by the executor's pool size.
	 */
	private static final int MAX_CONCURRENT_BATCHES = 8;

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(120);

	private static final String INST_TOKEN = System.getenv("SCOPUS_INST_TOKEN");
	private static final String API_KEY = System.getenv("SCOPUS_API_KEY");

	private final ExecutorService executor;
	private final Semaphore concurrencyLimiter;
	private final HttpClient httpClient;
	private final SAXParserFactory saxParserFactory;
	private final Retryer<List<ScopusArticle>> retryer;

	public ScopusArticleRetriever() {
		this.executor = Executors.newThreadPerTaskExecutor(virtualThreadFactory());
		this.concurrencyLimiter = new Semaphore(MAX_CONCURRENT_BATCHES);
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(CONNECT_TIMEOUT)
				.build();
		this.saxParserFactory = createHardenedFactory();
		// Retry only transient I/O (timeouts, connection resets, 429/5xx). Deterministic
		// failures (parse errors, 4xx) are not retried — re-fetching would fail identically.
		this.retryer = RetryerBuilder.<List<ScopusArticle>>newBuilder()
				.retryIfResult(Predicates.<List<ScopusArticle>>isNull())
				.retryIfExceptionOfType(IOException.class)
				.withWaitStrategy(WaitStrategies.fibonacciWait(100L, 2L, TimeUnit.MINUTES))
				.withStopStrategy(StopStrategies.stopAfterAttempt(10))
				.build();
		if (API_KEY == null || INST_TOKEN == null) {
			log.warn("SCOPUS_API_KEY and/or SCOPUS_INST_TOKEN are not set; "
					+ "Scopus requests will be rejected (401) until they are configured.");
		}
	}

	/**
	 * @param pmids identifiers to look up (PMID, DOI, or Scopus ID)
	 * @param type Scopus field code, e.g. {@code pmid}, {@code doi}
	 * @return the articles that were successfully retrieved (partial if some batches failed)
	 */
	public List<ScopusArticle> retrieveScopus(List<Object> pmids, String type) {
		log.info("Retrieving {} Scopus identifier(s) of type [{}]", pmids.size(), type);
		List<String> pmidQueries = buildBatchQueries(pmids, type, SCOPUS_BATCH_SIZE);

		List<Callable<List<ScopusArticle>>> callables = new ArrayList<>();
		for (String query : pmidQueries) {
			String scopusUrl = new ScopusXmlQuery.ScopusXmlQueryBuilder(query, SCOPUS_BATCH_SIZE)
					.build().getQueryUrl();
			callables.add(retryer.wrap(() -> fetchAndParseThrottled(scopusUrl)));
		}

		List<ScopusArticle> results = new ArrayList<>();
		try {
			List<Future<List<ScopusArticle>>> futures = executor.invokeAll(callables);
			for (Future<List<ScopusArticle>> future : futures) {
				try {
					results.addAll(future.get());
				} catch (ExecutionException e) {
					// One batch failed after retries; keep the others rather than failing the
					// whole request (the caller treats an exception as "no results at all").
					log.error("A Scopus batch failed after retries; returning partial results.",
							e.getCause());
				}
			}
		} catch (InterruptedException e) {
			log.error("Interrupted while retrieving Scopus batches; returning partial results.", e);
			Thread.currentThread().interrupt();
		}
		log.info("Retrieved {} Scopus article(s) across {} batch(es).", results.size(), pmidQueries.size());
		return results;
	}

	/**
	 * Acquires a {@link #concurrencyLimiter} permit before hitting Scopus and releases it
	 * afterward, so a burst of virtual threads still only has {@link #MAX_CONCURRENT_BATCHES}
	 * requests in flight at once — the throttle the old fixed platform-thread pool gave us for
	 * free via its bounded size.
	 */
	private List<ScopusArticle> fetchAndParseThrottled(String url)
			throws IOException, InterruptedException, ParserConfigurationException, SAXException {
		concurrencyLimiter.acquire();
		try {
			return fetchAndParse(url);
		} finally {
			concurrencyLimiter.release();
		}
	}

	private List<ScopusArticle> fetchAndParse(String url)
			throws IOException, InterruptedException, ParserConfigurationException, SAXException {
		log.info(url);
		HttpRequest.Builder builder = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(REQUEST_TIMEOUT)
				.header("Accept", "application/xml")
				.GET();
		if (INST_TOKEN != null) {
			builder.header("X-ELS-Insttoken", INST_TOKEN);
		}
		if (API_KEY != null) {
			builder.header("X-ELS-APIKey", API_KEY);
		}

		HttpResponse<InputStream> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
		int status = response.statusCode();
		if (status == 429 || status >= 500) {
			// Transient — let the retryer try again.
			try (InputStream ignored = response.body()) {
				throw new IOException("Scopus returned transient HTTP " + status + " for " + url);
			}
		}
		if (status >= 300) {
			// Permanent (e.g. 401/403/404) — not worth retrying; fail this batch only.
			try (InputStream ignored = response.body()) {
				throw new IllegalStateException("Scopus returned HTTP " + status + " for " + url);
			}
		}

		ScopusXmlHandler xmlHandler = new ScopusXmlHandler();
		SAXParser saxParser = newParser();
		try (InputStream in = response.body()) {
			saxParser.parse(new InputSource(in), xmlHandler);
		}
		List<ScopusArticle> articles = xmlHandler.getScopusArticles();
		int errorCount = xmlHandler.getErrorEntryCount();
		if (errorCount > 0) {
			log.warn("Scopus batch had {} error entries (articles dropped) for query=[{}]", errorCount, url);
		}
		log.info("Number of Scopus article retrieved=[{}], errors=[{}] for query=[{}]",
				articles.size(), errorCount, url);
		return articles;
	}

	private SAXParser newParser() throws ParserConfigurationException, SAXException {
		// SAXParserFactory configuration is not thread-safe; serialize parser creation. Each
		// SAXParser is then used by a single batch thread.
		synchronized (saxParserFactory) {
			return saxParserFactory.newSAXParser();
		}
	}

	@PreDestroy
	void shutdown() {
		executor.shutdown();
		try {
			if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	private static SAXParserFactory createHardenedFactory() {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		try {
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setXIncludeAware(false);
		} catch (ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException e) {
			log.warn("Could not fully harden SAX parser factory against XXE: {}", e.getMessage());
		}
		return factory;
	}

	/**
	 * Named, unpooled virtual threads (one per task) replacing the old fixed platform-thread
	 * pool. Virtual threads are daemon by nature and cheap to create, so no pool sizing is
	 * needed here — concurrency is instead capped by {@link #concurrencyLimiter}.
	 */
	private static ThreadFactory virtualThreadFactory() {
		return Thread.ofVirtual().name("scopus-retriever-", 0).factory();
	}

	/**
	 * Splits the identifiers into batches of at most {@code batchSize} and builds one Scopus
	 * OR-query per batch, e.g. {@code doi(10.1/x)+OR+doi(10.2/y)}. Each identifier value is
	 * URL-encoded so a DOI containing reserved characters (&amp;, #, spaces, parentheses)
	 * cannot break the request URL or inject extra query parameters. The forward slash present
	 * in every DOI is kept literal, matching the long-standing working behavior.
	 */
	static List<String> buildBatchQueries(List<Object> identifiers, String type, int batchSize) {
		List<String> queries = new ArrayList<>();
		for (int start = 0; start < identifiers.size(); start += batchSize) {
			int end = Math.min(start + batchSize, identifiers.size());
			StringBuilder sb = new StringBuilder();
			for (int i = start; i < end; i++) {
				if (i > start) {
					sb.append("+OR+");
				}
				sb.append(type).append('(').append(encodeIdentifier(identifiers.get(i))).append(')');
			}
			queries.add(sb.toString());
		}
		return queries;
	}

	static String encodeIdentifier(Object value) {
		return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8).replace("%2F", "/");
	}
}