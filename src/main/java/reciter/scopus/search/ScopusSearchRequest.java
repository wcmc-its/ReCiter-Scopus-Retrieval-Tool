package reciter.scopus.search;

/**
 * A Scopus search whose query is sent to Elsevier <strong>verbatim</strong>.
 *
 * <p>It is a POST body rather than a query string because a real search strategy is long — a
 * peer-reviewable Boolean query runs to thousands of characters once URL-encoded, which is an
 * uncomfortable place to be against a servlet's header limit. The PubMed retrieval tool takes its
 * complex query the same way, for the same reason.
 *
 * @param query the Scopus query, EXACTLY as Elsevier will see it: field codes, Booleans, top-level
 *              limits ({@code AND PUBYEAR > 2020}, {@code AND DOCTYPE(ar)}) and all. Nothing here
 *              wraps, rewrites or translates it — see {@link ScopusSearchService#searchRaw}.
 * @param count records per page. Optional; {@link #DEFAULT_COUNT} if absent. Bounded by the view —
 *              see {@link ScopusSearchService#maxCountFor}.
 * @param start zero-based offset of the first record. Optional; 0 if absent. This is how a caller
 *              gets more records than a single COMPLETE page allows.
 * @param view  {@code STANDARD} (default at Elsevier) or {@code COMPLETE} (abstracts and the full
 *              author list — entitled for our institution token, verified against the live API).
 */
public record ScopusSearchRequest(String query, Integer count, Integer start, String view) {

	/**
	 * Safe in EITHER view, which is why it is the default: a COMPLETE page cannot exceed 25.
	 *
	 * <p>It is emphatically not 0. Elsevier IGNORES {@code count=0} and silently returns 25
	 * records — so a caller who wanted "just the total, no records" and asked for zero would be
	 * billed for a page and handed one, with nothing in the response to say so.
	 */
	public static final int DEFAULT_COUNT = 25;

	public boolean isCompleteView() {
		return view != null && "COMPLETE".equalsIgnoreCase(view.trim());
	}

	public int countOrDefault() {
		return count == null ? DEFAULT_COUNT : count;
	}

	public int startOrDefault() {
		return start == null ? 0 : start;
	}
}
