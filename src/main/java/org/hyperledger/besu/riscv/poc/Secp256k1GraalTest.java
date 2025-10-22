/*
 * Copyright Consensys Software Inc., 2025
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.hyperledger.besu.riscv.poc;

import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;

import java.security.SecureRandom;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * Test case for SECP256K1 using LibSecp256k1Graal native library. Exercises key generation,
 * signing, verification, and public key recovery operations in a GraalVM native-image context.
 */
public class Secp256k1GraalTest {

  private Secp256k1GraalTest() {}

  /**
   * Runs the SECP256K1 Graal test suite.
   *
   * @throws Exception if cryptographic operations fail
   */
  public static void runTest() throws Exception {
    System.out.println("Initializing SECP256K1Graal...");
    SECP256K1Graal secp256k1 = new SECP256K1Graal();
    System.out.println("Native library available: " + secp256k1.isNative());
    System.out.println();

    // Test 1: Key Generation
    System.out.println("TEST 1: Key Generation");
    System.out.println("----------------------");
    KeyPair keyPair = secp256k1.generateKeyPair();

    System.out.println("Private key: " + keyPair.getPrivateKey().getEncodedBytes().toHexString());
    System.out.println("Public key:  " + keyPair.getPublicKey().getEncodedBytes().toHexString());
    System.out.println("Valid public key: " + secp256k1.isValidPublicKey(keyPair.getPublicKey()));
    System.out.println();

    // Test 2: Message Signing
    System.out.println("TEST 2: Message Signing (bouncycastle)");
    System.out.println("-----------------------");
    SecureRandom random = new SecureRandom();
    byte[] messageHash = new byte[32];
    random.nextBytes(messageHash);
    Bytes32 dataHash = Bytes32.wrap(messageHash);

    System.out.println("Message hash: " + dataHash.toHexString());

    SECPSignature signature = secp256k1.sign(dataHash, keyPair);
    // BigInteger.toByteArray() may return 31, 32, or 33 bytes depending on the sign bit
    Bytes rBytes = Bytes.of(signature.getR().toByteArray());
    Bytes sBytes = Bytes.of(signature.getS().toByteArray());
    System.out.println(
        "Signature R:  "
            + Bytes32.leftPad(rBytes.size() > 32 ? rBytes.slice(1) : rBytes).toHexString());
    System.out.println(
        "Signature S:  "
            + Bytes32.leftPad(sBytes.size() > 32 ? sBytes.slice(1) : sBytes).toHexString());
    System.out.println("Recovery ID:  " + signature.getRecId());
    System.out.println();

    // Test 3: Signature Verification
    System.out.println("TEST 3: Signature Verification");
    System.out.println("------------------------------");
    boolean isValid = secp256k1.verify(dataHash, signature, keyPair.getPublicKey());
    System.out.println("Signature valid: " + isValid);

    if (!isValid) {
      throw new RuntimeException("Signature verification failed!");
    }
    System.out.println();

    // Test 4: Public Key Recovery
    System.out.println("TEST 4: Public Key Recovery");
    System.out.println("---------------------------");
    Optional<SECPPublicKey> recoveredKey =
        secp256k1.recoverPublicKeyFromSignature(dataHash, signature);

    if (recoveredKey.isEmpty()) {
      throw new RuntimeException("Public key recovery failed!");
    }

    System.out.println(
        "Original public key:  " + keyPair.getPublicKey().getEncodedBytes().toHexString());
    System.out.println(
        "Recovered public key: " + recoveredKey.get().getEncodedBytes().toHexString());
    System.out.println("Keys match: " + keyPair.getPublicKey().equals(recoveredKey.get()));

    if (!keyPair.getPublicKey().equals(recoveredKey.get())) {
      throw new RuntimeException("Recovered public key does not match original!");
    }
    System.out.println();

    // Test 5: Invalid Signature Detection
    System.out.println("TEST 5: Invalid Signature Detection");
    System.out.println("-----------------------------------");
    byte[] wrongMessageHash = new byte[32];
    random.nextBytes(wrongMessageHash);
    Bytes32 wrongDataHash = Bytes32.wrap(wrongMessageHash);

    boolean invalidSigCheck = secp256k1.verify(wrongDataHash, signature, keyPair.getPublicKey());
    System.out.println("Wrong message hash: " + wrongDataHash.toHexString());
    System.out.println("Signature valid for wrong message: " + invalidSigCheck);

    if (invalidSigCheck) {
      throw new RuntimeException("Invalid signature was incorrectly verified as valid!");
    }
    System.out.println();

    // Test 6: Compressed Public Key
    System.out.println("TEST 6: Compressed Public Key");
    System.out.println("-----------------------------");
    Bytes compressedPubKey = secp256k1.compressPublicKey(keyPair.getPublicKey());
    System.out.println(
        "Uncompressed length: " + keyPair.getPublicKey().getEncodedBytes().size() + " bytes");
    System.out.println("Compressed length:   " + compressedPubKey.size() + " bytes");
    System.out.println("Compressed public key: " + compressedPubKey.toHexString());
    System.out.println();

    System.out.println("All tests completed successfully!");
  }
}
