package com.examlpe.encryptedexoplayer;

import com.google.android.exoplayer2.util.Assertions;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AesFlushingCipherMod {

    private final Cipher cipher;
    private final int blockSize;
    private final byte[] zerosBlock;
    private final byte[] flushedBlock;

    private int pendingXorBytes;

    public AesFlushingCipherMod(String decryptionKey, long offset) {
        try {
            cipher = Cipher.getInstance("AES/CTR/NoPadding");
            blockSize = cipher.getBlockSize();
            zerosBlock = new byte[blockSize];
            flushedBlock = new byte[blockSize];
            long counter = offset / blockSize;
            int startPadding = (int) (offset % blockSize);
            Key key = new SecretKeySpec(decryptionKey.getBytes("UTF-8"), "AES");
            IvParameterSpec iv = new IvParameterSpec(decryptionKey.getBytes("UTF-8"));
            cipher.init(Cipher.DECRYPT_MODE, key, iv);
            if (startPadding != 0) {
                updateInPlace(new byte[startPadding], 0, startPadding);
            }
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | UnsupportedEncodingException e) {
            // Should never happen.
            throw new RuntimeException(e);
        }
    }

    public void updateInPlace(byte[] data, int offset, int length) {
        update(data, offset, length, data, offset);
    }

    public void update(byte[] in, int inOffset, int length, byte[] out, int outOffset) {
        // If we previously flushed the cipher by inputting zeros up to a block boundary, then we need
        // to manually transform the data that actually ended the block. See the comment below for more
        // details.
        while (pendingXorBytes > 0) {
            out[outOffset] = (byte) (in[inOffset] ^ flushedBlock[blockSize - pendingXorBytes]);
            outOffset++;
            inOffset++;
            pendingXorBytes--;
            length--;
            if (length == 0) {
                return;
            }
        }

        // Do the bulk of the update.
        int written = nonFlushingUpdate(in, inOffset, length, out, outOffset);
        if (length == written) {
            return;
        }

        // We need to finish the block to flush out the remaining bytes. We do so by inputting zeros,
        // so that the corresponding bytes output by the cipher are those that would have been XORed
        // against the real end-of-block data to transform it. We store these bytes so that we can
        // perform the transformation manually in the case of a subsequent call to this method with
        // the real data.
        int bytesToFlush = length - written;
        Assertions.checkState(bytesToFlush < blockSize);
        outOffset += written;
        pendingXorBytes = blockSize - bytesToFlush;
        written = nonFlushingUpdate(zerosBlock, 0, pendingXorBytes, flushedBlock, 0);
        Assertions.checkState(written == blockSize);
        // The first part of xorBytes contains the flushed data, which we copy out. The remainder
        // contains the bytes that will be needed for manual transformation in a subsequent call.
        for (int i = 0; i < bytesToFlush; i++) {
            out[outOffset++] = flushedBlock[i];
        }
    }

    public Cipher getCipher() {
        return cipher;
    }

    private int nonFlushingUpdate(byte[] in, int inOffset, int length, byte[] out, int outOffset) {
        try {
            return cipher.update(in, inOffset, length, out, outOffset);
        } catch (ShortBufferException e) {
            // Should never happen.
            throw new RuntimeException(e);
        }
    }

    private byte[] getInitializationVector(long nonce, long counter) {
        return ByteBuffer.allocate(16).putLong(nonce).putLong(counter).array();
    }

}
