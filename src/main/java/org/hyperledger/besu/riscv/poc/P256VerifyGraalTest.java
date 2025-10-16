/*
 * Copyright contributors to Besu.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 */
package org.hyperledger.besu.riscv.poc;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.nativelib.boringssl.BoringSSLPrecompilesGraal;

import java.security.MessageDigest;

/**
 * Test case for P-256 (secp256r1) signature verification using BoringSSL.
 * Exercises signature verification operations in a GraalVM native-image context.
 * Tests valid signatures, malleated signatures, and invalid inputs.
 */
public class P256VerifyGraalTest {

    private P256VerifyGraalTest() {}

    private static final Bytes DATA = Bytes.fromHexString(
        "c35e2f092553c55772926bdbe87c9796827d17024dbb9233a545366e2e5987dd344deb72df987144b8c6c43bc41b654b94cc856e16b96d7a821c8ec039b503e3d86728c494a967d83011a0e090b5d54cd47f4e366c0912bc808fbb2ea96efac88fb3ebec9342738e225f7c7c2b011ce375b56621a20642b4d36e060db4524af1");

    private static final Bytes PUBLIC_KEY = Bytes.fromHexString(
        "e266ddfdc12668db30d4ca3e8f7749432c416044f2d2b8c10bf3d4012aeffa8abfa86404a2e9ffe67d47c587ef7a97a7f456b863b4d02cfc6928973ab5b1cb39");

    private static final Bytes INVALID_PUBLIC_KEY = Bytes.fromHexString(
        "f266ddfdc12668db30d4ca3e8f7749432c416044f2d2b8c10bf3d4012aeffa8abfa86404a2e9ffe67d47c587ef7a97a7f456b863b4d02cfc6928973ab5b1cb39");

    private static final Bytes SIGNATURE_R = Bytes.fromHexString(
        "976d3a4e9d23326dc0baa9fa560b7c4e53f42864f508483a6473b6a11079b2db");

    private static final Bytes INVALID_SIGNATURE_R = Bytes.fromHexString(
        "a76d3a4e9d23326dc0baa9fa560b7c4e53f42864f508483a6473b6a11079b2db");

    private static final Bytes SIGNATURE_S = Bytes.fromHexString(
        "1b766e9ceb71ba6c01dcd46e0af462cd4cfa652ae5017d4555b8eeefe36e1932");

    /**
     * Runs the P-256 verification test suite.
     *
     * @throws Exception if cryptographic operations fail
     */
    public static void runTest() throws Exception {
        System.out.println("Initializing P-256 verification tests...");

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] dataHash = digest.digest(DATA.toArrayUnsafe());
        System.out.println();

        // Test 1: Valid Signature Verification
        System.out.println("TEST 1: Valid Signature Verification");
        System.out.println("------------------------------------");
        testValidSignature(dataHash);
        System.out.println();

        // Test 2: Malleated Signature Verification
        System.out.println("TEST 2: Malleated Signature Verification");
        System.out.println("----------------------------------------");
        testMalleatedSignature(dataHash);
        System.out.println();

        // Test 3: Invalid Signature Detection
        System.out.println("TEST 3: Invalid Signature Detection");
        System.out.println("-----------------------------------");
        testInvalidSignature(dataHash);
        System.out.println();

        // Test 4: Invalid Public Key Detection
        System.out.println("TEST 4: Invalid Public Key Detection");
        System.out.println("------------------------------------");
        testInvalidPublicKey(dataHash);
        System.out.println();

        System.out.println("All tests completed successfully!");
    }

    /**
     * Test verification of a valid signature.
     */
    private static void testValidSignature(byte[] dataHash) {
        byte[] input = createInput(dataHash, SIGNATURE_R.toArrayUnsafe(),
                                   SIGNATURE_S.toArrayUnsafe(), PUBLIC_KEY.toArrayUnsafe());

        System.out.println("Message hash: " + Bytes.wrap(dataHash).toHexString());
        System.out.println("Signature R:  " + SIGNATURE_R.toHexString());
        System.out.println("Signature S:  " + SIGNATURE_S.toHexString());
        System.out.println("Public key:   " + PUBLIC_KEY.toHexString());

        BoringSSLPrecompilesGraal.P256VerifyResult result =
            BoringSSLPrecompilesGraal.p256Verify(input, input.length);

        System.out.println("Verification result: " + (result.status == 0 ? "VALID" : "INVALID"));
        System.out.println("Status code: " + result.status);

        if (result.status != 0) {
            throw new RuntimeException("Valid signature verification failed! Status: " + result.status);
        }

        System.out.println("Status: PASS");
    }

    /**
     * Test verification of a malleated signature (signature with s' = order - s).
     * Both s and s' should be valid for secp256r1.
     */
    private static void testMalleatedSignature(byte[] dataHash) {
        // P-256 order
        UInt256 order = UInt256.fromHexString(
            "FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551");
        UInt256 malleatedS = order.subtract(UInt256.fromBytes(SIGNATURE_S));

        System.out.println("Original S:  " + SIGNATURE_S.toHexString());
        System.out.println("Malleated S: " + malleatedS.toHexString());

        byte[] input = createInput(dataHash, SIGNATURE_R.toArrayUnsafe(),
                                   malleatedS.toArrayUnsafe(), PUBLIC_KEY.toArrayUnsafe());

        BoringSSLPrecompilesGraal.P256VerifyResult result =
            BoringSSLPrecompilesGraal.p256Verify(input, input.length);

        System.out.println("Verification result: " + (result.status == 0 ? "VALID" : "INVALID"));
        System.out.println("Status code: " + result.status);

        if (result.status != 0) {
            throw new RuntimeException("Malleated signature verification failed! Status: " + result.status);
        }

        System.out.println("Status: PASS");
    }

    /**
     * Test detection of an invalid signature.
     */
    private static void testInvalidSignature(byte[] dataHash) {
        byte[] input = createInput(dataHash, INVALID_SIGNATURE_R.toArrayUnsafe(),
                                   SIGNATURE_S.toArrayUnsafe(), PUBLIC_KEY.toArrayUnsafe());

        System.out.println("Using invalid signature R: " + INVALID_SIGNATURE_R.toHexString());

        BoringSSLPrecompilesGraal.P256VerifyResult result =
            BoringSSLPrecompilesGraal.p256Verify(input, input.length);

        System.out.println("Verification result: " + (result.status == 0 ? "VALID" : "INVALID"));
        System.out.println("Status code: " + result.status);

        if (result.status == 0) {
            throw new RuntimeException("Invalid signature was incorrectly verified as valid!");
        }

        System.out.println("Status: PASS (correctly detected invalid signature)");
    }

    /**
     * Test detection of an invalid public key.
     */
    private static void testInvalidPublicKey(byte[] dataHash) {
        byte[] input = createInput(dataHash, SIGNATURE_R.toArrayUnsafe(),
                                   SIGNATURE_S.toArrayUnsafe(), INVALID_PUBLIC_KEY.toArrayUnsafe());

        System.out.println("Using invalid public key: " + INVALID_PUBLIC_KEY.toHexString());

        BoringSSLPrecompilesGraal.P256VerifyResult result =
            BoringSSLPrecompilesGraal.p256Verify(input, input.length);

        System.out.println("Verification result: " + (result.status == 0 ? "VALID" : "INVALID"));
        System.out.println("Status code: " + result.status);
        if (result.error != null) {
            System.out.println("Error message: " + result.error);
        }

        if (result.status == 0) {
            throw new RuntimeException("Invalid public key was not detected!");
        }

        System.out.println("Status: PASS (correctly detected invalid public key)");
    }

    /**
     * Helper method to create 160-byte input array.
     * Format: hash(32) + r(32) + s(32) + pubkey(64) = 160 bytes
     *
     * @param hash 32-byte message hash
     * @param r 32-byte signature R component
     * @param s 32-byte signature S component
     * @param pubkey 64-byte uncompressed public key
     * @return 160-byte input array
     */
    private static byte[] createInput(byte[] hash, byte[] r, byte[] s, byte[] pubkey) {
        if (hash.length != 32 || r.length != 32 || s.length != 32 || pubkey.length != 64) {
            throw new IllegalArgumentException(
                String.format("Invalid component lengths: hash=%d, r=%d, s=%d, pubkey=%d",
                    hash.length, r.length, s.length, pubkey.length));
        }

        byte[] input = new byte[160];
        System.arraycopy(hash, 0, input, 0, 32);
        System.arraycopy(r, 0, input, 32, 32);
        System.arraycopy(s, 0, input, 64, 32);
        System.arraycopy(pubkey, 0, input, 96, 64);
        return input;
    }
}
