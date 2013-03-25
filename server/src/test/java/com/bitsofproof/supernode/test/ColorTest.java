/* 
 * Copyright 2013 Tamas Blummer tamas@bitsofproof.com
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
package com.bitsofproof.supernode.test;

import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.security.Security;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.bitsofproof.supernode.api.BCSAPI;
import com.bitsofproof.supernode.api.BCSAPIException;
import com.bitsofproof.supernode.api.Block;
import com.bitsofproof.supernode.api.Hash;
import com.bitsofproof.supernode.api.KeyGenerator;
import com.bitsofproof.supernode.api.Transaction;
import com.bitsofproof.supernode.api.TrunkListener;
import com.bitsofproof.supernode.api.ValidationException;
import com.bitsofproof.supernode.core.BlockStore;
import com.bitsofproof.supernode.core.Chain;
import com.bitsofproof.supernode.core.Difficulty;

@RunWith (SpringJUnit4ClassRunner.class)
@ContextConfiguration (locations = { "/context/store3.xml", "/context/EmbeddedBCSAPI.xml" })
public class ColorTest
{
	@Autowired
	BlockStore store;

	@Autowired
	Chain chain;

	@Autowired
	BCSAPI api;

	private static final long COIN = 100000000L;

	private static KeyGenerator wallet;

	private static Map<Integer, Block> blocks = new HashMap<Integer, Block> ();

	@BeforeClass
	public static void provider ()
	{
		Security.addProvider (new BouncyCastleProvider ());
	}

	@Test
	public void init () throws ValidationException, BCSAPIException
	{
		store.resetStore (chain);
		store.cache (chain, 0);
		byte[] chainCode = new byte[32];
		new SecureRandom ().nextBytes (chainCode);
		wallet = api.createKeyGenerator (0x0, 0x05);
	}

	@Test
	public void checkGenesis () throws BCSAPIException
	{
		String genesisHash = chain.getGenesis ().getHash ();
		assertTrue (api.getBlock (genesisHash).getHash ().equals (genesisHash));
	}

	@Test
	public void send1Block () throws BCSAPIException, ValidationException
	{
		Block block = createBlock (chain.getGenesis ().getHash (), Transaction.createCoinbase (wallet.generateNextKey (), 50 * COIN, 1));
		mineBlock (block);
		blocks.put (1, block);

		final String hash = block.getHash ();

		final Semaphore ready = new Semaphore (0);

		TrunkListener listener = new TrunkListener ()
		{
			@Override
			public void trunkUpdate (List<Block> removed, List<Block> added)
			{
				Block got = added.get (0);
				got.computeHash ();
				assertTrue (got.getHash ().equals (hash));
				ready.release ();
			}
		};

		api.registerTrunkListener (listener);

		api.sendBlock (block);

		try
		{
			assertTrue (ready.tryAcquire (2, TimeUnit.SECONDS));
			api.removeTrunkListener (listener);
		}
		catch ( InterruptedException e )
		{
		}
	}

	private Block createBlock (String previous, Transaction coinbase)
	{
		Block block = new Block ();
		block.setCreateTime (System.currentTimeMillis () / 1000);
		block.setDifficultyTarget (chain.getGenesis ().getDifficultyTarget ());
		block.setPreviousHash (previous);
		block.setVersion (2);
		block.setNonce (0);
		block.setTransactions (new ArrayList<Transaction> ());
		block.getTransactions ().add (coinbase);
		return block;
	}

	private void mineBlock (Block b)
	{
		for ( int nonce = Integer.MIN_VALUE; nonce <= Integer.MAX_VALUE; ++nonce )
		{
			b.setNonce (nonce);
			b.computeHash ();
			BigInteger hashAsInteger = new Hash (b.getHash ()).toBigInteger ();
			if ( hashAsInteger.compareTo (Difficulty.getTarget (b.getDifficultyTarget ())) <= 0 )
			{
				break;
			}
		}
	}
}