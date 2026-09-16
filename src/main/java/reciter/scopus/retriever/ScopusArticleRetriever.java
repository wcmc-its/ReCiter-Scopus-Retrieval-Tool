package reciter.scopus.retriever;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
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
import org.springframework.stereotype.Service;

import reciter.model.scopus.ScopusArticle;
import reciter.scopus.callable.ScopusUriParserCallable;
import reciter.scopus.querybuilder.ScopusXmlQuery;
import reciter.scopus.xmlparser.ScopusXmlHandler;

/**
 * Retrieves Scopus articles by PMID, DOI, or Scopus-ID.
 *
 * <p>Queries are batched into groups of {@value #SCOPUS_DEFAULT_THRESHOLD} and dispatched
 * concurrently using Java 21 virtual threads, so thousands of in-flight HTTP calls cost
 * virtually no platform-thread resources.
 */
@Service
public class ScopusArticleRetriever {

	private static final Logger log = LoggerFactory.getLogger(ScopusArticleRetriever.class);

	/** Maximum IDs per Scopus OR-query (Elsevier hard limit is 25). */
	protected static final int SCOPUS_DEFAULT_THRESHOLD = 24;
	protected static final int SCOPUS_MAX_THRESHOLD = 25;

	/**
	 * One virtual-thread executor shared across all requests.  Virtual threads are cheap
	 * (sub-millisecond creation, ~few KB stack), so a shared unbounded executor is the
	 * idiomatic Java 21 pattern — no pool sizing required.
	 */
	private static final ExecutorService VIRTUAL_EXECUTOR =
			Executors.newVirtualThreadPerTaskExecutor();

	public List<ScopusArticle> retrieveScopus(List<Object> ids, String type) {
		log.info("retrieveScopus ids={}", ids);

		List<String> pmidQueries = buildBatchQueries(ids, type);

		Retryer<List<ScopusArticle>> retryer = RetryerBuilder.<List<ScopusArticle>>newBuilder()
				.retryIfResult(Predicates.<List<ScopusArticle>>isNull())
				.retryIfExceptionOfType(IOException.class)
				.retryIfRuntimeException()
				.withWaitStrategy(WaitStrategies.fibonacciWait(100L, 2L, TimeUnit.MINUTES))
				.withStopStrategy(StopStrategies.stopAfterAttempt(10))
				.build();

		List<RetryerCallable<List<ScopusArticle>>> callables = new ArrayList<>(pmidQueries.size());
		for (String query : pmidQueries) {
			String url = new ScopusXmlQuery.ScopusXmlQueryBuilder(query, SCOPUS_MAX_THRESHOLD)
					.build()
					.getQueryUrl();
			callables.add(retryer.wrap(new ScopusUriParserCallable(new ScopusXmlHandler(), url)));
		}

		List<ScopusArticle> results = new ArrayList<>();
		try {
			VIRTUAL_EXECUTOR.invokeAll(callables)
					.stream()
					.map(future -> {
						try {
							return future.get();
						} catch (Exception e) {
							throw new IllegalStateException(e);
						}
					})
					.forEach(results::addAll);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error("Unable to invoke callable.", e);
		}
		return results;
	}


	/**
	 * Splits {@code ids} into OR-query batches of at most {@value #SCOPUS_DEFAULT_THRESHOLD}
	 * items each, e.g. {@code PMID(1)+OR+PMID(2)+OR+...}.
	 */
	private static List<String> buildBatchQueries(List<Object> pmids, String type) {
		List<String> pmidQueries = new ArrayList<>();
		if (pmids.size() == 1) {
			pmidQueries.add(type + "(" + pmids.get(0) + ")");
			return pmidQueries;
		}

		StringBuilder sb = new StringBuilder();
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
				sb = new StringBuilder();
			}
			i++;
		}
		// add the remaining pmids
		String remaining = sb.toString();
		if (!remaining.isEmpty()) {
			pmidQueries.add(remaining);
		}
		return pmidQueries;
	}
}
