/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.litetex.authback.shared.external.org.apache.commons.codec.binary;

import net.litetex.authback.shared.external.org.apache.commons.codec.DecoderException;


@SuppressWarnings("all")
public final class Hex
{
	/**
	 * Used to build output as hex.
	 */
	private static final char[] DIGITS_LOWER =
		{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
	
	/**
	 * Used to build output as hex.
	 */
	private static final char[] DIGITS_UPPER =
		{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};
	
	/**
	 * Converts an array of characters representing hexadecimal values into an array of bytes of those same values. The
	 * returned array will be half the length of the passed array, as it takes two characters to represent any given
	 * byte. An exception is thrown if the passed char array has an odd number of elements.
	 *
	 * @param data An array of characters containing hexadecimal digits
	 * @return A byte array containing binary data decoded from the supplied char array.
	 * @throws DecoderException Thrown if an odd number of characters or illegal characters are supplied
	 */
	public static byte[] decodeHex(final char[] data) throws DecoderException
	{
		final byte[] out = new byte[data.length >> 1];
		decodeHex(data, out, 0);
		return out;
	}
	
	/**
	 * Converts an array of characters representing hexadecimal values into an array of bytes of those same values. The
	 * returned array will be half the length of the passed array, as it takes two characters to represent any given
	 * byte. An exception is thrown if the passed char array has an odd number of elements.
	 *
	 * @param data      An array of characters containing hexadecimal digits
	 * @param out       A byte array to contain the binary data decoded from the supplied char array.
	 * @param outOffset The position within {@code out} to start writing the decoded bytes.
	 * @return the number of bytes written to {@code out}.
	 * @throws DecoderException Thrown if an odd number of characters or illegal characters are supplied
	 * @since 1.15
	 */
	public static int decodeHex(final char[] data, final byte[] out, final int outOffset) throws DecoderException
	{
		final int len = data.length;
		if((len & 0x01) != 0)
		{
			throw new DecoderException("Odd number of characters.");
		}
		final int outLen = len >> 1;
		if(out.length - outOffset < outLen)
		{
			throw new DecoderException("Output array is not large enough to accommodate decoded data.");
		}
		// two characters form the hex value.
		for(int i = outOffset, j = 0; j < len; i++)
		{
			int f = toDigit(data[j], j) << 4;
			j++;
			f |= toDigit(data[j], j);
			j++;
			out[i] = (byte)(f & 0xFF);
		}
		return outLen;
	}
	
	/**
	 * Converts a String representing hexadecimal values into an array of bytes of those same values. The returned
	 * array
	 * will be half the length of the passed String, as it takes two characters to represent any given byte. An
	 * exception is thrown if the passed String has an odd number of elements.
	 *
	 * @param data A String containing hexadecimal digits
	 * @return A byte array containing binary data decoded from the supplied char array.
	 * @throws DecoderException Thrown if an odd number of characters or illegal characters are supplied
	 * @since 1.11
	 */
	public static byte[] decodeHex(final String data) throws DecoderException
	{
		return decodeHex(data.toCharArray());
	}
	
	/**
	 * Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in
	 * order.
	 * The returned array will be double the length of the passed array, as it takes two characters to represent any
	 * given byte.
	 *
	 * @param data a byte[] to convert to hexadecimal characters
	 * @return A char[] containing lower-case hexadecimal characters
	 */
	public static char[] encodeHex(final byte[] data)
	{
		return encodeHex(data, true);
	}
	
	/**
	 * Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in
	 * order.
	 * The returned array will be double the length of the passed array, as it takes two characters to represent any
	 * given byte.
	 *
	 * @param data        a byte[] to convert to Hex characters
	 * @param toLowerCase {@code true} converts to lowercase, {@code false} to uppercase
	 * @return A char[] containing hexadecimal characters in the selected case
	 * @since 1.4
	 */
	public static char[] encodeHex(final byte[] data, final boolean toLowerCase)
	{
		return encodeHex(data, toAlphabet(toLowerCase));
	}
	
	/**
	 * Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in
	 * order.
	 * The returned array will be double the length of the passed array, as it takes two characters to represent any
	 * given byte.
	 *
	 * @param data     a byte[] to convert to hexadecimal characters
	 * @param toDigits the output alphabet (must contain at least 16 chars)
	 * @return A char[] containing the appropriate characters from the alphabet For best results, this should be either
	 * upper- or lower-case hex.
	 * @since 1.4
	 */
	private static char[] encodeHex(final byte[] data, final char[] toDigits)
	{
		final int dataLength = data.length;
		return encodeHex(data, 0, dataLength, toDigits, new char[dataLength << 1], 0);
	}
	
	/**
	 * Converts an array of bytes into an array of characters representing the hexadecimal values of each byte in
	 * order.
	 *
	 * @param data       a byte[] to convert to hexadecimal characters
	 * @param dataOffset the position in {@code data} to start encoding from
	 * @param dataLen    the number of bytes from {@code dataOffset} to encode
	 * @param toDigits   the output alphabet (must contain at least 16 chars)
	 * @param out        a char[] which will hold the resultant appropriate characters from the alphabet.
	 * @param outOffset  the position within {@code out} at which to start writing the encoded characters.
	 * @return the given {@code out}.
	 */
	private static char[] encodeHex(
		final byte[] data,
		final int dataOffset,
		final int dataLen,
		final char[] toDigits,
		final char[] out,
		final int outOffset)
	{
		// two characters form the hex value.
		for(int i = dataOffset, j = outOffset; i < dataOffset + dataLen; i++)
		{
			out[j++] = toDigits[(0xF0 & data[i]) >>> 4];
			out[j++] = toDigits[0x0F & data[i]];
		}
		return out;
	}
	
	/**
	 * Converts an array of bytes into a String representing the hexadecimal values of each byte in order. The returned
	 * String will be double the length of the passed array, as it takes two characters to represent any given byte.
	 *
	 * @param data a byte[] to convert to hexadecimal characters
	 * @return A String containing lower-case hexadecimal characters
	 * @since 1.4
	 */
	public static String encodeHexString(final byte[] data)
	{
		return new String(encodeHex(data));
	}
	
	/**
	 * Converts a boolean to an alphabet.
	 *
	 * @param toLowerCase true for lowercase, false for uppercase.
	 * @return an alphabet.
	 */
	private static char[] toAlphabet(final boolean toLowerCase)
	{
		return toLowerCase ? DIGITS_LOWER : DIGITS_UPPER;
	}
	
	/**
	 * Converts a hexadecimal character to an integer.
	 *
	 * @param ch    A character to convert to an integer digit
	 * @param index The index of the character in the source
	 * @return An integer
	 * @throws DecoderException Thrown if ch is an illegal hexadecimal character
	 */
	private static int toDigit(final char ch, final int index) throws DecoderException
	{
		final int digit = Character.digit(ch, 16);
		if(digit == -1)
		{
			throw new DecoderException("Illegal hexadecimal character " + ch + " at index " + index);
		}
		return digit;
	}
	
	private Hex()
	{
	}
}
