package ip

class Receiving(interfaceList: Array[LinkInterface]) extends Runnable {
	def run() {
	  //will repeat until the thread ends
	  while (true){
	    for (interface <- interfaceList){
	      //If the interface is down, continue next one
	      if (interface.isUpOrDown){ 
	        interface.recvPacket
	      }
	    }
	  }
	}
}