package com.sdg.golf.controller;

import com.sdg.golf.service.ImportRoundService;
import com.sdg.golf.service.ImportWeekMatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/import")
public class ImportController {

    private final ImportWeekMatchService importWeekMatchService;
    private final ImportRoundService importRoundService;
    @Autowired
    public ImportController(ImportWeekMatchService importWeekMatchService, ImportRoundService importRoundService) {
        this.importWeekMatchService = importWeekMatchService;
        this.importRoundService = importRoundService;
    }

    @PostMapping("/weeks")
    public ImportRequest importWeeks(@RequestBody ImportRequest importRequest) throws IOException {
        this.importWeekMatchService.importWeeks(importRequest.fileName(), importRequest.seasonId(), importRequest.year());
        return importRequest;
    }

    @PostMapping("/matchups")
    public ImportRequest importMatchUps(@RequestBody ImportRequest importRequest) throws Exception {
        this.importWeekMatchService.importMatchups(importRequest.fileName(), importRequest.seasonId(), importRequest.year());
        return importRequest;
    }

    @PostMapping("/rounds")
    public ImportRequest importRounds(@RequestBody ImportRequest importRequest) throws Exception {
        this.importRoundService.importRounds(importRequest.fileName(), importRequest.seasonId(), importRequest.year());
        return importRequest;
    }
    public record ImportRequest(String fileName, int seasonId, int year) {}
}
