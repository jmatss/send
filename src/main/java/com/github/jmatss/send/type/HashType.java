package com.github.jmatss.send.type;

import com.github.jmatss.send.exception.IncorrectHashTypeException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public enum HashType {
    NONE(0, null, 0),
    SHA1(1, "SHA-1", 20),
    MD5(2, "MD5", 16);

    private static final Map<Integer, HashType> lookup = new HashMap<>();
    private final int i;
    private final String hash;
    private final int size;

    private HashType(int i, String hash, int size) {
        this.i = i;
        this.hash = hash;
        this.size = size;
    }

    public int getValue() {
        return this.i;
    }

    public int getSize() {
        return this.size;
    }

    public String toString() {
        return this.hash;
    }

    static {
        for (HashType hashType : HashType.values()) {
            HashType.lookup.put(hashType.i, hashType);
        }
    }

    public static HashType valueOf(int key) throws IncorrectHashTypeException {
        HashType hashType;
        if ((hashType = HashType.lookup.get(key)) == null)
            throw new IncorrectHashTypeException("Received incorrect hash type integer: " + key);
        return hashType;
    }

    public MessageDigest getMessageDigest() {
        try {
            return MessageDigest.getInstance(this.hash);
        } catch (NoSuchAlgorithmException e) {
            // Shouldn't happen.
            throw new RuntimeException(e);
        }
    }
}
