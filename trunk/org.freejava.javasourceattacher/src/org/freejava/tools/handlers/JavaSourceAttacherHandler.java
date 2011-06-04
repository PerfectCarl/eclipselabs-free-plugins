package org.freejava.tools.handlers;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.IBiblioResolver;
import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Handler class for View Class/Package Dependency action.
 *
 * @see org.eclipse.core.commands.IHandler
 * @see org.eclipse.core.commands.AbstractHandler
 */
public class JavaSourceAttacherHandler extends AbstractHandler {
    /**
     * The constructor.
     */
    public JavaSourceAttacherHandler() {
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
        // * <li><code>org.eclipse.jdt.core.IPackageFragmentRoot</code></li>
        // * <li><code>org.eclipse.jdt.core.IJavaProject</code></li>
        IStructuredSelection structuredSelection = (IStructuredSelection) selection;
        List<IPackageFragmentRoot> selections = new ArrayList<IPackageFragmentRoot>();
        for (Iterator<?> iterator = structuredSelection.iterator(); iterator.hasNext();) {
            IJavaElement aSelection = (IJavaElement) iterator.next();
            if (aSelection instanceof IPackageFragmentRoot) {
                IPackageFragmentRoot pkgRoot = (IPackageFragmentRoot) aSelection;
                selections.add(pkgRoot);
            } else if (aSelection instanceof IJavaProject) {
                IJavaProject p = (IJavaProject) aSelection;
                try {
                    for (IPackageFragmentRoot pkgRoot : p.getPackageFragmentRoots()) {
                        selections.add(pkgRoot);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        updateSourceAttachments(selections);

        return null;
    }

    private void updateSourceAttachments(List<IPackageFragmentRoot> roots) {
        for (IPackageFragmentRoot pkgRoot : roots) {
            try {
                if (pkgRoot.getKind() == IPackageFragmentRoot.K_BINARY && pkgRoot.getSourceAttachmentPath() == null) {
                    File file = pkgRoot.getPath().toFile();
                    GAV gav = getGAV(file);
                    if (gav != null) {
                        File sourceFile = resolveArtifactSrc(gav);
                        if (sourceFile != null) {
                            pkgRoot.attachSource(new Path(sourceFile.getAbsolutePath()), null, null);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


    }

    private static GAV getGAV(File binFile) throws IOException {
        GAV result = null;
        String sha1 = DigestUtils.shaHex(FileUtils.openInputStream(binFile));
        System.out.println(sha1);
        //result = getGAVOnCentralBySHA1(binFile, sha1);
        if (result == null) {
        //    result = getGAVOnSonatypeBySHA1(binFile, sha1);
        }
        if (result == null) {
            result = getGAVOnGoogleBySHA1(binFile, sha1);
        }
        return result;
    }

    private static GAV getGAVOnCentralBySHA1(File binFile, String sha1) {
        GAV gav = null;
        try {
            String json = IOUtils.toString(new URL("http://search.maven.org/solrsearch/select?q=" + URLEncoder.encode("1:\"" + sha1 + "\"", "UTF-8") + "&rows=20&wt=json").openStream());
            JSONObject jsonObject = JSONObject.fromObject(json);
            JSONObject response = jsonObject.getJSONObject("response");
            for (int i = 0; i < response.getInt("numFound"); i++) {
                JSONArray docs = response.getJSONArray("docs");
                JSONObject doci = docs.getJSONObject(i);
                boolean hasSource = false;
                JSONArray ec = doci.getJSONArray("ec");
                String[] s = new String[ec.size()];
                for (int j = 0; j < ec.size(); j++) {
                    if ("sources.jar".equals(ec.getString(j))) {
                        hasSource  = true;
                        break;
                    }
                }
                if (!hasSource) continue;

                gav = new GAV();
                gav.setRepositoryUrl("http://repo1.maven.org/maven2/");
                gav.setG(doci.getString("g"));
                gav.setA(doci.getString("a"));
                gav.setV(doci.getString("v"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return gav;
    }

    private static GAV getGAVOnSonatypeBySHA1(File binFile, String sha1) {
        GAV gav = null;
        try {
            URL url = new URL("http://repository.sonatype.org/service/local/data_index?sha1=" + sha1);
            URLConnection con = url.openConnection();
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("Content-Type", "application/json");
            String json = IOUtils.toString(con.getInputStream());
            System.out.println(json);
            JSONObject jsonObject = JSONObject.fromObject(json);
            JSONArray data = jsonObject.getJSONArray("data");
            for (int i = 0; i < data.size(); i++) {
                JSONObject doci = data.getJSONObject(i);
                gav = new GAV();
                gav.setG(doci.getString("groupId"));
                gav.setA(doci.getString("artifectId"));
                gav.setV(doci.getString("version"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return gav;
    }

    private static GAV getGAVOnGoogleBySHA1(File binFile, String sha1) {
        GAV gav = null;
        try {
            URL url = new URL("http://ajax.googleapis.com/ajax/services/search/web?v=1.0&"
                    + "q=" + sha1);
            URLConnection connection = url.openConnection();
            String json = IOUtils.toString(connection.getInputStream());
            System.out.println(json);
            JSONObject jsonObject = JSONObject.fromObject(json);
            if (jsonObject.getInt("responseStatus") == 200) {
                JSONObject responseData = jsonObject.getJSONObject("responseData");
                JSONArray array = responseData.getJSONArray("results");
                for (int i = 0; i < array.size(); i ++) {
                    JSONObject obj = array.getJSONObject(i);
                    String rsurl = obj.getString("url");
                    System.out.println(rsurl);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return gav;
    }

    public static void main(String[] args) throws IOException {
        File bin = new File("D:\\projects\\free-plugins\\org.freejava.javasourceattacher\\lib\\commons-io-1.4.jar");
        GAV gav = null;
        gav = getGAV(bin);
        System.out.println("G:" + gav.getG());
        System.out.println("A:" + gav.getA());
        System.out.println("V:" + gav.getV());

        bin = new File("C:/Documents and Settings/Thai Ha/My Documents/Downloads/ant-1.5.1.jar");
        gav = getGAV(bin);
        System.out.println("G:" + gav.getG());
        System.out.println("A:" + gav.getA());
        System.out.println("V:" + gav.getV());

    }

    public File resolveArtifactSrc(GAV srcinfo) throws Exception {
        String groupId = srcinfo.getG();
        String artifactId = srcinfo.getA();
        String version = srcinfo.getV();
        //creates clear ivy settings
        IvySettings ivySettings = new IvySettings();
        IBiblioResolver resolver = new IBiblioResolver();
        resolver.setM2compatible(true);
        resolver.setName("central");
        ivySettings.addResolver(resolver);
        ivySettings.setDefaultResolver(resolver.getName());
        Ivy ivy = Ivy.newInstance(ivySettings);

        File ivyfile = File.createTempFile("ivy", ".xml");
        ivyfile.deleteOnExit();

        FileUtils.writeStringToFile(ivyfile,
        "<ivy-module version='2.0' xmlns:m='http://ant.apache.org/ivy/maven'><info organisation='apache' module='m2-with-sources' revision='1.0' /><dependencies><dependency org='" +
         groupId + "' name='" +artifactId+ "' rev='" + version + "' conf='default->sources' /></dependencies></ivy-module>");
        String[] confs = new String[]{"default"};
        ResolveOptions resolveOptions = new ResolveOptions().setConfs(confs);

        //init resolve report
        ResolveReport report = ivy.resolve(ivyfile.toURL(), resolveOptions);

        //so you can get the jar library
        File jarArtifactFile = report.getAllArtifactsReports()[0].getLocalFile();

        return jarArtifactFile;
    }

}
