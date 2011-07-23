package org.freejava.tools.handlers;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.sonatype.nexus.rest.model.NexusArtifact;
import org.sonatype.nexus.rest.model.NexusNGArtifact;
import org.sonatype.nexus.rest.model.NexusNGArtifactHit;
import org.sonatype.nexus.rest.model.SearchNGResponse;
import org.sonatype.nexus.rest.model.SearchResponse;

public class NexusSourceCodeFinder extends AbstractSourceCodeFinder implements SourceCodeFinder {

	private boolean cancelled = false;
	private String serviceUrl;

	public NexusSourceCodeFinder(String serviceUrl) {
		this.serviceUrl = serviceUrl;
	}

	@Override
    public void cancel() {
		this.cancelled = true;
    }

	@Override
    public void find(String binFile, List<SourceFileResult> results) {
        Collection<GAV> gavs = new HashSet<GAV>();
		try {
			String sha1;
	        FileInputStream fis = FileUtils.openInputStream(new File(binFile));
	        try {
	        	sha1 = DigestUtils.shaHex(fis);
	        } finally {
	        	IOUtils.closeQuietly(fis);
	        }
	        gavs.addAll(findArtifactsUsingNexus(serviceUrl, null, null, null, null, sha1));
        } catch (Exception e) {
            e.printStackTrace();
        }

		if (cancelled) return;

		try {
			gavs.addAll(findGAVFromFile(binFile));
        } catch (Exception e) {
            e.printStackTrace();
        }

		if (cancelled) return;

		Collection<String> sourcesUrls = new HashSet<String>();
		try {
			sourcesUrls.addAll(findSourcesUsingNexus(gavs, serviceUrl));
        } catch (Exception e) {
            e.printStackTrace();
        }

		for (String url : sourcesUrls) {
        	String result = download(url);
        	if (isSourceCodeFor(result, binFile)) {
        		results.add(new SourceFileResult(binFile, result, 100));
        	}
		}
    }

	private Collection<String> findSourcesUsingNexus(Collection<GAV> gavs, String serviceUrl) throws Exception {
		Collection<String> results = new HashSet<String>();
		String nexusUrl = getNexusContextUrl(serviceUrl);
        for (GAV gav : gavs) {
        	if (cancelled) return results;
        	Set<GAV> gavs2 = findArtifactsUsingNexus(serviceUrl, gav.getG(), gav.getA(), gav.getV(), "sources", null);
	        for (GAV gav2 : gavs2) {
	        	results.add(gav2.getArtifactLink());
	        }
        }

        return results;
	}

	private Set<GAV> findArtifactsUsingNexus(String serviceUrl, String g, String a, String v, String c, String sha1) throws Exception {
    	// http://repository.sonatype.org/service/local/lucene/search?sha1=686ef3410bcf4ab8ce7fd0b899e832aaba5facf7
		// http://repository.sonatype.org/service/local/data_index?sha1=686ef3410bcf4ab8ce7fd0b899e832aaba5facf7
        Set<GAV> results = new HashSet<GAV>();
		String nexusUrl = getNexusContextUrl(serviceUrl);

		String[] endpoints = new String[] {nexusUrl + "service/local/data_index"/*, nexusUrl + "service/local/lucene/search"*/};
		for (String endpoint : endpoints) {
        	if (cancelled) return results;
			String urlStr = endpoint;
	        LinkedHashMap<String, String> params = new LinkedHashMap<String, String>();
	        if (g != null) {
	        	params.put("g", g);
	        }
	        if (a != null) {
	        	params.put("a", a);
	        }
	        if (v != null) {
	        	params.put("v", v);
	        }
	        if (c != null) {
	        	params.put("c", c);
	        }
	        if (sha1 != null) {
	        	params.put("sha1", sha1);
	        }
	        for (Map.Entry<String, String> entry : params.entrySet()) {
	        	if (!urlStr.endsWith("&") && !urlStr.endsWith("?")) {
	        		if (urlStr.indexOf('?') == -1) urlStr += "?";
	        		else urlStr += "&";
	        	}
	        	urlStr += URLEncoder.encode(entry.getKey(), "UTF-8") + "=" + URLEncoder.encode(entry.getValue(), "UTF-8");
	        }

	        JAXBContext context = JAXBContext.newInstance(SearchResponse.class, SearchNGResponse.class );
	        Unmarshaller unmarshaller = context.createUnmarshaller();
	        URLConnection connection = new URL(urlStr).openConnection();
	        connection.connect();
	        try {
		        Object resp = unmarshaller.unmarshal( connection.getInputStream() );
		        if (resp instanceof SearchResponse) {
		        	SearchResponse srsp = (SearchResponse) resp;
		        	for (NexusArtifact ar : srsp.getData()) {
		        		GAV gav = new GAV();
		        		gav.setG(ar.getGroupId());
		        		gav.setA(ar.getArtifactId());
		        		gav.setV(ar.getVersion());
		        		gav.setArtifactLink(ar.getArtifactLink());
		        		results.add(gav);
		        	}
		        }
		        if (resp instanceof SearchNGResponse) {
		        	SearchNGResponse ngrsp = (SearchNGResponse) resp;
		        	for (NexusNGArtifact ar : ngrsp.getData()) {
		        		for (NexusNGArtifactHit hit : ar.getArtifactHits()) {
			        		GAV gav = new GAV();
			        		gav.setG(ar.getGroupId());
			        		gav.setA(ar.getArtifactId());
			        		gav.setV(ar.getVersion());
			        		results.add(gav);
		        		}
		        	}
		        }
	        } catch (Exception e) {
	        	e.printStackTrace();
			}
		}
        return results;
	}

	private String getNexusContextUrl(String serviceUrl) {
		String result = serviceUrl.substring(0, serviceUrl.lastIndexOf('/'));
		if (!result.endsWith("/")) {
			result += '/';
		}
		return result;
	}
}
