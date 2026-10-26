rodeo

Requires Java 21 and Git on the Mac. Install them first, for example with Homebrew:
  brew install openjdk@21 git

1. Copy the files:
   sudo mkdir -p /usr/local/rodeo /usr/local/etc/rodeo
   sudo cp rodeo.jar rodeo /usr/local/rodeo/
   sudo cp config.example.toml /usr/local/etc/rodeo/config.toml

2. Edit /usr/local/etc/rodeo/config.toml and set the webhook secrets.

3. Start rodeo as a background service:
   sudo cp com.oyamo.rodeo.plist /Library/LaunchDaemons/
   sudo launchctl bootstrap system /Library/LaunchDaemons/com.oyamo.rodeo.plist

To stop it:
   sudo launchctl bootout system/com.oyamo.rodeo
