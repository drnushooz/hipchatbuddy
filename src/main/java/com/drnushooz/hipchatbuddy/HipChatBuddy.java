package com.drnushooz.hipchatbuddy;

import org.apache.log4j.BasicConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Hipchat status changer based on Google calendar
 *
 * @author abhinav chawade
 */
public class HipChatBuddy implements ActionListener {
    private static final Logger LOG = LoggerFactory.getLogger(HipChatBuddy.class);
    private ScheduledExecutorService gCalPoller;
    private GoogleCalendarThread gCalThread;
    private MenuItem pauseItem, exitItem, settingsItem, resumeItem;
    private TrayIcon trayIcon;

    private HipChatBuddy() {
        Map<String, Object> settingsMap;
        try {
            BasicConfigurator.configure();
            settingsMap = readSettingsFile("settings.yaml");
            LOG.info("Starting Hipchat Buddy version " + System.getProperty("version"));
            LOG.info("Settings: " + settingsMap.toString());
            //Initialize the system tray
            if (SystemTray.isSupported()) {
                LOG.info("System tray is supported");
                initializeSystemTray();
                startCalendarPoller(settingsMap);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readSettingsFile(String settingsFileName) {
        try {
            Yaml settings = new Yaml();
            Path settingsPath = Paths.get(
                System.getProperty("user.home")
                    + System.getProperty("file.separator")
                    + ".hipchatbuddy/"
                    + settingsFileName
            );
            BufferedReader settingsReader = Files.newBufferedReader(settingsPath);
            Map<String, Object> settingsMap = (Map<String, Object>) settings.load(settingsReader);
            settingsReader.close();
            return settingsMap;
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyMap();
        }
    }

    private void initializeSystemTray() throws AWTException {

        SystemTray systemTray = SystemTray.getSystemTray();

        PopupMenu popupMenu = new PopupMenu();
        pauseItem = new MenuItem("Pause");
        pauseItem.addActionListener(this);

        exitItem = new MenuItem("Exit");
        exitItem.addActionListener(this);

        settingsItem = new MenuItem("Settings");
        settingsItem.addActionListener(this);

        resumeItem = new MenuItem("Resume");
        resumeItem.addActionListener(this);

        popupMenu.add(pauseItem);
        popupMenu.add(resumeItem);
        popupMenu.addSeparator();
        popupMenu.add(settingsItem);
        popupMenu.add(exitItem);

        URL iconUrl = getClass().getClassLoader().getResource("hipchatbuddy.png");
        if (iconUrl != null) {
            trayIcon = new TrayIcon((new ImageIcon(iconUrl).getImage()));
            trayIcon.setPopupMenu(popupMenu);
            trayIcon.setToolTip("Hipchat Buddy is active");
            systemTray.add(trayIcon);
        }
    }

    private void startCalendarPoller(Map<String, Object> settingsMap) {
        gCalThread = new GoogleCalendarThread(settingsMap);
        LOG.info("Created google calendar and hipchat event threads. Starting");

        long pollInterval = (Long) settingsMap.get("poll_interval_ms");
        gCalPoller = Executors.newScheduledThreadPool(1);
        gCalPoller.scheduleWithFixedDelay(gCalThread, 0, pollInterval, TimeUnit.MILLISECONDS);
    }

    public void actionPerformed(ActionEvent event) {
        Object eventSource = event.getSource();
        if (eventSource == exitItem) {
            LOG.info("Shut down message received. Bye!!");
            gCalPoller.shutdown();
            System.exit(0);
        } else if (eventSource == pauseItem) {
            LOG.info("Pausing calendar poll");
            gCalThread.pause();
            if(trayIcon != null)
                trayIcon.setToolTip("Hipchat Buddy is paused");
        } else if (eventSource == resumeItem) {
            LOG.info("Resuming calendar poll");
            gCalThread.resume();
            if(trayIcon != null)
                trayIcon.setToolTip("Hipchat Buddy is active");
        } else if (eventSource == settingsItem) {
            LOG.info("Settings haven't been implemented yet");
            //TODO: add a settings dialog box
        }
    }

    public static void main(String[] args) {
        System.setProperty("apple.awt.UIElement", "true");
        new HipChatBuddy();
    }
}
