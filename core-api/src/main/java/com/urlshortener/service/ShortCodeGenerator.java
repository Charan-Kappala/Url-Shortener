package com.urlshortener.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigInteger;

/**
 * Generates short codes that can never repeat, no matter how many app instances run.
 *
 * 1. Range allocation: an instance leases a block of ids with one {@code nextval('short_code_seq')} call
 *    (the sequence increments by the block size) and hands them out from memory. Postgres never returns
 *    the same nextval twice, so no two instances can hold overlapping blocks. Ids left in a block when an
 *    instance stops are simply skipped.
 * 2. Scramble: id -> (id * MULTIPLIER) mod 62^7. MULTIPLIER shares no factor with 62^7, so this is a
 *    one-to-one mapping (distinct ids give distinct numbers) that makes consecutive codes look unrelated.
 *    It hides creation order and volume from casual inspection; it is not a security boundary.
 * 3. Encode the number in base62, left-padded to 7 characters.
 */
@Component
public class ShortCodeGenerator {

    static final int CODE_LENGTH = 7;
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final long SPACE = (long) Math.pow(62, CODE_LENGTH); // 3,521,614,606,208 codes
    // About 0.618 * SPACE, so consecutive ids land far apart; odd and not a multiple of 31 (62^7 = 2^7 * 31^7).
    private static final BigInteger MULTIPLIER = BigInteger.valueOf(2_176_477_521_915L);

    private final JdbcTemplate jdbc;
    private long next;
    private long blockEnd; // exclusive; next == blockEnd means a new block is needed

    public ShortCodeGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public synchronized String nextCode() {
        if (next == blockEnd) {
            next = jdbc.queryForObject("SELECT nextval('short_code_seq')", Long.class);
            blockEnd = next + jdbc.queryForObject(
                    "SELECT increment_by FROM pg_sequences WHERE sequencename = 'short_code_seq'", Long.class);
        }
        long id = next++;
        if (id >= SPACE) {
            throw new IllegalStateException("Short code space exhausted");
        }
        return encode(scramble(id));
    }

    static long scramble(long id) {
        return BigInteger.valueOf(id).multiply(MULTIPLIER).mod(BigInteger.valueOf(SPACE)).longValue();
    }

    static String encode(long n) {
        char[] out = new char[CODE_LENGTH];
        for (int i = CODE_LENGTH - 1; i >= 0; i--) {
            out[i] = ALPHABET.charAt((int) (n % 62));
            n /= 62;
        }
        return new String(out);
    }
}
