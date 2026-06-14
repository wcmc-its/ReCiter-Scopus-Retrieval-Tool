package reciter.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import reciter.model.scopus.ScopusArticle;
import reciter.model.scopus.ScopusQuery;
import reciter.scopus.retriever.ScopusArticleRetriever;

@Controller
@RequestMapping("/scopus")
@Api(value="ScopusController", tags = {"Querying Scopus with PMID, SCOPUS-ID or DOI"})
public class ScopusController {
    private static final Logger slf4jLogger = LoggerFactory.getLogger(ScopusController.class);

    /** Identifier types this service understands (compared case-insensitively). */
    static final Set<String> ALLOWED_TYPES = Set.of("pmid", "doi", "scopus-id", "af-id");

    private final ScopusArticleRetriever scopusArticleRetriever;

    public ScopusController(ScopusArticleRetriever scopusArticleRetriever) {
        this.scopusArticleRetriever = scopusArticleRetriever;
    }

    @ApiOperation(value = "Querying Scopus with PMID, SCOPUS-ID or DOI. Add type it only accepts PMID,SCOPUS-ID or DOI(case-insensitive)", response = List.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Successfully retrieved list"),
            @ApiResponse(code = 400, message = "The request body is missing required fields or has an unsupported type"),
            @ApiResponse(code = 401, message = "You are not authorized to view the resource"),
            @ApiResponse(code = 403, message = "Accessing the resource you were trying to reach is forbidden"),
            @ApiResponse(code = 404, message = "The resource you were trying to reach is not found")
    })
    @PostMapping(value = "/query/", produces = "application/json")
    @ResponseBody
    public ResponseEntity<List<ScopusArticle>> retrieve(@RequestBody ScopusQuery scopusQuery) {
        validate(scopusQuery);
        int size = scopusQuery.getQuery().size();
        slf4jLogger.info("calling retrieve with pmids size=[" + size + "]");
        List<ScopusArticle> scopusArticles = scopusArticleRetriever.retrieveScopus(new ArrayList<>(scopusQuery.getQuery()), scopusQuery.getType());
        slf4jLogger.info("finished retrieving with pmids size=[" + size + "]");
        return ResponseEntity.ok(scopusArticles);
    }

    /**
     * Rejects malformed requests with an {@link IllegalArgumentException} (mapped to HTTP 400
     * by {@link GlobalExceptionHandler}) instead of letting a null {@code query} NPE into a 500
     * or an unsupported {@code type} get concatenated raw into the Scopus query.
     */
    static void validate(ScopusQuery scopusQuery) {
        if (scopusQuery == null || scopusQuery.getQuery() == null || scopusQuery.getQuery().isEmpty()) {
            throw new IllegalArgumentException("'query' must be a non-empty list of identifiers.");
        }
        String type = scopusQuery.getType();
        if (type == null || !ALLOWED_TYPES.contains(type.trim().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("'type' must be one of "
                    + ALLOWED_TYPES.stream().sorted().collect(Collectors.toList())
                    + " (case-insensitive).");
        }
    }
}
