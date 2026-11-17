package reciter.scopus.callable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import reciter.model.scopus.ScopusArticle;
import reciter.scopus.xmlparser.ScopusXmlHandler;

/**
 * Fetches one Scopus XML page and parses it into {@link ScopusArticle} objects.
 *
 * <h3>Java 21 improvements over the original</h3>
 * <ul>
 *   <li><b>java.net.http.HttpClient</b> replaces the legacy {@code HttpURLConnection}.
 *       Its blocking {@code send()} call is virtual-thread-friendly on JDK 21 — the
 *       carrier thread is unmounted (not pinned) while waiting on the network, as long
 *       as this Callable is run on a virtual-thread executor.</li>
 *   <li><b>Shared HttpClient instance</b> — a single client is reused across all
 *       callables, so TCP connections are pooled and TLS handshakes are amortised.</li>
 *   <li><b>ThreadLocal SAXParser pool</b> — {@code SAXParserFactory.newInstance()} and
 *       {@code factory.newSAXParser()} are both expensive (reflection + synchronisation
 *       inside the JDK). This is a real win if this Callable is submitted to a bounded
 *       platform-thread pool. If it instead runs on a fresh virtual thread per task
 *       (e.g. {@code Executors.newVirtualThreadPerTaskExecutor()}), each virtual thread
 *       is typically short-lived and not reused, so the pool provides little benefit —
 *       in that case a plain {@code factory.newSAXParser()} per call is simpler and
 *       avoids retaining parser instances via ThreadLocal.</li>
 * </ul>
 */
public class ScopusUriParserCallable implements Callable<List<ScopusArticle>> {

	private static final Logger log = LoggerFactory.getLogger(ScopusUriParserCallable.class);

	private static final String INST_TOKEN = System.getenv("SCOPUS_INST_TOKEN");
	private static final String API_KEY    = System.getenv("SCOPUS_API_KEY");

	/**
	 * Shared across every callable / virtual thread.  HttpClient maintains an internal
	 * connection pool, so reusing a single instance is both safe and beneficial.
	 */
	private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(15))
			.build();

	/**
	 * One SAXParser per thread (virtual or platform).  The handler is reset by passing a
	 * fresh {@link ScopusXmlHandler} on each call, so no state leaks between requests.
	 */
	private static final ThreadLocal<SAXParser> SAX_PARSER_POOL = ThreadLocal.withInitial(() -> {
		try {
			SAXParserFactory factory = SAXParserFactory.newInstance();
			return factory.newSAXParser();
		} catch (ParserConfigurationException | SAXException e) {
			throw new IllegalStateException("Failed to initialise SAXParser", e);
		}
	});

	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

	private final ScopusXmlHandler xmlHandler;
	private final String uri;

	public ScopusUriParserCallable(ScopusXmlHandler xmlHandler, String uri) {
		this.xmlHandler = xmlHandler;
		this.uri = uri;
	}

	public List<ScopusArticle> parse(String uri) throws IOException, InterruptedException, SAXException {
		log.info("Fetching Scopus URL: {}", uri);

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(uri))
				.timeout(REQUEST_TIMEOUT)
				.header("Accept", "application/xml")
				.header("X-ELS-Insttoken", INST_TOKEN != null ? INST_TOKEN : "")
				.header("X-ELS-APIKey",    API_KEY    != null ? API_KEY    : "")
				.GET()
				.build();

		HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());

		if (response.statusCode() != 200) {
			String body = response.body() != null ? new String(response.body(), StandardCharsets.UTF_8) : "";
			throw new IOException("Scopus returned HTTP " + response.statusCode() + " for " + uri
					+ (body.isBlank() ? "" : ": " + body));
		}

		SAXParser parser = SAX_PARSER_POOL.get();
		parser.reset(); // clear any state from a previous parse on this thread
		parser.parse(new InputSource(new ByteArrayInputStream(response.body())), xmlHandler);

		List<ScopusArticle> scopusArticles = xmlHandler.getScopusArticles();
		log.info("Number of Scopus articles retrieved=[{}] for query=[{}].",scopusArticles.size(), uri);
		return scopusArticles;
	}

	@Override
	public List<ScopusArticle> call() throws Exception {
		return parse(uri);
	}
}
