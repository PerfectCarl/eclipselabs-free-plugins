package org.freejava.tools.handlers;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IRegion;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IViewReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.freejava.tools.Activator;
import org.freejava.tools.handlers.dependency.DependencyView;

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

/**
 * Handler class for View Class/Package Dependency action.
 *
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class ViewDependencyHandler extends AbstractHandler {
    /**
     * The constructor.
     */
    public ViewDependencyHandler() {
    }

    /**
     * the command has been executed, so extract extract the needed information
     * from the application context.
     */
    public Object execute(ExecutionEvent event) throws ExecutionException {
        ISelection selection = HandlerUtil.getCurrentSelection(event);

        if (!(selection instanceof IStructuredSelection)) {
            return null;
        }
//   	 * <li><code>org.eclipse.jdt.core.ICompilationUnit</code></li>
//	 * <li><code>org.eclipse.jdt.core.IPackageFragment</code></li>
//	 * <li><code>org.eclipse.jdt.core.IPackageFragmentRoot</code></li>
//	 * <li><code>org.eclipse.jdt.core.IJavaProject</code></li>
        try {
            boolean isViewPackageDependency = event.getCommand().getId().equals("org.freejava.tools.commands.viewPackageDependencyCommand");
            IStructuredSelection structuredSelection = (IStructuredSelection) selection;
            Set<String> names = new HashSet<String>();
            Set<String> interfaces = new HashSet<String>();
            Set<File> files = new HashSet<File>();
            findFilterNamesAndJarClassFiles(isViewPackageDependency, structuredSelection, names, interfaces, files);

            Collection<Node> graphNodes;
            if (isViewPackageDependency) {
                graphNodes = getPackageDependency(files, names);
            } else {
                graphNodes = getClassDependency(files, names);
            }

            IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();

            // Send the result to the Dependency view and show that view to user
            IViewReference[] viewRefs =  page.getViewReferences();
            DependencyView  dependencyView = null;
            for (IViewReference viewRef : viewRefs) {
                if (viewRef.getId().equals(DependencyView.ID)) {
                    try {
                        dependencyView = (DependencyView) viewRef.getPage().showView(DependencyView.ID, null, IWorkbenchPage.VIEW_ACTIVATE);
                    } catch (PartInitException e) {
                        Activator.logError("Cannot show view " + DependencyView.ID, e);				}
                }
            }
            if (dependencyView == null) {
                try {
                    dependencyView = (DependencyView) page.showView(DependencyView.ID, null, IWorkbenchPage.VIEW_ACTIVATE);
                } catch (Exception e) {
                    Activator.logError("Cannot show view " + DependencyView.ID, e);
                }
            }
            dependencyView.setDependencyInfo(graphNodes, interfaces);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void findFilterNamesAndJarClassFiles(boolean isViewPackageDependency,
            IStructuredSelection structuredSelection, Set<String> names, Set<String> interfaces,
            Set<File> files) throws JavaModelException {
        for (Iterator<?> iterator = structuredSelection.iterator(); iterator.hasNext();) {
            IJavaElement aSelection = (IJavaElement) iterator.next();
            if (isViewPackageDependency) {
                if (aSelection instanceof ICompilationUnit) {
                    // ignore
                } else if (aSelection instanceof IPackageFragment) {
                    names.add(aSelection.getElementName());
                    if ((((IPackageFragment) aSelection).getKind() == IPackageFragmentRoot.K_BINARY)) {
                        IPackageFragment pkg = (IPackageFragment) aSelection;
                        IPackageFragmentRoot pkgRoot = ((IPackageFragmentRoot)pkg.getParent());
                        File file;
                        if (!pkgRoot.isExternal()) {
                            file = pkgRoot.getResource().getLocation().toFile();
                        } else {
                            file = pkgRoot.getPath().toFile();
                        }
                        files.add(file);
                    } else {
                        IRegion region = JavaCore.newRegion();
                        region.add(aSelection);
                        IResource[] resources = JavaCore.getGeneratedResources(region, false);
                        for(IResource resource : resources){
                            files.add(resource.getLocation().toFile());
                        }
                    }
                } else if (aSelection instanceof IPackageFragmentRoot) {
                    IPackageFragmentRoot pkgRoot = (IPackageFragmentRoot) aSelection;
                    for (IJavaElement e : pkgRoot.getChildren()) {
                        names.add(e.getElementName());
                    }
                    if (pkgRoot.getKind() == IPackageFragmentRoot.K_BINARY) {
                        File file;
                        if (!pkgRoot.isExternal()) {
                            file = pkgRoot.getResource().getLocation().toFile();
                        } else {
                            file = pkgRoot.getPath().toFile();
                        }
                        files.add(file);
                    } else {
                        IRegion region = JavaCore.newRegion();
                        region.add(aSelection);
                        IResource[] resources = JavaCore.getGeneratedResources(region, false);
                        for(IResource resource : resources){
                            files.add(resource.getLocation().toFile());
                        }
                    }
                } else if (aSelection instanceof IJavaProject) {
                    IJavaProject p = (IJavaProject) aSelection;
                    for (IPackageFragment pkg : p.getPackageFragments()) {
                        names.add(pkg.getElementName());
                    }
                    for (IPackageFragmentRoot pkgRoot : p.getPackageFragmentRoots()) {
                        if (pkgRoot.getKind() == IPackageFragmentRoot.K_BINARY) {
                            File file;
                            if (!pkgRoot.isExternal()) {
                                file = pkgRoot.getResource().getLocation().toFile();
                            } else {
                                file = pkgRoot.getPath().toFile();
                            }
                            files.add(file);
                        } else {
                            IRegion region = JavaCore.newRegion();
                            region.add(aSelection);
                            IResource[] resources = JavaCore.getGeneratedResources(region, false);
                            for(IResource resource : resources){
                                files.add(resource.getLocation().toFile());
                            }
                        }
                    }
                }
            } else {
                if (aSelection instanceof IClassFile) {
                    IClassFile clf = (IClassFile) aSelection;
                    names.add(clf.getType().getFullyQualifiedName());
                    if (clf.getType().isInterface()) interfaces.add(clf.getType().getFullyQualifiedName());
                    IPackageFragment pkg = (IPackageFragment) clf.getParent();
                    IPackageFragmentRoot pkgRoot = ((IPackageFragmentRoot)pkg.getParent());
                    File file;
                    if (!pkgRoot.isExternal()) {
                        file = pkgRoot.getResource().getLocation().toFile();
                    } else {
                        file = pkgRoot.getPath().toFile();
                    }
                    files.add(file);
                } else if (aSelection instanceof ICompilationUnit) {
                    ICompilationUnit unit = (ICompilationUnit) aSelection;
                    for(IType type : unit.getTypes()){
                        names.add(type.getFullyQualifiedName());
                        if (type.isInterface()) interfaces.add(type.getFullyQualifiedName());
                    }
                    IRegion region = JavaCore.newRegion();
                    region.add(aSelection);
                    IResource[] resources = JavaCore.getGeneratedResources(region, false);
                    for(IResource resource : resources){
                        files.add(resource.getLocation().toFile());
                    }
                } else if (aSelection instanceof IPackageFragment) {
                    IPackageFragment pkg = (IPackageFragment) aSelection;
                    for (IJavaElement e : pkg.getChildren()) {
                        if (e instanceof ICompilationUnit) {
                            ICompilationUnit unit = (ICompilationUnit) e;
                            for(IType type : unit.getTypes()){
                                names.add(type.getFullyQualifiedName());
                                if (type.isInterface()) interfaces.add(type.getFullyQualifiedName());
                            }
                        }
                        if (e instanceof IClassFile) {
                            IClassFile clf = (IClassFile) e;
                            names.add(clf.getType().getFullyQualifiedName());
                            if (clf.getType().isInterface()) interfaces.add(clf.getType().getFullyQualifiedName());
                        }
                    }
                    if ((((IPackageFragment)aSelection).getKind() == IPackageFragmentRoot.K_BINARY)) {
                        IPackageFragmentRoot pkgRoot = ((IPackageFragmentRoot)pkg.getParent());
                        File file;
                        if (!pkgRoot.isExternal()) {
                            file = pkgRoot.getResource().getLocation().toFile();
                        } else {
                            file = pkgRoot.getPath().toFile();
                        }
                        files.add(file);
                    } else {
                        IRegion region = JavaCore.newRegion();
                        region.add(aSelection);
                        IResource[] resources = JavaCore.getGeneratedResources(region, false);
                        for(IResource resource : resources){
                            files.add(resource.getLocation().toFile());
                        }
                    }
                } else if (aSelection instanceof IPackageFragmentRoot) {
                    IPackageFragmentRoot pkgRoot = (IPackageFragmentRoot) aSelection;
                    for (IJavaElement e : pkgRoot.getChildren()) {
                        IPackageFragment pkg = (IPackageFragment) e;
                        for (IJavaElement e2 : pkg.getChildren()) {
                            if (e2 instanceof ICompilationUnit) {
                                ICompilationUnit unit = (ICompilationUnit) e2;
                                for(IType type : unit.getTypes()){
                                    names.add(type.getFullyQualifiedName());
                                    if (type.isInterface()) interfaces.add(type.getFullyQualifiedName());
                                }
                            }
                            if (e2 instanceof IClassFile) {
                                IClassFile clf = (IClassFile) e2;
                                names.add(clf.getType().getFullyQualifiedName());
                                if (clf.getType().isInterface()) interfaces.add(clf.getType().getFullyQualifiedName());
                            }
                        }
                    }
                    if (pkgRoot.getKind() == IPackageFragmentRoot.K_BINARY) {
                        File file;
                        if (!pkgRoot.isExternal()) {
                            file = pkgRoot.getResource().getLocation().toFile();
                        } else {
                            file = pkgRoot.getPath().toFile();
                        }
                        files.add(file);
                    } else {
                        IRegion region = JavaCore.newRegion();
                        region.add(aSelection);
                        IResource[] resources = JavaCore.getGeneratedResources(region, false);
                        for(IResource resource : resources){
                            files.add(resource.getLocation().toFile());
                        }
                    }
                } else if (aSelection instanceof IJavaProject) {
                    IJavaProject p = (IJavaProject) aSelection;
                    for (IPackageFragment pkg : p.getPackageFragments()) {
                        for (IJavaElement e2 : pkg.getChildren()) {
                            if (e2 instanceof ICompilationUnit) {
                                ICompilationUnit unit = (ICompilationUnit) e2;
                                for(IType type : unit.getTypes()){
                                    names.add(type.getFullyQualifiedName());
                                    if (type.isInterface()) interfaces.add(type.getFullyQualifiedName());
                                }
                            }
                            if (e2 instanceof IClassFile) {
                                IClassFile clf = (IClassFile) e2;
                                names.add(clf.getType().getFullyQualifiedName());
                                if (clf.getType().isInterface()) interfaces.add(clf.getType().getFullyQualifiedName());
                            }
                        }
                    }
                    for (IPackageFragmentRoot pkgRoot : p.getPackageFragmentRoots()) {
                        if (pkgRoot.getKind() == IPackageFragmentRoot.K_BINARY) {
                            File file;
                            if (!pkgRoot.isExternal()) {
                                file = pkgRoot.getResource().getLocation().toFile();
                            } else {
                                file = pkgRoot.getPath().toFile();
                            }
                            files.add(file);
                        } else {
                            IRegion region = JavaCore.newRegion();
                            region.add(aSelection);
                            IResource[] resources = JavaCore.getGeneratedResources(region, false);
                            for(IResource resource : resources){
                                files.add(resource.getLocation().toFile());
                            }
                        }
                    }
                }
            }
        }
    }

    private Collection<Node> getPackageDependency(Collection<File> files, Collection<String> names) {

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
