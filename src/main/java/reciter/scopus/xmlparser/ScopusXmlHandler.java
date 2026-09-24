package reciter.scopus.xmlparser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import reciter.model.scopus.Affiliation;
import reciter.model.scopus.Author;
import reciter.model.scopus.ScopusArticle;

/**
 * The {@code ScopusXmlHandler} class parses a batch of Scopus Search API XML entries
 * into {@link ScopusArticle} objects.
 *
 * <p>Text content is accumulated in a {@link StringBuilder} ({@link #buf}) while inside a
 * captured leaf element and consumed in {@link #endElement}. SAX may invoke
 * {@link #characters} multiple times for a single element — when the text contains XML
 * entities ({@code &amp;}, {@code &lt;}, {@code &#48;}) or exceeds the parser's character
 * buffer — so accumulating (rather than overwriting on each callback) is required to avoid
 * silently truncating titles, journal names, and author names.</p>
 *
 * <p>Per-entry, per-author, and per-affiliation fields are reset when the corresponding
 * element <em>starts</em>, so a sparse later element (e.g. an author with no
 * {@code <surname>}, or an entry with no {@code <pubmed-id>}) can never inherit a value
 * from the preceding one.</p>
 *
 * @author jil3004
 */
public class ScopusXmlHandler extends DefaultHandler {

	private static final Logger slf4jLogger = LoggerFactory.getLogger(ScopusXmlHandler.class);

	private static final String SCOPUS_ID_PREFIX = "SCOPUS_ID:";

	/** Accumulates character data for the leaf element currently being captured. */
	private final StringBuilder buf = new StringBuilder();
	/** True while inside a leaf element whose text we want to keep. */
	private boolean capturing;

	/** Context flags — at most one of these is true at any time within an entry. */
	private boolean bAffiliation;
	private boolean bAuthor;
	private boolean bError;

	private int errorEntryCount;

	private ScopusArticle scopusArticle;

	// Per-entry fields.
	private String scopusDocId;
	private long pubmedId;
	private String doi;
	private String subType;
	private String subTypeDescription;
	private String title;
	private String publicationName;
	private String coverDate;
	private String coverDisplayDate;
	private String issn;
	private String eissn;
	private String volume;
	private String issueIdentifier;
	private String pageRange;
	private long citedByCount;

	// Per-affiliation fields.
	private int afid;
	private String affilname;
	private String affiliationCity;
	private String affiliationCountry;
	private final Map<Integer, Affiliation> affiliations = new LinkedHashMap<>();

	// Per-author fields.
	private Integer seq;
	private long authid;
	private String authname;
	private String surname;
	private String givenName;
	private String initials;
	private List<Integer> afids;
	private final Map<Integer, Author> authors = new LinkedHashMap<>();

	private final List<ScopusArticle> scopusArticles = new ArrayList<>();

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		// Any new element ends the previous leaf's capture and resets the buffer.
		buf.setLength(0);
		capturing = false;

		if (qName.equalsIgnoreCase("entry")) {
			resetEntry();
		} else if (qName.equalsIgnoreCase("affiliation")) {
			bAffiliation = true;
			resetAffiliation();
		} else if (qName.equalsIgnoreCase("author")) {
			bAuthor = true;
			resetAuthor();
			seq = parseIntOrDefault(attributes.getValue("seq"), authors.size() + 1);
		} else if (qName.equalsIgnoreCase("error")) {
			bError = true;
		} else if (isCapturedLeaf(qName)) {
			capturing = true;
		}
	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		if (capturing) {
			buf.append(ch, start, length);
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		if (qName.equalsIgnoreCase("entry")) {
			endEntry();
			capturing = false;
			return;
		}
		if (qName.equalsIgnoreCase("affiliation")) {
			if (bAffiliation && afid != 0) {
				affiliations.put(afid, Affiliation.builder()
						.afid(afid)
						.affilname(affilname)
						.affiliationCity(affiliationCity)
						.affiliationCountry(affiliationCountry)
						.build());
			}
			bAffiliation = false;
			capturing = false;
			return;
		}
		if (qName.equalsIgnoreCase("author")) {
			if (bAuthor && authid != 0) {
				authors.put(seq, Author.builder()
						.seq(seq)
						.afids(afids)
						.authid(authid)
						.authname(authname)
						.surname(surname)
						.givenName(givenName)
						.initials(initials)
						.build());
			}
			bAuthor = false;
			capturing = false;
			return;
		}

		// Leaf elements: assign the accumulated text to the right field.
		String text = buf.toString();
		if (qName.equalsIgnoreCase("dc:identifier")) {
			scopusDocId = text.replace(SCOPUS_ID_PREFIX, "");
		} else if (qName.equalsIgnoreCase("pubmed-id")) {
			pubmedId = parseLongOrDefault(text, 0L);
		} else if (qName.equalsIgnoreCase("prism:doi")) {
			doi = text;
		} else if (qName.equalsIgnoreCase("subtype")) {
			subType = text;
		} else if (qName.equalsIgnoreCase("subtypeDescription")) {
			subTypeDescription = text;
		} else if (qName.equalsIgnoreCase("dc:title")) {
			title = text;
		} else if (qName.equalsIgnoreCase("prism:publicationName")) {
			publicationName = text;
		} else if (qName.equalsIgnoreCase("prism:coverDate")) {
			coverDate = text;
		} else if (qName.equalsIgnoreCase("prism:coverDisplayDate")) {
			coverDisplayDate = text;
		} else if (qName.equalsIgnoreCase("prism:issn")) {
			issn = text;
		} else if (qName.equalsIgnoreCase("prism:eIssn")) {
			eissn = text;
		} else if (qName.equalsIgnoreCase("prism:volume")) {
			volume = text;
		} else if (qName.equalsIgnoreCase("prism:issueIdentifier")) {
			issueIdentifier = text;
		} else if (qName.equalsIgnoreCase("prism:pageRange")) {
			pageRange = text;
		} else if (qName.equalsIgnoreCase("citedby-count")) {
			citedByCount = parseLongOrDefault(text, 0L);
		} else if (qName.equalsIgnoreCase("affilname")) {
			if (bAffiliation) {
				affilname = text;
			}
		} else if (qName.equalsIgnoreCase("affiliation-city")) {
			if (bAffiliation) {
				affiliationCity = text;
			}
		} else if (qName.equalsIgnoreCase("affiliation-country")) {
			if (bAffiliation) {
				affiliationCountry = text;
			}
		} else if (qName.equalsIgnoreCase("authid")) {
			if (bAuthor) {
				authid = parseLongOrDefault(text, 0L);
			}
		} else if (qName.equalsIgnoreCase("authname")) {
			if (bAuthor) {
				authname = text;
			}
		} else if (qName.equalsIgnoreCase("surname")) {
			if (bAuthor) {
				surname = text;
			}
		} else if (qName.equalsIgnoreCase("given-name")) {
			if (bAuthor) {
				givenName = text;
			}
		} else if (qName.equalsIgnoreCase("initials")) {
			if (bAuthor) {
				initials = text;
			}
		} else if (qName.equalsIgnoreCase("afid")) {
			// <afid> means the affiliation's own id inside <affiliation>, but an
			// author-to-affiliation reference inside <author>.
			if (bAuthor) {
				int ref = parseIntOrDefault(text, 0);
				if (ref != 0 && !afids.contains(ref)) {
					afids.add(ref);
				}
			} else if (bAffiliation) {
				afid = parseIntOrDefault(text, 0);
			}
		}

		capturing = false;
	}

	private void endEntry() {
		if (bError) {
			errorEntryCount++;
			slf4jLogger.warn("Scopus returned error entry (total errors in this batch: {})", errorEntryCount);
			scopusArticle = null;
			bError = false;
			return;
		}
		scopusArticle = ScopusArticle.builder()
				.scopusDocId(scopusDocId)
				.pubmedId(pubmedId)
				.affiliations(new ArrayList<>(affiliations.values()))
				.doi(doi)
				.subType(subType)
				.subTypeDescription(subTypeDescription)
				.title(title)
				.publicationName(publicationName)
				.coverDate(coverDate)
				.coverDisplayDate(coverDisplayDate)
				.issn(issn)
				.eIssn(eissn)
				.volume(volume)
				.issueIdentifier(issueIdentifier)
				.pageRange(pageRange)
				.citedByCount(citedByCount)
				.authors(new ArrayList<>(authors.values()))
				.build();
		scopusArticles.add(scopusArticle);
	}

	private void resetEntry() {
		bError = false;
		bAffiliation = false;
		bAuthor = false;
		scopusDocId = null;
		pubmedId = 0;
		doi = null;
		subType = null;
		subTypeDescription = null;
		title = null;
		publicationName = null;
		coverDate = null;
		coverDisplayDate = null;
		issn = null;
		eissn = null;
		volume = null;
		issueIdentifier = null;
		pageRange = null;
		citedByCount = 0;
		affiliations.clear();
		authors.clear();
		resetAffiliation();
		resetAuthor();
	}

	private void resetAffiliation() {
		afid = 0;
		affilname = null;
		affiliationCity = null;
		affiliationCountry = null;
	}

	private void resetAuthor() {
		seq = null;
		authid = 0;
		authname = null;
		surname = null;
		givenName = null;
		initials = null;
		afids = new ArrayList<>();
	}

	private static boolean isCapturedLeaf(String qName) {
		return qName.equalsIgnoreCase("dc:identifier")
				|| qName.equalsIgnoreCase("pubmed-id")
				|| qName.equalsIgnoreCase("prism:doi")
				|| qName.equalsIgnoreCase("subtype")
				|| qName.equalsIgnoreCase("subtypeDescription")
				|| qName.equalsIgnoreCase("dc:title")
				|| qName.equalsIgnoreCase("prism:publicationName")
				|| qName.equalsIgnoreCase("prism:coverDate")
				|| qName.equalsIgnoreCase("prism:coverDisplayDate")
				|| qName.equalsIgnoreCase("prism:issn")
				|| qName.equalsIgnoreCase("prism:eIssn")
				|| qName.equalsIgnoreCase("prism:volume")
				|| qName.equalsIgnoreCase("prism:issueIdentifier")
				|| qName.equalsIgnoreCase("prism:pageRange")
				|| qName.equalsIgnoreCase("citedby-count")
				|| qName.equalsIgnoreCase("afid")
				|| qName.equalsIgnoreCase("affilname")
				|| qName.equalsIgnoreCase("affiliation-city")
				|| qName.equalsIgnoreCase("affiliation-country")
				|| qName.equalsIgnoreCase("authid")
				|| qName.equalsIgnoreCase("authname")
				|| qName.equalsIgnoreCase("surname")
				|| qName.equalsIgnoreCase("given-name")
				|| qName.equalsIgnoreCase("initials");
	}

	private long parseLongOrDefault(String value, long fallback) {
		if (value == null) {
			return fallback;
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return fallback;
		}
		try {
			return Long.parseLong(trimmed);
		} catch (NumberFormatException e) {
			slf4jLogger.warn("Non-numeric value [{}] where a number was expected; using {}.", trimmed, fallback);
			return fallback;
		}
	}

	private int parseIntOrDefault(String value, int fallback) {
		if (value == null) {
			return fallback;
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty()) {
			return fallback;
		}
		try {
			return Integer.parseInt(trimmed);
		} catch (NumberFormatException e) {
			slf4jLogger.warn("Non-numeric value [{}] where a number was expected; using {}.", trimmed, fallback);
			return fallback;
		}
	}

	public ScopusArticle getScopusArticle() {
		return scopusArticle;
	}

	public List<ScopusArticle> getScopusArticles() {
		return scopusArticles;
	}

	public int getErrorEntryCount() {
		return errorEntryCount;
	}
}
