package org.bitcoinj.params;

import static com.google.common.base.Preconditions.checkState;

import java.math.BigInteger;

import org.bitcoinj.core.Block;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegTestLitecoinNetParams extends TestLitecoinNetParams {
	
	private static Logger LOGGER = LoggerFactory.getLogger(RegTestLitecoinNetParams.class);
	
	private static final BigInteger MAX_TARGET = new BigInteger("7fffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff", 16);
	
	public RegTestLitecoinNetParams() {
		super();
        // Difficulty adjustments are disabled for regtest. 
        // By setting the block interval for difficulty adjustments to Integer.MAX_VALUE we make sure difficulty never changes.    
        interval = Integer.MAX_VALUE;
        maxTarget = MAX_TARGET;
        subsidyDecreaseBlockCount = 150;
        port = 19444;
        id = ID_REGTEST;
        
        packetMagic = 0xfabfb5da; 
        		
        majorityEnforceBlockUpgrade = MainNetParams.MAINNET_MAJORITY_ENFORCE_BLOCK_UPGRADE;
        majorityRejectBlockOutdated = MainNetParams.MAINNET_MAJORITY_REJECT_BLOCK_OUTDATED;
        majorityWindow = MainNetParams.MAINNET_MAJORITY_WINDOW;
        
        dnsSeeds = null;
	}
	
	private static Block genesis;

    @Override
    public Block getGenesisBlock() {
        synchronized (RegTestParams.class) {
            if (genesis == null) {
                genesis = super.getGenesisBlock();
                genesis.setNonce(0);
                genesis.setDifficultyTarget(0x207fFFFFL);
                genesis.setTime(1296688602L);               
                checkState(genesis.getHashAsString().toLowerCase().equals("530827f38f93b43ed12af0b3ad25a288dc02ed74d6d7857862df51fc56c416f9"));
            }
            return genesis;
        }
    }
    
    private static RegTestLitecoinNetParams instance;
    public static synchronized RegTestLitecoinNetParams get() {
        if (instance == null) {
            instance = new RegTestLitecoinNetParams();
        }
        return instance;
    }

    @Override
    public String getPaymentProtocolId() {
        return PAYMENT_PROTOCOL_ID_REGTEST;
    }

}
