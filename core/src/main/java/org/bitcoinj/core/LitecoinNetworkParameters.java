package org.bitcoinj.core;

import static com.google.common.base.Preconditions.checkState;
import static org.bitcoinj.core.Coin.COIN;

import java.math.BigInteger;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.utils.MonetaryFormat;
import org.bitcoinj.utils.VersionTally;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Stopwatch;

public class LitecoinNetworkParameters extends NetworkParameters {

    private static final Logger log = LoggerFactory.getLogger(LitecoinNetworkParameters.class);

    public static final int MAINNET_MAJORITY_WINDOW = 1000;
    public static final int MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED = 950;
    public static final int MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE = 750;

    public LitecoinNetworkParameters() {

        id = "org.litecoin.main";

        alertSigningKey = Utils.HEX.decode("04302390343f91cc401d56d68b123028bf52e5fca1939df127f63c6467cdf9c8e2c14b61104cf817d0b780da337893ecc4aaff1309e536162dabbdb45200ca2b0a");

        targetTimespan = (int)(3.5 * 24 * 60 * 60);
        interval = targetTimespan/((int)(2.5 * 60));

        maxTarget = Utils.decodeCompactBits(0x1e0fffffL);

        addressHeader = 48;
        p2shHeader = 5;
        dumpedPrivateKeyHeader = 176;

        packetMagic = 0xfbc0b6dbL;

//        acceptableAddressCodes = new int[] { addressHeader, p2shHeader };

        bip32HeaderP2PKHpub = 0x0488B21E;
        bip32HeaderP2PKHpriv = 0x0488ADE4;

        port = 9333;

        genesisBlock = new Block(this, Block.BLOCK_VERSION_GENESIS);
        genesisBlock.setDifficultyTarget(0x1e0ffff0L);
        genesisBlock.setTime(1317972665L);
        genesisBlock.setNonce(2084524493L);
        genesisBlock.setMerkleRoot(Sha256Hash.wrap("97ddfbbae6be97fd6cdf3e7ca13232a3afff2353e29badfab7f73011edd4ced9"));
        genesisBlock.setPrevBlockHash(Sha256Hash.ZERO_HASH);

        String genesisHash = genesisBlock.getHashAsString();

        checkState(genesisHash.equals("12a765e31ffd4059bada1e25190f6e98c99d9714d334efa41a195a7e7e04bfe2"),
                genesisBlock);

        subsidyDecreaseBlockCount = 840000;		// TODO test with 840000

        addrSeeds = null;
//        dnsSeeds = null;

        dnsSeeds = new String[] {
                "seed-a.litecoin.loshan.co.uk",
                "dnsseed.thrasher.io",
                "dnsseed.litecointools.com",
                "dnsseed.litecoinpool.org",
                "dnsseed.koin-project.com"
        };

        majorityEnforceBlockUpgrade = MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = MAINNET_MAJORITY_WINDOW;

    }

    private static Coin MAX_MONEY = COIN.multiply(21000000);
    @Override
    public Coin getMaxMoney() { return MAX_MONEY; }

    @Override
    public Block getGenesisBlock() {
        return genesisBlock;
    }

    private static LitecoinNetworkParameters instance;
    public static synchronized LitecoinNetworkParameters get() {
        if (instance == null) {
            instance = new LitecoinNetworkParameters();
        }
        return instance;
    }

//    /** The number of previous blocks to look at when calculating the next Block's difficulty */
//    @Override
//    public int getRetargetBlockCount(StoredBlock cursor) {
//        if (cursor.getHeight() + 1 != getInterval()) {
//            //Logger.getLogger("wallet_ltc").info("Normal LTC retarget");
//            return getInterval();
//        } else {
//            //Logger.getLogger("wallet_ltc").info("Genesis LTC retarget");
//            return getInterval() - 1;
//        }
//    }

    @Override
    public String getUriScheme() {
        return "litecoin:";
    }

//    /** Gets the hash of the given block for the purpose of checking its PoW */
//    public Sha256Hash calculateBlockPoWHash(Block b) {
//        byte[] blockHeader = b.cloneAsHeader().bitcoinSerialize();
//        try {
//            return new Sha256Hash(Utils.reverseBytes(SCrypt.scrypt(blockHeader, blockHeader, 1024, 1, 1, 32)));
//        } catch (GeneralSecurityException e) {
//            throw new RuntimeException(e);
//        }
//    }
//
//    static {
//        NetworkParameters.registerParams(get());
//        NetworkParameters.PROTOCOL_VERSION = 70002;
//    }

    /**
     * The flags indicating which block validation tests should be applied to
     * the given block. Enables support for alternative blockchains which enable
     * tests based on different criteria.
     *
     * @param block block to determine flags for.
     * @param height height of the block, if known, null otherwise. Returned
     * tests should be a safe subset if block height is unknown.
     */
    public EnumSet<Block.VerifyFlag> getBlockVerificationFlags(final Block block,
                                                               final VersionTally tally, final Integer height) {
        final EnumSet<Block.VerifyFlag> flags = EnumSet.noneOf(Block.VerifyFlag.class);

        if (block.isBIP34()) {
            final Integer count = tally.getCountAtOrAbove(Block.BLOCK_VERSION_BIP34);
            if (null != count && count >= getMajorityEnforceBlockUpgrade()) {
                flags.add(Block.VerifyFlag.HEIGHT_IN_COINBASE);
            }
        }
        return flags;
    }

    /** How many blocks pass between difficulty adjustment periods. Bitcoin standardises this to be 2015. */
    public int getInterval() {
        return interval;
    }

    protected boolean isDifficultyTransitionPoint(StoredBlock storedPrev) {
        return ((storedPrev.getHeight() + 1) % this.getInterval()) == 0;
    }

    @Override
    public void checkDifficultyTransitions(final StoredBlock storedPrev, final Block nextBlock,
                                           final BlockStore blockStore) throws VerificationException, BlockStoreException {
        Block prev = storedPrev.getHeader();

        // Is this supposed to be a difficulty transition point?
        if (!isDifficultyTransitionPoint(storedPrev)) {

            // No ... so check the difficulty didn't actually change.
            if (nextBlock.getDifficultyTarget() != prev.getDifficultyTarget())
                throw new VerificationException("Unexpected change in difficulty at height " + storedPrev.getHeight() +
                        ": " + Long.toHexString(nextBlock.getDifficultyTarget()) + " vs " +
                        Long.toHexString(prev.getDifficultyTarget()));
            return;
        }

        // workaround to use checkpoints
        StoredBlock previous = blockStore.get(prev.getHash());
        for (int i = 0; i < 2; i++) {
            if(previous == null) {
                return;
            }
            previous = blockStore.get(previous.getHeader().getPrevBlockHash());
        }

        // We need to find a block far back in the chain. It's OK that this is expensive because it only occurs every
        // two weeks after the initial block chain download.
        final Stopwatch watch = Stopwatch.createStarted();
        StoredBlock cursor = blockStore.get(prev.getHash());

        // Litecoin: This fixes an issue where a 51% attack can change difficulty at will.
        // Go back the full period unless it's the first retarget after genesis. Code courtesy of Art Forz
        int blockstogoback = this.getInterval() -1;
        if((storedPrev.getHeight() + 1) != this.getInterval()) {
            blockstogoback = this.getInterval();
        }

        for (int i = 0; i < blockstogoback; i++) {
            if (cursor == null) {
                // This should never happen. If it does, it means we are following an incorrect or busted chain.
                throw new VerificationException(
                        "Difficulty transition point but we did not find a way back to the genesis block.");
            }
            cursor = blockStore.get(cursor.getHeader().getPrevBlockHash());
        }
        watch.stop();
        if (watch.elapsed(TimeUnit.MILLISECONDS) > 50)
            log.info("Difficulty transition traversal took {}", watch);

        Block blockIntervalAgo = cursor.getHeader();
        int timespan = (int) (prev.getTimeSeconds() - blockIntervalAgo.getTimeSeconds());
        // Limit the adjustment step.
        final int targetTimespan = this.getTargetTimespan();
        if (timespan < targetTimespan / 4)
            timespan = targetTimespan / 4;
        if (timespan > targetTimespan * 4)
            timespan = targetTimespan * 4;

        BigInteger newTarget = Utils.decodeCompactBits(prev.getDifficultyTarget());
        newTarget = newTarget.multiply(BigInteger.valueOf(timespan));
        newTarget = newTarget.divide(BigInteger.valueOf(targetTimespan));

        if (newTarget.compareTo(this.getMaxTarget()) > 0) {
            log.info("Difficulty hit proof of work limit: {}", newTarget.toString(16));
            newTarget = this.getMaxTarget();
        }

        int accuracyBytes = (int) (nextBlock.getDifficultyTarget() >>> 24) - 3;
        long receivedTargetCompact = nextBlock.getDifficultyTarget();

        // The calculated difficulty is to a higher precision than received, so reduce here.
        BigInteger mask = BigInteger.valueOf(0xFFFFFFL).shiftLeft(accuracyBytes * 8);
        newTarget = newTarget.and(mask);
        long newTargetCompact = Utils.encodeCompactBits(newTarget);

        if (newTargetCompact != receivedTargetCompact)
            throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                    Long.toHexString(newTargetCompact) + " vs " + Long.toHexString(receivedTargetCompact));
    }


    @Override
    public Coin getMinNonDustOutput() {
        return Transaction.MIN_NONDUST_OUTPUT;
    }

    @Override
    public MonetaryFormat getMonetaryFormat() {
        return new MonetaryFormat();
    }

    @Override
    public int getProtocolVersionNum(final ProtocolVersion version) {
        return 70002;
    }

    @Override
    public BitcoinSerializer getSerializer(boolean parseRetain) {
        return new BitcoinSerializer(this, parseRetain);
    }

    @Override
    public boolean hasMaxMoney() {
        return true;
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_TESTNET;
    }
}
