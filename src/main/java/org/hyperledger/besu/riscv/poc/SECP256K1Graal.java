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

import org.hyperledger.besu.crypto.AbstractSECP256;
import org.hyperledger.besu.crypto.KeyPair;
import org.hyperledger.besu.crypto.SECPPublicKey;
import org.hyperledger.besu.crypto.SECPSignature;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1Graal;

import java.math.BigInteger;
import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.signers.DSAKCalculator;
import org.bouncycastle.crypto.signers.HMacDSAKCalculator;
import org.bouncycastle.math.ec.custom.sec.SecP256K1Curve;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * GraalVM native-image only SECP256K1 implementation. This class extends AbstractSECP256 and uses
 * LibSecp256k1Graal for verification and public key recovery operations. Signing uses BouncyCastle
 * as LibSecp256k1Graal doesn't yet expose the signing function. This implementation is suitable for
 * GraalVM native-image contexts.
 */
public class SECP256K1Graal extends AbstractSECP256 {

  private static final Logger LOG = LoggerFactory.getLogger(SECP256K1Graal.class);

  public static final String CURVE_NAME = "secp256k1";

  /**
   * Instantiates a new SECP256K1Graal. This implementation always uses the native library and will
   * throw an exception if LibSecp256k1Graal is not available.
   */
  public SECP256K1Graal() {
    super(CURVE_NAME, SecP256K1Curve.q);

    if (LibSecp256k1Graal.getContext().isNull()) {
      throw new RuntimeException(
          "LibSecp256k1Graal native library is not available. "
              + "This implementation requires GraalVM native-image with statically linked secp256k1.");
    }

    LOG.info("SECP256K1Graal initialized with native library");
  }

  @Override
  public void disableNative() {
    throw new UnsupportedOperationException(
        "SECP256K1Graal requires native library and cannot disable it");
  }

  @Override
  public boolean maybeEnableNative() {
    return true;
  }

  @Override
  public boolean isNative() {
    return true;
  }

  @Override
  public DSAKCalculator getKCalculator() {
    return new HMacDSAKCalculator(new SHA256Digest());
  }

  @Override
  public String getCurveName() {
    return CURVE_NAME;
  }

  @Override
  public SECPSignature sign(final Bytes32 dataHash, final KeyPair keyPair) {
    // Use BouncyCastle for signing as LibSecp256k1Graal doesn't yet expose signing
    return super.sign(dataHash, keyPair);
  }

  @Override
  public boolean verify(final Bytes data, final SECPSignature signature, final SECPPublicKey pub) {
    return verifyNative(data, signature, pub);
  }

  @Override
  public Optional<SECPPublicKey> recoverPublicKeyFromSignature(
      final Bytes32 dataHash, final SECPSignature signature) {
    Optional<SECPPublicKey> result = recoverFromSignatureNative(dataHash, signature);
    if (result.isEmpty()) {
      throw new IllegalArgumentException("Could not recover public key");
    }
    return result;
  }

  @Override
  protected BigInteger recoverFromSignature(
      final int recId, final BigInteger r, final BigInteger s, final Bytes32 dataHash) {
    return recoverFromSignatureNative(dataHash, new SECPSignature(r, s, (byte) recId))
        .map(key -> new BigInteger(1, key.getEncoded()))
        .orElse(null);
  }

  /**
   * Verify a signature using the native library.
   *
   * @param data the data that was signed (typically 32-byte hash)
   * @param signature the signature to verify
   * @param pub the public key to verify against
   * @return true if the signature is valid
   */
  private boolean verifyNative(
      final Bytes data, final SECPSignature signature, final SECPPublicKey pub) {
    // Parse public key
    final Bytes encodedPubKey = Bytes.concatenate(Bytes.of(0x04), pub.getEncodedBytes());
    byte[] pubkeyInternal = new byte[64];

    int parseResult =
        LibSecp256k1Graal.ecPubkeyParse(
            LibSecp256k1Graal.getContext(), pubkeyInternal, encodedPubKey.toArrayUnsafe());

    if (parseResult != 1) {
      throw new IllegalArgumentException("Could not parse public key");
    }

    // Verify signature (use only r and s, not recovery ID)
    byte[] compactSignature = signature.encodedBytes().slice(0, 64).toArrayUnsafe();
    return LibSecp256k1Graal.ecdsaVerify(
            LibSecp256k1Graal.getContext(), compactSignature, data.toArrayUnsafe(), pubkeyInternal)
        != 0;
  }

  /**
   * Recover a public key from a signature using the native library.
   *
   * @param dataHash the 32-byte message hash that was signed
   * @param signature the signature containing r, s, and recovery id
   * @return the recovered public key, or empty if recovery failed
   */
  private Optional<SECPPublicKey> recoverFromSignatureNative(
      final Bytes32 dataHash, final SECPSignature signature) {
    // Use the compact signature + recid version
    byte[] compactSignature = signature.encodedBytes().slice(0, 64).toArrayUnsafe();
    int recId = signature.getRecId();

    byte[] recoveredPubkey =
        LibSecp256k1Graal.ecdsaRecover(
            LibSecp256k1Graal.getContext(), compactSignature, dataHash.toArrayUnsafe(), recId);

    if (recoveredPubkey == null) {
      return Optional.empty();
    }

    // The recovered pubkey is already in internal 64-byte format
    // Serialize to uncompressed format (65 bytes with 0x04 prefix)
    byte[] serialized =
        LibSecp256k1Graal.ecPubkeySerialize(LibSecp256k1Graal.getContext(), recoveredPubkey, false);

    // Remove the 0x04 prefix to get the 64-byte public key
    return Optional.of(SECPPublicKey.create(Bytes.wrap(serialized).slice(1), ALGORITHM));
  }
}
