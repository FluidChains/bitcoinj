/*
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.tools;

import org.bitcoinj.core.listeners.NewBestBlockListener;
import org.bitcoinj.core.*;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestLitecoinNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestLitecoinNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.MemoryBlockStore;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Charsets;
import com.google.common.base.Stopwatch;
import com.google.common.io.Resources;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkState;

/**
 * Downloads and verifies a full chain from your local peer, emitting checkpoints at each difficulty transition period
 * to a file which is then signed with your key.
 */
public class BuildCheckpoints {
	
	private static final Logger log = LoggerFactory.getLogger(BuildCheckpoints.class);
	
    private static NetworkParameters params;

    public static void main(String[] args) throws Exception {
        BriefLogFormatter.initWithSilentBitcoinJ();

        OptionParser parser = new OptionParser();
        parser.accepts("help");
        OptionSpec<NetworkEnum> netFlag = parser.accepts("net").withRequiredArg().ofType(NetworkEnum.class).defaultsTo(NetworkEnum.MAIN);
        parser.accepts("peer").withRequiredArg();
        OptionSpec<Integer> daysFlag = parser.accepts("days").withRequiredArg().ofType(Integer.class).defaultsTo(30);
        OptionSet options = parser.parse(args);

        if (options.has("help")) {
            System.out.println(Resources.toString(BuildCheckpoints.class.getResource("build-checkpoints-help.txt"), Charsets.UTF_8));
            return;
        }

        int port;
        int interval;
        
        final String prefix;
        switch (netFlag.value(options)) {
            case MAIN:
            case PROD:
                params = MainNetParams.get();
                prefix = params.getId();
                port = params.getPort();
                interval = params.getInterval();
                break;
                
            case TEST:
                params = TestNet3Params.get();
                prefix = params.getId();
                port = params.getPort();
				interval = params.getInterval();
				break;
                
            case REGTEST:
                params = RegTestParams.get();
                prefix = "com.uniquid.regtest";
                port = 19000;
                interval = 2016;
                break;
            
            case LCMAIN:
            	params = LitecoinNetworkParameters.get();
            	prefix = params.getId();
            	port = 9333;
				interval = params.getInterval();
				break;
            	
            case LCTEST:
            	params = TestLitecoinNetParams.get();
            	prefix = "com.uniquid.litecointest";
            	port = 19335;
				interval = 2016;
				break;
            	
            case LCREG:
            	params = RegTestLitecoinNetParams.get();
            	prefix = "com.uniquid.params.UniquidLitecoinRegTest";
            	port = 19000;
				interval = 2016;
				break;
            	
            default:
                throw new RuntimeException("Unreachable.");
        }

        final InetAddress ipAddress;
        if (options.has("peer")) {
            String peerFlag = (String) options.valueOf("peer");
            try {
                ipAddress = InetAddress.getByName(peerFlag);
            } catch (UnknownHostException e) {
                System.err.println("Could not understand peer domain name/IP address: " + peerFlag + ": " + e.getMessage());
                System.exit(1);
                return;
            }
        } else {
            ipAddress = InetAddress.getLocalHost();
        }
        //final PeerAddress peerAddress = new PeerAddress(ipAddress, params.getPort());
        final PeerAddress peerAddress = new PeerAddress(ipAddress, port);

        // Sorted map of block height to StoredBlock object.
        final TreeMap<Integer, StoredBlock> checkpoints = new TreeMap<Integer, StoredBlock>();

        // Configure bitcoinj to fetch only headers, not save them to disk, connect to a local fully synced/validated
        // node and to save block headers that are on interval boundaries, as long as they are <1 month old.
        final BlockStore store = new MemoryBlockStore(params);
        final BlockChain chain = new BlockChain(params, store);
        final PeerGroup peerGroup = new PeerGroup(params, chain);
        
        System.out.println("Connecting to " + peerAddress + "...");
        peerGroup.addAddress(peerAddress);
        
        long now = new Date().getTime() / 1000;
        peerGroup.setFastCatchupTimeSecs(now);

        final long timeAgo = now - (86400 * options.valueOf(daysFlag));
        System.out.println("Checkpointing up to " + Utils.dateTimeFormat(timeAgo * 1000));

        chain.addNewBestBlockListener(Threading.SAME_THREAD, new NewBestBlockListener() {
            @Override
            public void notifyNewBestBlock(StoredBlock block) throws VerificationException {
                int height = block.getHeight();
                
                if(height % 500 == 0) {
                	System.out.println("height is now " + height);
                }

                if(params.equals(LitecoinNetworkParameters.get())
						|| params.equals(TestLitecoinNetParams.get())
						|| params.equals(RegTestLitecoinNetParams.get())
				) {
                	height += 1;
				}

                if (height % interval == 0 && block.getHeader().getTimeSeconds() <= timeAgo) {
                    System.out.println(String.format("Checkpointing block %s at height %d, time %s",
                            block.getHeader().getHash(), block.getHeight(), Utils.dateTimeFormat(block.getHeader().getTime())));
                    checkpoints.put(height, block);
                }
            }
        });

        peerGroup.start();
        
        System.out.println("id = " + params.getId());
        System.out.println("interval = " + interval);

        Stopwatch sw = Stopwatch.createStarted();
        
        String date = new SimpleDateFormat("dd/MM/yyyy HH:mm:SS").format(new Date());
        
        System.out.println(date + " Starting download...");
        peerGroup.downloadBlockChain();

        System.out.println("Checking state of checkpoints... " + checkpoints.size());
        checkState(checkpoints.size() > 0);

        date = new SimpleDateFormat("dd/MM/yyyy HH:mm:SS").format(new Date());
        System.out.println(date + " Creating files...");
        final File plainFile = new File(prefix + ".uniquidcheckpoints");
        final File textFile = new File(prefix + ".uniquidcheckpoints" + ".txt");

        // Write checkpoint data out.
        writeBinaryCheckpoints(checkpoints, plainFile);
        writeTextualCheckpoints(checkpoints, textFile);

        peerGroup.stop();
        store.close();

        System.out.println("File created in " + sw.elapsed(TimeUnit.SECONDS) + " sec");
        
        // Sanity check the created files.
        sanityCheck(plainFile, checkpoints.size());
        sanityCheck(textFile, checkpoints.size());
     
    }

    private static void writeBinaryCheckpoints(TreeMap<Integer, StoredBlock> checkpoints, File file) throws Exception {
        final FileOutputStream fileOutputStream = new FileOutputStream(file, false);
        MessageDigest digest = Sha256Hash.newDigest();
        final DigestOutputStream digestOutputStream = new DigestOutputStream(fileOutputStream, digest);
        digestOutputStream.on(false);
        final DataOutputStream dataOutputStream = new DataOutputStream(digestOutputStream);
        dataOutputStream.writeBytes("CHECKPOINTS 1");
        dataOutputStream.writeInt(0);  // Number of signatures to read. Do this later.
        digestOutputStream.on(true);
        dataOutputStream.writeInt(checkpoints.size());
        ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
        for (StoredBlock block : checkpoints.values()) {
            block.serializeCompact(buffer);
            dataOutputStream.write(buffer.array());
            buffer.position(0);
        }
        dataOutputStream.close();
        Sha256Hash checkpointsHash = Sha256Hash.wrap(digest.digest());
        System.out.println("Hash of checkpoints data is " + checkpointsHash);
        digestOutputStream.close();
        fileOutputStream.close();
        System.out.println("Checkpoints written to '" + file.getCanonicalPath() + "'.");
    }

    private static void writeTextualCheckpoints(TreeMap<Integer, StoredBlock> checkpoints, File file) throws IOException {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), Charsets.US_ASCII));
        writer.println("TXT CHECKPOINTS 1");
        writer.println("0"); // Number of signatures to read. Do this later.
        writer.println(checkpoints.size());
        ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_SIZE);
        for (StoredBlock block : checkpoints.values()) {
            block.serializeCompact(buffer);
            writer.println(CheckpointManager.BASE64.encode(buffer.array()));
            buffer.position(0);
        }
        writer.close();
        System.out.println("Checkpoints written to '" + file.getCanonicalPath() + "'.");
    }

    private static void sanityCheck(File file, int expectedSize) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        CheckpointManager manager;
        try {
            manager = new CheckpointManager(params, fis);
        } finally {
            fis.close();
        }

        checkState(manager.numCheckpoints() == expectedSize);

        if (params.getId().equals(NetworkParameters.ID_MAINNET)) {
            StoredBlock test = manager.getCheckpointBefore(1390500000); // Thu Jan 23 19:00:00 CET 2014
            checkState(test.getHeight() == 280224);
            checkState(test.getHeader().getHashAsString()
                    .equals("00000000000000000b5d59a15f831e1c45cb688a4db6b0a60054d49a9997fa34"));
        } else if (params.getId().equals(NetworkParameters.ID_TESTNET)) {
            StoredBlock test = manager.getCheckpointBefore(1390500000); // Thu Jan 23 19:00:00 CET 2014
            checkState(test.getHeight() == 167328);
            checkState(test.getHeader().getHashAsString()
                    .equals("0000000000035ae7d5025c2538067fe7adb1cf5d5d9c31b024137d9090ed13a9"));
        }
    }
}
