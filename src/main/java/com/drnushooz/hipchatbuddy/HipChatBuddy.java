package com.drnushooz.hipchatbuddy;

import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.BasicConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import javax.swing.ImageIcon;

/**
 * Hipchat status changer based on Google calendar
 * @author abhinav chawade
 *
 */
public class HipChatBuddy implements ActionListener
{
	private static final Logger LOG = LoggerFactory.getLogger(HipChatBuddy.class);
	private ScheduledExecutorService gCalPoller;
	private GoogleCalendarThread gCalThread;
	private final String settingsFile = "settings.yaml";
	private long pollInterval;
	private Map<String,Object> settingsMap;
	private MenuItem pauseItem,exitItem,settingsItem,resumeItem;
	private TrayIcon trayIcon;

	@SuppressWarnings("unchecked")
	private HipChatBuddy()
	{
		try
		{
			BasicConfigurator.configure();
			Yaml settings = new Yaml();
			File icon = new File("hipchatbuddy.png");
			BufferedReader settingsReader = new BufferedReader(new FileReader(System.getProperty("user.home")+System.getProperty("file.separator")+".hipchatbuddy/"+settingsFile));
			settingsMap = (Map<String,Object>) settings.load(settingsReader);
			settingsReader.close();
			pollInterval = (Integer)settingsMap.get("poll_interval_ms") * 1l;
			LOG.info("Starting Hipchat Buddy version "+System.getProperty("version"));
			LOG.info("Settings: "+settingsMap.toString());
			//Initialize the system tray
			if(SystemTray.isSupported())
			{
				LOG.info("System tray is supported");

				SystemTray systemTray = SystemTray.getSystemTray();
				trayIcon = new TrayIcon((new ImageIcon(getClass().getClassLoader().getResource("hipchatbuddy.png")).getImage()));

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
				
				trayIcon.setPopupMenu(popupMenu);
				trayIcon.setToolTip("Hipchat Buddy is active");
				systemTray.add(trayIcon);

				gCalThread = new GoogleCalendarThread(settingsMap);
				LOG.info("Created google calendar and hipchat event threads. Starting");

				gCalPoller = Executors.newScheduledThreadPool(1);
				gCalPoller.scheduleWithFixedDelay(gCalThread, 0, pollInterval, TimeUnit.MILLISECONDS);
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
		}
	}

	public void actionPerformed(ActionEvent event)
	{
		Object eventSource = event.getSource();
		if(eventSource == exitItem)
		{
			LOG.info("Shut down message received. Bye!!");
			gCalPoller.shutdown();
			System.exit(0);
		}
		else if(eventSource == pauseItem)
		{
			LOG.info("Pausing poll");
			gCalThread.pause();
			trayIcon.setToolTip("Hipchat Buddy is paused");
		}
		else if(eventSource == resumeItem)
		{
			LOG.info("Resuming poll");
			gCalThread.resume();
			trayIcon.setToolTip("Hipchat Buddy is active");
		}
		else if(eventSource == settingsItem)
		{
			LOG.info("Settings haven't been implemented yet");
			//TODO: add a settings dialog box
		}
	}

	public static void main( String[] args )
	{
		HipChatBuddy appl = new HipChatBuddy();
	}
}