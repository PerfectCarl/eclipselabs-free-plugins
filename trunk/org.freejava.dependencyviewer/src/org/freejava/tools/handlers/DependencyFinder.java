package org.freejava.tools.handlers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.jeantessier.classreader.AggregatingClassfileLoader;
import com.jeantessier.classreader.ClassNameHelper;
import com.jeantessier.classreader.ClassfileLoader;
import com.jeantessier.classreader.Field_info;
import com.jeantessier.classreader.LoadListenerVisitorAdapter;
import com.jeantessier.classreader.Method_info;
import com.jeantessier.classreader.Signature_attribute;
import com.jeantessier.classreader.TransientClassfileLoader;
import com.jeantessier.classreader.Visitable;
import com.jeantessier.classreader.Visitor;
import com.jeantessier.dependency.CodeDependencyCollector;
import com.jeantessier.dependency.CollectionSelectionCriteria;
import com.jeantessier.dependency.GraphSummarizer;
import com.jeantessier.dependency.LinkMaximizer;
import com.jeantessier.dependency.Node;
import com.jeantessier.dependency.NodeFactory;

public class DependencyFinder {

    public Collection<Node> getPackageDependency(Collection<File> files, Collection<String> names) {

        Collection<String> sources = new HashSet<String>();
        for (File file : files) {
            sources.add(file.getAbsolutePath());
        }

        NodeFactory factory = new NodeFactory();
        Visitor visitor = new CodeDependencyCollector(factory);
        ClassfileLoader loader = new TransientClassfileLoader();
        loader.addLoadListener(new LoadListenerVisitorAdapter(visitor));
        loader.load(sources);

        LinkMaximizer maximizer = new LinkMaximizer();
        maximizer.traverseNodes(factory.getPackages().values());

        CollectionSelectionCriteria scopeCriteria = new CollectionSelectionCriteria(names, null);
        scopeCriteria.setMatchingPackages(true);
        scopeCriteria.setMatchingClasses(false);
        scopeCriteria.setMatchingFeatures(false);
        CollectionSelectionCriteria filterCriteria = new CollectionSelectionCriteria(names, null);
        filterCriteria.setMatchingPackages(true);
        filterCriteria.setMatchingClasses(false);
        filterCriteria.setMatchingFeatures(false);

        GraphSummarizer dependenciesQuery = new GraphSummarizer(scopeCriteria, filterCriteria);
        dependenciesQuery.traverseNodes(factory.getPackages().values());
        Collection<Node> nodes = new ArrayList<Node>();
        nodes.addAll(dependenciesQuery.getScopeFactory().getPackages().values());
        return nodes;
    }

    public Collection<Node> getClassDependency(Collection<File> files, Collection<String> names) {
        Collection<String> sources = new HashSet<String>();
        for (File file : files) {
            sources.add(file.getAbsolutePath());
        }

        NodeFactory factory = new NodeFactory();
        Visitor visitor = new CodeDependencyCollector(factory) {
        	@Override
        	public void visitSignature_attribute(Signature_attribute attribute) {
        		super.visitSignature_attribute(attribute);
        		Visitable owner = attribute.getOwner();
        		String ownerFullSignature = null;
        		if (owner instanceof Field_info) {
        			ownerFullSignature = ((Field_info) owner).getFullSignature();
        		} else if (owner instanceof Method_info) {
        			ownerFullSignature = ((Method_info) owner).getFullSignature();
        		}
        		if (ownerFullSignature != null) {
	        		String sig = attribute.getSignature();
	        		for (String id : Splitter.on(CharMatcher.anyOf("<>;")).omitEmptyStrings().split(sig)) {
	                	if (id.startsWith("L")) {
	                		String className = id.substring(1);
	                		className = ClassNameHelper.path2ClassName(className);
	                		Node node = getFactory().createFeature(ownerFullSignature, true);
	                		node.addDependency(getFactory().createClass(className));
	                	}
	        		}
        		}
        	}
        };
        ClassfileLoader loader = new AggregatingClassfileLoader();
        loader.addLoadListener(new LoadListenerVisitorAdapter(visitor));
        loader.load(sources);

        LinkMaximizer maximizer = new LinkMaximizer();
        maximizer.traverseNodes(factory.getPackages().values());

        CollectionSelectionCriteria scopeCriteria = new CollectionSelectionCriteria(names, null);
        scopeCriteria.setMatchingPackages(false);
        scopeCriteria.setMatchingClasses(true);
        scopeCriteria.setMatchingFeatures(false);
        CollectionSelectionCriteria filterCriteria = new CollectionSelectionCriteria(names, null);
        filterCriteria.setMatchingPackages(false);
        filterCriteria.setMatchingClasses(true);
        filterCriteria.setMatchingFeatures(false);

        GraphSummarizer dependenciesQuery = new GraphSummarizer(scopeCriteria, filterCriteria);
        dependenciesQuery.traverseNodes(factory.getPackages().values());
        Collection<Node> nodes = new ArrayList<Node>();
        nodes.addAll(dependenciesQuery.getScopeFactory().getClasses().values());
        return nodes;
    }
}
