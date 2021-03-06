package com.ajisaac.scrapebatch.scrape.executors;

import com.ajisaac.scrapebatch.dto.DatabaseService;
import com.ajisaac.scrapebatch.dto.JobPosting;
import com.ajisaac.scrapebatch.network.PageGrabber;
import com.ajisaac.scrapebatch.network.WebsocketNotifier;
import com.ajisaac.scrapebatch.scrape.CleanseDescription;
import com.ajisaac.scrapebatch.scrape.scrapers.Scraper;

import javax.inject.Inject;
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
  //  @Inject
  DatabaseService db;
  private WebsocketNotifier notifier;
  private final String name;
  private static final int PAUSE_TIME = 10;

  private boolean stopped = false;

  public MultiPageScrapingExecutor(Scraper scraper) {
    this.scraper = scraper;
    this.name = scraper.getName();
  }

  public void setDb(DatabaseService db) {
    this.db = db;
  }

  @Override
  public void setWebsocketNotifier(WebsocketNotifier notifier) {
    this.notifier = notifier;
  }

  public void scrape() {

    while (true) {
      if (stopped) {
        notifier.send("Received signal to stop", this.name);
        return;
      }
      pause(PAUSE_TIME);
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

      jobPostings = scraper.removeJobPostingsBasedOnHref(jobPostings, db);
      notifier.send("Found " + jobPostings.size() + " non duplicate postings from " + uri + " for " + this.name, this.name);

      for (JobPosting jobPosting : jobPostings) {
        if (stopped) {
          notifier.send("Received signal to stop", this.name);
          return;
        }
        if (jobPosting == null)
          continue;

        pause(PAUSE_TIME);
        notifier.scrapingDescPage(jobPosting.getHref(), this.name);

        String jobDescriptionPage = PageGrabber.grabPage(jobPosting.getHref());
        if (jobDescriptionPage == null || jobDescriptionPage.isBlank()) {
          notifier.failedDescPageScrape(jobPosting.getHref(), this.name);
          continue;
        }

        scraper.parseJobDescriptionPage(jobDescriptionPage, jobPosting);

        var desc = jobPosting.getDescription();
        if (desc != null) {
          desc = CleanseDescription.cleanse(desc);
          jobPosting.setDescription(desc);
        }

        notifier.successfulDescPageScrape(jobPosting, this.name);

        jobPosting.setJobSite(scraper.getJobSite().name());
        jobPosting.setScraperName(this.scraper.getName());
        jobPosting.setStatus("new");

        db.storeJobPostingInDatabase(jobPosting);
      }

      if (!scraper.moreResults()) {
        break;
      }
    }
  }

  @Override
  public synchronized void stopScraping() {
    this.stopped = true;
  }

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
