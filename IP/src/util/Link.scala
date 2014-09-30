package util

import java.net.InetAddress

class Link {  
  var localVirtIP: InetAddress = _
  
  var remotePhysHost: InetAddress = _
  var remotePhysPort: Int = _
  var remoteVirtIP: InetAddress = _
}