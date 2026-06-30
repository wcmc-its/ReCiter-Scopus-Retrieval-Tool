package reciter.controller;

import java.util.ArrayList;
import java.util.List;

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

    @Operation(summary = "Querying Scopus with PMID, SCOPUS-ID or DOI. Add type it only accepts PMID,SCOPUS-ID or DOI(case-insensitive)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list"),
            @ApiResponse(responseCode = "401", description = "You are not authorized to view the resource"),
            @ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
            @ApiResponse(responseCode = "404", description = "The resource you were trying to reach is not found")
    })
    @PostMapping(value = "/query/", produces = "application/json")
    public ResponseEntity<List<ScopusArticle>> retrieve(@RequestBody ScopusQuery scopusQuery) {
    	log.info("Calling retrieve with PMIDs size=[{}]", scopusQuery.getQuery().size());
        ScopusArticleRetriever scopusArticleRetriever = new ScopusArticleRetriever();
        List<ScopusArticle> scopusArticles = scopusArticleRetriever.retrieveScopus(new ArrayList<>(scopusQuery.getQuery()), scopusQuery.getType());
        log.info("Finished retrieving with PMIDs size=[{}]", scopusQuery.getQuery().size());
        return ResponseEntity.ok(scopusArticles);
    }
}
