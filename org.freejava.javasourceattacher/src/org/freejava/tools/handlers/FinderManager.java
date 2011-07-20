package org.freejava.tools.handlers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class FinderManager {

    private static String[] urls = new String[] {
            // Nexus
            "http://repository.sonatype.org/index.html",
            "https://repository.apache.org/index.html",
            "https://repository.jboss.org/nexus/index.html",
            "http://oss.sonatype.org/index.html",
            "http://repository.ow2.org/nexus/index.html",
            "https://nexus.codehaus.org/index.html",
            "http://maven2.exoplatform.org/index.html",
            "http://maven.nuxeo.org/nexus/index.html",
            "http://maven.alfresco.com/nexus/index.html",
            "https://repository.cloudera.com/index.html",
            "http://nexus.xwiki.org/nexus/index.html",

            // Maven Central
            "http://search.maven.org/",

            // jarvana
            "http://www.jarvana.com",

            // mvnsearch
            "http://www.mvnsearch.org/",

            // findjar
            "http://www.findjar.com/index.x",

            // Artifact Repository
            "http://www.artifact-repository.org",

            // mvnrepository
            "http://mvnrepository.com",

            // mvnbrowser
            "http://www.mvnbrowser.com",

            // mavenreposearch
            "http://www.mavenreposearch.com/",

            // ozacc
            "http://maven.ozacc.com/",

            // google
            "http://www.google.com"

    };
    private List<SourceFileResult> results;
    private Worker[] workers;

    public FinderManager() {
        results = Collections.synchronizedList(new ArrayList<SourceFileResult>());
        // Create a set of worker threads
        final int numWorkers = 10;
        workers = new Worker[numWorkers];
    }

    public List<SourceFileResult> getResults() {
		return results;
	}

    public boolean isRunning() {
        boolean result = false;
    	for (int i = 0; i < workers.length; i++) {
            if (workers[i] != null && workers[i].isAlive()) {
            	result = true;
            	break;
            }
        }
    	return result;
    }

    public void cancel() {
    	for (int i = 0; i < workers.length; i++) {
            if (workers[i] != null && workers[i].isAlive()) {
            	workers[i].cancel();
            }
        }
    	/*
    	for (int i = 0; i < workers.length; i++) {
            if (workers[i] != null && workers[i].isAlive()) {
            	try {
            		workers[i].join();
            	} catch (Exception e) {
					e.printStackTrace();
				}
            }
        }*/
    }

    public void findSourceFile(String binFile) throws Exception {

        // Create the work queue
        WorkQueue queue = new WorkQueue();
        for (int i = 0; i < workers.length; i++) {
            workers[i] = new Worker(queue, binFile, results);
            workers[i].start();
        }

        // Add some work to the queue
        for (int i = 0; i < urls.length; i++) {
            queue.addWork(urls[i]);
        }

        // Add special end-of-stream markers to terminate the workers
        for (int i = 0; i < workers.length; i++) {
            queue.addWork(Worker.NO_MORE_WORK);
        }
    }

    private static class WorkQueue {
        LinkedList queue = new LinkedList();

        // Add work to the work queue
        public synchronized void addWork(Object o) {
            queue.addLast(o);
            notify();
        }

        // Retrieve work from the work queue; block if the queue is empty
        public synchronized Object getWork() throws InterruptedException {
            while (queue.isEmpty()) {
                wait();
            }
            return queue.removeFirst();
        }
    }

    private static class Worker extends Thread {
        public static final Object NO_MORE_WORK = new Object();
        private WorkQueue q;
        private String binFile;
        private List<SourceFileResult> results;
        private boolean stopping;
        private SourceCodeFinder finder;

        public Worker(WorkQueue q, String binFile, List<SourceFileResult> results) {
            this.q = q;
            this.binFile = binFile;
            this.results = results;
            this.finder = new SourceCodeFinderFacade();
        }

        public void cancel() {
        	stopping = true;
        	this.finder.cancel();
        }

        public void run() {
            try {
                while (true && !stopping) {
                    Object x = q.getWork();
                    if (x == NO_MORE_WORK) {
                        break;
                    }
                    this.finder.find(binFile, (String) x, results);
                }
            } catch (InterruptedException e) {
            	e.printStackTrace();
            }
        }
    }
}
