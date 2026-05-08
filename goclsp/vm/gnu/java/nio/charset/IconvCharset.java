/*
 * @(#) $(JCGO)/goclsp/vm/gnu/java/nio/charset/IconvCharset.java --
 * Generic Charset that delegates encoding/decoding to the VM-side
 * VMIconvCharset bridge. Used by Provider.charsetForName as a
 * fallback when a requested charset isn't one of the pure-Java
 * built-ins -- so user code can write `new String(bytes, "Shift_JIS")`
 * and have it resolve through the OS.
 *
 * Project: JCGO Modernization (https://github.com/robot-pizza/JCGO)
 * Copyright (C) 2026 robot.pizza
 * All rights reserved.
 *
 * GPL v2 with the Classpath exception (see COPYING and LICENSE).
 */

package gnu.java.nio.charset;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

public final class IconvCharset extends Charset
{
 public IconvCharset(String name)
 {
  super(name, null);
 }

 public boolean contains(Charset cs)
 {
  return cs.equals(this);
 }

 public CharsetDecoder newDecoder()
 {
  return new IconvDecoder(this);
 }

 public CharsetEncoder newEncoder()
 {
  return new IconvEncoder(this);
 }

 // All-at-once decoder. JCGO doesn't yet need partial-buffer
 // streaming -- the API users for non-Latin charsets are
 // String(byte[], "Shift_JIS") / getBytes("Shift_JIS") /
 // InputStreamReader, all of which buffer up before decode.
 // averageCharsPerByte = 1.0f (UTF-16-ish typical), max = 2.0f
 // (handles surrogate pairs from CJK code points outside the BMP).
 private static final class IconvDecoder extends CharsetDecoder
 {
  private final String charsetName;

  IconvDecoder(IconvCharset cs)
  {
   super(cs, 1.0f, 2.0f);
   this.charsetName = cs.name();
  }

  protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out)
  {
   int remaining = in.remaining();
   if (remaining == 0) return CoderResult.UNDERFLOW;
   byte[] buf;
   int off;
   if (in.hasArray())
   {
    buf = in.array();
    off = in.arrayOffset() + in.position();
   }
   else
   {
    buf = new byte[remaining];
    int savedPos = in.position();
    in.get(buf);
    in.position(savedPos);
    off = 0;
   }
   char[] result = VMIconvCharset.decode(buf, off, remaining,
            charsetName);
   if (result == null)
    return CoderResult.unmappableForLength(remaining);
   if (out.remaining() < result.length)
    return CoderResult.OVERFLOW;
   out.put(result);
   in.position(in.position() + remaining);
   return CoderResult.UNDERFLOW;
  }
 }

 // All-at-once encoder. averageBytesPerChar = 2.0f / max = 4.0f
 // covers GB18030's 4-byte sequences for non-BMP characters.
 private static final class IconvEncoder extends CharsetEncoder
 {
  private final String charsetName;

  IconvEncoder(IconvCharset cs)
  {
   super(cs, 2.0f, 4.0f);
   this.charsetName = cs.name();
  }

  protected CoderResult encodeLoop(CharBuffer in, ByteBuffer out)
  {
   int remaining = in.remaining();
   if (remaining == 0) return CoderResult.UNDERFLOW;
   char[] buf;
   int off;
   if (in.hasArray())
   {
    buf = in.array();
    off = in.arrayOffset() + in.position();
   }
   else
   {
    buf = new char[remaining];
    int savedPos = in.position();
    in.get(buf);
    in.position(savedPos);
    off = 0;
   }
   byte[] result = VMIconvCharset.encode(buf, off, remaining,
            charsetName);
   if (result == null)
    return CoderResult.unmappableForLength(remaining);
   if (out.remaining() < result.length)
    return CoderResult.OVERFLOW;
   out.put(result);
   in.position(in.position() + remaining);
   return CoderResult.UNDERFLOW;
  }
 }
}
