package reciter.scopus.callable;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import reciter.model.scopus.ScopusArticle;
import reciter.scopus.xmlparser.ScopusXmlHandler;

public class ScopusUriParserCallable implements Callable<List<ScopusArticle>> {

	private static final Logger slf4jLogger = LoggerFactory.getLogger(ScopusUriParserCallable.class);

	private static final String INST_TOKEN = System.getenv("SCOPUS_INST_TOKEN");
	private static final String API_KEY = System.getenv("SCOPUS_API_KEY");

	private static final int CONNECT_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(15);
	private static final int READ_TIMEOUT_MS = (int) TimeUnit.SECONDS.toMillis(120);

	/**
	 * Shared, XXE-hardened SAX parser factory. {@code SAXParserFactory} configuration is
	 * not thread-safe, but it is configured once here and {@link #newHardenedParser()}
	 * synchronizes the {@code newSAXParser()} call; each {@link SAXParser} is then used by
	 * a single thread.
	 */
	private static final SAXParserFactory SAX_PARSER_FACTORY = createHardenedFactory();

	private final ScopusXmlHandler xmlHandler;
	private final String uri;

	public ScopusUriParserCallable(ScopusXmlHandler xmlHandler, String uri) {
		this.xmlHandler = xmlHandler;
		this.uri = uri;
	}

	private static SAXParserFactory createHardenedFactory() {
		SAXParserFactory factory = SAXParserFactory.newInstance();
		try {
			// Block DTDs entirely (Scopus responses contain none) — the strongest XXE defense.
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setXIncludeAware(false);
		} catch (ParserConfigurationException | SAXNotRecognizedException | SAXNotSupportedException e) {
			slf4jLogger.warn("Could not fully harden SAX parser factory against XXE: {}", e.getMessage());
		}
		return factory;
	}

	private static SAXParser newHardenedParser() throws ParserConfigurationException, SAXException {
		synchronized (SAX_PARSER_FACTORY) {
			return SAX_PARSER_FACTORY.newSAXParser();
		}
	}

	public List<ScopusArticle> parse(String uri) throws ParserConfigurationException, SAXException, IOException {
		URL url = new URL(uri);
		slf4jLogger.info(url.toString());
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		try {
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
			conn.setReadTimeout(READ_TIMEOUT_MS);
			conn.setRequestProperty("Accept", "application/xml");
			conn.setRequestProperty("X-ELS-Insttoken", INST_TOKEN);
			conn.setRequestProperty("X-ELS-APIKey", API_KEY);

			SAXParser saxParser = newHardenedParser();
			try (InputStream in = conn.getInputStream()) {
				saxParser.parse(new InputSource(in), xmlHandler);
			}
		} finally {
			conn.disconnect();
		}

		List<ScopusArticle> scopusArticles = xmlHandler.getScopusArticles();
		int errorCount = xmlHandler.getErrorEntryCount();
		if (errorCount > 0) {
			slf4jLogger.warn("Scopus batch had {} error entries (articles silently dropped) for query=[{}]", errorCount, uri);
		}
		slf4jLogger.info("Number of Scopus article retrieved=[{}], errors=[{}] for query=[{}]",
				scopusArticles.size(), errorCount, uri);
		return scopusArticles;
	}

	@Override
	public List<ScopusArticle> call() throws Exception {
		return parse(uri);
	}

}
