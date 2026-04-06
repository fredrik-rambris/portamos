Portamos — macOS Notes
======================

IMPORTANT: Gatekeeper quarantine
---------------------------------
Because this binary is not signed with an Apple Developer ID, macOS will
block it the first time you try to run it:

  "portamos" cannot be opened because the developer cannot be verified.

To remove the quarantine flag, run this once after extracting the archive:

  xattr -d com.apple.quarantine portamos

Then move the binary somewhere on your PATH, for example:

  mv portamos /usr/local/bin/

You only need to do this once per downloaded binary.


Architecture
------------
The binary in this archive was built for Apple Silicon (arm64). It will
NOT run on Intel Macs. For Intel Macs, use the fat JAR instead:

  java -jar portamos-<version>-all.jar <arguments>

Java 21 or newer is required for the JAR. Download Eclipse Temurin 21
from https://adoptium.net/ if you do not have a suitable JVM installed.


Usage
-----
See README.md for the full CLI reference.
