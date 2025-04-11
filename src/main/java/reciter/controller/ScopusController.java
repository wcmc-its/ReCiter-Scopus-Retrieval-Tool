package reciter.controller;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import reciter.model.scopus.ScopusArticle;
import reciter.model.scopus.ScopusQuery;
import reciter.scopus.retriever.ScopusArticleRetriever;

@Controller
@RequestMapping("/scopus")
@Tag(name="ScopusController", description ="Querying Scopus with PMID, SCOPUS-ID or DOI")
public class ScopusController {
    private static final Logger slf4jLogger = LoggerFactory.getLogger(ScopusController.class);

    @Operation(summary = "Querying Scopus with PMID, SCOPUS-ID or DOI. Add type it only accepts PMID,SCOPUS-ID or DOI(case-insensitive)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved list"),
            @ApiResponse(responseCode = "401", description = "You are not authorized to view the resource"),
            @ApiResponse(responseCode = "403", description = "Accessing the resource you were trying to reach is forbidden"),
            @ApiResponse(responseCode = "404", description = "The resource you were trying to reach is not found")
    })
    @PostMapping(value = "/query/", produces = "application/json")
    @ResponseBody
    public ResponseEntity<List<ScopusArticle>> retrieve(@RequestBody ScopusQuery scopusQuery) {
        slf4jLogger.info("calling retrieve with pmids size=[" + scopusQuery.getQuery().size() + "]");
        ScopusArticleRetriever scopusArticleRetriever = new ScopusArticleRetriever();
        List<ScopusArticle> scopusArticles = scopusArticleRetriever.retrieveScopus(new ArrayList<>(scopusQuery.getQuery()), scopusQuery.getType());
        slf4jLogger.info("finished retrieving with pmids size=[" + scopusQuery.getQuery().size() + "]");
        return ResponseEntity.ok(scopusArticles);
    }
}
