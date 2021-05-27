package com.ajisaac.scrapebatch.scrape.executors;

import com.ajisaac.scrapebatch.dto.DatabaseService;
import com.ajisaac.scrapebatch.dto.JobPosting;
import com.ajisaac.scrapebatch.network.PageGrabber;
import com.ajisaac.scrapebatch.network.WebsocketNotifier;
import com.ajisaac.scrapebatch.scrape.CleanseDescription;
import com.ajisaac.scrapebatch.scrape.Scraper;
import com.ajisaac.scrapebatch.scrape.ScrapingExecutor;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * This type of class will have the ability to scrape a static non javascript site where all the
 * results are contained across multiple pages.
 */
public class MultiPageScrapingExecutor implements ScrapingExecutor {

  private final Scraper scraper;
  private DatabaseService databaseService;
  private WebsocketNotifier notifier;
  private final String name;

  private boolean hasMorePages = true;
  private int pauseTime = 10;
  // set this to true to stop the madness
  private boolean stopScrapingIndicator = false;

  public MultiPageScrapingExecutor(Scraper scraper) {
    this.scraper = scraper;
    this.name = scraper.getJobSite().name();
  }

  public void setDatabaseService(DatabaseService databaseService) {
    this.databaseService = databaseService;
  }

  @Override
  public void setWebsocketNotifier(WebsocketNotifier notifier) {
    this.notifier = notifier;
  }

  /**
   * Scrapes a job site once.
   */
  public void scrape() {

    while (hasMorePages && !stopScrapingIndicator) {
      pause(pauseTime);
      // get the page to scrape
      URI uri = scraper.getNextMainPageURI();
      if (uri == null) {
        break;
      }

      notifier.scrapingMainPage(uri.toString(), this.name);
      String mainPage = PageGrabber.grabPage(uri);
      if (mainPage == null) {
        notifier.failMainPageScrape(uri.toString(), this.name);
        break;
      }

      notifier.successfulMainPageScrape(uri.toString(), this.name);
      List<JobPosting> jobPostings = scraper.parseMainPage(mainPage);
      notifier.foundPostings(jobPostings.size(), this.name, uri.toString());

      for (JobPosting jobPosting : jobPostings) {
        if (jobPosting == null) {
          continue;
        }
        pause(pauseTime);
        notifier.scrapingDescPage(jobPosting.getHref(), this.name);
        String jobDescriptionPage = PageGrabber.grabPage(jobPosting.getHref());
        if (jobDescriptionPage == null || jobDescriptionPage.isBlank()) {
          notifier.failedDescPageScrape(jobPosting.getHref(), this.name);
          continue;
        }
        scraper.parseJobDescriptionPage(jobDescriptionPage, jobPosting);

        String desc = jobPosting.getDescription();
        if (desc != null) {
          desc = CleanseDescription.cleanse(desc);
          jobPosting.setDescription(desc);
        }

        notifier.successfulDescPageScrape(jobPosting, this.name);
        jobPosting.setJobSite(scraper.getJobSite().name());
        jobPosting.setStatus("new");
        databaseService.storeJobPostingInDatabase(jobPosting);
      }
    }
  }

  @Override
  public void stopScraping() {
    // will pretty much attempt to stop scraping before and after any
    // action that might take any amount of time
    this.stopScrapingIndicator = true;
  }

  /**
   * pause for random seconds to simulate being a human
   */
  private void pause(int maxSeconds) {
    try {
      int r = ThreadLocalRandom.current().nextInt(maxSeconds) + 1;
      notifier.sleeping(r, this.name);
      TimeUnit.SECONDS.sleep(r);
    } catch (InterruptedException e) {
      notifier.error(e, this.name);
      e.printStackTrace();
    }
  }


}
