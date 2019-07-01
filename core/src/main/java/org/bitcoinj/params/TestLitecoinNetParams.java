package org.bitcoinj.params;

import static com.google.common.base.Preconditions.checkState;

import java.math.BigInteger;
import java.util.Date;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.LitecoinNetworkParameters;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;

public class TestLitecoinNetParams extends LitecoinNetworkParameters {

    public TestLitecoinNetParams() {
        super();

        id = "org.litecoin.test";

        addressHeader = 111;
        p2shHeader = 196;
        dumpedPrivateKeyHeader = 239;
        port = 19335;
        packetMagic = 0xfdd2c8f1L;
        dumpedPrivateKeyHeader = 128 + addressHeader;
        maxTarget = Utils.decodeCompactBits(0x1e0fffffL);		// max difficulty

        bip32HeaderP2PKHpub = 0x043587CF;
        bip32HeaderP2PKHpriv = 0x04358394;

        dnsSeeds = new String[] {
                "testnet-seed.litecointools.com",
                "seed-b.litecoin.loshan.co.uk",
                "dnsseed-testnet.thrasher.io"
        };

        addrSeeds = null;

        majorityEnforceBlockUpgrade = TestNet3Params.TESTNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = TestNet3Params.TESTNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = TestNet3Params.TESTNET_MAJORITY_WINDOW;
    }

    @Override
    public boolean allowEmptyPeerChain() {

        return true;

    }

    private static Block genesis;

    @Override
    public Block getGenesisBlock() {
        synchronized (RegTestParams.class) {
            if (genesis == null) {
                genesis = super.getGenesisBlock();
                genesis.setNonce(293345L);
                genesis.setDifficultyTarget(0x1e0ffff0L);
                genesis.setTime(1486949366L);
                checkState(genesis.getHashAsString().toLowerCase().equals("4966625a4b2851d9fdee139e56211a0d88575f59ed816ff5e6a63deb4e3e29a0"));
            }
            return genesis;
        }
    }

    private static TestLitecoinNetParams instance;
    public static synchronized TestLitecoinNetParams get() {
        if (instance == null) {
            instance = new TestLitecoinNetParams();
        }
        return instance;

    }


    @Override
    public String getPaymentProtocolId() {

        return PAYMENT_PROTOCOL_ID_TESTNET;

    }

    // February 16th 2012
    private static final Date testnetDiffDate = new Date(1329264000000L);

    @Override
    public void checkDifficultyTransitions(final StoredBlock storedPrev, final Block nextBlock, final BlockStore blockStore)
            throws VerificationException, BlockStoreException {
        if (!isDifficultyTransitionPoint(storedPrev) && nextBlock.getTime().after(testnetDiffDate)) {
            Block prev = storedPrev.getHeader();

            // After 15th February 2012 the rules on the testnet change to avoid people running up the difficulty
            // and then leaving, making it too hard to mine a block. On non-difficulty transition points, easy
            // blocks are allowed if there has been a span of 20 minutes without one.
            final long timeDelta = nextBlock.getTimeSeconds() - prev.getTimeSeconds();
            // There is an integer underflow bug in bitcoin-qt that means mindiff blocks are accepted when time
            // goes backwards.
            if (timeDelta >= 0 && timeDelta <= (2.5 * 60) * 2) {
                // Walk backwards until we find a block that doesn't have the easiest proof of work, then check
                // that difficulty is equal to that one.
                StoredBlock cursor = storedPrev;
                while (!cursor.getHeader().equals(getGenesisBlock()) &&
                        cursor.getHeight() % getInterval() != 0 &&
                        cursor.getHeader().getDifficultyTargetAsInteger().equals(getMaxTarget()))
                    cursor = cursor.getPrev(blockStore);
                BigInteger cursorTarget = cursor.getHeader().getDifficultyTargetAsInteger();
                BigInteger newTarget = nextBlock.getDifficultyTargetAsInteger();
                if (!cursorTarget.equals(newTarget))
                    throw new VerificationException("Testnet block transition that is not allowed: " +
                            Long.toHexString(cursor.getHeader().getDifficultyTarget()) + " vs " +
                            Long.toHexString(nextBlock.getDifficultyTarget()));
            }
        } else {
            super.checkDifficultyTransitions(storedPrev, nextBlock, blockStore);
        }
    }

}
