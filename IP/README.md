README IP
=========

Design
------
1.	NodeInterface and LinkInterface:
	* NodeInterface: consist of UDP port, recv or send packet, a list of LinkInterface, it contains the configuration of this node.

	* LinkInterface: link information and up or down.

2.	Threads:
	* User input thread (main)

	* Sending thread: try to get packet from each outBuffer of interface

	* Receiving thread: try to push the packet from UDP into each inBuffer of interface
	
	* HandlerManager thread: the user should register two kinds of handler for protocol 0 and protocol 200

	* PeriodicUpdate (5s) thread: send out all the routing table

	* Expire (12s) thread: remove the entry from routing table has expired

3. 	Two kinds of Buffer for each LinkInterface:
	* inBuffer: read the data from UDP and assign packet to the corresponding inBuffer by Receiving thread, the HandlerManger thread will read the packet from inBuffer to call the handler depending on the protocol

	* outBuffer: HandlerManger will assign the output packet to the outBuffer of the corresponding interface, the sending thread will send it by UDP

4.	Lock: 
	* inBuffer/outBuffer - synchronized for read and write

	* routing table - read/write lock

	* expire - read/write lock

5.	RIP:
	* Total sending: periodic updates (LinkedHashMap to store address and time) and response to RIP request

	* Part sending: triggered updates

	* Sending interfaces: 1) when response to RIP request 2) when periodic updates

6. 	Convert Number/Object:

	In the node, all the things are object, we only convert bytes to object when receiving from UDP or convert object to bytes when sending by UDP. In the Scala, there is no unsigned type, we only make the type larger to be fit of that size, such as int corresponding to uint16 in C.

7. 	Identification:

	Start from 0, until meeting 2^16 - 1, then start 0 again.

8.	Debug function:

	In PrintIPPacket, you can choose print as general string or binary code to give more information about receiving or sending

User Manual
-----------
Usage: node <linkfile>

Test net (all the nodes): ./test.sh net/loop/

Manual:

	[h]elp							Help Printing

    [i]nterfaces					Interface information (local and remote IP)

    [r]outes						Routing table

    [d]own <integer>				Bring one interface down

    [u]p <integer>					Bring one interface up

    [s]end <vip> <proto> <string>	Send message to virtual IP

    [m]tu <integer0> <integer1> 	Set the MTU for link integer0 to integer1 bytes

    [q]uit 							Quit the node

Extra Credit
------------
1.	The minimum mtu is set to the head size + minimum offset (20 + 8 = 28)

2.	Fragmenting is done before sending the packet, while assembling the packet is done before receiving the packet that corresponding to one of interface of that node

3.	The time out (20s) of assembling is similar to that of expire

Limitation or Bug
-----------------
All the interfaces will be sent for periodic update. It means if the user brings down one interface, it will update after 5s or remove from other nodes after 12s. It makes sense if the user brings down, then brings up quickly (reboot). We don't update the RIP for this situation.
