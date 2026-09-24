package reciter.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import reciter.model.scopus.ScopusArticle;
import reciter.model.scopus.ScopusQuery;
import reciter.scopus.retriever.ScopusArticleRetriever;

@RestController
@RequestMapping("/scopus")
@Tag(name="ScopusController", description ="Querying Scopus with PMID, SCOPUS-ID or DOI")
public class ScopusController {
	
    private static final Logger log = LoggerFactory.getLogger(ScopusController.class);
    
    /** Identifier types this service understands (compared case-insensitively). */
    static final Set<String> ALLOWED_TYPES = Set.of("pmid", "doi", "scopus-id", "af-id");
    
    private final ScopusArticleRetriever scopusArticleRetriever;

    public ScopusController(ScopusArticleRetriever scopusArticleRetriever) {
        this.scopusArticleRetriever = scopusArticleRetriever;
    }
    

    @Operation(summary = "Querying Scopus with PMID, SCOPUS-ID or DOI. Add type it only accepts PMID,SCOPUS-ID or DOI(case-insensitive)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list"),
            @ApiResponse(responseCode = "400", description = "The request body is missing required fields or has an unsupported type"),
            @ApiResponse(responseCode = "401", description = "You are not authorized to view the resource"),
            @ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
            @ApiResponse(responseCode = "404", description = "The resource you were trying to reach is not found")
    })
    @PostMapping(value = "/query/", produces = "application/json")
    public ResponseEntity<List<ScopusArticle>> retrieve(@RequestBody ScopusQuery scopusQuery) {
    	validate(scopusQuery);
    	int size = scopusQuery.getQuery() == null ? 0 : scopusQuery.getQuery().size();
    	log.info("Calling retrieve with PMIDs size=[{}]", size);
        List<ScopusArticle> scopusArticles = scopusArticleRetriever.retrieveScopus(new ArrayList<>(scopusQuery.getQuery()), scopusQuery.getType());
        log.info("Finished retrieving with PMIDs size=[{}]", scopusQuery.getQuery().size());
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
