/*
 * Copyright (c) 2019 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.compression.rans.RANS;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

import java.nio.ByteBuffer;
import java.util.Objects;

public class RANSExternalCompressor extends ExternalCompressor {
    private final RANS.ORDER order;
    private final RANS rans;

    /**
     * We use a shared RANS instance for all compressors.
     * @param rans
     */
    public RANSExternalCompressor(final RANS rans) {
        this(RANS.ORDER.ZERO, rans);
    }

    public RANSExternalCompressor(final int order, final RANS rans) {
        this(RANS.ORDER.fromInt(order), rans);
    }

    public RANSExternalCompressor(final RANS.ORDER order, final RANS rans) {
        super(BlockCompressionMethod.RANS);
        this.rans = rans;
        this.order = order;
    }

    @Override
    public byte[] compress(final byte[] data) {
        final ByteBuffer buffer = rans.compress(ByteBuffer.wrap(data), order);
        return toByteArray(buffer);
    }

    @Override
    public byte[] uncompress(byte[] data) {
        final ByteBuffer buf = rans.uncompress(ByteBuffer.wrap(data));
        return toByteArray(buf);
    }

    public RANS.ORDER getOrder() { return order; }

    @Override
    public String toString() {
        return String.format("%s(%s)", this.getMethod(), order);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RANSExternalCompressor that = (RANSExternalCompressor) o;

        return this.order == that.order;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getMethod(), order);
    }

    private byte[] toByteArray(final ByteBuffer buffer) {
        if (buffer.hasArray() && buffer.arrayOffset() == 0 && buffer.array().length == buffer.limit()) {
            return buffer.array();
        }

        final byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

}
