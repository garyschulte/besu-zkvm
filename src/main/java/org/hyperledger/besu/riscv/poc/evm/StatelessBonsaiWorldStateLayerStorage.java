package org.hyperledger.besu.riscv.poc.evm;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateLayerStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.flat.BonsaiFlatDbStrategyProvider;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SnappedKeyValueStorage;

import java.util.Optional;

import static org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE;

public class StatelessBonsaiWorldStateLayerStorage extends BonsaiWorldStateKeyValueStorage {


    public StatelessBonsaiWorldStateLayerStorage(final StorageProvider provider, final MetricsSystem metricsSystem, final DataStorageConfiguration dataStorageConfiguration) {
        super(provider, metricsSystem, dataStorageConfiguration);
    }

    @Override
    public Optional<Bytes> getAccountStateTrieNode(final Bytes location, final Bytes32 nodeHash) {
        return getStateTrieNode(nodeHash);
    }

    @Override
    public Optional<Bytes> getAccountStorageTrieNode(final Hash accountHash, final Bytes location, final Bytes32 nodeHash) {
        return getStateTrieNode(nodeHash);
    }


}
