package reciter.scopus.retriever;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.Retryer.RetryerCallable;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.google.common.base.Predicates;

import reciter.model.scopus.ScopusArticle;
import reciter.scopus.callable.ScopusUriParserCallable;
import reciter.scopus.querybuilder.ScopusXmlQuery;
import reciter.scopus.xmlparser.ScopusXmlHandler;

public class ScopusArticleRetriever {

	private final static Logger slf4jLogger = LoggerFactory.getLogger(ScopusArticleRetriever.class);

	/**
	 * Scopus retrieval threshold.
	 */
	protected static final int SCOPUS_DEFAULT_THRESHOLD = 24;

	/**
	 * Scopus retrieval max threshold.
	 */
	protected static final int SCOPUS_MAX_THRESHOLD = 25;

	/**
	 * @param pmids scopus query could be DOI, scopusDocId, PMID
	 * @param type to query scopus like DOI(), SCOPUS-ID(), PMID() etc.
	 * @return
	 */
	public List<ScopusArticle> retrieveScopus(List<Object> pmids, String type) {
		slf4jLogger.info("Pmids:" + pmids);
		List<String> pmidQueries = new ArrayList<>();
		if (pmids.size() == 1) {
			pmidQueries.add(type + "(" + pmids.get(0) + ")");
		} else {
			StringBuffer sb = new StringBuffer();
			int i = 0;
			Iterator<Object> itr = pmids.iterator();
			while (itr.hasNext()) {
				Object pmid = itr.next();
				if (i == 0 || (i % SCOPUS_DEFAULT_THRESHOLD != 0 && i != pmids.size() - 1)) {
					sb.append(type + "(");
					sb.append(pmid);
					sb.append(")+OR+");
				} else {
					sb.append(type + "(");
					sb.append(pmid);
					sb.append(")");
				}
				if (i != 0 && i % SCOPUS_DEFAULT_THRESHOLD == 0) {
					pmidQueries.add(sb.toString());
					sb = new StringBuffer();
				}
				i++;
			}
			// add the remaining pmids
			String remaining = sb.toString();
			if (!remaining.isEmpty()) {
				pmidQueries.add(remaining);
			}
		}

		/*List<RetryerCallable<List<ScopusArticle>>> callables = new ArrayList<RetryerCallable<List<ScopusArticle>>>();
		Retryer<List<ScopusArticle>> retryer = RetryerBuilder.<List<ScopusArticle>>newBuilder()
                .retryIfResult(Predicates.<List<ScopusArticle>>isNull())
                .retryIfExceptionOfType(IOException.class)
                .retryIfRuntimeException()
                .withWaitStrategy(WaitStrategies.fibonacciWait(100L, 2L, TimeUnit.MINUTES))
                .withStopStrategy(StopStrategies.stopAfterAttempt(10))
                .build();*/
		List<Callable<List<ScopusArticle>>> callables = new ArrayList<>();

		for (String query : pmidQueries) {
			ScopusXmlQuery scopusXmlQuery = new ScopusXmlQuery.ScopusXmlQueryBuilder(query, SCOPUS_MAX_THRESHOLD).build();
			String scopusUrl = scopusXmlQuery.getQueryUrl();
			ScopusUriParserCallable scopusUriParserCallable = new ScopusUriParserCallable(new ScopusXmlHandler(), scopusUrl);
			//RetryerCallable<List<ScopusArticle>> retryerCallable = retryer.wrap(scopusUriParserCallable);
        	//callables.add(retryerCallable);
			callables.add(scopusUriParserCallable);
		}

		List<List<ScopusArticle>> list = new ArrayList<>();

		int numAvailableProcessors = Runtime.getRuntime().availableProcessors();
		ExecutorService executor = Executors.newFixedThreadPool(numAvailableProcessors);

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
}
