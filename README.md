# PZRconServerPatch

Fix Project Zomboids RCON server, so it doesn't get stuck.

## Problem

I have observed that under heavy load, project zomboids RCON server will inevitably and inexplicably stop accepting connections.

After reviewing the code I believe this is due to a complete lack of timeout for all connections, commands and threads.

## Solution

Add timeout wherever possible. Here are the individual changes I've made:

1. **FIX(exec-timeout)**: add a timeout to the ExecCommand response handler, so it won't wait forever for a command. (Hardcoded 10 seconds, TODO: make this configurable)

2. **FIX(server-socket-timeout)**: add a timeout on the server socket so it won't wait forever for accepting new connections, this probably isn't a big deal, but it's good practice to not have this thread blocked forever.

3. **FIX(client-socket-timeout)**: add a timeout on the client socket so it won't wait forever for the client to do something.

4. **FIX(client-thread-quit-timeout)**: don't let the quit handler hang forever

5. **FIX(zombie-threads)**: monitor for zombie threads

6. **FIX(double-auth-send)**: for some reason, the zomboids rcon server send the auth packet twice.

7. **FIX(client-unexpected-error)**: quit on unexpected errors in the ClientThread

8. **FIX(packet-size-validation)**: don't trust the clients given packet size