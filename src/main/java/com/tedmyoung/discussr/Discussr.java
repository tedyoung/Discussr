package com.tedmyoung.discussr;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

/**
 *
 */
public class Discussr {

  public static void main(String[] args) throws IOException {
    String savedTweetsDirectory = "c:/java/Discussr/";
    File sourceDir = new File(savedTweetsDirectory);
    for (File file : sourceDir.listFiles(new FilenameFilter() {
      public boolean accept(File dir, String name) {
        return name.startsWith("TweetStorage");
      }
    })) {
      String newFileName = file.getCanonicalPath().replace("TweetStorage", "TweetStorage\\");
      boolean renameSucceeded = file.renameTo(new File(newFileName));
      System.out.println("Rename to " + newFileName + " = " + renameSucceeded);
    }
//    Mongo mongo = new Mongo();
//    DB discussrDb = mongo.getDB("discussr");
//    BasicDBObject twitterStatusDoc = new BasicDBObject();
//    twitterStatusDoc.put("timelineOwner", "jitterted");
//    twitterStatusDoc.put("statusId", "188986042001534977");
//    twitterStatusDoc.put("sender", "robpark");
//    twitterStatusDoc.put("dateTime", "Sun Apr 08 06:45:58 PDT 2012");
//    twitterStatusDoc.put("inReplyToId", "188985346216833024");
//
//    BasicDBList links = new BasicDBList();
//    links.add("http://t.co/34ogGowe");
//    twitterStatusDoc.put("links", links);
//
//    DBCollection statusCollection = discussrDb.getCollection("statuses");
//    statusCollection.insert(twitterStatusDoc);
//
//    statusCollection = discussrDb.getCollection("statuses");
//    System.out.println("There are now " + statusCollection.getCount() + " documents.");
  }
}
