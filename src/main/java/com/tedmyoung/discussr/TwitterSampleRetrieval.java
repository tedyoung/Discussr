package com.tedmyoung.discussr;

import com.google.common.collect.Iterables;
import org.apache.commons.io.FileUtils;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.URLEntity;
import twitter4j.User;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * @author tyoung
 */
public class TwitterSampleRetrieval {
  private PrintWriter _writer;
  public static final String MOST_RECENT_SAVED_STATUS_ID_FILENAME = "C:/java/Discussr/MostRecentSavedStatusId.txt";

  private final ScheduledExecutorService _scheduler = Executors.newScheduledThreadPool(1);
  private static final boolean SHOULD_EXCLUDE_RETWEETS = false;

  public static void main(String[] args) throws IOException, TwitterException {

    System.out.println("Starting Scheduled Tweet Saver\n========");

    final TwitterSampleRetrieval retriever = new TwitterSampleRetrieval();

    retriever.start();
  }

  private void start() {
    Runnable tweetSaverTask = new Runnable() {
      public void run() {
        try {
          saveNewTweets();
        } catch (TwitterException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    };

    final ScheduledFuture<?> tweetSaverHandle = _scheduler.scheduleAtFixedRate(tweetSaverTask, 0, 15, MINUTES);
//    _scheduler.schedule(new Runnable() {
//      public void run() {
//        boolean canceled = tweetSaverHandle.cancel(true);
//        if (canceled) {
//          System.out.println("TweetSaver successfully shutdown.");
//          System.exit(0);
//        } else {
//          System.out.println("TweetSaver could not be shutdown.");
//          System.exit(-1);
//        }
//      }
//    }, 90, MINUTES);
  }

  public void saveNewTweets() throws TwitterException, IOException {

    ConfigurationBuilder cb = new ConfigurationBuilder();
    cb.setDebugEnabled(true)
        .setIncludeEntitiesEnabled(true)
        .setIncludeRTsEnabled(true)
        .setOAuthConsumerKey("Pckb0LT45Hyd1hIDmv2tew")
        .setOAuthConsumerSecret("fS1F31kQMJrT2R5ao7XSP0TurHIocthdFXD9NnMqVOg")
        .setOAuthAccessToken("16459579-Pjr6GOlyckrUwPFWQmeEdaKoGrTEpkxjopNNanNr5")
        .setOAuthAccessTokenSecret("Aqr13EzxHWX8vHrJqcjcCHMYhaltcol6h5LUpLcqEE");
    Configuration configuration = cb.build();

    // gets Twitter instance with default credentials
    Twitter twitter = new TwitterFactory(configuration).getInstance();
    User user = twitter.verifyCredentials();

//      ResponseList<Status> statuses = twitter.getUserTimeline("@neilkheop");
//      ResponseList<Status> statuses = twitter.getUserTimeline("academicdave");
//      ResponseList<Status> statuses = twitter.getUserTimeline("timoreilly", paging);

    _writer = new PrintWriter("c:/java/Discussr/" + user.getScreenName() + "-tweets-2012-4-" + new Date().getDate() + "-" + new Date().getHours() + "-" + new Date().getMinutes() + ".txt");

    System.out.println("Showing @" + user.getScreenName() + "'s home timeline.");

    int tweetCount = 0;
    Paging paging = new Paging();
    paging.setCount(100);
    long sinceId = Long.parseLong(FileUtils.readFileToString(new File(MOST_RECENT_SAVED_STATUS_ID_FILENAME)));
    System.out.println("Saving new statuses since ID: " + sinceId);
    paging.setSinceId(sinceId);
    long mostRecentSavedStatusId = 0;
    for (int page = 1; page <= 31; page++) {
      paging.setPage(page);
      PageInfo pageInfo = dumpPage(twitter, paging);
      if (page == 1) {
        mostRecentSavedStatusId = pageInfo.getMostRecentStatusIdInPage();
      }
      int pageTweetCount = pageInfo.getStatusesReceived();
      tweetCount += pageTweetCount;
      if (pageTweetCount != 0) {
        System.out.println("On page " + page + " saved " + pageTweetCount + " tweets.");
      } else {
        System.out.println("On page " + page + " didn't get any tweets, so we're done for now.");
        break;
      }
      System.out.println("----");
    }
    _writer.close();
    FileUtils.writeStringToFile(new File(MOST_RECENT_SAVED_STATUS_ID_FILENAME), String.valueOf(mostRecentSavedStatusId));
    System.out.println("At " + new Date() + ", saved " + tweetCount + " tweets, with most recent saved Status ID: " + mostRecentSavedStatusId);
  }

  private PageInfo dumpPage(Twitter twitter, Paging paging) throws TwitterException {
    System.out.println("Requesting page " + paging.getPage() + " with Since ID " + paging.getSinceId());
    ResponseList<Status> statuses = twitter.getHomeTimeline(paging);
    System.out.println("Received " + statuses.size() + " statuses.");
    if (statuses.size() == 0) {
      return new PageInfo(0, 0);
    }
    for (Status status : statuses) {
      String statusText = status.getText();
      if (SHOULD_EXCLUDE_RETWEETS && (statusText.contains(" RT @") || statusText.startsWith("RT @"))) continue;
      println("[" + status.getId() + "] " + authoringScreenNameFor(status) + " (" + timeOf(status) + ")" + ": "
                      + statusText);
      if (status.getInReplyToScreenName() != null) {
        print("--> Is in reply to: " + inReplyToScreenNameFor(status));
        long replyToStatusId = status.getInReplyToStatusId();
        if (replyToStatusId != -1) {
          showInReplyToStatus(twitter, replyToStatusId);
        } else {
          println(" [Error in Reply To Status ID = -1]");
        }
      }
      if (status.getURLEntities().length > 0) {
        for (URLEntity urlEntity : status.getURLEntities()) {
          resolveRedirectsFor(urlEntity.getURL());
        }
      }
    }

    long oldestIdFromThisPage = Iterables.getLast(statuses).getId();
    paging.setMaxId(oldestIdFromThisPage);
    return new PageInfo(statuses.size(), statuses.get(0).getId());
  }

  private void println(String string) {
    _writer.println(string);
//    System.out.println(string);
  }

  private void print(String string) {
    _writer.print(string);
//    System.out.print(string);
  }

  private static String timeOf(Status status) {
    return status.getCreatedAt().toString();
  }

  private static String authoringScreenNameFor(Status status) {
    return "@" + status.getUser().getScreenName();
  }

  private static String inReplyToScreenNameFor(Status status) {
    return "@" + status.getInReplyToScreenName();
  }

  private void showInReplyToStatus(Twitter twitter, long replyToStatusId) throws TwitterException {
    try {
      print(", replying to status: " + replyToStatusId);
      Status replyStatus = twitter.showStatus(replyToStatusId);
      println(" - " + replyStatus.getText());
    } catch (TwitterException e) {
      println("-- !! couldn't get in reply to status !! " + e.getErrorMessage());
      e.printStackTrace(System.err);
    }
  }

  private String resolveRedirectsFor(URL url) {
    print("  " + url.toString());
    String newLocation = null;
    try {
      HttpURLConnection connection = createHttpUrlConnectionFor(url.toString());
      while (connection.getResponseCode() == 301) {
        newLocation = connection.getHeaderField("location");
        print(" --> " + newLocation);
        connection = createHttpUrlConnectionFor(newLocation);
      }
    } catch (SocketTimeoutException ste) {
      System.err.println("Caught SocketTimeoutException: " + ste.getMessage());
    } catch (ConnectException ce) {
      System.err.println("Caught ConnectException: " + ce.getMessage());
    } catch (MalformedURLException e) {
      System.err.println("Caught MalformedURLException: " + e.getMessage());
//      e.printStackTrace();
    } catch (IOException e) {
      System.err.println("Caught IOException: " + e.getMessage());
//      e.printStackTrace();
    }
    println("");
    return newLocation;
  }

  private static HttpURLConnection createHttpUrlConnectionFor(String newLocation) throws IOException {
    HttpURLConnection connection;
    connection = (HttpURLConnection) new URL(newLocation).openConnection();
    connection.setInstanceFollowRedirects(false);
    connection.setConnectTimeout(1000);
    connection.setReadTimeout(500);
    return connection;
  }

  private class PageInfo {
    private int _statusesReceived;
    private long _mostRecentStatusIdInPage;

    public PageInfo(int statusesReceived, long mostRecentStatusIdInPage) {
      _statusesReceived = statusesReceived;
      _mostRecentStatusIdInPage = mostRecentStatusIdInPage;
    }

    public int getStatusesReceived() {
      return _statusesReceived;
    }

    public long getMostRecentStatusIdInPage() {
      return _mostRecentStatusIdInPage;
    }
  }
}
