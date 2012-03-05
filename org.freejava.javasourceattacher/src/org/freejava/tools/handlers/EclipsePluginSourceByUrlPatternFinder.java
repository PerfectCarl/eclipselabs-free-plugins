package org.freejava.tools.handlers;

import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.text.EditorKit;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;

import org.apache.commons.io.IOUtils;
import org.freejava.tools.handlers.classpathutil.Logger;

public class EclipsePluginSourceByUrlPatternFinder extends AbstractSourceCodeFinder implements SourceCodeFinder {

	private boolean canceled = false;

	// http://www.mmnt.ru/int/get?st={0}
	// http://www.searchftps.com/indexer/search.aspx?__LASTFOCUS=&__EVENTTARGET=ctl00%24MainContent%24SearchButton&__EVENTARGUMENT=&ctl00%24MainContent%24SearchKeywordTextBox={0}&ctl00%24MainContent%24SearchTypeDropDownList=And&ctl00%24MainContent%24SearchOrderDropDownList=DateDesc&ctl00%24MainContent%24SearchFilterDropDownList=NoFilter
	private String urlPattern;


    public EclipsePluginSourceByUrlPatternFinder(String urlPattern) {
    	this.urlPattern = urlPattern;
    }

	@Override
	public String toString() {
		return this.getClass() + "; urlPattern=" + urlPattern;
	}

	public void cancel() {
		this.canceled = true;
	}

	public void find(String binFile, List<SourceFileResult> results) {
		File bin = new File(binFile);
        String result[] = null;
        try {
	        String fileName = bin.getName();
        	int position = fileName.lastIndexOf('_');
	        if (position != -1) {
	        	String baseName = fileName.substring(0, position);
	        	String version = fileName.substring(position + 1);
		        String sourceFileName = baseName + ".source_" + version;
		        result = findFile(sourceFileName, bin);
	        }
        } catch (Exception e) {
			e.printStackTrace();
		}

        if (result != null && result[0] != null) {
        	String name = result[0].substring(result[0].lastIndexOf('/') + 1);

    		SourceFileResult object = new SourceFileResult(binFile, result[1], name, 50);
    		Logger.debug(this.toString() + " FOUND: " + object, null);
    		results.add(object);

        }
    }

    private String[] findFile(String fileName, File bin) throws Exception {
        String file = null;
        String url = null;

        List<String> links = searchFileLinksByName(fileName);
        for (Iterator<String> it = links.iterator(); it.hasNext();) {
            String link = it.next();
            boolean keep = false;
            if (link.endsWith("/" + fileName)) {
                    keep = true;
            }
            if (!keep) {
                it.remove();
            }
        }

        for (String url1 : links) {
            String tmpFile = download(url1);
        	if (tmpFile != null && isSourceCodeFor(tmpFile, bin.getAbsolutePath())) {
                file = tmpFile;
                url = url1;
                break;
            }
        }
        return new String[]{url, file};
    }

    private List<String> searchFileLinksByName(String fileName) throws Exception {
        List<String> result = new ArrayList<String>();
        URL url2 = new URL(urlPattern.replace("{0}", URLEncoder.encode(fileName, "UTF-8")));
        String html = getString(url2);
        //FileUtils.writeStringToFile(new File("D:\\debug.txt"), html);

        List<String> links = new ArrayList<String>();
        // Parse the HTML
        EditorKit kit = new HTMLEditorKit();
        HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
        doc.putProperty("IgnoreCharsetDirective", new Boolean(true));
        java.io.Reader reader = new StringReader(html);
        kit.read(reader, doc, 0);

        // Find all the A elements in the HTML document
        HTMLDocument.Iterator it = doc.getIterator(HTML.Tag.A);
        while (it.isValid()) {
            SimpleAttributeSet s = (SimpleAttributeSet)it.getAttributes();

            String href = (String) s.getAttribute(HTML.Attribute.HREF);
            if (href != null && !href.startsWith("javascript:") && !href.startsWith("news:") && href.indexOf('#') == -1) {
                String absHref = new URL(new URL(url2.toString()), href).toString();
                links.add(absHref);
            }
            it.next();
        }

        result.addAll(links);
        return result;
    }

    private String getString(URL url) throws Exception {
        String result = null;
        try {
            URLConnection con = url.openConnection();
            con.setRequestProperty("User-Agent", "Mozilla 5.0 (Windows; U; " + "Windows NT 5.1; en-US; rv:1.8.0.11) ");
            InputStream is = null;
            try {
                is = con.getInputStream();
                result = IOUtils.toString(is);
            } finally {
                IOUtils.closeQuietly(is);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    public static void main(String[] args) {
    	EclipsePluginSourceByUrlPatternFinder finder = new EclipsePluginSourceByUrlPatternFinder("http://www.mmnt.ru/int/get?st={0}");
    	List<SourceFileResult> results = new ArrayList<SourceFileResult>();
    	//finder.find("d:\\programs\\eclipse_mk4\\eclipse\\plugins\\org.eclipse.jdt.apt.core_3.3.500.v20110420-1015.jar", results);
    	//System.out.println(results.get(0).getSource());

    	finder = new EclipsePluginSourceByUrlPatternFinder("http://www.searchftps.com/indexer/search.aspx?__LASTFOCUS=&__EVENTTARGET=ctl00%24MainContent%24SearchButton&__EVENTARGUMENT=&ctl00%24MainContent%24SearchKeywordTextBox={0}&ctl00%24MainContent%24SearchTypeDropDownList=And&ctl00%24MainContent%24SearchOrderDropDownList=DateDesc&ctl00%24MainContent%24SearchFilterDropDownList=NoFilter");
    	results = new ArrayList<SourceFileResult>();
    	finder.find("d:\\programs\\eclipse_mk4\\eclipse\\plugins\\org.eclipse.jdt.apt.core_3.3.500.v20110420-1015.jar", results);
    	System.out.println(results.get(0).getSource());
	}
}
