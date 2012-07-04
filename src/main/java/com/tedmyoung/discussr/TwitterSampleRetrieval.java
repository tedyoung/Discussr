package com.tedmyoung.discussr;

import org.apache.commons.io.FileUtils;
import twitter4j.Paging;
import twitter4j.Query;
import twitter4j.QueryResult;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Tweet;
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
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * @author tyoung
 */
public class TwitterSampleRetrieval {
  public static final String SAVED_TWEETS_DIRECTORY = "c:/java/Discussr/TweetStorage/";
  private PrintWriter _writer;
  public static final String MOST_RECENT_SAVED_STATUS_ID_FILENAME = "C:/java/Discussr/MostRecentSavedStatusId.txt";

  private final ScheduledExecutorService _scheduler = Executors.newScheduledThreadPool(1);
  private static final boolean SHOULD_EXCLUDE_RETWEETS = false;
  private Configuration _configuration;
  private int _cumulativeSavedTweetCount = 0;

  public static void main(String[] args) throws IOException, TwitterException {

    System.out.println("Starting Scheduled Tweet Saver\n========");

    final TwitterSampleRetrieval retriever = new TwitterSampleRetrieval();

    retriever.start();
//    retriever.saveTwitterSearch();

  }

  private void start() {
    Runnable tweetSaverTask = new Runnable() {
      public void run() {
        try {
          saveNewTweets();
//          saveTwitterSearch("agileroots");
//          saveTwitterSearch("zomblatt");
        } catch (IOException e) {
          System.err.println("\nTweet Saver Task threw an exception:" + e.getMessage());
          e.printStackTrace();
        }
      }
    };

    final ScheduledFuture<?> tweetSaverHandle = _scheduler.scheduleAtFixedRate(tweetSaverTask, 0, 20, MINUTES);
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

  private void saveTwitterSearch(String queryString) {
    Twitter twitter = new TwitterFactory(getConfiguration()).getInstance();
    User user = getUser(twitter);
    if (user == null) {
      return;
    }

    System.out.println("Showing Search");
    Query searchQuery = new Query();
    searchQuery.setQuery(queryString);
//    searchQuery.setSinceId(211126682231386112L);
    searchQuery.setRpp(100);

    for (int i = 1; i <= 15; i++) {
      searchQuery.setPage(i);
      try {
        QueryResult queryResult = twitter.search(searchQuery);
        List<Tweet> foundTweets = queryResult.getTweets();
        if (foundTweets.isEmpty()) {
          break;
        }
        System.out.println("---- PAGE " + i );
        dumpTweets(foundTweets);
      } catch (TwitterException e) {
        e.printStackTrace();
      }
    }

  }

  private void dumpTweets(List<Tweet> tweets) {
    for (Tweet tweet: tweets) {
      String statusText = tweet.getText();
      System.out.println("[" + tweet.getId() + "] " + authoringScreenNameFor(tweet) + " (" + tweet.getCreatedAt().toString() + ")" + ": " + statusText);
    }
  }

  private String authoringScreenNameFor(Tweet tweet) {
    return "@" + tweet.getFromUser();
  }

  public void saveNewTweets() throws IOException {

    // gets Twitter instance with default credentials
    Twitter twitter = new TwitterFactory(getConfiguration()).getInstance();
    User user = getUser(twitter);
    if (user == null) {
      System.err.println("Failed to obtain user (see stack trace for more info)");
      return;
    }

//      ResponseList<Status> statuses = twitter.getUserTimeline("@neilkheop");
//      ResponseList<Status> statuses = twitter.getUserTimeline("academicdave");
//      ResponseList<Status> statuses = twitter.getUserTimeline("timoreilly", paging);

    System.out.println("Showing @" + user.getScreenName() + "'s home timeline.");

    int tweetCount = 0;
    Paging paging = new Paging();
    paging.setCount(100);
    long sinceId = Long.parseLong(FileUtils.readFileToString(new File(MOST_RECENT_SAVED_STATUS_ID_FILENAME)));
    System.out.println("Saving new statuses since ID: " + sinceId);
    if (sinceId <= 0) {
      System.err.println("Invalid last 'Since ID': " + sinceId);
      System.exit(-1);
    }
    paging.setSinceId(sinceId);
    long mostRecentSavedStatusId = 0;

    String fileName = SAVED_TWEETS_DIRECTORY + user.getScreenName() + "-tweets-2012-"
            + (Calendar.getInstance().get(Calendar.MONTH) + 1)
            + "-" + new Date().getDate() + "-"
            + new Date().getHours() + "-" + new Date().getMinutes() + ".txt";
    File tweetOutputFile = new File(fileName);
    _writer = new PrintWriter(tweetOutputFile);

    for (int page = 31; page >= 1; page--) {
      paging.setPage(page);
      PageInfo pageInfo;
      try {
        pageInfo = dumpPage(twitter, paging);
        if (pageInfo == null) { // abandon and retry later
          _writer.close();
          FileUtils.deleteQuietly(tweetOutputFile);
          System.out.println("Twitter is having problems (possibly over capacity), so abandoning retrieval on " + new Date());
          return;
        }
      } catch (TwitterException e) {
        e.printStackTrace();
        break;
      }

      mostRecentSavedStatusId = pageInfo.getMostRecentStatusIdInPage();

      int pageTweetCount = pageInfo.getStatusesReceived();
      tweetCount += pageTweetCount;
      if (pageTweetCount != 0) {
        System.out.println("On page " + page + " saved " + pageTweetCount + " tweets, with most recent status ID = " + mostRecentSavedStatusId);
        System.out.println("(Diff between Since ID and Most Recent: " + (mostRecentSavedStatusId - sinceId));
        System.out.println("----");
        System.out.flush();
      }
    }
    _writer.close();
    FileUtils.writeStringToFile(new File(MOST_RECENT_SAVED_STATUS_ID_FILENAME), String.valueOf(mostRecentSavedStatusId));
    _cumulativeSavedTweetCount += tweetCount;
    System.out.println("On " + new Date() + ", saved " + tweetCount + " tweets (" + _cumulativeSavedTweetCount + " overall), with most recent saved Status ID: " + mostRecentSavedStatusId);
    System.out.flush();
    System.err.flush();
  }

  private User getUser(Twitter twitter) {
    User user = null;
    try {
      user = twitter.verifyCredentials();
    } catch (TwitterException e) {
      e.printStackTrace();
      System.out.println("Error during verify credentials call. Exception message = '" + e.getErrorMessage() + "'" + ", Status Code = " + e.getStatusCode());
    }
    return user;
  }

  private Configuration getConfiguration() {
    if (_configuration == null) {
      ConfigurationBuilder cb = new ConfigurationBuilder();
      cb.setDebugEnabled(true)
          .setIncludeEntitiesEnabled(true)
          .setIncludeRTsEnabled(true)
          .setOAuthConsumerKey("Pckb0LT45Hyd1hIDmv2tew")
          .setOAuthConsumerSecret("fS1F31kQMJrT2R5ao7XSP0TurHIocthdFXD9NnMqVOg")
          .setOAuthAccessToken("16459579-Pjr6GOlyckrUwPFWQmeEdaKoGrTEpkxjopNNanNr5")
          .setOAuthAccessTokenSecret("Aqr13EzxHWX8vHrJqcjcCHMYhaltcol6h5LUpLcqEE");
      _configuration = cb.build();
    }
    return _configuration;
  }

  private PageInfo dumpPage(Twitter twitter, Paging paging) throws TwitterException {
    System.out.println("Requesting page " + paging.getPage() + " with Since ID " + paging.getSinceId() + " and MaxID " + paging.getMaxId());
    ResponseList<Status> statuses = null;
    try {
      statuses = twitter.getHomeTimeline(paging);
    } catch (TwitterException e) {
      System.err.println("Twitter Exception: " + e.getMessage());
      return null; // indicate that we should abandon trying to get info
    }
//    ResponseList<Status> statuses = twitter.getUserTimeline(paging);
    System.out.println("Received " + statuses.size() + " statuses.");
    if (statuses.size() == 0) {
      return new PageInfo(0, 0);
    }
    dumpStatuses(twitter, statuses);
    System.out.println();
    System.out.flush();

//    long oldestIdFromThisPage = Iterables.getLast(statuses).getId();
//    paging.setMaxId(oldestIdFromThisPage);
    return new PageInfo(statuses.size(), statuses.get(0).getId());
  }

  private void dumpStatuses(Twitter twitter, Iterable<Status> statuses) throws TwitterException {
    for (Status status : statuses) {
      String statusText = status.getText();
//      System.out.print(".");
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
  }

  private void println(String string) {
    _writer.println(string);
    System.out.println(string);
    System.out.flush();
  }

  private void print(String string) {
    _writer.print(string);
    System.out.print(string);
    System.out.flush();
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
    HttpURLConnection connection = null;
    try {
      connection = createHttpUrlConnectionFor(url.toString());
      while (connection.getResponseCode() == 301) {
        String previousLocation = newLocation;
        newLocation = connection.getHeaderField("location");
        print(" --> " + newLocation);
        if (previousLocation != null && previousLocation.equals(newLocation)) {
          System.err.println("\n\n!! Exiting due to redirect to same location."); System.err.flush();
          break;
        }
        connection = createHttpUrlConnectionFor(newLocation);
      }
    } catch (SocketTimeoutException ste) {
      System.err.println("\nCaught SocketTimeoutException: " + ste.getMessage() + (connection == null ? "" : " -> " + connection.getURL()));
    } catch (ConnectException ce) {
      System.err.println("\nCaught ConnectException: " + ce.getMessage() + (connection == null ? "" : " -> " + connection.getURL()));
    } catch (MalformedURLException e) {
      System.err.println("\nCaught MalformedURLException: " + e.getMessage() + (connection == null ? "" : " -> " + connection.getURL()));
//      e.printStackTrace();
    } catch (IOException e) {
      System.err.println("\nCaught IOException: " + e.getMessage() + url + (connection == null ? "" : " -> " + connection.getURL()));
//      e.printStackTrace();
    } finally {
      System.err.flush();
    }
    println("");
    return newLocation;
  }

  private static HttpURLConnection createHttpUrlConnectionFor(String newLocation) throws IOException {
    HttpURLConnection connection;
    connection = (HttpURLConnection) new URL(newLocation).openConnection();
    connection.setInstanceFollowRedirects(false);
    connection.setConnectTimeout(3000);
    connection.setReadTimeout(2000);
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
