package com.github.jmatss.send.type;

import com.github.jmatss.send.exception.IncorrectHashTypeException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public enum HashType {
    NONE(0, null, 0),
    SHA1(1, "SHA-1", 20),
    MD5(2, "MD5", 16);
    private final int i;
    private final String hash;
    private final int size;

    private HashType(int i, String hash, int size) {
        this.i = i;
        this.hash = hash;
        this.size = size;
    }

    public int value() {
        return this.i;
    }

    public int size() {
        return this.size;
    }

    public String toString() {
        return this.hash;
    }

    public static int getSize(int hashType) throws IncorrectHashTypeException {
        if (hashType == HashType.NONE.value())
            return HashType.NONE.size();
        else if (hashType == HashType.SHA1.value())
            return HashType.SHA1.size();
        else if (hashType == HashType.MD5.value())
            return HashType.MD5.size();
        else
            throw new IncorrectHashTypeException("Received incorrect hash type.");
    }

    public static MessageDigest getMessageDigest(int hashType) throws IncorrectHashTypeException {
        try {
            if (hashType == HashType.NONE.value())
                return null;
            else if (hashType == HashType.SHA1.value())
                return MessageDigest.getInstance(HashType.SHA1.toString());
            else if (hashType == HashType.MD5.value())
                return MessageDigest.getInstance(HashType.MD5.toString());
            else
                throw new IncorrectHashTypeException("Received incorrect hash type.");
        } catch (NoSuchAlgorithmException e) {
            // Shouldn't happen.
            throw new RuntimeException(e);
        }
    }
}
