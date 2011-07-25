
package org.freejava.tools.handlers;

import java.io.PrintStream;
import java.net.URL;

import com.google.gdata.client.codesearch.CodeSearchService;
import com.google.gdata.data.codesearch.CodeSearchEntry;
import com.google.gdata.data.codesearch.CodeSearchFeed;


/**
 * An application that serves as a sample to show how the CodeSearchService
 * can be used to retrieve data from Google Code Search.
 *
 *
 */


public class CodeSearchClient {
  private static final String CODESEARCH_FEEDS_URL =
    "https://www.google.com/codesearch/feeds/search?";

  private CodeSearchService codesearchService;
  private URL privateFeedUrl;

  public CodeSearchClient(String query,  String nresults, String start)
      throws Exception {
    codesearchService = new CodeSearchService("gdata-sample-codesearch");
    privateFeedUrl = new URL(CODESEARCH_FEEDS_URL + "q=" + query
                             + "&start-index=" + start +
                             "&max-results="+ nresults);
  }

  /**
   * Driver for the sample.
   *
   * @param out outputStream to which to write status and messages
   */

  public void run(PrintStream out) throws Exception {
    retrieveFeed(out);
  }

  /**
   * Retrieves a query feed.
   *
   * @param out outputStream on which to write status info
   * @throws Exception if error in retrieving feed
   */
  private void retrieveFeed(PrintStream out)
      throws Exception {
    //PrintWriter writer = new PrintWriter(out);
    //XmlWriter xmlWriter = new XmlWriter(writer);
    CodeSearchFeed myFeed =
      codesearchService.getFeed(privateFeedUrl, CodeSearchFeed.class);
    out.println("Retrieved feed: ");
    out.println("Title: " + myFeed.getTitle().getPlainText());
    out.println("Entries: " + myFeed.getEntries().size());
    out.println("Updated: " + myFeed.getUpdated());
    out.println("Start in: " + myFeed.getStartIndex());
    out.println("Entries:");

    for (CodeSearchEntry entry: myFeed.getEntries() ){
      // Default Gdata elements
      out.println("\tId: " + entry.getId());
      //out.println("\tTitle: " + entry.getTitle());
      //out.println("\tLink: " + entry.getHtmlLink().getHref());
      //out.println("\tUpdated: " + entry.getUpdated());
      //out.println("\tAuthor: " + entry.getAuthors().get(0).getName());
      //if (entry.getRights() != null)
      //  out.println("\tLicense:" + entry.getRights().getPlainText());
      // Codesearch Elements
      //out.println("\tPackage: ");
      //out.println("\t\t Name:" +
      //            entry.getPackage().getName());
      out.println("\t\t URI:" +
                  entry.getPackage().getUri());
      //entry.getPackage().generate(
      //    xmlWriter,
      //    codesearchService.getExtensionProfile());
      //out.println("XML: ");
      //writer.flush();
      out.println("");
      out.println("\tFile: " + entry.getFile().getName());
      //entry.getFile().generate(
      //    xmlWriter,
      //    codesearchService.getExtensionProfile());
      //out.println("XML: ");
      //writer.flush();
      //out.println("");
      //out.println("\tMatches: ");
      //for (Match m : entry.getMatches()) {
        //out.println(m.getLineNumber() + ": " +
        //            m.getLineText().getPlainText());
        //m.generate(
        //    xmlWriter,
        //    codesearchService.getExtensionProfile());
        //out.println("XML: ");
        //writer.flush();
        //out.println("");
      //}
    }
  }

  /**
   * Main entry point.  Parses arguments and creates and invokes the
   * CodeSearchClient.
   */
  public static void main(String[] arg)
      throws Exception {
    String query = "file:org/apache/commons/io/FilenameUtils.java";
    String nresults = "10";
    String start = "1";


    CodeSearchClient client = new CodeSearchClient(query, nresults, start);
    client.run(System.out);
  }
}
