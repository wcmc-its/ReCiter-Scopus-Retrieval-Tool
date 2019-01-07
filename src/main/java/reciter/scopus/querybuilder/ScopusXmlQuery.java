package reciter.scopus.querybuilder;

public class ScopusXmlQuery {
	
	private final String query;
	private final int count;
	private final String field;
	private final int start;
	private final String queryUrl;
	
	private static final String SCOPUS_URL_PREFIX = "https://api.elsevier.com/content/search/scopus?query=";
	
	private ScopusXmlQuery(ScopusXmlQueryBuilder scopusXmlQueryBuilder) {
		query = scopusXmlQueryBuilder.query;
		count = scopusXmlQueryBuilder.count;
		field = scopusXmlQueryBuilder.field;
		start = scopusXmlQueryBuilder.start;
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
		
		public ScopusXmlQueryBuilder(String query) {
			this.query = query;
			count = 1; // Assuming that it's fetching a single Scopus article.
		}
		
		public ScopusXmlQueryBuilder(String query, int count) {
			this.query = query;
			this.count = count;
		}
		public ScopusXmlQueryBuilder count(int count) {
			this.count = count;
			return this;
		}
		public ScopusXmlQueryBuilder field(String field) {
			this.field = field;
			return this;
		}
		public ScopusXmlQueryBuilder start(int start) {
			this.start = start;
			return this;
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
		
		public ScopusXmlQuery buildSingle() {
			StringBuilder sb = new StringBuilder();
			sb.append(ScopusXmlQuery.SCOPUS_URL_PREFIX);
			sb.append("pmid(");
			sb.append(query);
			sb.append(")");
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

	public String getQuery() {
		return query;
	}

	public int getCount() {
		return count;
	}

	public String getField() {
		return field;
	}

	public int getStart() {
		return start;
	}

	public static String getScopusUrlPrefix() {
		return SCOPUS_URL_PREFIX;
	}

	public String getQueryUrl() {
		return queryUrl;
	}
}
