package org.freejava.mirthtools;

import java.io.File;
import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

public class ImportSelectedChannelsHandler extends AbstractHandler {

    /**
     * the command has been executed, so extract extract the needed information
     * from the application context.
     */
    public Object execute(ExecutionEvent event) throws ExecutionException {

        System.out.println("ImportSelectedChannelsHandler");

        ISelection selection = HandlerUtil.getCurrentSelection(event);
        if (!(selection instanceof IStructuredSelection)) {
            return null;
        }
        IStructuredSelection sel = (IStructuredSelection) selection;
        List<File> files = ToolsUtils.structuredSelectionToOsPathList(sel, event);

        if (!files.isEmpty()) {
            try {

                MirthSupport mirth = new MirthSupport();
                MirthSupport.ImportSelectedChannelsCommand command = new MirthSupport.ImportSelectedChannelsCommand();
                command.channels = files;
                mirth.run(command);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }


}
