/*
 * Licensed to the University of California, Berkeley under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package tachyon.util.io;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import tachyon.Constants;

/**
 * Utilities related to buffers, not only {@link ByteBuffer}.
 */
public final class BufferUtils {
  private static final Logger LOG = LoggerFactory.getLogger(Constants.LOGGER_TYPE);
  private static Method sCleanerCleanMethod;
  private static Method sByteBufferCleanerMethod;

  /**
   * Converts a byte to an integer.
   *
   * @param b the byte to convert
   * @return the integer representation of the byte
   */
  public static int byteToInt(byte b) {
    return b & 0xFF;
  }

  /**
   * Forces to unmap a direct buffer if this buffer is no longer used. After calling this method,
   * this direct buffer should be discarded. This is unsafe operation and currently a walk-around to
   * avoid huge memory occupation caused by memory map.
   *
   * <p>
   * NOTE: DirectByteBuffers are not guaranteed to be garbage-collected immediately after their
   * references are released and may lead to OutOfMemoryError. This function helps by calling the
   * Cleaner method of a DirectByteBuffer explicitly. See <a
   * href="http://stackoverflow.com/questions/1854398/how-to-garbage-collect-a-direct-buffer-java"
   * >more discussion</a>.
   *
   * @param buffer the byte buffer to be unmapped, this must be a direct buffer
   */
  public static void cleanDirectBuffer(ByteBuffer buffer) {
    Preconditions.checkNotNull(buffer);
    Preconditions.checkArgument(buffer.isDirect(), "buffer isn't a DirectByteBuffer");
    try {
      if (sByteBufferCleanerMethod == null) {
        sByteBufferCleanerMethod = buffer.getClass().getMethod("cleaner");
        sByteBufferCleanerMethod.setAccessible(true);
      }
      final Object cleaner = sByteBufferCleanerMethod.invoke(buffer);
      if (cleaner == null) {
        if (buffer.capacity() > 0) {
          LOG.error("Failed to get cleaner for ByteBuffer");
        }
        return;
      }
      if (sCleanerCleanMethod == null) {
        sCleanerCleanMethod = cleaner.getClass().getMethod("clean");
      }
      sCleanerCleanMethod.invoke(cleaner);
    } catch (Exception e) {
      LOG.warn("Fail to unmap direct buffer due to " + e.getMessage(), e);
    } finally {
      buffer = null;
    }
  }

  /**
   * Clones a {@link ByteBuffer}.
   * <p>
   * The new bytebuffer will have the same content, but the type of the bytebuffer may not be the
   * same.
   *
   * @param buf The ByteBuffer to clone
   * @return The new ByteBuffer
   */
  public static ByteBuffer cloneByteBuffer(ByteBuffer buf) {
    ByteBuffer ret = ByteBuffer.allocate(buf.limit() - buf.position());
    if (buf.hasArray()) {
      ret.put(buf.array(), buf.position(), buf.limit() - buf.position());
    } else {
      // direct buffer
      ret.put(buf);
    }
    ret.flip();
    return ret;
  }

  /**
   * Clones a list of {@link ByteBuffer}s.
   *
   * @param source the list of ByteBuffers to clone
   * @return the new list of ByteBuffers
   */
  public static List<ByteBuffer> cloneByteBufferList(List<ByteBuffer> source) {
    List<ByteBuffer> ret = new ArrayList<ByteBuffer>(source.size());
    for (ByteBuffer b : source) {
      ret.add(cloneByteBuffer(b));
    }
    return ret;
  }

  /**
   * Extracts a correct {@link ByteBuffer} from Thrift RPC result.
   *
   * @param data result of Thrift RPC
   * @return ByteBuffer with data extracted from the Thrift RPC result
   */
  public static ByteBuffer generateNewByteBufferFromThriftRPCResults(ByteBuffer data) {
    // TODO(cc): This is a trick to fix the issue in thrift. Change the code to use metadata
    // directly when thrift fixes the issue.
    ByteBuffer correctData = ByteBuffer.allocate(data.limit() - data.position());
    correctData.put(data);
    correctData.flip();
    return correctData;
  }

  /**
   * Puts a byte (the first byte of an integer) into a {@link ByteBuffer}.
   *
   * @param buf ByteBuffer to use
   * @param b byte to put into the ByteBuffer
   */
  public static void putIntByteBuffer(ByteBuffer buf, int b) {
    buf.put((byte) (b & 0xFF));
  }

  /**
   * Gets an increasing sequence of bytes starting at zero.
   *
   * @param len the target length of the sequence
   * @return an increasing sequence of bytes
   */
  public static byte[] getIncreasingByteArray(int len) {
    return getIncreasingByteArray(0, len);
  }

  /**
   * Gets an increasing sequence of bytes starting with the given value.
   *
   * @param start the starting value to use
   * @param len the target length of the sequence
   * @return an increasing sequence of bytes
   */
  public static byte[] getIncreasingByteArray(int start, int len) {
    byte[] ret = new byte[len];
    for (int k = 0; k < len; k ++) {
      ret[k] = (byte) (k + start);
    }
    return ret;
  }

  /**
   * Checks if the given byte array starts with an increasing sequence of bytes starting at zero of
   * length equal to or greater than the given length.
   *
   * @param len the target length of the sequence
   * @param arr the byte array to check
   * @return true if the byte array has a prefix of length {@code len} that is an increasing
   *         sequence of bytes starting at zero
   */
  public static boolean equalIncreasingByteArray(int len, byte[] arr) {
    return equalIncreasingByteArray(0, len, arr);
  }

  /**
   * Checks if the given byte array starts with an increasing sequence of bytes starting at the
   * given value of length equal to or greater than the given length.
   *
   * @param start the starting value to use
   * @param len the target length of the sequence
   * @param arr the byte array to check
   * @return true if the byte array has a prefix of length {@code len} that is an increasing
   *         sequence of bytes starting at {@code start}
   */
  public static boolean equalIncreasingByteArray(int start, int len, byte[] arr) {
    if (arr == null || arr.length != len) {
      return false;
    }
    for (int k = 0; k < len; k ++) {
      if (arr[k] != (byte) (start + k)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Gets a {@link ByteBuffer} containing an increasing sequence of bytes starting at zero.
   *
   * @param len the target length of the sequence
   * @return ByteBuffer containing an increasing sequence of bytes
   */
  public static ByteBuffer getIncreasingByteBuffer(int len) {
    return getIncreasingByteBuffer(0, len);
  }

  /**
   * Gets a {@link ByteBuffer} containing an increasing sequence of bytes starting at the given
   * value.
   *
   * @param len the target length of the sequence
   * @param start the starting value to use
   * @return ByteBuffer containing an increasing sequence of bytes
   */
  public static ByteBuffer getIncreasingByteBuffer(int start, int len) {
    return ByteBuffer.wrap(getIncreasingByteArray(start, len));
  }

  /**
   * Checks if the given {@link ByteBuffer} starts with an increasing sequence of bytes starting at
   * the given value of length equal to or greater than the given length.
   *
   * @param start the starting value to use
   * @param len the target length of the sequence
   * @param buf the ByteBuffer to check
   * @return true if the ByteBuffer has a prefix of length {@code len} that is an increasing
   *         sequence of bytes starting at {@code start}
   */
  public static boolean equalIncreasingByteBuffer(int start, int len, ByteBuffer buf) {
    if (buf == null) {
      return false;
    }
    buf.rewind();
    if (buf.remaining() != len) {
      return false;
    }
    for (int k = 0; k < len; k ++) {
      if (buf.get() != (byte) (start + k)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Gets a {@link ByteBuffer} containing an increasing sequence of integers starting at zero.
   *
   * @param len the target length of the sequence
   * @return ByteBuffer containing an increasing sequence of integers
   */
  public static ByteBuffer getIncreasingIntBuffer(int len) {
    return getIncreasingIntBuffer(0, len);
  }

  /**
   * Get a {@link ByteBuffer} containing an increasing sequence of integers starting at the given
   * value.
   *
   * @param start the starting value to use
   * @param len the target length of the sequence
   * @return ByteBuffer containing an increasing sequence of integers
   */
  public static ByteBuffer getIncreasingIntBuffer(int start, int len) {
    ByteBuffer ret = ByteBuffer.allocate(len * 4);
    for (int k = 0; k < len; k ++) {
      ret.putInt(start + k);
    }
    ret.flip();
    return ret;
  }

  /**
   * Writes buffer to the given file path.
   *
   * @param path file path to write the data
   * @param buffer raw data
   * @throws IOException if the operation fails
   */
  public static void writeBufferToFile(String path, byte[] buffer) throws IOException {
    FileOutputStream os = new FileOutputStream(path);
    try {
      os.write(buffer);
    } finally {
      os.close();
    }
  }

  /**
   * An efficient copy between two channels with a fixed-size buffer.
   *
   * @param src the source channel
   * @param dest the destination channel
   * @throws IOException if the copy fails
   */
  public static void fastCopy(final ReadableByteChannel src, final WritableByteChannel dest)
      throws IOException {
    // TODO(yupeng): make the buffer size configurable
    final ByteBuffer buffer = ByteBuffer.allocateDirect(16 * 1024);

    while (src.read(buffer) != -1) {
      buffer.flip();
      dest.write(buffer);
      buffer.compact();
    }

    buffer.flip();

    while (buffer.hasRemaining()) {
      dest.write(buffer);
    }
  }

  private BufferUtils() {} // prevent instantiation
}
