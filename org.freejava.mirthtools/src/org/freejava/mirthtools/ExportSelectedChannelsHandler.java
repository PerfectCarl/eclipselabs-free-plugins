package org.freejava.mirthtools;

import java.io.File;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

public class ExportSelectedChannelsHandler extends AbstractHandler {

    /**
     * the command has been executed, so extract extract the needed information
     * from the application context.
     */
    public Object execute(ExecutionEvent event) throws ExecutionException {

        System.out.println("ExportAllChannelsHandler");

        ISelection selection = HandlerUtil.getCurrentSelection(event);
        if (!(selection instanceof IStructuredSelection)) {
            return null;
        }
        IStructuredSelection sel = (IStructuredSelection) selection;

        List<File> files = ToolsUtils.structuredSelectionToOsPathList(sel, event);
        for (Iterator<File> it = files.iterator(); it.hasNext();) {
            File file = it.next();
            if (!file.getName().endsWith(".xml")) {
                it.remove();
            }
        }
        if (!files.isEmpty()) {
            try {

                MirthSupport mirth = new MirthSupport();
                MirthSupport.ExportSelectedChannelsCommand command = new MirthSupport.ExportSelectedChannelsCommand();
                command.channels = files;
                mirth.run(command);

                for (Iterator it = sel.iterator(); it.hasNext();) {
                    Object object = it.next();
                    IResource resource;
                    if (object instanceof IResource) {
                        resource = (IResource) object;
                    } else {
                        resource = (IResource) ((IAdaptable) object).getAdapter(IResource.class);
                    }
                    resource.refreshLocal(IResource.DEPTH_INFINITE, null);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

}
