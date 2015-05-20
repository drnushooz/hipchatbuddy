Hipchatbuddy

Introduction
------------
Hipchat buddy is a standalone system tray application which poll primary Google calendar and changes hipchat visibility status. The application has no external dependencies but requires some initial steps to be completed so that it can access primary calendar. This is a 2 step process which requires token generation on Google calendar and Hipchat.

Getting started
--------------
1) Create directory name .hipchatbuddy under your home directory.
2) Generate a token for Google API
This is required to get read access to specific calendars.
Visit https://console.developers.google.com/project and click on "Create Project"
The application name should be HipchatBuddy.
Let the project ID be default.
Expand the advanced settings and select the datacenter that is closest to you.
Once the project is created, click on its name and go to "APIs & Auth". This is where you can customize the credentials and authorization. Hipchat buddy only needs read access to your primary calendar.
Click on APIs and select Calendar. if you can't find the API name, type it in search box.
Once propert access has been granted, click on credential and generate a new client ID. Once this is generated, download it as JSON and save it under ~/.hipchatbuddy as client_secrets.json

3) Generate a token for Hipchat API access
To be able to change status, the application requires write access. To generate a token visit https://www.hipchat.com and log in.
On right top corner, click on your picture and go to account settings.
Under account settings, visit API access. You will be asked for your password.
This should show you your OAuth2 bearer token. Keep in mind that this token allows access to ALL of your hipchat activity so treat this carefully. It is always possible to recreate this token.
Save this value in a notepad. You will need this in step 4.

4) Create ~/.hipchatbuddy/settings.yaml under it with following keys
hipchat_token: 'your unique token' # Token generated in step 3
hipchat_username: 'john.doe@datastax.com' # Usually your email address
hipchat_fullname : 'John Doe' # Exact name as it appears in Hipchat
hipchat_mention_name: 'johndoe' # Mention name without @
hipchat_email_address: 'john.doe@datastax.com' #
poll_interval_ms: 30000 # Be aware that Google only allows 10000 requests from any app per day.

5) Create the logging directory /var/log/hipchatbuddy with write access to your user.

6) Build the application
mvn clean package -DskipTests

7) Set JAVA_HOME in bin/launch.bash

8) Launch it using bin/launch.bash

When the application is launched, you will see little Koala bear in System tray area.

Features
-------------
* Observes primary calendar
* Sets status based on meeting topic with availability as stated in 'Show me as'
* Allows pausing and resuming integration

TODO
-------------
* Allow users to specify list of calendars to monitor
* Create settings dialog box
