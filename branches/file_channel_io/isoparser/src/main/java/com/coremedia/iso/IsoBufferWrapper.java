package com.coremedia.iso;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * Encapsulates access to underlying storage.
 */
public interface IsoBufferWrapper {
    int readUInt8() throws IOException;

    int readUInt24() throws IOException;

    String readIso639() throws IOException;

    String readString() throws IOException;

    String readString(int i) throws IOException;

    int read(byte[] buffer) throws IOException;

    long readUInt32() throws IOException;

    int readInt32() throws IOException;

    long readUInt64() throws IOException;

    byte readByte() throws IOException;

    int read() throws IOException;

    int readUInt16() throws IOException;

    byte[] read(int i) throws IOException;

    double readFixedPoint1616() throws IOException;

    float readFixedPoint88() throws IOException;

    int readUInt16BE() throws IOException;

    long readUInt32BE() throws IOException;

    /**
     * Reads i bits from the underlying buffers.
     * Caveat: this method always consumes full bytes even if just a bit is readByte!
     *
     * @param i number of bits to readByte, 31 max
     * @return bitstring value as unsigned int
     */
    public int readBits(int i) throws IOException;

    /**
     * Gets the number number bits that must be read to be byte aligned again.
     *
     * @return offset to next byte in bit
     */
    int getReadBitsRemaining();

    ReadableByteChannel getFileChannel();


}
