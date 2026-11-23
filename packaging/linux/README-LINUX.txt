Cozy Rabbit Study for Linux
===========================

1. Install Java, version 21 or newer (once):
     Ubuntu / Debian:  sudo apt install openjdk-21-jre
     Fedora 44+:       sudo dnf install java-25-openjdk
                       (Fedora no longer ships Java 21; Java 25 works the same.
                        If that name isn't found:  sudo dnf install java-latest-openjdk)
   Check with:  java -version

   Works on normal Intel/AMD PCs and on ARM, including Fedora Asahi Remix on Apple-chip Macs.

2. Unzip this folder somewhere it can stay, for example ~/Apps/cozy-rabbit-study

3. Add it to your app menu (once):
     cd ~/Apps/cozy-rabbit-study
     bash install.sh

   Then open "Cozy Rabbit Study" from the app menu (or the desktop icon).
   You can also start it directly with:  ./start.sh

Study rooms (studying together on the same Wi-Fi)
-------------------------------------------------
Joining a friend's room works right away.
If YOU host a room and your firewall is on, allow the two ports once:
     Ubuntu (ufw):  sudo ufw allow 47321/tcp && sudo ufw allow 47322/udp
     Fedora:        sudo firewall-cmd --add-port=47321/tcp --add-port=47322/udp --permanent
                    sudo firewall-cmd --reload

If a room doesn't connect
-------------------------
Rooms work over the local network. Many school, university, hotel and cafe networks (eduroam, for
example) don't let devices talk to each other. If joining fails there:
  - use a phone hotspot for everybody, or
  - put everybody on a VPN app such as Tailscale and type the host's VPN address in "Join a room".
The host's address is shown under "Study room" in the journal. 127.0.0.1 means "this computer" and
never works for a friend. Joining by the 4-digit code only works on a network that allows broadcasts.

Your study data is stored in ~/.cozyrabbit

If it doesn't start
-------------------
Run it from a terminal to see the message:  ./start.sh
If you have several Java versions, pick one with:
     Fedora:  sudo alternatives --config java
     Ubuntu:  sudo update-alternatives --config java
