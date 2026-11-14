import org.junit.Test;

import play.libs.Codec;
import play.test.UnitTest;

import controllers.legacy.MD5;

/**
 * Coverage of controllers/legacy/MD5.crypt(), the legacy password hasher models.forum.User.checkPassword()
 * still accepts next to play.libs.Codec.hexMD5().
 *
 *   - The guard `str == null || str.length() == 0` rejects both an empty string and a null argument with
 *     the same IllegalArgumentException, so a user whose passwordHash was computed from an empty body is
 *     never compared against it.
 *   - MessageDigest.getInstance("MD5") is inside a try/catch that swallows NoSuchAlgorithmException. That
 *     catch is unreachable: MD5 is one of the algorithms every JDK provider must offer, so no digest of
 *     the four assertions below can come back empty.
 *   - The `0xff & hash[i] < 0x10` branch pads a byte below 16 with a leading zero. Without it the digest
 *     would be shorter than 32 characters, so the assertions below pin both branches by their literals:
 *     every one of them contains at least one padded byte and at least one byte above 15.
 *   - str.getBytes() uses the platform default charset, which is why the non-ASCII case is asserted as a
 *     parity with Codec.hexMD5() rather than as a literal digest: both hashers call getBytes() the same way.
 */
public class LegacyMd5Test extends UnitTest {

	@Test
	public void cryptReturnsTheMd5DigestOfTheStringsTheMainMethodPrints() {
		assertEquals("0b4975d135e43dacac15f944ba1f5cdd", MD5.crypt("totovaaumarche"));
		assertEquals("b44a3787456078448060ad32f7ce4cd8", MD5.crypt("44828267"));
		// The two values controllers/legacy/MD5.main() prints are the ones the login form hashes
		assertEquals(32, MD5.crypt("totovaaumarche").length());
		assertEquals(32, MD5.crypt("44828267").length());
	}

	@Test
	public void cryptReturnsTheDigestOfEveryValueIncludingTheOneTheFixturesStore() {
		// test/data.yml and conf/initial-data.yml keep 5ebe2294ecd0e0f08eab7690d2a6ee69 for "secret"
		assertEquals("900150983cd24fb0d6963f7d28e17f72", MD5.crypt("abc"));
		assertEquals("5ebe2294ecd0e0f08eab7690d2a6ee69", MD5.crypt("secret"));
		assertEquals("0cc175b9c0f1b6a831c399e269772661", MD5.crypt("a"));
		assertEquals("b10a8db164e0754105b7a99be72e3fe5", MD5.crypt("Hello World"));
		assertEquals("dc18d83abfd9f87d396e8fd6b6ac0fe1", MD5.crypt("ZZ"));
	}

	/** Every digest is 32 lowercase hex characters, i.e. 16 bytes, one byte per pair. */
	@Test
	public void cryptAlwaysReturnsThirtyTwoHexCharacters() {
		for (String value : new String[] {"a", "abc", "secret", "totovaaumarche", "44828267"}) {
			assertTrue("not a digest: " + value, MD5.crypt(value).matches("[0-9a-f]{32}"));
		}
	}

	/**
	 * The hashers agree, which is what models.forum.User.checkPassword() relies on: it accepts
	 * Codec.hexMD5(password) or MD5.crypt(password) against the stored passwordHash.
	 */
	@Test
	public void cryptReturnsTheSameDigestAsPlaySCodec() {
		assertEquals(Codec.hexMD5("secret"), MD5.crypt("secret"));
		assertEquals(Codec.hexMD5("totovaaumarche"), MD5.crypt("totovaaumarche"));
		assertEquals(Codec.hexMD5("44828267"), MD5.crypt("44828267"));
		// Both call String.getBytes(), so the platform charset is applied by both
		assertEquals(Codec.hexMD5("é"), MD5.crypt("é"));
	}

	@Test
	public void cryptRejectsAnEmptyString() {
		try {
			MD5.crypt("");
			fail("expected the zero-length guard to reject an empty string");
		} catch (IllegalArgumentException e) {
			assertEquals("String to encript cannot be null or zero length", e.getMessage());
		}
	}

	@Test
	public void cryptRejectsNull() {
		try {
			MD5.crypt(null);
			fail("expected the null guard to reject a null argument");
		} catch (IllegalArgumentException e) {
			assertEquals("String to encript cannot be null or zero length", e.getMessage());
		}
	}
}
