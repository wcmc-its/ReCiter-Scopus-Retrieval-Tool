package reciter.scopus.xmlparser;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.util.List;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

import reciter.model.scopus.Author;
import reciter.model.scopus.ScopusArticle;

/**
 * Tests for {@link ScopusXmlHandler}, focusing on correct per-entry field isolation
 * and error-entry handling.
 *
 * <p>The parser processes batches of up to 24 Scopus search results in a single XML
 * document. A critical invariant is that fields from entry N must never leak into
 * entry N+1 — especially {@code pubmedId}, which previously was not reset between
 * entries and could cause DynamoDB overwrites in DOI-fallback queries where some
 * entries legitimately lack a {@code <pubmed-id>} tag.</p>
 */
class ScopusXmlHandlerTest {

    private SAXParser saxParser;

    @BeforeEach
    void setUp() throws Exception {
        saxParser = SAXParserFactory.newInstance().newSAXParser();
    }

    private List<ScopusArticle> parse(String xml) throws Exception {
        ScopusXmlHandler handler = new ScopusXmlHandler();
        saxParser.parse(new InputSource(new StringReader(xml)), handler);
        return handler.getScopusArticles();
    }

    private ScopusXmlHandler parseWithHandler(String xml) throws Exception {
        ScopusXmlHandler handler = new ScopusXmlHandler();
        saxParser.parse(new InputSource(new StringReader(xml)), handler);
        return handler;
    }

    // ------------------------------------------------------------------
    // Core regression test: pubmedId must not leak between entries
    // ------------------------------------------------------------------

    @Test
    void pubmedIdDoesNotLeakToNextEntry() throws Exception {
        // Entry 1 has pubmed-id 12345678; Entry 2 has NO pubmed-id tag at all.
        // Before the fix, entry 2 would inherit pubmedId=12345678 from entry 1.
        String xml = "<search-results>"
                + "<entry>"
                + "  <dc:identifier>SCOPUS_ID:111</dc:identifier>"
                + "  <pubmed-id>12345678</pubmed-id>"
                + "  <prism:doi>10.1000/first</prism:doi>"
                + "  <dc:title>First Article</dc:title>"
                + "  <citedby-count>5</citedby-count>"
                + "  <author seq=\"1\"><authid>100</authid><surname>A</surname>"
                + "    <given-name>Alice</given-name><initials>A.</initials></author>"
                + "</entry>"
                + "<entry>"
                + "  <dc:identifier>SCOPUS_ID:222</dc:identifier>"
                + "  <prism:doi>10.1000/second</prism:doi>"
                + "  <dc:title>Second Article (no PubMed ID)</dc:title>"
                + "  <citedby-count>0</citedby-count>"
                + "  <author seq=\"1\"><authid>200</authid><surname>B</surname>"
                + "    <given-name>Bob</given-name><initials>B.</initials></author>"
                + "</entry>"
                + "</search-results>";

        List<ScopusArticle> articles = parse(xml);
        assertEquals(2, articles.size());

        ScopusArticle first = articles.get(0);
        assertEquals(12345678L, first.getPubmedId());
        assertEquals("10.1000/first", first.getDoi());
        assertEquals("111", first.getScopusDocId());
        assertEquals(5L, first.getCitedByCount());

        ScopusArticle second = articles.get(1);
        assertEquals(0L, second.getPubmedId(), "pubmedId must be 0 when <pubmed-id> tag is absent");
        assertEquals("10.1000/second", second.getDoi());
        assertEquals("222", second.getScopusDocId());
        assertEquals(0L, second.getCitedByCount());
    }

    // ------------------------------------------------------------------
    // All scalar fields must not leak between entries
    // ------------------------------------------------------------------

    @Test
    void allFieldsResetBetweenEntries() throws Exception {
        // Entry 1 is fully populated. Entry 2 has only the bare minimum.
        String xml = "<search-results>"
                + "<entry>"
                + "  <dc:identifier>SCOPUS_ID:AAA</dc:identifier>"
                + "  <pubmed-id>99999999</pubmed-id>"
                + "  <prism:doi>10.9999/full</prism:doi>"
                + "  <subtype>ar</subtype>"
                + "  <subtypeDescription>Article</subtypeDescription>"
                + "  <dc:title>Full Article</dc:title>"
                + "  <prism:publicationName>Big Journal</prism:publicationName>"
                + "  <prism:coverDate>2024-01-01</prism:coverDate>"
                + "  <prism:coverDisplayDate>January 2024</prism:coverDisplayDate>"
                + "  <prism:issn>1111-2222</prism:issn>"
                + "  <prism:eIssn>3333-4444</prism:eIssn>"
                + "  <prism:volume>10</prism:volume>"
                + "  <prism:issueIdentifier>2</prism:issueIdentifier>"
                + "  <prism:pageRange>100-200</prism:pageRange>"
                + "  <citedby-count>42</citedby-count>"
                + "  <affiliation>"
                + "    <afid>60007997</afid><affilname>Weill Cornell</affilname>"
                + "    <affiliation-city>New York</affiliation-city>"
                + "    <affiliation-country>United States</affiliation-country>"
                + "  </affiliation>"
                + "  <author seq=\"1\"><authid>777</authid><authname>Smith J.</authname>"
                + "    <surname>Smith</surname><given-name>John</given-name>"
                + "    <initials>J.</initials><afid>60007997</afid></author>"
                + "</entry>"
                + "<entry>"
                + "  <dc:identifier>SCOPUS_ID:BBB</dc:identifier>"
                + "  <citedby-count>0</citedby-count>"
                + "  <author seq=\"1\"><authid>888</authid><surname>Doe</surname>"
                + "    <given-name>Jane</given-name><initials>J.</initials></author>"
                + "</entry>"
                + "</search-results>";

        List<ScopusArticle> articles = parse(xml);
        assertEquals(2, articles.size());

        ScopusArticle sparse = articles.get(1);
        assertEquals("BBB", sparse.getScopusDocId());
        assertEquals(0L, sparse.getPubmedId(), "pubmedId leaked");
        assertNull(sparse.getDoi(), "doi leaked");
        assertNull(sparse.getSubType(), "subType leaked");
        assertNull(sparse.getSubTypeDescription(), "subTypeDescription leaked");
        assertNull(sparse.getTitle(), "title leaked");
        assertNull(sparse.getPublicationName(), "publicationName leaked");
        assertNull(sparse.getCoverDate(), "coverDate leaked");
        assertNull(sparse.getCoverDisplayDate(), "coverDisplayDate leaked");
        assertNull(sparse.getIssn(), "issn leaked");
        assertNull(sparse.getEIssn(), "eIssn leaked");
        assertNull(sparse.getVolume(), "volume leaked");
        assertNull(sparse.getIssueIdentifier(), "issueIdentifier leaked");
        assertNull(sparse.getPageRange(), "pageRange leaked");
        assertEquals(0L, sparse.getCitedByCount(), "citedByCount leaked");
        assertTrue(sparse.getAffiliations().isEmpty(), "affiliations leaked");
        // Authors should contain only the one from entry 2
        assertEquals(1, sparse.getAuthors().size());
        assertEquals("Doe", sparse.getAuthors().get(0).getSurname());
    }

    // ------------------------------------------------------------------
    // Error entries are skipped and counted
    // ------------------------------------------------------------------

    @Test
    void errorEntriesAreSkippedAndCounted() throws Exception {
        String xml = "<search-results>"
                + "<entry>"
                + "  <dc:identifier>SCOPUS_ID:111</dc:identifier>"
                + "  <pubmed-id>11111111</pubmed-id>"
                + "  <citedby-count>1</citedby-count>"
                + "  <author seq=\"1\"><authid>100</authid><surname>Good</surname>"
                + "    <given-name>Author</given-name><initials>A.</initials></author>"
                + "</entry>"
                + "<entry>"
                + "  <error>Result set was empty</error>"
                + "</entry>"
                + "<entry>"
                + "  <dc:identifier>SCOPUS_ID:333</dc:identifier>"
                + "  <pubmed-id>33333333</pubmed-id>"
                + "  <citedby-count>3</citedby-count>"
                + "  <author seq=\"1\"><authid>300</authid><surname>Also</surname>"
                + "    <given-name>Good</given-name><initials>G.</initials></author>"
                + "</entry>"
                + "</search-results>";

        ScopusXmlHandler handler = parseWithHandler(xml);
        List<ScopusArticle> articles = handler.getScopusArticles();

        assertEquals(2, articles.size(), "Error entry should be excluded");
        assertEquals(1, handler.getErrorEntryCount());
        assertEquals(11111111L, articles.get(0).getPubmedId());
        assertEquals(33333333L, articles.get(1).getPubmedId());
    }

    @Test
    void errorEntryDoesNotLeakFieldsToNextEntry() throws Exception {
        // Entry 1 has real data, entry 2 is an error, entry 3 should be clean.
        String xml = "<search-results>"
                + "<entry>"
                + "  <dc:identifier>SCOPUS_ID:AAA</dc:identifier>"
                + "  <pubmed-id>44444444</pubmed-id>"
                + "  <prism:doi>10.1000/before-error</prism:doi>"
                + "  <citedby-count>10</citedby-count>"
                + "  <author seq=\"1\"><authid>400</authid><surname>Before</surname>"
                + "    <given-name>Error</given-name><initials>E.</initials></author>"
                + "</entry>"
                + "<entry>"
                + "  <error>some error</error>"
                + "</entry>"
                + "<entry>"
                + "  <dc:identifier>SCOPUS_ID:CCC</dc:identifier>"
                + "  <citedby-count>0</citedby-count>"
                + "  <author seq=\"1\"><authid>600</authid><surname>After</surname>"
                + "    <given-name>Error</given-name><initials>E.</initials></author>"
                + "</entry>"
                + "</search-results>";

        ScopusXmlHandler handler = parseWithHandler(xml);
        List<ScopusArticle> articles = handler.getScopusArticles();

        assertEquals(2, articles.size());
        assertEquals(1, handler.getErrorEntryCount());

        // The entry after the error must not inherit fields from entry 1
        ScopusArticle afterError = articles.get(1);
        assertEquals("CCC", afterError.getScopusDocId());
        assertEquals(0L, afterError.getPubmedId(), "pubmedId leaked through error entry");
        assertNull(afterError.getDoi(), "doi leaked through error entry");
        assertEquals(0L, afterError.getCitedByCount());
    }

    // ------------------------------------------------------------------
    // General parsing correctness
    // ------------------------------------------------------------------

    @Test
    void parsesFullyPopulatedEntry() throws Exception {
        String xml = "<search-results>"
                + "<entry>"
                + "  <dc:identifier>SCOPUS_ID:85012345678</dc:identifier>"
                + "  <pubmed-id>34567890</pubmed-id>"
                + "  <prism:doi>10.1016/j.example.2023.01.001</prism:doi>"
                + "  <subtype>ar</subtype>"
                + "  <subtypeDescription>Article</subtypeDescription>"
                + "  <dc:title>Example Article</dc:title>"
                + "  <prism:publicationName>Test Journal</prism:publicationName>"
                + "  <prism:coverDate>2023-06-15</prism:coverDate>"
                + "  <prism:coverDisplayDate>June 2023</prism:coverDisplayDate>"
                + "  <prism:issn>0000-1111</prism:issn>"
                + "  <prism:eIssn>2222-3333</prism:eIssn>"
                + "  <prism:volume>42</prism:volume>"
                + "  <prism:issueIdentifier>3</prism:issueIdentifier>"
                + "  <prism:pageRange>123-145</prism:pageRange>"
                + "  <citedby-count>15</citedby-count>"
                + "  <affiliation>"
                + "    <afid>60007997</afid>"
                + "    <affilname>Weill Cornell Medicine</affilname>"
                + "    <affiliation-city>New York</affiliation-city>"
                + "    <affiliation-country>United States</affiliation-country>"
                + "  </affiliation>"
                + "  <affiliation>"
                + "    <afid>60027950</afid>"
                + "    <affilname>Cornell University</affilname>"
                + "    <affiliation-city>Ithaca</affiliation-city>"
                + "    <affiliation-country>United States</affiliation-country>"
                + "  </affiliation>"
                + "  <author seq=\"1\">"
                + "    <authid>7101234567</authid>"
                + "    <authname>Smith, John A.</authname>"
                + "    <surname>Smith</surname>"
                + "    <given-name>John A.</given-name>"
                + "    <initials>J.A.</initials>"
                + "    <afid>60007997</afid>"
                + "  </author>"
                + "  <author seq=\"2\">"
                + "    <authid>7109876543</authid>"
                + "    <authname>Doe, Jane</authname>"
                + "    <surname>Doe</surname>"
                + "    <given-name>Jane</given-name>"
                + "    <initials>J.</initials>"
                + "    <afid>60007997</afid>"
                + "    <afid>60027950</afid>"
                + "  </author>"
                + "</entry>"
                + "</search-results>";

        List<ScopusArticle> articles = parse(xml);
        assertEquals(1, articles.size());

        ScopusArticle a = articles.get(0);
        assertEquals("85012345678", a.getScopusDocId());
        assertEquals(34567890L, a.getPubmedId());
        assertEquals("10.1016/j.example.2023.01.001", a.getDoi());
        assertEquals("ar", a.getSubType());
        assertEquals("Article", a.getSubTypeDescription());
        assertEquals("Example Article", a.getTitle());
        assertEquals("Test Journal", a.getPublicationName());
        assertEquals("2023-06-15", a.getCoverDate());
        assertEquals("June 2023", a.getCoverDisplayDate());
        assertEquals("0000-1111", a.getIssn());
        assertEquals("2222-3333", a.getEIssn());
        assertEquals("42", a.getVolume());
        assertEquals("3", a.getIssueIdentifier());
        assertEquals("123-145", a.getPageRange());
        assertEquals(15L, a.getCitedByCount());

        // Affiliations (HashMap-backed, order not guaranteed — look up by afid)
        assertEquals(2, a.getAffiliations().size());
        assertTrue(a.getAffiliations().stream().anyMatch(af ->
                af.getAfid() == 60007997 && "Weill Cornell Medicine".equals(af.getAffilname())));
        assertTrue(a.getAffiliations().stream().anyMatch(af ->
                af.getAfid() == 60027950 && "Cornell University".equals(af.getAffilname())));

        // Authors
        assertEquals(2, a.getAuthors().size());
        Author author1 = a.getAuthors().stream().filter(au -> au.getSeq() == 1).findFirst().orElseThrow();
        assertEquals(7101234567L, author1.getAuthid());
        assertEquals("Smith", author1.getSurname());
        assertEquals("John A.", author1.getGivenName());
        assertEquals("J.A.", author1.getInitials());
        assertEquals(1, author1.getAfids().size());
        assertEquals(60007997, author1.getAfids().get(0));

        Author author2 = a.getAuthors().stream().filter(au -> au.getSeq() == 2).findFirst().orElseThrow();
        assertEquals(7109876543L, author2.getAuthid());
        assertEquals("Doe", author2.getSurname());
        assertEquals(2, author2.getAfids().size(), "Author 2 should have two affiliation IDs");
    }

    @Test
    void emptyResultReturnsNoArticles() throws Exception {
        String xml = "<search-results></search-results>";
        List<ScopusArticle> articles = parse(xml);
        assertTrue(articles.isEmpty());
    }

    @Test
    void multipleErrorEntriesAllCounted() throws Exception {
        String xml = "<search-results>"
                + "<entry><error>err1</error></entry>"
                + "<entry><error>err2</error></entry>"
                + "<entry><error>err3</error></entry>"
                + "</search-results>";

        ScopusXmlHandler handler = parseWithHandler(xml);
        assertEquals(0, handler.getScopusArticles().size());
        assertEquals(3, handler.getErrorEntryCount());
    }
}
