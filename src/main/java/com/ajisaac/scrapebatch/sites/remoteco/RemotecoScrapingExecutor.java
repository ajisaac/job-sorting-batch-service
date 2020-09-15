package com.ajisaac.scrapebatch.sites.remoteco;

import com.ajisaac.scrapebatch.dto.JobPosting;
import com.ajisaac.scrapebatch.dto.ScrapeJob;
import com.ajisaac.scrapebatch.scrape.ScrapingExecutorType;
import com.ajisaac.scrapebatch.scrape.SinglePageScrapingExecutor;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.text.StringEscapeUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RemotecoScrapingExecutor extends SinglePageScrapingExecutor {
  private final String remotecoUrl = "https://remote.co/remote-jobs/developer";

  @Override
  protected List<JobPosting> parseMainPage(String mainPage) {
    // parse the page for jobs
    Document document = Jsoup.parse(mainPage);
    Elements anchors = document.getElementsByTag("a");
    List<Element> jobs =
        anchors.stream()
            .filter(a -> a.attr("href").startsWith("/job/"))
            .collect(Collectors.toList());

    List<JobPosting> jobPostings = new ArrayList<>();

    for (Element job : jobs) {
      JobPosting jobPosting = parseBasicJobPosting(job);
      if (jobPosting != null) {
        jobPostings.add(jobPosting);
      }
    }
    return jobPostings;
  }

  /** Parse the basic job that we got from the main page. */
  private JobPosting parseBasicJobPosting(Element job) {

    JobPosting jobPosting = new JobPosting();

    String href = parseHref(job);
    if (href == null) {
      return null;
    }
    jobPosting.setHref(href);
    String date = parseDate(job);
    jobPosting.setDate(date);

    return jobPosting;
  }

  private String parseDate(Element job) {
    Element dateElement = job.selectFirst("date");
    if (dateElement == null) {
      return "";
    } else {
      return dateElement.text();
    }
  }

  private String parseHref(Element job) {
    String rel = job.attr("href");
    if (rel.isBlank()) {
      return null;
    } else {
      return "https://remote.co" + rel;
    }
  }

  @Override
  protected JobPosting parseJobDescriptionPage(String jobDescriptionPage, JobPosting jobPosting) {

    Document document = Jsoup.parse(jobDescriptionPage);
    Element jsonData = document.selectFirst("body>script[type=\"application/ld+json\"]");
    if (jsonData == null) {
      // we still have the URL at least
      return jobPosting;
    }

    // all our data comes in a json object, so we will read that object.
    List<Node> nodes = jsonData.childNodes();
    if (nodes.isEmpty()) {
      return jobPosting;
    }

    Node n = nodes.get(0);
    String json = n.toString().trim();
    json = json.replaceAll("[\\n]", "");
    // the json isn't well formatted, we will yank out the job description
    String description = parseDescription(json);
    if (description == null) {
      // just fail fast if no description.
      return jobPosting;
    }
    jobPosting.setDescription(description);

    json = cleanJson(json);
    if (json == null) {
      return jobPosting;
    }

    JsonNode node;
    try {
      ObjectMapper objectMapper = new ObjectMapper();
      node = objectMapper.readTree(json);
    } catch (JsonProcessingException e) {
      return jobPosting;
    }

    JsonNode title = node.get("title");
    if (title != null) {
      jobPosting.setJobTitle(title.asText());
    }

    JsonNode datePosted = node.get("datePosted");
    if (datePosted != null) {
      jobPosting.setDate(datePosted.asText());
    }

    JsonNode applicationLocationRequirements = node.get("applicantLocationRequirements");
    if (applicationLocationRequirements != null) {
      JsonNode name = applicationLocationRequirements.get("name");
      if (name != null) {
        String misc = jobPosting.getMiscText();
        jobPosting.setMiscText(misc + " - " + name.asText());
      }
    }

    JsonNode hiringOrganization = node.get("hiringOrganization");
    if (hiringOrganization != null) {
      JsonNode name = hiringOrganization.get("name");
      if (name != null) {
        jobPosting.setCompany(name.asText());
      }
    }

    return jobPosting;
  }

  // find the description inside the json
  private String parseDescription(String json) {
    int descriptionOccurance = json.indexOf("\"description\" : \"");
    if (descriptionOccurance < 0) {
      return null;
    }
    if ((descriptionOccurance + 17) > json.length()) {
      return null;
    }
    int descriptionEnd = json.indexOf("\",", descriptionOccurance + 17);
    if (descriptionEnd < 0) {
      return null;
    }
    String description = json.substring(descriptionOccurance + 17, descriptionEnd);
    return description;
  }

  // remove the description so we can have clean json
  private String cleanJson(String json) {
    int descriptionOccurance = json.indexOf("\"description\" : \"");
    if (descriptionOccurance < 0) {
      return null;
    }
    if ((descriptionOccurance + 17) > json.length()) {
      return null;
    }
    int descriptionEnd = json.indexOf("\",", descriptionOccurance + 17);
    if (descriptionEnd < 0) {
      return null;
    }
    descriptionEnd += 2;
    String firstHalf = json.substring(0, descriptionOccurance);
    String secondHalf = json.substring(descriptionEnd);
    return firstHalf + secondHalf;
  }

  @Override
  protected String getMainPageHref() {
    return remotecoUrl;
  }

  @Override
  public void setScrapeJob(ScrapeJob scrapeJob) {
    // no current need
  }

  @Override
  public JobPosting setJobSite(JobPosting jobPosting) {
    jobPosting.setJobSite(ScrapingExecutorType.REMOTECO.toString());
    return jobPosting;
  }
}