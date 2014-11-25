TCP over IP over UDP
====================

About
-----
One monitor, two men(Dev and QA by turns), seven weeks for peer coding, more than 70 scala files, more than 100 commits, more than 4400 lines code, sending 5GB data reliably in 11MB/s (Effective Bandwidth). Now, we have own TCP over own IP over UDP in Scala.

IP Design
---------
We treat UDP as our link layer, since the network model of UDP and Ethernet are similar. Our own IP and TCP are built on top of real UDP.
1.	NodeInterface and LinkInterface:
	* NodeInterface: Abstraction of Node, contains several LinkInterfaces belonged to this node. Also, it implements logics for forwarding, routing, multiplexing/demultiplexing to up-layer TCP, etc.

	* LinkInterface: Abstraction of NIC, contains information about the NIC, includes NIC information, buffer status, etc. Buffers in LinkInterface emulates queues in real NICs, storing packets that need to send to the network or receive from the network.

2.	Threads:
	* User input thread (main)

	* Sending thread: Fetch packets from each output Queue(Buffer) in LinkInterface and push these packets into the network 

	* Receiving thread: Get packets from network, and store these packets to corresponding input Queue(Buffer) in LinkInterface, based on virtual IP
	
	* HandlerManager thread: Get packets from input Queue and send to corresponding handler in the Node, based on protocol in the IP head (0 for RIP, 200 for Data, 6 for TCP)

	* PeriodicUpdate (5s) thread: Periodicly generate hearbeat RIP advertisements to all neighbors, includes all routing table and all up interfaces

	* Expire (12s) thread: Remove expired routes in the routing table, based on periodicUpdate received by neighbors

3. 	Queues(Buffers) in LinkInterface:
	* Input Queue (inBuffer): Store packets received by the Receiving thread from real UDP, and wait for HandlerManager thread to retrieve and get to the corresponding handler

	* Output Queue (outBuffer): When handlers finish corresponding logic, they may modify the original packets or generate new packets. After that, these packets will be stored in correct output queues, waiting for sending thread to send to the real network

4.	Lock: 
	* inBuffer/outBuffer - mutex for read/write

	* routing table - read/write lock

	* expire - read/write lock

5.	RIP:
	
	The node will generate three kinds of RIP advertisement:
	* Periodic Updates: Send all routing table entries and interfaces

	* RIP updates: Only send modified updates

	* RIP response: Send all routing table entries and interfaces to reply a RIP request

6. 	Convert Number/Object:

	We follow OOP model to design and implement the whole TCP/IP. So, in the node, everything is object, we convert all packet(bytes) from the network to object when we need to handle logic operations. Meanwhile, when we need to push it back to the network, we convert these objects to bytes, based on  IP head. In addition, in Scala, we also need to do bit shifting ourself, since Scala doesn't support uint16.

7. 	Identification:

	The range for identifier is from 0 to 2^16 - 1.

8.	Debug function:

	Our IP provides will log packetts, which can print received and sent packets in string or binary

TCP Design
----------
1.	Channel between IP and TCP:
	* TCPHandler: add TCP handler in the IP level to accept TCP segment sent to this node.

	* Two blocking buffer: store TCP segment, src IP and dst IP. If TCPHandler receives the packet and deliver the payload as segment to demultiplexing buffer. If TCP level sends the segment, it will store into multiplexing buffer. The buffer is implemented as blocking queue, which means when there is nothing to read, it will block the reading thread.
	
	* Demultiplex function: read from blocking buffer, check the tcp checksum, then find the corresponding connection (transmission control block - TCB) and call receive function in that TCB. Otherwise, drop that segment or send segment with reset bit when connecting to wrong port number.
	
	* Multiplex function: read from blocking buffer, add the tcp checksum, then send to remote node.

2.	TCP Connection(TCB):
	* Basic information: src IP, dst IP, src port, dst port (listen server is special one).

	* State machine: we have dealt with most TCP state machine, there are two kinds of situations to change the state, one is to call TCP API to manually set state and another is to change the state depending on receiving segment. If we receive segment that doesn't satisfies any matching case, we will drop it.

	* Sending buffer: in the wrapping buffer, we implement it by two arrays - writing buffer and flight buffer. If the user writes the data, it will append into writing buffer. If the sending data thread sends the data, it will remove from head of writing buffer and add into flight buffer. Finally, if we receive the acks, it will remove the length of receiving acks from flight buffer. Here, we add blocking if there is no enough space to write. In addition, here, we don't consider any logic about acknowledge number. The function that uses the wrapping buffer only pass the length of difference between new ack and original ack.

	* Receiving buffer: in the wrapping buffer, we implement it by two arrays - receiving buffer and pending buffer. Pending buffer will store the data that is valid (in the sliding window, this size is depending on the available space of receiving buffer). Each element in the array is a tuple, such as (begin pointer-included, end pointer-excluded, data array). For example, the user sends the data [a, b, c, d] from 0 to 3, we have received three segments [d], [b, c], [a, b] as this order. The pending buffer will store as follows:
		* before: empty, add: (3, 4, [d]) => after: (3, 4, [d]), no deliver to receiving buffer

		* before: (3, 4, [d]), add: (1, 3, [b, c]) => after: (1, 4, [b, c, d]), no deliver to receiving buffer

		* before: (1, 4, [b, c, d]), add: (0, 2, [a, b]) => after: (0, 4, [a, b, c, d]), deliver to receiving buffer

	  Here, we can see if there is hole in the pending array, it will store them or merge them until the data starting from 0 and deliver this slice. The user can read from receiving buffer if there is some data. Otherwise, it may block here. In addition, we deliver data from pending buffer to receiving buffer, the function will return the length of delivering data. Because, we don't deal with any sequence number here and it will deal with this logic in who uses this wrapping buffer.

	* Sequence/acknowledge number: at the beginning, the sequence number is a random value and acknowledge number will be updated on the three-way handshake. We also provide function to increase number depending whether it will start from 0. The TCB controls the sequence or acknowledge number and sending or receiving buffer only knows the difference between new or orignal number.

	* Flow control: the sending will know the advertised window size (flow window). The sliding window size of sending is depending on the minimum value between destination receiving flow window size and congection window size (Extra Credit). Sender keeps sending 1-byte segments when window size is 0 to probe the remote window size.

	* Three duplicate acks: the TCB always record ack for each receiving segment. When receiving three duplicate acks, we read one MSS segment from flight buffer of wrapping sending buffer and do fast retransmit.

	* Timeout: two kinds of timeout
		*	Connecting or teardown: it will 3 re-transmit SYN or FIN for connecting or teardown. Once it receives the valid ack back, it will cancel the timeout. Otherwise, after 9 seconds (3 * 3s), it will change the state to CLOSE and remove this connection.

		*	Data sending timeout: when sending some segments, we can't receive the updated ACKs after one RTO, which results into sending all the pending data (under the sliding window size) in the flight buffer from wrapping sending buffer. Otherwise, we cancel this timeout and set again by computed RTO. Once, there is no change for 20 times and it will set to CLOSE state, then disconnect.

	* RTO based on RTT: when sending segment, we record and set flag to wait for receiving. when receiving the segment that satisfied the sequence number of sending one, we computer SRTT and RTO. Later, set flag back to wait for sending.
		*	SRTT = ( ALPHA * SRTT ) + ((1-ALPHA) * RTT)

		*	RTO = min(1000, max(10, 2 * SRTT)) - unit: millisecond

	* Others: set block receiving or sending flag to block the sending or receiving this TCB.

3.	Threads:
	*	Demultiplex: one demultiplex control thread and one fixed thread pool that has 10 threads. When reading one segment from demultiplexing buffer, it will call one thread from pool in order to deal with this segment in TCP level.

	*	Multiplex: one multiplex control thread and one fixed thread pool that has 10 threads. When reading one segment from multiplexing buffer, it will call one thread from pool in order to send to IP level

	*	Data sending: this thread only starts to send data if and only if there is some data in sending buffer or receiving data, which means it needs to reply ACK. Otherwise, wait there. It is created after setting established state.

	*	Connect or teardown timeout: in the each state of three-way handshake or teardown, it will set timeout thread once sending out SYN or FIN segment. After changing state, it will be cancelled.

	*	Data sending timeout: similar to connect or teardown timeout thread, this thread is created after setting established state. It will wait for some time after sending some segments. Once receiving data, the timeout is reset. Otherwise, it is fired and sends all the flight data under sliding window size.

	*	Accept: application level, only when listening server receives some clients. The server offers the clients into queue, the accept thread poll out each one. 

	*	Receive file: application level, only when starting to receive the data into file.

	*	Sending file: application level, only when starting to send the data from file.

4.	Lock: three levels, we keep the lock order as TCP -> TCP connection -> Sending/receiving buffer, we avoid locking high level in the low level.
	*	TCP: the synchronized lock controls all the sockets, mapping sockets to each connection, mapping client/server tuples to each conncection. It will make sure they are synchronized to do any modify or read from global variables.

	*	TCP connection(TCB): the synchronized lock controls each connection. It will make sure sending segments and receiving segments are not same time. Because two behaviors may update or read acks or seqs. Only one thread can deal with this conncection.

	*	Sending/receiving buffer: the synchronized lock control each buffer. This aim is to avoid reading and writing at the same time.

5.	TCP API:
	*	virSocket: register one socket, it will find the minimum free socket number starting from 3

	*	virBind: given port number, bind to the socket

	*	virListen: give the socket and set to LISTEN state

	*	virConnect: connect to given ip and port until established or 3 re-transmitted SYNs timeout

	*	virAccept: poll one socket from listening queue and implement three-way handshake

	*	virRead: read from given socket

	*	virWrite: write to given socket

	*	virShutDown: depending on the type, block read/write/both, here, it doesn't remove from socket array

	*	virClose: it will send FIN to remote connection and remove itself from socket array, blocking writing

6. 	Exception(Scala):
	*	BoundedSocketException: the socket has been used

	*	DestinationUnreadable: the remote ip can't be reachable

	*	ErrorTCPStateException: error tcp state when expecting another state

	*	InvalidPortException: port is not valid. Range should be 1024 - 65535

	*	InvalidSocketException: the socket should from 3 to 65535, (0 - stdin, 1 - stdout 2 - stderr)

	*	PortUsedUpException: the port from 1024 to 65535 has been used

	*	ReadblockException: reading has been closed due to shutdown

	*	ServerCannotShutdownException: server(listen state) cannot be shutdown of read or write type, only can be shutdown by both type

	*	ServerHasCloseException: server has closed

	*	ServerClosedException: Socket has been closed

	*	SocketUsedUpException: the socket from 3 to 65535 has been used

	*	UnboundSocketException: socket is has not been bounded when using

	*	UninitialSocketException: socket has not been initiated when using

	*	UsedPortException: port has been used

	*	WriteBlockException: writing has been closed

User Manual
-----------
Usage: ./node linkfile

Test net (all the nodes): ./test.sh net/loop/

Manual:

	[h]help									Print this list of commands

	[li]interfaces							Interface information (local and remote IP)

	[lr]routes								Route information						

	[ls]sockets								Sockets, along with the state, connection

	[d]down <integer>						Bring one interface down

	[u]up <integer>							Bring one interface up

	[a]accept <port>						Spawn a socket, accept connections

	[c]connect <ip> <port>					Connect to the given ip address

	[si]sendip <ip> <proto> <data>			Send message to virtual IP

	[s/w]send <socket> <data>				Send a string on a socket

	[r]recv <socket> <numbytes> <y/n>		Read data from a given socket

	[sf]sendfile <filename> <ip> <port>		Send the entirety of the specified file

	[rf]recvfile <filename> <port>			Receive from socket to the given file

	[wd]window <socket>						Print the socket's send/receive window size

	[sd]shutdown <socket> <read/write/both>	Shutdown socket depending on kinds (not remove)

	[cl]close <socket>						Close on the given socket and remove socket

	[m]mtu <integer0> <integer1>			Set the MTU for link integer0 to integer1 bytes

	[q]quit									Quit the node

Extra Credit
------------
IP (TCP doesn't send MSS larger than mtu):

1.	The minimum mtu is set to the max head size + minimum offset (60 + 8 = 68)

2.	Fragmenting is done before sending the packet, while assembling the packet is done before receiving the packet that corresponding to one of interface of that node

3.	The time out (20s) of assembling is similar to that of expire

TCP (Congection control):

1.	Slow Start: start from one MSS, sendLen-the length for removing from flight buffer after receiving, totalLen-the total length of flight buffer
	*	cwd >= threshold: cwd = cwd + (sendLen/totalLen) * MSS

	*	cwd < threshold: cwd = cwd + sendLen

2.	Three duplicate acks: we set to half of congection window size, change threshold to this new congection window size

3.	Data sending timeout: we set threshold to half of congection window size and set congection window size to one MSS

Default Value
-------------
1.	(De)Multiplexing buffer size: 2 * 1024 * 1024 * 1024 - 1 Bytes

2.	Flow buffer size: 64 * 1024 - 1 Bytes

3.	MSS: 1400 - 40 Bytes

4. 	MTU: 1400 Bytes

5.	Listen pending queue: 64 * 1024 - 1 Bytes

6.	MSL: 2 * 60 * 1000 ms

7.	Retransmit times of connection or teardown: 3

8.	Timeout of connection or teardown: 3 * 1000 ms

9.	Retransmit of data fro the same ACKs: 20

10.	Threads pool: 10

11.	Socket: 3 - 65535

12.	Port: 1024 - 65535

13.	Loss: 0%, the range is [0%, 100%]

14.	Trace for multiplex/demuliplex: false (turn off debug printing)

15.	RTO: initial - 30 ms, upbound - 1000 ms, lowbound - 10 ms

16.	I/O file: 1024 * 10 bytes

Performance
-----------
Test 1GB data, based on Mac 16 GB 1600 MHz DDR3, 2.7 GHz Intel Core i7

1.	Effective bandwidth (a perfect link, two nodes, no loss): 10 MB/s (80Mb/s)

2.	Effective bandwidth (a perfect link, two nodes, 2% lost): 5 MB/s (40Mb/s), once lost, it may affect congection window, timeout and set to one MSS

3.	Effective bandwidth (a perfect link, two nodes, 2% lost, no congection control): 10 MB/s (80Mb/s)

4.	Effective bandwidth (a perfect link, two nodes, 5% lost, no congection control): 8 MB/s (64Mb/s)

Test 5GB data for all the sequence number range from 0 to 2^32 - 1, it is successful and effective bandwidth: 11MB/s (88Mb/s)

Limitation or Bug
-----------------
1.	All the interfaces will be sent for periodic update. It means if the user brings down one interface, it will update after 5s or remove from other nodes after 12s. It makes sense if the user brings down, then brings up quickly (reboot). We don't update the RIP for this situation.

2.	All the codes are compiled to byte code, then running on the JVM. It will be a little slower than other binary code. In addition, if we send data file to remote node with more than one hops, it will be very slow. This is due to some loss in each node, which is limited by UDP buffer.