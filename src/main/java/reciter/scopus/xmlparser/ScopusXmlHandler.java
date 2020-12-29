package reciter.scopus.xmlparser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import reciter.model.scopus.Affiliation;
import reciter.model.scopus.Author;
import reciter.model.scopus.ScopusArticle;

/**
 * The {@code ScopusXmlHandler} class parses Scopus XML.
 * 
 * @author jil3004
 *
 */
public class ScopusXmlHandler extends DefaultHandler {

	private ScopusArticle scopusArticle;
	
	private boolean bScopusDocId;

	private boolean bAffiliation;
	private boolean bAfid;
	private boolean bAffilname;
	private boolean bAffiliationCity;
	private boolean bAffiliationCountry;

	private boolean bPubmedId;
	private boolean bDoi;
	private boolean bSubtype;
	private boolean bSubTypeDescription;
	private boolean bTitle;
	private boolean bPublicationName;
	private boolean bCoverDate;
	private boolean bCoverDisplayDate;
	private boolean bIssn;
	private boolean bEissn;
	private boolean bVolume;
	private boolean bIssueIdentifier;
	private boolean bPageRange;
	
	private boolean bCitedByCount;

	private boolean bAuthor;
	private boolean bAuthid;
	private boolean bAuthname;
	private boolean bSurname;
	private boolean bGivenName;
	private boolean bInitials;
	private boolean bAfids;
	private boolean bError;

	private int afid;
	private StringBuilder affilname = new StringBuilder();
	private String affiliationCity;
	private String affiliationCountry;
	private Map<Integer, Affiliation> affiliations = new HashMap<>();
	
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
	private Integer seq;
	private long authid;
	private String authname;
	private String surname;
	private String givenName;
	private String initials;
	private List<Integer> afids;
	private Map<Integer, Author> authors = new HashMap<>();
	
	private List<ScopusArticle> scopusArticles = new ArrayList<>();

	@Override
	public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
		// <affiliation>
		if (qName.equalsIgnoreCase("affiliation")) {
			bAffiliation = true;
		}

		if (bAffiliation) {
			if (qName.equalsIgnoreCase("afid")) {
				afid = 0;
				bAfid = true;
			}
			if (qName.equalsIgnoreCase("affilname")) {
				affilname.setLength(0);
				bAffilname = true;
			}
			if (qName.equalsIgnoreCase("affiliation-city")) {
				bAffiliationCity = true;
			}
			if (qName.equalsIgnoreCase("affiliation-country")) {
				bAffiliationCountry = true;
			}
		}
		// end </affiliation> tag.
		
		//<dc:identifier>
		if(qName.equalsIgnoreCase("dc:identifier")) {
			bScopusDocId = true;
		}

		// <pubmed-id>
		if (qName.equalsIgnoreCase("pubmed-id")) {
			bPubmedId = true;
		}
		// end </pubmed-id> tag.

		// <prism:doi>
		if (qName.equalsIgnoreCase("prism:doi")) {
			bDoi = true;
		}
		
		//<subtype>
		if (qName.equals("subtype")) {
			bSubtype = true;
		}
		
		//<subtypeDescription>
		if (qName.equals("subtypeDescription")) {
			bSubTypeDescription = true;
		}
		
		//<citedby-count>
		if (qName.equals("citedby-count")) {
			bCitedByCount = true;
		}
		
		if (qName.equals("dc:title")) {
			bTitle = true;
		}
		
		if (qName.equals("prism:publicationName")) {
			bPublicationName = true;
		}
		
		if (qName.equals("prism:coverDate")) {
			bCoverDate = true;
		}
		
		if (qName.equals("prism:coverDisplayDate")) {
			bCoverDisplayDate = true;
		}
		
		if (qName.equals("prism:issn")) {
			bIssn = true;
		}
		
		if (qName.equals("prism:eIssn")) {
			bEissn = true;
		}
		
		if (qName.equals("prism:volume")) {
			bVolume = true;
		}
		
		if (qName.equals("prism:issueIdentifier")) {
			bIssueIdentifier = true;
		}
		
		if (qName.equals("prism:pageRange")) {
			bPageRange = true;
		}

		// <author>
		if (qName.equalsIgnoreCase("author")) {
			bAuthor = true;
			seq = Integer.parseInt(attributes.getValue("seq"));
			afids = new ArrayList<>();
		}
		if (bAuthor) {
			if (qName.equalsIgnoreCase("authid")) {
				bAuthid = true;
			}
			if (qName.equalsIgnoreCase("authname")) {
				bAuthname = true;
			}
			if (qName.equalsIgnoreCase("surname")) {
				bSurname = true;
			}
			if (qName.equalsIgnoreCase("given-name")) {
				bGivenName = true;
			}
			if (qName.equalsIgnoreCase("initials")) {
				bInitials = true;
			}
			if (qName.equalsIgnoreCase("afid")) {
				bAfids = true;
			}
		}
		// end </author> tag.

		// <error>
		if (qName.equalsIgnoreCase("error")) {
			bError = true;
		}
		// end </error> tag.
	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {

		if (bAffiliation) {
			if (bAfid) {
				afid = Integer.parseInt(new String(ch, start, length));
			} 
			if (bAffilname) {
				affilname.append(ch, start, length);
			}
			if (bAffiliationCity) {
				affiliationCity = new String(ch, start, length);
			}
			if (bAffiliationCountry) {
				affiliationCountry = new String(ch, start, length);
			}
		}
		
		if (bScopusDocId) {
			scopusDocId = new String(ch, start, length).replaceAll("SCOPUS_ID:", "");
		}

		if (bDoi) {
			doi = new String(ch, start, length);
		}

		if (bPubmedId) {
			pubmedId = Long.parseLong(new String(ch, start, length));
		}
		
		if (bSubtype) {
			subType = new String(ch, start, length);
		}
		
		if (bSubTypeDescription) {
			subTypeDescription = new String(ch, start, length);
		}
		
		if (bTitle) {
			title = new String(ch, start, length);
		}
		
		if (bPublicationName) {
			publicationName = new String(ch, start, length);
		}
		
		if (bCoverDate) {
			coverDate = new String(ch, start, length);
		}
		
		if (bCoverDisplayDate) {
			coverDisplayDate = new String(ch, start, length);
		}
		
		if (bIssn) {
			issn = new String(ch, start, length);
		}
		
		if (bEissn) {
			eissn = new String(ch, start, length);
		}
		
		if (bVolume) {
			volume = new String(ch, start, length);
		}
		
		if (bIssueIdentifier) {
			issueIdentifier = new String(ch, start, length);
		}
		
		if (bPageRange) {
			pageRange = new String(ch, start, length);
		}
		
		if (bCitedByCount) {
			citedByCount = Long.parseLong(new String(ch, start, length));
		}

		if (bAuthor) {
			if (bAuthid) {
				authid = Long.parseLong(new String(ch, start, length));
			}
			if (bAuthname) {
				authname = new String(ch, start, length);
			}
			if (bSurname) {
				surname = new String(ch, start, length);
			}
			if (bGivenName) {
				givenName = new String(ch, start, length);
			}
			if (bInitials) {
				initials = new String(ch, start, length);
			}
			if (bAfids) {
				int afid = Integer.parseInt(new String(ch, start, length));
				if (afid != 0
						&&
						!afids.contains(afid)) {
					afids.add(afid);
				}
			}
		}
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {

		if (qName.equalsIgnoreCase("affiliation") && bAffiliation) {
			if (afid != 0) {
				affiliations.put(afid,
						Affiliation.builder()
								.affiliationCity(affiliationCity)
								.afid(afid)
								.affilname(affilname.toString())
								.affiliationCountry(affiliationCountry)
								.build());
			}
			bAffiliation = false;
		}

		// <affiliation> child tags need to be checked for empty contents.
		// Check for empty XML tags: ie: <afid />
		if (bAffiliation) {
			if (qName.equalsIgnoreCase("afid")) {
				bAfid = false;
			}
			if (qName.equalsIgnoreCase("affilname")) {
				bAffilname = false;
			}
			if (qName.equalsIgnoreCase("affiliation-city")) {
				bAffiliationCity = false;
			}
			if (qName.equalsIgnoreCase("affiliation-country")) {
				bAffiliationCountry = false;
			}
		}
		
		if(qName.equalsIgnoreCase("dc:identifier")) {
			bScopusDocId = false;
		}
		
		if (qName.equalsIgnoreCase("pubmed-id")) {
			bPubmedId = false;
		}

		if (qName.equalsIgnoreCase("prism:doi")) {
			bDoi = false;
		}
		
		if (qName.equals("subtype")) {
			bSubtype = false;
		}
		
		if (qName.equals("subtypeDescription")) {
			bSubTypeDescription = false;
		}
		
		if (qName.equals("dc:title")) {
			bTitle = false;
		}
		
		if (qName.equals("prism:publicationName")) {
			bPublicationName = false;
		}
		
		if (qName.equals("prism:coverDate")) {
			bCoverDate = false;
		}
		
		if (qName.equals("prism:coverDisplayDate")) {
			bCoverDisplayDate = false;
		}
		
		if (qName.equals("prism:issn")) {
			bIssn = false;
		}
		
		if (qName.equals("prism:eIssn")) {
			bEissn = false;
		}
		
		if (qName.equals("prism:volume")) {
			bVolume = false;
		}
		
		if (qName.equals("prism:issueIdentifier")) {
			bIssueIdentifier = false;
		}
		
		if (qName.equals("prism:pageRange")) {
			bPageRange = false;
		}
		
		if (qName.equals("citedby-count")) {
			bCitedByCount = false;
		}

		if (qName.equalsIgnoreCase("author") && bAuthor) {
			if (authid != 0) {
				authors.put(seq,
						Author.builder()
								.seq(seq)
								.afids(afids)
								.authid(authid)
								.authname(authname)
								.surname(surname)
								.givenName(givenName)
								.initials(initials).build());
			}
			bAuthor = false;
		}

		if (bAuthor) {
			if (qName.equalsIgnoreCase("authid")) {
				bAuthid = false;
			}
			if (qName.equalsIgnoreCase("authname")) {
				bAuthname = false;
			}
			if (qName.equalsIgnoreCase("surname")) {
				bSurname = false;
			}
			if (qName.equalsIgnoreCase("given-name")) {
				bGivenName = false;
			}
			if (qName.equalsIgnoreCase("initials")) {
				bInitials = false;
			}
			if (qName.equalsIgnoreCase("afid")) {
				bAfids = false;
			}
		}

		// Check for error entry. Return null.
		if (qName.equalsIgnoreCase("entry")) {
			if (bError) {
				scopusArticle = null;
			} else {
				List<Affiliation> affiliationList = new ArrayList<>();
				List<Author> authorList = new ArrayList<>();
				for (Affiliation affiliation : affiliations.values()) {
					affiliationList.add(affiliation);
				}
				for (Author author : authors.values()) {
					authorList.add(author);
				}
				scopusArticle = ScopusArticle.builder()
						.scopusDocId(scopusDocId)
						.pubmedId(pubmedId)
						.affiliations(affiliationList)
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
						.authors(authorList).build();
				scopusArticles.add(scopusArticle);
				scopusArticle = null;
				doi=null;
				affiliations.clear();
				authors.clear();
			}
		}
	}

	public ScopusArticle getScopusArticle() {
		return scopusArticle;
	}

	public List<ScopusArticle> getScopusArticles() {
		return scopusArticles;
	}
}
