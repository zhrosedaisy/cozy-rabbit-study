Cozy Rabbit Study for Windows
=============================

Works on 64-bit Windows 10 and 11 with an Intel or AMD processor.

1. Install Java 21 or newer (once)
   Open "Terminal" or "Command Prompt" and run:
       winget install EclipseAdoptium.Temurin.21.JRE
   or download Temurin 21 (JRE or JDK) from https://adoptium.net and, in the installer,
   keep "Add to PATH" enabled.
   Check it worked:  java -version

2. Unzip this folder somewhere it can stay
   Tip: right-click the zip file, choose Properties and tick "Unblock" (if you see it) BEFORE extracting.
   Then "Extract All..." to a folder such as  C:\Users\<you>\Apps\cozy-rabbit-study

3. Add it to your Desktop and Start menu (once)
   Double-click  install.bat
   Then open "Cozy Rabbit Study" from the Desktop or the Start menu.
   You can also just double-click  start.bat  without installing anything.

Windows warnings
----------------
The app is not signed with a paid certificate, so Windows may show "Windows protected your PC"
(SmartScreen) or "Open File - Security Warning". Click "More info" and then "Run anyway" (or "Run").

Study rooms (studying together on the same network)
---------------------------------------------------
Joining a friend's room works right away.
If YOU host a room, Windows Defender Firewall asks for permission the first time: choose
"Allow access" for private networks. To allow it by hand, run this once in a Command Prompt
that was opened with "Run as administrator":
    netsh advfirewall firewall add rule name="Cozy Rabbit Study TCP" dir=in action=allow protocol=TCP localport=47321
    netsh advfirewall firewall add rule name="Cozy Rabbit Study UDP" dir=in action=allow protocol=UDP localport=47322

If a room doesn't connect
-------------------------
Rooms work over the local network. Many school, university, hotel and cafe networks (eduroam, for
example) don't let devices talk to each other. If joining fails there:
  - use a phone hotspot for everybody, or
  - put everybody on a VPN app such as Tailscale and type the host's VPN address in "Join a room".
The host's address is shown under "Study room" in the journal. 127.0.0.1 means "this computer" and
never works for a friend. Joining by the 4-digit code only works on a network that allows broadcasts.

If it doesn't start
-------------------
Open a Command Prompt in this folder and run:
    java -cp "lib\common\*;lib\win\*" com.cozyrabbit.Launcher
The message it prints tells what is wrong.

Your study data is stored in  C:\Users\<you>\.cozyrabbit
