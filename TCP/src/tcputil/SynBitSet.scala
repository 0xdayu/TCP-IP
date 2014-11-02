package tcputil

import java.util.BitSet

class SynBitSet {
  val bitSet = new BitSet

  def get(bitIndex: Int): Boolean = {
    this.synchronized {
      bitSet.get(bitIndex)
    }
  }

  def set(bitIndex: Int) {
    this.synchronized {
      bitSet.set(bitIndex)
    }
  }
}