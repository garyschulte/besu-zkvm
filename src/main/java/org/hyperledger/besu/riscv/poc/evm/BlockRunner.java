package org.hyperledger.besu.riscv.poc.evm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.config.GenesisConfig;
import org.hyperledger.besu.consensus.merge.PostMergeContext;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.ProtocolContext;
import org.hyperledger.besu.ethereum.chain.BadBlockManager;
import org.hyperledger.besu.ethereum.chain.GenesisState;
import org.hyperledger.besu.ethereum.chain.MutableBlockchain;
import org.hyperledger.besu.ethereum.chain.DefaultBlockchain;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.core.Difficulty;
import org.hyperledger.besu.ethereum.core.MiningConfiguration;
import org.hyperledger.besu.ethereum.core.MutableWorldState;
import org.hyperledger.besu.ethereum.mainnet.MainnetProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSchedule;
import org.hyperledger.besu.ethereum.mainnet.ProtocolSpec;
import org.hyperledger.besu.ethereum.mainnet.MainnetBlockHeaderFunctions;
import org.hyperledger.besu.ethereum.rlp.RLP;
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
import org.hyperledger.besu.services.kvstore.InMemoryKeyValueStorage;
import org.hyperledger.besu.services.kvstore.SegmentedInMemoryKeyValueStorage;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage.WORLD_BLOCK_HASH_KEY;
import static org.hyperledger.besu.ethereum.trie.pathbased.common.storage.PathBasedWorldStateKeyValueStorage.WORLD_ROOT_HASH_KEY;

public class BlockRunner {

    /**
     * Record to hold command line arguments for file paths.
     *
     * @param stateJsonPath Optional path to state.json file
     * @param blockRlpPath Optional path to block.rlp file
     * @param genesisConfigPath Optional path to genesis config file
     */
    private record CommandLineArgs(Optional<String> stateJsonPath, Optional<String> blockRlpPath, Optional<String> genesisConfigPath) {}

    private final ProtocolSchedule protocolSchedule;
    private final MutableBlockchain blockchain;
    private final org.hyperledger.besu.ethereum.trie.pathbased.bonsai.BonsaiWorldStateProvider worldStateArchive;
    private final ProtocolContext protocolContext;

public static BlockRunner create(final List<BlockHeader> prevHeaders, final Map<Hash,Bytes> trienodes, final Map<Hash,Bytes> codes, final String genesisConfigJson) {

        final GenesisConfig genesisConfig = GenesisConfig.fromConfig(genesisConfigJson);
        final NoOpMetricsSystem noOpMetricsSystem = new NoOpMetricsSystem();

        EvmConfiguration evmConfiguration = new EvmConfiguration(32_000L, EvmConfiguration.WorldUpdaterMode.JOURNALED, false);

        ProtocolSchedule protocolSchedule = MainnetProtocolSchedule.fromConfig(
                genesisConfig.getConfigOptions(),
            evmConfiguration,
                // use workaround miningConfiguration until upstream besu version meta is fixed
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

        final KeyValueStoragePrefixedKeyBlockchainStorage.Updater updater = blockchainStorage.updater();
        prevHeaders.forEach(blockHeader -> {
            updater.putBlockHeader(blockHeader.getBlockHash(), blockHeader);
            updater.putBlockHash(blockHeader.getNumber(), blockHeader.getBlockHash());
            updater.setChainHead(blockHeader.getBlockHash());
            updater.putTotalDifficulty(blockHeader.getBlockHash(), Difficulty.ZERO);
        });
        updater.commit();

        // WorldState
        var bonsaiStorage = new StatelessBonsaiWorldStateLayerStorage(
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

        final BonsaiWorldStateKeyValueStorage.Updater stateUpdater = bonsaiStorage.updater();
        trienodes.forEach((hash, value) -> {
            stateUpdater.getWorldStateTransaction().put(KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE,hash.toArray(),value.toArray());
        });
        codes.forEach((hash, value) -> {
            stateUpdater.getWorldStateTransaction().put(KeyValueSegmentIdentifier.CODE_STORAGE, hash.toArrayUnsafe(),value.toArrayUnsafe());
        });
        final BlockHeader head = prevHeaders.get(prevHeaders.size() - 1);
        stateUpdater. getWorldStateTransaction().put(KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE, WORLD_ROOT_HASH_KEY,head.getStateRoot().toArray());
        stateUpdater.getWorldStateTransaction().put(KeyValueSegmentIdentifier.TRIE_BRANCH_STORAGE, WORLD_BLOCK_HASH_KEY,head.getBlockHash().toArray());
        stateUpdater.commit();

        var worldStateArchive = new BonsaiWorldStateProvider(
                bonsaiStorage,
                blockchain,
                Optional.empty(), // maxLayersToLoad
                new NoopBonsaiCachedMerkleTrieLoader(),
                serviceManager,
                evmConfiguration,
                () -> (__, ___) -> {},
                new CodeCache()
        );

        /*
        curl --location 'http://127.0.0.1:8545' \
        --data '{
            "jsonrpc": "2.0",
            "method": "debug_traceTransaction",
            "params": [
                "0xca36c1e529714639c25fc2ac9a3b13c4d0a3e81a74f451313ec2926c54cc48f3",
                {
                    "disableStorage": true
                }
            ],
            "id": 1
        }'
         */
        var postMergeContext = new PostMergeContext();

        var protocolContext = new ProtocolContext.Builder()
                .withBlockchain(blockchain)
                .withWorldStateArchive(worldStateArchive)
                .withConsensusContext(postMergeContext)
                .withBadBlockManager(new BadBlockManager())
                .withServiceManager(serviceManager)
                .build();

        return new BlockRunner(protocolSchedule,protocolContext, blockchain, worldStateArchive);
    }

    private BlockRunner(
            ProtocolSchedule protocolSchedule,
            ProtocolContext protocolContext,
            MutableBlockchain blockchain,
            BonsaiWorldStateProvider worldStateArchive) {
        this.protocolSchedule = protocolSchedule;
        this.protocolContext = protocolContext;
        this.blockchain = blockchain;
        this.worldStateArchive = worldStateArchive;
    }

    public void processBlock(final Block block) {
        BlockHeader parentHeader = blockchain.getBlockHeader(block.getHeader().getParentHash())
                .orElseThrow(() -> new RuntimeException("Parent block not found"));

        MutableWorldState worldState = worldStateArchive.getWorldState(WorldStateQueryParams.newBuilder().withBlockHeader(parentHeader).withShouldWorldStateUpdateHead(true)
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
            System.out.println("Block " + block.getHeader().getNumber()
                    + " failed with error message "+ result.errorMessage);
            throw new RuntimeException("Block processing failed");
        }

        blockchain.appendBlock(block, result.getReceipts());

        System.out.println("✓ Block " + block.getHeader().getNumber() + " processed successfully");
        System.out.println("  Transactions: " + block.getBody().getTransactions().size());
        System.out.println("  Receipts: " + result.getReceipts().size());
        System.out.println("  Gas used: " + block.getHeader().getGasUsed());
        System.out.println("  Stateroot: " + result.getYield().get().getWorldState().rootHash());
    }

    /**
     * Print usage information and exit.
     */
    private static void printUsageAndExit() {
        System.out.println("Usage: BlockRunner [OPTIONS]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --state=<path>    Path to state.json file");
        System.out.println("  --block=<path>    Path to block.rlp file");
        System.out.println("  --genesis=<path>  Path to genesis config file");
        System.out.println("  --help, -h, ?     Display this help message");
        System.out.println();
        System.out.println("Defaults:");
        System.out.println("  --state   : bundled /state.json resource");
        System.out.println("  --block   : bundled /block.rlp resource");
        System.out.println("  --genesis : bundled /mainnet.json resource");
        System.exit(0);
    }

    /**
     * Parse command line arguments for state, block, and genesis file paths.
     *
     * @param args command line arguments
     * @return CommandLineArgs record with optional file paths
     */
    private static CommandLineArgs parseArguments(final String[] args) {
        Optional<String> stateJsonPath = Optional.empty();
        Optional<String> blockRlpPath = Optional.empty();
        Optional<String> genesisConfigPath = Optional.empty();

        // Parse named arguments
        for (String arg : args) {
            if (arg.equals("--help") || arg.equals("-h") || arg.equals("?")) {
                printUsageAndExit();
            } else if (arg.startsWith("--state=")) {
                stateJsonPath = Optional.of(arg.substring("--state=".length()));
            } else if (arg.startsWith("--block=")) {
                blockRlpPath = Optional.of(arg.substring("--block=".length()));
            } else if (arg.startsWith("--genesis=")) {
                genesisConfigPath = Optional.of(arg.substring("--genesis=".length()));
            } else {
                System.err.println("Unknown argument: " + arg);
                System.err.println("Use --help for usage information");
                System.exit(1);
            }
        }

        return new CommandLineArgs(stateJsonPath, blockRlpPath, genesisConfigPath);
    }

    /**
     * Load file content from either classpath resource or filesystem path.
     * Tries classpath resource first, then falls back to filesystem if not found.
     *
     * @param filePath Optional filesystem path or resource path
     * @param defaultResourcePath default classpath resource path (e.g., "/state.json")
     * @return file content as string
     */
    private static String loadFileContent(final Optional<String> filePath, final String defaultResourcePath) throws Exception {
        String path = filePath.orElse(defaultResourcePath);

        // Try loading from classpath resource first
        var inputStream = BlockRunner.class.getResourceAsStream(path);
        if (inputStream != null) {
            System.out.println("Loading from classpath: " + path);
            return new String(inputStream.readAllBytes());
        }

        // Fall back to filesystem
        System.out.println("Loading from filesystem: " + path);
        return Files.readString(Path.of(path));
    }

    public static void main(final String[] args) {
        System.out.println("Starting BlockRunner");
        CommandLineArgs cmdArgs = parseArguments(args);

        try {
            ObjectMapper objectMapper = new ObjectMapper();

            // Load genesis config
            String genesisConfigJson = loadFileContent(cmdArgs.genesisConfigPath(), "/mainnet.json");
            System.out.println("✓ Loaded genesis config");

            /*
                curl --location 'http://127.0.0.1:8545' --data '{
                    "jsonrpc": "2.0",
                    "method": "debug_executionWitness",
                    "params": [
                        "0xE9E53"
                    ],
                    "id": 1
                }'
             */
            String stateJsonContent = loadFileContent(cmdArgs.stateJsonPath(), "/state.json");
            ExecutionWitnessJson executionWitness = objectMapper.readValue(stateJsonContent, ExecutionWitnessJson.class);
            System.out.println("✓ Loaded execution witness");
            System.out.println("  State: " + executionWitness.getState().size());
            System.out.println("  Keys: " + executionWitness.getKeys().size());
            System.out.println("  Codes: " + executionWitness.getCodes().size());
            System.out.println("  Headers: " + executionWitness.getHeaders().size());

             /*
                curl --location 'http://127.0.0.1:8545' --data '{
                    "jsonrpc": "2.0",
                    "method": "debug_getRawBlock",
                    "params": [
                        "0xE9E53"
                    ],
                    "id": 1
                }'
             */
            String blockRlpContent = loadFileContent(cmdArgs.blockRlpPath(), "/block.rlp");
            Block blockToImport = Block.readFrom(
                    RLP.input(Bytes.fromHexString(blockRlpContent.trim())),
                    new MainnetBlockHeaderFunctions());
            System.out.println("✓ Loaded block to import " + blockToImport.getHeader().getNumber());

            final Map<Hash, Bytes> trieNodes = executionWitness.getState().stream()
                    .map(Bytes::fromHexString)
                    .collect(Collectors.toMap(
                            Hash::hash,
                            o -> o
                    ));
            final Map<Hash, Bytes> codes = executionWitness.getCodes().stream()
                    .map(Bytes::fromHexString)
                    .collect(Collectors.toMap(
                            Hash::hash,
                            o -> o
                    ));

            final List<BlockHeader> previousHeaders = executionWitness.getHeaders()
                    .stream()
                    .map(s -> BlockHeader.readFrom(RLP.input(Bytes.fromHexString(s)), new MainnetBlockHeaderFunctions()))
                    .sorted(Comparator.comparing(BlockHeader::getNumber))
                    .toList();

            BlockRunner runner = BlockRunner.create(previousHeaders, trieNodes, codes, genesisConfigJson);

            runner.processBlock(blockToImport);
        } catch (Exception e) {
            System.err.println("Error loading or processing block: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

}
