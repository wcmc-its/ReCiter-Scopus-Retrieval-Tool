package reciter.scopus.callable;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
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

public class ScopusUriParserCallable implements Callable<List<ScopusArticle>> {

	private static final Logger slf4jLogger = LoggerFactory.getLogger(ScopusUriParserCallable.class);
	
	private static final String INST_TOKEN = System.getenv("SCOPUS_INST_TOKEN");
	private static final String API_KEY = System.getenv("SCOPUS_API_KEY");
	
	private final ScopusXmlHandler xmlHandler;
	private final String uri;
	
	public ScopusUriParserCallable(ScopusXmlHandler xmlHandler, String uri) {
		this.xmlHandler = xmlHandler;
		this.uri = uri;
	}
	
	public List<ScopusArticle> parse(String uri) throws ParserConfigurationException, SAXException, IOException {
		URL url = new URL(uri);
		slf4jLogger.info(url.toString());
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setRequestMethod("GET");
		conn.setRequestProperty("Accept", "application/xml");
		conn.setRequestProperty("X-ELS-Insttoken", INST_TOKEN);
		conn.setRequestProperty("X-ELS-APIKey", API_KEY);
		
		InputSource source = new InputSource(conn.getInputStream());
		
		SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
		saxParser.parse(source, xmlHandler);
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