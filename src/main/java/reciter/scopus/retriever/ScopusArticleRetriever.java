package reciter.scopus.retriever;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.Retryer.RetryerCallable;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.base.Predicates;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reciter.model.scopus.ScopusArticle;
import reciter.scopus.callable.ScopusUriParserCallable;
import reciter.scopus.querybuilder.ScopusXmlQuery;
import reciter.scopus.xmlparser.ScopusXmlHandler;

public class ScopusArticleRetriever {

	private static final Logger slf4jLogger = LoggerFactory.getLogger(ScopusArticleRetriever.class);

	/**
	 * Maximum number of identifiers per Scopus request. Each identifier (PMID/DOI/Scopus-ID)
	 * matches at most one article, so this also bounds the result count requested per batch.
	 */
	protected static final int SCOPUS_BATCH_SIZE = 25;

	/**
	 * @param pmids scopus query could be DOI, scopusDocId, PMID
	 * @param type to query scopus like DOI(), SCOPUS-ID(), PMID() etc.
	 * @return
	 */
	public List<ScopusArticle> retrieveScopus(List<Object> pmids, String type) {
		slf4jLogger.info("Pmids:" + pmids);
		List<String> pmidQueries = buildBatchQueries(pmids, type, SCOPUS_BATCH_SIZE);

		List<RetryerCallable<List<ScopusArticle>>> callables = new ArrayList<>();
		
		Retryer<List<ScopusArticle>> retryer = RetryerBuilder.<List<ScopusArticle>>newBuilder()
                .retryIfResult(Predicates.<List<ScopusArticle>>isNull())
                .retryIfExceptionOfType(IOException.class)
                .retryIfRuntimeException()
                .withWaitStrategy(WaitStrategies.fibonacciWait(100L, 2L, TimeUnit.MINUTES))
                .withStopStrategy(StopStrategies.stopAfterAttempt(10))
                .build();
		
		//List<Callable<List<ScopusArticle>>> callables = new ArrayList<>();

		for (String query : pmidQueries) {
			ScopusXmlQuery scopusXmlQuery = new ScopusXmlQuery.ScopusXmlQueryBuilder(query, SCOPUS_BATCH_SIZE).build();
			String scopusUrl = scopusXmlQuery.getQueryUrl();
			ScopusUriParserCallable scopusUriParserCallable = new ScopusUriParserCallable(new ScopusXmlHandler(), scopusUrl);
			RetryerCallable<List<ScopusArticle>> retryerCallable = retryer.wrap(scopusUriParserCallable);
        	callables.add(retryerCallable);
			//callables.add(scopusUriParserCallable);
		}

		List<List<ScopusArticle>> list = new ArrayList<>();

		//int numAvailableProcessors = Runtime.getRuntime().availableProcessors();
		ExecutorService executor = Executors.newCachedThreadPool();//Executors.newFixedThreadPool(numAvailableProcessors);

		try {
			executor.invokeAll(callables)
			.stream()
			.map(future -> {
				try {
					return future.get();
				}
				catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}).forEach(list::add);
		} catch (InterruptedException e) {
			slf4jLogger.error("Unable to invoke callable.", e);
		}

		List<ScopusArticle> results = new ArrayList<>();
		list.forEach(results::addAll);
		return results;
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
