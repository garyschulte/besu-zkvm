package org.hyperledger.besu.riscv.poc.evm;

import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.storage.StorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueSegmentIdentifier;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStoragePrefixedKeyBlockchainStorage;
import org.hyperledger.besu.ethereum.storage.keyvalue.KeyValueStorageProvider;
import org.hyperledger.besu.ethereum.storage.keyvalue.VariablesKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.BonsaiWorldStateProvider;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.CodeCache;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.cache.NoopBonsaiCachedMerkleTrieLoader;
import org.hyperledger.besu.ethereum.trie.pathbased.bonsai.storage.BonsaiWorldStateKeyValueStorage;
import org.hyperledger.besu.ethereum.trie.pathbased.common.provider.WorldStateQueryParams;
import org.hyperledger.besu.ethereum.worldstate.DataStorageConfiguration;
import org.hyperledger.besu.evm.internal.EvmConfiguration;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.BesuService;
import org.hyperledger.besu.plugin.services.storage.KeyValueStorage;
import org.hyperledger.besu.plugin.services.storage.SegmentIdentifier;
import org.hyperledger.besu.plugin.services.storage.SegmentedKeyValueStorage;
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedInMemoryKeyValueStorage;

import java.util.List;
import java.util.Optional;

public class PragueBlockRunner {
    
    private final ProtocolSchedule protocolSchedule;
    private final MutableBlockchain blockchain;
    private final org.hyperledger.besu.ethereum.trie.pathbased.bonsai.BonsaiWorldStateProvider worldStateArchive;
    private final ProtocolContext protocolContext;

    public static PragueBlockRunner create() {

        final GenesisConfig genesisConfig = GenesisConfig.mainnet();
        final NoOpMetricsSystem noOpMetricsSystem = new NoOpMetricsSystem();

        ProtocolSchedule protocolSchedule = MainnetProtocolSchedule.fromConfig(
                genesisConfig.getConfigOptions(),
                EvmConfiguration.DEFAULT,
                MiningConfiguration.MINING_DISABLED,
                new BadBlockManager(),
                false,
                false,
                noOpMetricsSystem
        );
        
        // Genesis State
        GenesisState genesisState = GenesisState.fromConfig(genesisConfig, protocolSchedule, new CodeCache());

        StorageProvider storageProvider = new KeyValueStorageProvider(
                segments -> new SegmentedInMemoryKeyValueStorage(),
                new InMemoryKeyValueStorage(),
                noOpMetricsSystem
        );

        var variablesStorage = new VariablesKeyValueStorage(
                storageProvider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.VARIABLES)
        );
        var blockchainStorage = new KeyValueStoragePrefixedKeyBlockchainStorage(
                storageProvider.getStorageBySegmentIdentifier(KeyValueSegmentIdentifier.BLOCKCHAIN),
                variablesStorage,
                new MainnetBlockHeaderFunctions(),
                false
        );
        MutableBlockchain blockchain = DefaultBlockchain.createMutable(
                genesisState.getBlock(),
                blockchainStorage,
                noOpMetricsSystem,
                0
        );
        
        // WorldState
        var bonsaiStorage = new BonsaiWorldStateKeyValueStorage(
                storageProvider,
                noOpMetricsSystem,
                DataStorageConfiguration.DEFAULT_BONSAI_PARTIAL_DB_CONFIG
        );

        ServiceManager serviceManager = new ServiceManager() {
            @Override
            public <T extends BesuService> void addService(Class<T> serviceType, T service) {
                //no nop
            }

            @Override
            public <T extends BesuService> Optional<T> getService(Class<T> serviceType) {
                return Optional.empty();
            }
        };

        var worldStateArchive = new BonsaiWorldStateProvider(
                bonsaiStorage,
                blockchain,
                Optional.empty(), // maxLayersToLoad
                new NoopBonsaiCachedMerkleTrieLoader(),
                serviceManager,
                EvmConfiguration.DEFAULT,
                () -> (__, ___) -> {},
                new CodeCache()
        );

        var protocolContext = new ProtocolContext.Builder()
                .withBlockchain(blockchain)
                .withWorldStateArchive(worldStateArchive)
                .withConsensusContext(null) //????
                .withBadBlockManager(new BadBlockManager())
                .withServiceManager(serviceManager)
                .build();

        return new PragueBlockRunner(protocolSchedule,protocolContext, blockchain, worldStateArchive);
    }

    private PragueBlockRunner(
            ProtocolSchedule protocolSchedule,
            ProtocolContext protocolContext,
            MutableBlockchain blockchain,
            BonsaiWorldStateProvider worldStateArchive) {
        this.protocolSchedule = protocolSchedule;
        this.protocolContext = protocolContext;
        this.blockchain = blockchain;
        this.worldStateArchive = worldStateArchive;
    }

    public void processBlock(Block block) {
        BlockHeader parentHeader = blockchain.getBlockHeader(block.getHeader().getParentHash())
                .orElseThrow(() -> new RuntimeException("Parent block not found"));

        MutableWorldState worldState = worldStateArchive.getWorldState(WorldStateQueryParams.newBuilder().withBlockHeader(parentHeader).withShouldWorldStateUpdateHead(false)
                        .build())
                .orElseThrow(() -> new RuntimeException("Cannot get mutable world state"));

        ProtocolSpec protocolSpec = protocolSchedule.getByBlockHeader(block.getHeader());

        var result = protocolSpec.getBlockProcessor().processBlock(
                protocolContext,
                blockchain,
                worldState,
                block
        );

        if (!result.isSuccessful()) {
            throw new RuntimeException("Block processing failed");
        }

        blockchain.appendBlock(block, result.getReceipts());

        System.out.println("✓ Block " + block.getHeader().getNumber() + " processed successfully");
        System.out.println("  Transactions: " + block.getBody().getTransactions().size());
        System.out.println("  Receipts: " + result.getReceipts().size());
        System.out.println("  Gas used: " + block.getHeader().getGasUsed());
    }
    
    public static void main(String[] args) {
        PragueBlockRunner runner = PragueBlockRunner.create();
        // runner.processBlock(yourBlock);
    }
}