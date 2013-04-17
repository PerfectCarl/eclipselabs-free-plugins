package org.freejava.mirthtools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;

import com.mirth.connect.client.core.Client;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.Connector;
import com.mirth.connect.model.LoginStatus;
import com.mirth.connect.model.Step;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.model.util.ImportConverter;
import com.mirth.connect.util.PropertyVerifier;

public class MirthSupport {

    private String server = "https://127.0.0.1:8443", user = "admin", password = "admin", version = "0.0.0";

    public static class ExportAllChannelsCommand {
        public File targetDir;
    };

    public static class ImportSelectedChannelsCommand {
        public List<File> channels;
    };
    public static class ExportSelectedChannelsCommand {
        public List<File> channels;
    };
    public MirthSupport() {
        try {
            InputStream is = getClass().getResourceAsStream("/org/freejava/mirthtools/mirth-cli-config.properties");
            Properties p = new Properties();
            p.load(is);
            is.close();

            if (p.containsKey("server"))
                server = p.getProperty("server");
            if (p.containsKey("user"))
                user = p.getProperty("user");
            if (p.containsKey("password"))
                password = p.getProperty("password");
            if (p.containsKey("version"))
                version = p.getProperty("version");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run(Object command) {
        try {
            Client client = new Client(server);

            if (client.login(user, password, version).getStatus() != LoginStatus.Status.SUCCESS) {
                System.out.println("Could not login to server.");
                return;
            }
            System.out.println("Connected to Mirth Connect server @ " + server + " (" + client.getVersion() + ")");

            if (command instanceof ExportAllChannelsCommand) {
                exportAllChannels(client, (ExportAllChannelsCommand) command);
            }
            if (command instanceof ExportSelectedChannelsCommand) {
                exportSelectedChannels(client, (ExportSelectedChannelsCommand) command);
            }
            if (command instanceof ImportSelectedChannelsCommand) {
                importSelectedChannels(client, (ImportSelectedChannelsCommand) command);
            }

            client.cleanup();
            client.logout();
            System.out.println("Disconnected from server.");
        } catch (ClientException ce) {
            ce.printStackTrace();
        }
    }

    private void importSelectedChannels(Client client, ImportSelectedChannelsCommand command) throws ClientException {
        for (File file : command.channels) {
            doImportChannel(client, file, true);
        }
    }

    private void doImportChannel(Client client, File importFile, boolean force) throws ClientException {
        String channelXML = "";

        try {
            channelXML = ImportConverter.convertChannelString(FileUtils.readFileToString(importFile));
        } catch (Exception e1) {
            System.out.println("invalid channel file." + e1);
            return;
        }

        ObjectXMLSerializer serializer = new ObjectXMLSerializer();
        Channel importChannel;

        try {
            importChannel = (Channel) serializer.fromXML(channelXML.replaceAll("\\&\\#x0D;\\n", "\n").replaceAll("\\&\\#x0D;", "\n"));
            PropertyVerifier.checkChannelProperties(importChannel);
            PropertyVerifier.checkConnectorProperties(importChannel, client.getConnectorMetaData());

        } catch (Exception e) {
            System.out.println("invalid channel file." + e);
            return;
        }

        String channelName = importChannel.getName();
        String tempId = client.getGuid();

        // Check to see that the channel name doesn't already exist.
        if (!checkChannelName(client, channelName, tempId)) {
            if (!force) {
                importChannel.setRevision(0);
                importChannel.setName(tempId);
                importChannel.setId(tempId);
            } else {
                for (Channel channel : client.getChannel(null)) {
                    if (channel.getName().equalsIgnoreCase(channelName)) {
                        // If overwriting, use the old revision number and id
                        importChannel.setRevision(channel.getRevision());
                        importChannel.setId(channel.getId());
                    }
                }
            }
        } else {
            // Start the revision number over for a new channel
            importChannel.setRevision(0);

            // If the channel name didn't already exist, make sure
            // the id doesn't exist either.
            if (!checkChannelId(client, importChannel.getId())) {
                importChannel.setId(tempId);
            }

        }

        importChannel.setVersion(client.getVersion());
        client.updateChannel(importChannel, true);
        System.out.println("Channel '" + channelName + "' imported successfully.");
    }

    /**
     * Checks to see if the passed in channel id already exists
     */
    public boolean checkChannelId(Client client, String id) throws ClientException {
        for (Channel channel : client.getChannel(null)) {
            if (channel.getId().equalsIgnoreCase(id)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks to see if the passed in channel name already exists and is
     * formatted correctly
     */
    public boolean checkChannelName(Client client, String name, String id) throws ClientException {
        if (StringUtils.isEmpty(name)) {
            System.out.println("Channel name cannot be empty.");
            return false;
        } else if (name.length() > 40) {
            System.out.println("Channel name cannot be longer than 40 characters.");
            return false;
        }

        Pattern alphaNumericPattern = Pattern.compile("^[a-zA-Z_0-9\\-\\s]*$");
        Matcher matcher = alphaNumericPattern.matcher(name);

        if (!matcher.find()) {
            System.out.println("Channel name cannot have special characters besides hyphen, underscore, and space.");
            return false;
        }

        for (Channel channel : client.getChannel(null)) {
            if (channel.getName().equalsIgnoreCase(name) && !channel.getId().equals(id)) {
                System.out.println("Channel \"" + name + "\" already exists.");
                return false;
            }
        }
        return true;
    }

    private void exportAllChannels(Client client, ExportAllChannelsCommand command) throws ClientException {
        List<Channel> allChannels = client.getChannel(null);
        for (Channel channel : allChannels) {
            try {
                System.out.println("Exporting channel: " + channel.getName());
                updateChannelFiles(channel, command.targetDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    private void exportSelectedChannels(Client client, ExportSelectedChannelsCommand command) throws ClientException {
        // get channel names
        Map<String, File> selectedChannels = new Hashtable<String, File>();
        for (File file : command.channels) {
            // Handle channel XML files changed events (exporting, SVN checkout)
            String fileName = file.getName();
            if (fileName.endsWith(".xml")) {
                try {
                    if (FileUtils.readFileToString(file).indexOf("<channel>") != -1) {
                        selectedChannels.put(fileName.substring(0, fileName.length() - 4), file);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        List<Channel> allChannels = client.getChannel(null);
        for (Channel channel : allChannels) {
            String channelName = channel.getName();
            if (selectedChannels.containsKey(channelName)) {
                try {
                    System.out.println("Exporting channel: " + channelName);
                    File targetDir = selectedChannels.get(channelName).getParentFile();
                    updateChannelFiles(channel, targetDir);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean updateChannelFiles(Channel channel, File targetDir) throws IOException {
        boolean changed = false;
        String channelName = channel.getName();
        ObjectXMLSerializer serializer = new ObjectXMLSerializer();
        File fXml = new File(targetDir, channelName + ".xml");
        String channelXML = serializer.toXML(channel);
        String oldXml = null;
        if (fXml.exists()) {
            oldXml = FileUtils.readFileToString(fXml, "UTF-8");
        }
        if (oldXml == null || !oldXml.equals(channelXML)) {
            // Write channel file
            FileUtils.writeStringToFile(fXml, channelXML, "UTF-8");
            changed = true;
        }
        return changed;
    }


    public List<File> onFileChanged(File file) throws Exception {
        List<File> changed = new ArrayList<File>();
        String fileName = file.getName();

        // Handle channel XML files changed events (exporting, SVN checkout)
        if (fileName.endsWith(".xml")) {
            if (FileUtils.readFileToString(file).indexOf("<channel>") != -1) {
                changed = onChannelXMLFileChanged(file);
            }
        }

        // Handle channel JS files changed events
        if (fileName.endsWith(".js")) {
            String basepath = fileName.substring(0, fileName.indexOf('.'));
            if (new File(file.getParentFile(), basepath + ".xml").exists()) {
                changed = onChannelJSFileChanged(file);
            }
        }
        return changed;
    }

    private List<File> onChannelJSFileChanged(File file) throws Exception {
        List<File> changedFiles = new ArrayList<File>();
        String fileName = file.getName();
        String channelName = fileName.substring(0, fileName.indexOf('.'));
        File channelFile = new File(file.getParentFile(), channelName + ".xml");
        if (channelFile.exists()) {

            Channel channel = loadChannelFromXMLFile(channelFile);

            String filePrefix = channelName + ".sourceConnector.";

            // Read JS files for source connector
            boolean changed = updateFromConnectorJSFileToChannelIfMatch(channel.getSourceConnector(), channelFile.getParentFile(), filePrefix, channel, file);

            // Read JS files for steps of source connector
            changed = updateFromTransformerJSFilesToChannelIfMatch(channel.getSourceConnector(), channelFile.getParentFile(), filePrefix, channel, file) || changed;

            List<Connector> connectors = channel.getDestinationConnectors();
            for (int i = 0; i < connectors.size(); i++) {
                Connector connector = connectors.get(i);
                filePrefix = channelName + ".destinationConnector." + i + ".";
                // Read JS files for destination connector
                changed = updateFromConnectorJSFileToChannelIfMatch(connector, channelFile.getParentFile(), filePrefix, channel, file) || changed;
                // Read JS files for steps of destination connectors
                changed = updateFromTransformerJSFilesToChannelIfMatch(connector, channelFile.getParentFile(), filePrefix, channel, file) || changed;
            }

            // Save channel back to XML file
            if (changed) {
                changed = updateChannelFiles(channel, channelFile.getParentFile());
                if (changed) {
                    changedFiles.add(channelFile);
                }
            }
        }
        return changedFiles;
    }

    private boolean updateFromConnectorJSFileToChannelIfMatch(Connector connector, File targetDir, String filePrefix, Channel channel, File file) throws IOException {

        boolean changed = false;

        Properties properties = connector.getProperties();
        if (StringUtils.equals(properties.getProperty("DataType"), "JavaScript Reader") || StringUtils.equals(properties.getProperty("DataType"), "JavaScript Writer")) {
            String jsFileName =  filePrefix  + "JavaScript.js";
            File jsFile = new File(targetDir, jsFileName);
            if (jsFile.getCanonicalPath().equals(file.getCanonicalPath())) {
                String newJS = FileUtils.readFileToString(jsFile, "UTF-8");

                String script = properties.getProperty("script");
                if (!StringUtils.equals(newJS, script)) {

                    properties.put("script", newJS);
                    connector.setProperties(properties);
                    changed = true;

                }
            }

        }
        return changed;
    }

    private boolean updateFromTransformerJSFilesToChannelIfMatch(Connector connector, File targetDir, String filePrefix, Channel channel, File file) throws IOException {
        boolean changed = false;
        List<Step> steps = connector.getTransformer().getSteps();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            if ("JavaScript".equals(step.getType())) {
                String jsFileName =  filePrefix  + i + "."+ step.getName() + ".js";
                File jsFile = new File(targetDir, jsFileName);
                if (jsFile.getCanonicalPath().equals(file.getCanonicalPath())) {
                    String newJS = FileUtils.readFileToString(jsFile, "UTF-8");

                    Map<Object, Object> data = (Map<Object, Object>) step.getData();
                    data.put("Script", newJS);
                    step.setData(data);
                    step.setScript(newJS);

                    changed = true;
                }
            }
        }
        return changed;
    }

    private Channel loadChannelFromXMLFile(File file) throws Exception {
        // Load Channel from XML
        Channel channel;
        Client client = new Client(server);
        if (client.login(user, password, version).getStatus() != LoginStatus.Status.SUCCESS) {
            System.out.println("Could not login to server.");
            throw new Exception("Could not login to server.");
        }
        System.out.println("Connected to Mirth Connect server @ " + server + " (" + client.getVersion() + ")");
        String channelXML = ImportConverter.convertChannelString(FileUtils.readFileToString(file));
        ObjectXMLSerializer serializer = new ObjectXMLSerializer();
        channel = (Channel) serializer.fromXML(channelXML.replaceAll("\\&\\#x0D;\\n", "\n").replaceAll("\\&\\#x0D;", "\n"));
        PropertyVerifier.checkChannelProperties(channel);
        PropertyVerifier.checkConnectorProperties(channel, client.getConnectorMetaData());
        client.cleanup();
        client.logout();
        System.out.println("Disconnected from server.");
        return channel;
    }

    private List<File> onChannelXMLFileChanged(File file) throws Exception {
        List<File> changed = new ArrayList<File>();

        Channel channel = loadChannelFromXMLFile(file);

        String channelName = channel.getName();
        File targetDir = file.getParentFile();

        String filePrefix = channelName + ".sourceConnector.";

        // Write JS file for source connector (JavaScript Reader) if any
        changed.addAll(writeConnectorJavaScriptReaderOrWriter(channel.getSourceConnector(), targetDir, filePrefix));

        // Write JS files for transformer steps of source connector
        changed.addAll(writeTransformerJSFiles(channel.getSourceConnector(), targetDir, filePrefix));

        List<Connector> connectors = channel.getDestinationConnectors();
        for (int i = 0; i < connectors.size(); i++) {
            Connector connector = connectors.get(i);
            filePrefix = channelName + ".destinationConnector." + i + ".";

            // Write JS files for destination connector (JavaScript Writer) if any
            changed.addAll(writeConnectorJavaScriptReaderOrWriter(connector, targetDir, filePrefix));

            // Write JS files for transformer steps of destination connectors
            changed.addAll(writeTransformerJSFiles(connector, targetDir, filePrefix));

        }
        return changed;
    }

    private List<File> writeConnectorJavaScriptReaderOrWriter(Connector connector, File targetDir, String filePrefix) throws IOException {

        List<File> changed = new ArrayList<File>();
        Properties properties = connector.getProperties();
        if (StringUtils.equals(properties.getProperty("DataType"), "JavaScript Reader") || StringUtils.equals(properties.getProperty("DataType"), "JavaScript Writer")) {
            String jsFileName =  filePrefix  + "JavaScript.js";
            File jsFile = new File(targetDir, jsFileName);
            String oldJS = null;
            if (jsFile.exists()) {
                oldJS = FileUtils.readFileToString(jsFile, "UTF-8");
            }
            String script = properties.getProperty("script");
            if (oldJS == null || !oldJS.equals(script)) {
                FileUtils.writeStringToFile(jsFile, script, "UTF-8");
                changed.add(jsFile);
            }

        }

        return changed;
    }

    private List<File> writeTransformerJSFiles(Connector connector, File targetDir, String filePrefix) throws IOException {
        List<File> changed = new ArrayList<File>();
        List<Step> steps = connector.getTransformer().getSteps();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            if ("JavaScript".equals(step.getType())) {
                String jsFileName =  filePrefix  + i + "."+ step.getName() + ".js";
                File jsFile = new File(targetDir, jsFileName);
                String oldJS = null;
                if (jsFile.exists()) {
                    oldJS = FileUtils.readFileToString(jsFile, "UTF-8");
                }
                String script = step.getScript();
                if (oldJS == null || !oldJS.equals(script)) {
                    FileUtils.writeStringToFile(jsFile, script, "UTF-8");
                    changed.add(jsFile);
                }

            }
        }
        return changed;
    }

}
