package com.ajisaac.scrapebatch.scrape.scrapers;

import com.ajisaac.scrapebatch.dto.JobPosting;
import com.ajisaac.scrapebatch.dto.ScrapeJob;
import com.ajisaac.scrapebatch.scrape.ScrapingExecutorType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class WorkingNomadsScraper extends Scraper {

  public WorkingNomadsScraper(ScrapeJob scrapeJob) {
    super(scrapeJob);
  }

  public List<JobPosting> parseMainPage(String mainPage) {
    final List<JobPosting> jobPostings = new ArrayList<>();
    // parse the page for jobs
    // they give us an API for convenience
    JsonNode nodes;
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      nodes = objectMapper.readTree(mainPage);
    } catch (JsonProcessingException e) {
      return jobPostings;
    }

    if (nodes == null) {
      return jobPostings;
    }

    if (!nodes.isArray()) {
      return jobPostings;
    }

    for (JsonNode job : nodes) {
      JobPosting jobPosting = new JobPosting();
      jobPosting.setIgnoreScrapeDescriptionPage(true);

      // url
      JsonNode url = job.get("url");
      if (url == null) {
        continue;
      }
      jobPosting.setHref(url.asText());

      // category_name
      JsonNode category = job.get("category_name");
      if (category == null || (!category.asText().equals("Development"))) {
        continue;
      }

      // title
      JsonNode title = job.get("title");
      if (title != null) {
        jobPosting.setJobTitle(title.asText());
      }

      // description
      JsonNode description = job.get("description");
      if (description != null) {
        jobPosting.setDescription(description.asText());
      } else {
        jobPosting.setDescription("");
      }

      // company_name
      JsonNode companyName = job.get("company_name");
      if (companyName != null) {
        jobPosting.setCompany(companyName.asText());
      } else {
        jobPosting.setCompany("unknown");
      }

      // tags
      JsonNode tags = job.get("tags");
      if (tags != null) {
        String[] tagsArr = tags.asText().split(",");
        String tagsFinal = String.join(" - ", tagsArr);
        jobPosting.setTags(tagsFinal);
      }

      // location
      JsonNode location = job.get("location");
      if (location != null) {
        jobPosting.setLocation(location.asText());
      }

      // pub_date
      JsonNode pubDate = job.get("pub_date");
      if (pubDate != null) {
        jobPosting.setDate(pubDate.asText());
      }

      jobPostings.add(jobPosting);
    }

    return jobPostings;
  }


  public ScrapingExecutorType getJobSite() {
    return ScrapingExecutorType.WORKINGNOMADS;
  }

  public URI getNextMainPageURI() {
    return URI.create("https://www.workingnomads.co/api/exposed_jobs");
  }


}
