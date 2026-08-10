package kh.edu.acleda.campuslink.modules.admin.auth.totp;

/**
 * RFC 4648 Base32, which is the alphabet every authenticator app expects for a TOTP secret.
 *
 * <p>Hand-written rather than pulled from a library: the JDK has no Base32, and the only
 * candidate already in the dependency graph ({@code commons-codec}) arrives transitively rather
 * than declared. Depending on a transitive jar for a security primitive means a dependency
 * bump elsewhere can silently remove it. Forty lines pinned to the RFC's own test vectors is
 * the cheaper risk - see {@code Base32Test}.
 */
public final class Base32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int BITS_PER_CHAR = 5;
    private static final int BITS_PER_BYTE = 8;

    private Base32() {
    }

    /**
     * Encodes without padding.
     *
     * <p>Padding is legal in the RFC but authenticator apps generally reject a secret
     * containing {@code =}, and it carries no information here because the length is known.
     */
    public static String encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * BITS_PER_BYTE + 4) / BITS_PER_CHAR);
        int buffer = 0;
        int bitsLeft = 0;

        for (byte b : data) {
            buffer = (buffer << BITS_PER_BYTE) | (b & 0xFF);
            bitsLeft += BITS_PER_BYTE;
            while (bitsLeft >= BITS_PER_CHAR) {
                bitsLeft -= BITS_PER_CHAR;
                out.append(ALPHABET.charAt((buffer >> bitsLeft) & 0x1F));
            }
        }
        // Trailing bits become one final character, left-aligned per the RFC.
        if (bitsLeft > 0) {
            out.append(ALPHABET.charAt((buffer << (BITS_PER_CHAR - bitsLeft)) & 0x1F));
        }
        return out.toString();
    }

    /** Accepts lower case and the padding an app might send back; rejects anything else. */
    public static byte[] decode(String encoded) {
        String cleaned = encoded.trim().replace("=", "").replace(" ", "").toUpperCase();
        byte[] out = new byte[cleaned.length() * BITS_PER_CHAR / BITS_PER_BYTE];

        int buffer = 0;
        int bitsLeft = 0;
        int index = 0;

        for (int i = 0; i < cleaned.length(); i++) {
            int value = ALPHABET.indexOf(cleaned.charAt(i));
            if (value < 0) {
                throw new IllegalArgumentException("Not a Base32 character at position " + i);
            }
            buffer = (buffer << BITS_PER_CHAR) | value;
            bitsLeft += BITS_PER_CHAR;
            if (bitsLeft >= BITS_PER_BYTE) {
                bitsLeft -= BITS_PER_BYTE;
                out[index++] = (byte) ((buffer >> bitsLeft) & 0xFF);
            }
        }
        return out;
    }
}
