package com.tedmyoung.discussr;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.Mongo;

import java.net.UnknownHostException;

/**
 *
 */
public class Discussr {

  public static void main(String[] args) throws UnknownHostException {
    Mongo mongo = new Mongo();
    DB discussrDb = mongo.getDB("discussr");
    BasicDBObject twitterStatusDoc = new BasicDBObject();
    twitterStatusDoc.put("timelineOwner", "jitterted");
    twitterStatusDoc.put("statusId", "188986042001534977");
    twitterStatusDoc.put("sender", "robpark");
    twitterStatusDoc.put("dateTime", "Sun Apr 08 06:45:58 PDT 2012");
    twitterStatusDoc.put("inReplyToId", "188985346216833024");

    BasicDBList links = new BasicDBList();
    links.add("http://t.co/34ogGowe");
    twitterStatusDoc.put("links", links);

    DBCollection statusCollection = discussrDb.getCollection("statuses");
    statusCollection.insert(twitterStatusDoc);

    statusCollection = discussrDb.getCollection("statuses");
    System.out.println("There are now " + statusCollection.getCount() + " documents.");
  }
}
