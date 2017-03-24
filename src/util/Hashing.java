package util;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hashing {
    private static final int HASH_SIZE = 40;

    private Hashing() {
    }

    public static String getHash(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest(data);
        BigInteger hashInt = new BigInteger(1, hash);
        return String.format("%040x", hashInt);
    }

    public static int getHashSize() {
        return HASH_SIZE;
    }

}
