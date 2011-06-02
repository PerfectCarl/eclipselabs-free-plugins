package org.freejava.tools.handlers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IRegion;
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

import com.jeantessier.classreader.AggregatingClassfileLoader;
import com.jeantessier.classreader.Classfile;
import com.jeantessier.classreader.ClassfileLoader;
import com.jeantessier.classreader.LoadListenerVisitorAdapter;
import com.jeantessier.classreader.TransientClassfileLoader;
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
        IStructuredSelection structuredSelection = (IStructuredSelection) selection;
        List<IJavaElement> selections = new ArrayList<IJavaElement>();
        for (Iterator<?> iterator = structuredSelection.iterator(); iterator.hasNext();) {
        	IJavaElement aSelection = (IJavaElement) iterator.next();
            if (isViewPackageDependency(event)) {
            	if (aSelection instanceof ICompilationUnit) {
            		// ignore
            	} else if (aSelection instanceof IPackageFragment) {
                    selections.add(aSelection);
            	} else if (aSelection instanceof IPackageFragmentRoot) {
            		IPackageFragmentRoot pkgRoot = (IPackageFragmentRoot) aSelection;
            		try {
						for (IJavaElement e : pkgRoot.getChildren()) {
							selections.add(e);
						}
					} catch (JavaModelException e) {
						e.printStackTrace();
					}
            	} else if (aSelection instanceof IJavaProject) {
            		IJavaProject p = (IJavaProject) aSelection;
            		try {
						for (IJavaElement e : p.getPackageFragments()) {
							selections.add(e);
						}
					} catch (JavaModelException e) {
						e.printStackTrace();
					}
            	}
            } else {
            	if (aSelection instanceof ICompilationUnit) {
                    selections.add(aSelection);
            	} else if (aSelection instanceof IPackageFragment) {
            		IPackageFragment pkg = (IPackageFragment) aSelection;
            		try {
						for (IJavaElement e : pkg.getCompilationUnits()) {
							selections.add(e);
						}
					} catch (JavaModelException e) {
						e.printStackTrace();
					}
            	} else if (aSelection instanceof IPackageFragmentRoot) {
            		IPackageFragmentRoot pkgRoot = (IPackageFragmentRoot) aSelection;
            		try {
						for (IJavaElement e : pkgRoot.getChildren()) {
							IPackageFragment pkg = (IPackageFragment) e;
							for (IJavaElement e1 : pkg.getCompilationUnits()) {
								selections.add(e1);
							}
						}
					} catch (JavaModelException ex) {
						ex.printStackTrace();
					}
            	} else if (aSelection instanceof IJavaProject) {
            		IJavaProject p = (IJavaProject) aSelection;
            		try {
						for (IJavaElement e : p.getPackageFragments()) {
		            		IPackageFragment pkg = (IPackageFragment) e;
		            		try {
								for (IJavaElement e1 : pkg.getCompilationUnits()) {
									selections.add(e1);
								}
							} catch (JavaModelException ex) {
								ex.printStackTrace();
							}
						}
					} catch (JavaModelException e) {
						e.printStackTrace();
					}
            	}
            }
        }
        try {
            List<String> classFiles = getCorrespondingSources(selections);
            Collection<Node> graphNodes;
            if (isViewPackageDependency(event)) {
            	graphNodes = getPackageDependency(classFiles, selections);
            } else {
            	graphNodes = getClassDependency(classFiles);
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
            dependencyView.setDependencyInfo(graphNodes);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

	private boolean isViewPackageDependency(ExecutionEvent event) {
		return event.getCommand().getId().equals("org.freejava.tools.commands.viewPackageDependencyCommand");
	}

    private static List<String> getCorrespondingSources(List<? extends IJavaElement> elements){
        List<String> result = new ArrayList<String>();
        IRegion region = JavaCore.newRegion();
        for(IJavaElement element : elements) {
        	if (element.getClass().getSimpleName().equals("JarPackageFragment")) {
        		IPackageFragment fragment = (IPackageFragment) element;
        		String jarFilePath = fragment.getPath().toFile().getAbsolutePath();
        		if (!result.contains(jarFilePath)) {
        			result.add(jarFilePath);
        		}
        	} else {
        		region.add(element);
        	}
        }
        IResource[] resources = JavaCore.getGeneratedResources(region, false);
        for(IResource resource : resources){
            result.add(resource.getLocation().toOSString());
        }
        return result;
    }

//
//	private MessageConsole findConsole(String name) {
//		ConsolePlugin plugin = ConsolePlugin.getDefault();
//		IConsoleManager conMan = plugin.getConsoleManager();
//		IConsole[] existing = conMan.getConsoles();
//		for (int i = 0; i < existing.length; i++)
//			if (name.equals(existing[i].getName()))
//				return (MessageConsole) existing[i];
//		// no console found, so create a new one
//		MessageConsole myConsole = new MessageConsole(name, null);
//		conMan.addConsoles(new IConsole[] { myConsole });
//		return myConsole;
//	}

    private Collection<Node> getPackageDependency(Collection<String> sources, List<IJavaElement> selectedPackages) {

        NodeFactory factory = new NodeFactory();
        Visitor visitor = new CodeDependencyCollector(factory);
        ClassfileLoader loader = new TransientClassfileLoader();
        loader.addLoadListener(new LoadListenerVisitorAdapter(visitor));
        loader.load(sources);

        LinkMaximizer maximizer = new LinkMaximizer();
        maximizer.traverseNodes(factory.getPackages().values());

        List<String> names = new ArrayList<String>();
        for (IJavaElement element : selectedPackages) {
            names.add(element.getElementName());
        }
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
    private Collection<Node> getClassDependency(Collection<String> sources) {

        NodeFactory factory = new NodeFactory();
        Visitor visitor = new CodeDependencyCollector(factory);
        ClassfileLoader loader = new AggregatingClassfileLoader();
        loader.addLoadListener(new LoadListenerVisitorAdapter(visitor));
        loader.load(sources);

        LinkMaximizer maximizer = new LinkMaximizer();
        maximizer.traverseNodes(factory.getPackages().values());

        List<String> names = new ArrayList<String>();
        for (Classfile cf : loader.getAllClassfiles()) {
        	names.add(cf.getClassName());
        }
//        for (IJavaElement element : selectedClasses) {
//            names.add(element.getElementName());
//        }
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

//	public static Collection<String> getAllClassFilesUnderDirectory(File dir) {
//    	Collection<String> result = new ArrayList<String>();
//        if (dir.isDirectory()) {
//            String[] children = dir.list();
//            for (int i=0; i<children.length; i++) {
//            	result.addAll(getAllClassFilesUnderDirectory(new File(dir, children[i])));
//            }
//        } else {
//            if (dir.getName().endsWith(".class")) result.add(dir.getAbsolutePath());
//        }
//        return result;
//    }
}
