package pain.onec.gui.screen;

import com.google.gson.Gson;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.input.WIntEdit;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.network.ServerInfo.ServerType;
import net.minecraft.client.option.ServerList;
import net.minecraft.util.Util;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import pain.onec.util.ServerScanner;


public class ServerScannerScreen extends WindowScreen {
    private WTextBox startIpField;
    private WTextBox endIpField;
    private WIntEdit portField;
    private WIntEdit threadCountField;
    private boolean addToServerList;
    private boolean addOnlyActive;
    private WButton addOnlyActiveToggleButton;
    private WButton addToServerListToggleButton;

    private Thread scanThread;

    private final ServerScanner scanner = new ServerScanner();

    private ScannerSettings settings;

    public ServerScannerScreen(GuiTheme theme) {
        super(theme, "Server Scanner");
        this.settings = ScannerSettings.load();
        this.addToServerList = settings.addToServerList;
        this.addOnlyActive = settings.addOnlyActive;
    }

    @Override
    public void initWidgets() {
        WHorizontalList infoList = add(theme.horizontalList()).expandX().widget();
        WTable table = add(theme.table()).expandX().widget();

        infoList.add(theme.label("I very, very strongly recommend you do not use this in it's current state, \nbut it is here if you want it. Back up your servers.dat file before using."));
        
        table.add(theme.label("Start IP:")).padRight(10);
        startIpField = table.add(theme.textBox(settings.startIp)).expandX().widget();
        table.row();

        table.add(theme.label("End IP:")).padRight(10);
        endIpField = table.add(theme.textBox(settings.endIp)).expandX().widget();
        table.row();

        table.add(theme.label("Port:")).padRight(10);
        portField = table.add(theme.intEdit(settings.port, 0, 65535, 0, 65535, false)).expandX().widget();
        table.row();

        table.add(theme.label("Threads:")).padRight(10);
        threadCountField = table.add(theme.intEdit(settings.threads, 0, 1024, 0, 1024, false)).expandX().widget();
        table.row();

        // Add to Server List toggle
        table.add(theme.label("Add to server list:"));
        addToServerListToggleButton = table.add(theme.button(Boolean.toString(addToServerList))).widget();
        addToServerListToggleButton.action = () -> {
            addToServerList = !addToServerList;
            addToServerListToggleButton.set(Boolean.toString(addToServerList));
        };

        table.row();

        // Add only active servers toggle
        table.add(theme.label("Add only active:"));
        addOnlyActiveToggleButton = table.add(theme.button(Boolean.toString(addOnlyActive))).widget();
        addOnlyActiveToggleButton.action = () -> {
            addOnlyActive = !addOnlyActive;
            addOnlyActiveToggleButton.set(Boolean.toString(addOnlyActive));
        };

        WHorizontalList buttonsRow = table.add(theme.horizontalList()).expandX().widget();

        WButton runButton = buttonsRow.add(theme.button("Run")).expandX().widget();
        runButton.action = this::runScript;

        WButton stopButton = buttonsRow.add(theme.button("Stop")).expandX().widget();
        stopButton.action = this::stopScript;

        table.row();

        WButton clearServersButton = table.add(theme.button("Clear Server List")).expandX().widget();
        clearServersButton.action = this::clearServerList;

        WButton refreshServersButton = table.add(theme.button("Refresh From JSON")).expandX().widget();
        refreshServersButton.action = this::refreshServers;
    }

    @Override
    public void close() {
        saveSettings();
        super.close();
    }

    private void saveSettings() {
        settings.startIp = startIpField.get();
        settings.endIp = endIpField.get();
        settings.port = portField.get();
        settings.threads = threadCountField.get();
        settings.addToServerList = addToServerList;
        settings.addOnlyActive = addOnlyActive;

        settings.save();
    }

    public void runScript() {
        String startIp = startIpField.get();
        String endIp = endIpField.get();
        Integer port = portField.get();
        Integer threadCount = threadCountField.get();

        // Stop any previous scan
        stopScript();

        scanThread = new Thread(() -> scanner.run(startIp, endIp, port, addToServerList, threadCount, false, addOnlyActive));
        scanThread.start();
    }

    public void stopScript() {
        if (scanThread != null && scanThread.isAlive()) {
            scanThread.interrupt();
            scanThread = null;
        }
    }

    public void clearServerList() {
        ServerList serverList = new ServerList(client);
        serverList.saveFile();
    }

    public void refreshServers() {
        Integer port = portField.get();
        Integer threadCount = threadCountField.get();

        stopScript();

        scanThread = new Thread(() -> scanner.run("0.0.0.0" , "0.0.0.0", port, addToServerList, threadCount, true, addOnlyActive));
        scanThread.start();
    }

    private static class ScannerSettings {
        public String startIp = "0.0.0.1";
        public String endIp = "255.255.255.255";
        public int port = 25565;
        public int threads = 128;
        public boolean addToServerList = true;
        public boolean addOnlyActive = false;

        private static final File FILE = new File("OneC/Scanner/settings.json");
        private static final Gson gson = new Gson();

        public void save() {
            try {
                FILE.getParentFile().mkdirs();
                FileWriter writer = new FileWriter(FILE);
                gson.toJson(this, writer);
                writer.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public static ScannerSettings load() {
            try {
                if (FILE.exists()) {
                    FileReader reader = new FileReader(FILE);
                    ScannerSettings settings = gson.fromJson(reader, ScannerSettings.class);
                    reader.close();
                    return settings;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            return new ScannerSettings(); // default values
        }
    }
}
