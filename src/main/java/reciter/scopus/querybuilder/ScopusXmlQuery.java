package reciter.scopus.querybuilder;

public class ScopusXmlQuery {
	
	private final String queryUrl;

	private static final String SCOPUS_URL_PREFIX = "https://api.elsevier.com/content/search/scopus?query=";

	private ScopusXmlQuery(ScopusXmlQueryBuilder scopusXmlQueryBuilder) {
		queryUrl = scopusXmlQueryBuilder.queryUrl;
	}
	
	/**
	 * Scopus XML Query builder class for constructing a Scopus XML query.
	 * @author jil3004
	 *
	 */
	public static class ScopusXmlQueryBuilder {
		// Required parameters.
		private final String query;
		
		// Optional parameters - initialized to default values.
		private int count;
		private String field = "dc:identifier,doi,pubmed-id,subtype,subtypeDescription,affiliation,author,afid,citedby-count,dc:title,prism:publicationName,prism:coverDate,prism:coverDisplayDate,prism:issn,prism:eIssn,prism:volume,prism:issueIdentifier,prism:pageRange";
		private int start = 0;
		private String queryUrl;
		
		public ScopusXmlQueryBuilder(String query, int count) {
			this.query = query;
			this.count = count;
		}
		public ScopusXmlQuery build() {
			StringBuilder sb = new StringBuilder();
			sb.append(ScopusXmlQuery.SCOPUS_URL_PREFIX);
			sb.append(query);
			sb.append("&count=");
			sb.append(count);
			sb.append("&field=");
			sb.append(field);
			sb.append("&start=");
			sb.append(start);
			queryUrl = sb.toString();
			return new ScopusXmlQuery(this);
		}
	}

	public String getQueryUrl() {
		return queryUrl;
	}
}
