# WrapUdp - Tunnel simple UDP protocols via SSH.

`WrapUdp` is a small java application designed to wrap UDP datagrams so that they
can be shipped over TCP and therefore use any `ssh` tunnel.

Simple protocols that send datagrams to a server and receive a reply directed back
to the source IP/port are supported. Efficiency and throughput are not design goals.

Two instances of `WrapUdp` are executing:
  - on the *inside*, i.e., inside a firewall which is where the UDP service
    you wish to contact is running.
  - on the *outside* which is somewhere on the internet from where you want
    to reach the UDP sevice behind a firewall.
Both instances communicate via a `ssh` tunnel.

`WrapUdp` supports starting the `ssh` tunnel either from the *inside* or
from the *outside*. We call the computer where the `ssh` client runs
the *local* instance and the `ssh` server the *remote* instance.

In a 'normal' configuration the *local* computer is on the *outside*
and connects to a *remote* ssh server where the *inside* instance
of `WrapUdp` is executing and talking to the target:

     source -- UDP --> local/outside  --- SSH / TCP ------ remote/inside -- UDP -> target
                        ssh client                          ssh server

In a 'reverse' configuration the *local* computer is on the *inside*
and connects to a *remote* ssh server where the *outside* instance of
`WrapUdp` is executing.

     source -- UDP --> remote/outside --- SSH / TCP ------ local/inside  -- UDP -> target
                        ssh server                          ssh client

## Starting `WrapUdp` and the Tunnel

The *outside* instance of `WrapUdp` must be passed the destination address and port
of the target UDP server (which is residing on the *inside*):

    -H <hostname or ip of UDP server>
    -P <port number of UDP server>
    -L <port number where outside proxy is listening>

Note that even though the UDP server lives on the *inside* the information must be
passed to the *outside* instance of `WrapUdp`. The `-L` option is not mandatory;
if it is omitted the target UDP server's port number is used.

When using host names instead of IP numbers keep in mind that the names are resolved
on the *outside* and may not be publicly visible. In this case you must resort to
numerical addresses.

Since `WrapUdp` is executed on both, the local and the remote machine you need
a copy of `wrudp.jar` and a java-run time installation on both machines.

## Online Help

Brief usage info is printed by the '-h' option

    java -jar wrudp.jar -h

## Example of a 'normal' Setup.

On the 'local', 'outside' machine execute

    java -jar wrudp.jar -H insideUdpServer -P udpServerPort -- ssh ssh_server java -jar wrudp.jar

A connection to `localhost:udpServerPort` on the local machine is then proxied/forwarded to
`insideUdpServer:udpServerPort`.

## Example of a 'reverse' Setup.

On the 'local', 'inside' machine (ssh client *inside* the firewall) execute

    java -jar wrudp.jar -- ssh ssh_server java -jar wrudp.jar -H insideUdpServer -P udpServerPort -L localPort

A connection to `ssh_server:localPort` (on the *outside*) is then forwarded to 
`insideUdpServer:udpServerPort` *inside* the firewall.
