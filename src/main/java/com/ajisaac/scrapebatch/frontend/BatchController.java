package com.ajisaac.scrapebatch.frontend;

import com.ajisaac.scrapebatch.dto.ScrapeJob;
import com.ajisaac.scrapebatch.scrape.ScrapingExecutorType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/batch")
public class BatchController {

  private final BatchJobService batchJobService;

  @Autowired
  public BatchController(BatchJobService batchJobService) {
    this.batchJobService = batchJobService;
  }

  // todo fix all this weirdness, return proper responses if failure

  @PostMapping("/scrape-job")
  public ResponseEntity createScrapeJob(@RequestBody ScrapeJob scrapeJob) {
    scrapeJob = batchJobService.createScrapeJob(scrapeJob);
    return new ResponseEntity(scrapeJob, HttpStatus.ACCEPTED);
  }

  @PostMapping("/scrape-jobs")
  public ResponseEntity createScrapeJobs(@RequestBody List<ScrapeJob> scrapeJobs) {
    List<ScrapeJob> createdJobs = batchJobService.createScrapeJobs(scrapeJobs);
    return new ResponseEntity(createdJobs, HttpStatus.ACCEPTED);
  }

  @GetMapping("/scrape-jobs")
  public ResponseEntity<List<ScrapeJob>> getScrapeJobs() {
    return new ResponseEntity<>(batchJobService.getAllScrapeJobs(), HttpStatus.OK);
  }

  @GetMapping("/sites")
  public ResponseEntity<List<String>> getSites() {
    List<String> sites = new ArrayList<>();

    for (ScrapingExecutorType i : ScrapingExecutorType.values())
      sites.add(i.toString());

    return new ResponseEntity<>(sites, HttpStatus.OK);
  }

  @PostMapping("/scrape/{id}")
  public ResponseEntity<String> doScrape(@PathVariable Long id) {
    if (batchJobService.isCurrentlyScraping(id))
      return new ResponseEntity<>("Already scraping this site.", HttpStatus.OK);

    String message = batchJobService.doScrape(id);
    if (message == null)
      return new ResponseEntity<>("Batch scrape job " + id + " submitted", HttpStatus.OK);

    // todo make the easier to understand
    return new ResponseEntity<>(message, HttpStatus.BAD_REQUEST);
  }
}
