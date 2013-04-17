package org.freejava.mirthtools;

import java.io.File;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

public class ExportAllChannelsHandler extends AbstractHandler {

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

        if (files.size() == 1 && files.get(0).isDirectory()) {
            try {
                File file = files.get(0);
                MirthSupport mirth = new MirthSupport();
                MirthSupport.ExportAllChannelsCommand command = new MirthSupport.ExportAllChannelsCommand();
                command.targetDir = file;
                mirth.run(command);
                IResource resource;
                if (sel.getFirstElement() instanceof IResource) {
                    resource = (IResource) sel.getFirstElement();
                } else {
                    resource = (IResource) ((IAdaptable) sel.getFirstElement()).getAdapter(IResource.class);
                }
                resource.refreshLocal(IResource.DEPTH_INFINITE, null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }

}
