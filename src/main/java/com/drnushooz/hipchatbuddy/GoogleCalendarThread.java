/**
 *
 */
package com.drnushooz.hipchatbuddy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.Events;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.request.body.RequestBodyEntity;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author abhinav chawade
 *
 */
class GoogleCalendarThread implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(GoogleCalendarThread.class);
    private HttpTransport httpTransport;
    private FileDataStoreFactory dataStoreFactory;
    private com.google.api.services.calendar.Calendar service;
    private Path dataStoreDir;
    private JsonFactory jsonFactory;
    private String hipchatAccessToken, hipchatUserName, hipchatMentionName, hipchatEmail, hipchatFullName;
    private AtomicBoolean pausedFlag;
    private long pollIntervalMs;


    GoogleCalendarThread(Map<String, Object> settingsMap) {
        assert (settingsMap != null);

        final String applicationName = "HipchatBuddy";
        pausedFlag = new AtomicBoolean(false);
        hipchatFullName = (String) settingsMap.get("hipchat_fullname");
        hipchatAccessToken = (String) settingsMap.get("hipchat_token");
        hipchatUserName = (String) settingsMap.get("hipchat_username");
        hipchatMentionName = (String) settingsMap.get("hipchat_mention_name");
        hipchatEmail = (String) settingsMap.get("hipchat_email_address");
        pollIntervalMs = (Integer) settingsMap.get("poll_interval_ms");

        try {
            String fileSeparator = System.getProperty("file.separator");
            String userHome = System.getProperty("user.home");
            dataStoreDir = Paths.get(userHome + fileSeparator + ".hipchatbuddy"); //Place where client token is saved
            if (Files.notExists(dataStoreDir)) {
                LOG.debug("Settings directory doesn't exist. Creating at " + dataStoreDir.toAbsolutePath());
                Files.createDirectories(dataStoreDir);
            }
            jsonFactory = JacksonFactory.getDefaultInstance();
            httpTransport = GoogleNetHttpTransport.newTrustedTransport();
            LOG.debug("Created http transport");
            dataStoreFactory = new FileDataStoreFactory(dataStoreDir.toFile());
            LOG.debug("Created data store factory at " + dataStoreDir.toAbsolutePath());
            Credential credential = authorize();
            service = new Calendar.Builder(httpTransport, jsonFactory, credential).setApplicationName(applicationName).build();
            LOG.debug("Created an authorized client");
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private Credential authorize() throws Exception {
        String fileSeparator = System.getProperty("file.separator");
        String userHome = System.getProperty("user.home");
        Path clientSecretsPath = Paths.get(userHome + fileSeparator + ".hipchatbuddy" + fileSeparator + "client_secrets.json");
        LOG.debug("Trying to read client_secrets.json from " + clientSecretsPath.toAbsolutePath());
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(jsonFactory, Files.newBufferedReader(clientSecretsPath));
        if (clientSecrets == null) {
            LOG.error("Client credentials in client_secrets.json cannot be null");
            System.exit(1);
        }
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
            httpTransport,
            jsonFactory,
            clientSecrets,
            Collections.singleton(CalendarScopes.CALENDAR_READONLY)
        ).setDataStoreFactory(dataStoreFactory).build();
        Credential cred = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize(hipchatEmail);
        LOG.info("User credentials saved to " + dataStoreDir.toAbsolutePath());
        return cred;
    }

    public void run() {
        final String hipchatApiUri = "https://api.hipchat.com";
        if (pausedFlag.get())
            return;
        try {
            long currentTime = System.currentTimeMillis();
            DateTime now = new DateTime(currentTime);
            DateTime window = new DateTime(currentTime + pollIntervalMs);
            Events events = service.events().list("primary")
                .setMaxResults(1)
                .setTimeMin(now)
                .setTimeMax(window)
                .setOrderBy("startTime")
                .setSingleEvents(true)
                .execute();
            List<Event> items = events.getItems();
            LOG.debug("Got information from Google calendar. Now setting status in HipChat");

            ObjectMapper objMapper = new ObjectMapper();
            Map<String, Object> messageBody = new HashMap<>();
            Map<String, String> statusField = new HashMap<>();

            messageBody.put("name", hipchatFullName);
            messageBody.put("mention_name", hipchatMentionName);
            statusField.put("status", "");
            statusField.put("show", null); //Available = null
            if (items != null && items.size() > 0) {
                Event event = items.get(0);
                DateTime start = event.getStart().getDateTime();
                if (start == null)
                    start = event.getStart().getDate();
                statusField.put("status", event.getSummary());
                if (event.getTransparency() == null || event.getTransparency().equals("opaque"))
                    statusField.put("show", "dnd");
            } else
                LOG.debug("No events at " + DateFormatUtils.ISO_TIME_NO_T_TIME_ZONE_FORMAT.format(now.getValue()));
            messageBody.put("presence", statusField);
            messageBody.put("email", hipchatEmail);

            RequestBodyEntity updateRequest = Unirest.put(hipchatApiUri + "/v2/user/" + hipchatUserName)
                .header("content-type", "application/json")
                .header("Authorization", "Bearer " + hipchatAccessToken)
                .body(objMapper.writeValueAsString(messageBody));
            HttpResponse<JsonNode> updateResponse = updateRequest.asJson();
            LOG.debug("Received response to update request " + updateResponse.getStatus() + " " + updateResponse.getStatusText());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    boolean isPaused() {
        return pausedFlag.get();
    }

    void pause() {
        pausedFlag.set(true);
    }

    void resume() {
        pausedFlag.set(false);
    }
}
