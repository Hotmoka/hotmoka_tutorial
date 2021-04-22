/*
    A blockchain startup and transactions example with Hotmoka.
    Copyright (C) 2021 Fausto Spoto (fausto.spoto@gmail.com)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/

package io.takamaka.family;

import static java.math.BigInteger.ONE;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.hotmoka.beans.SignatureAlgorithm;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.requests.JarStoreTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest.Signer;
import io.hotmoka.beans.signatures.CodeSignature;
import io.hotmoka.beans.values.BigIntegerValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.crypto.SignatureAlgorithmForTransactionRequests;
import io.hotmoka.memory.MemoryBlockchain;
import io.hotmoka.memory.MemoryBlockchainConfig;
import io.hotmoka.nodes.ConsensusParams;
import io.hotmoka.nodes.GasHelper;
import io.hotmoka.nodes.Node;
import io.hotmoka.views.InitializedNode;

/**
 * Go inside the hotmoka project, run
 * 
 * . set_variables.sh
 * 
 * then move inside this project and run
 * 
 * mvn clean package
 * java --module-path $explicit:$automatic:target/blockchain2-0.0.1-SNAPSHOT.jar -classpath $unnamed"/*" --module blockchain/io.takamaka.family.Main
 */
public class Main {
  public final static BigInteger GREEN_AMOUNT = BigInteger.valueOf(1_000_000_000);
  public final static BigInteger RED_AMOUNT = BigInteger.ZERO;

  public static void main(String[] args) throws Exception {
    MemoryBlockchainConfig config = new MemoryBlockchainConfig.Builder().build();
    ConsensusParams consensus = new ConsensusParams.Builder().build();

    // the path of the packaged runtime Takamaka classes
    Path takamakaCodePath = Paths.get
      ("../../hotmoka/modules/explicit/io-takamaka-code-1.0.0.jar");

    // the path of the user jar to install
    Path familyPath = Paths.get("../family/target/family-0.0.1-SNAPSHOT.jar");

    try (Node node = MemoryBlockchain.init(config, consensus)) {
      // we store io-takamaka-code-1.0.0.jar and create the manifest and the gamete
      InitializedNode initialized = InitializedNode.of
        (node, consensus, takamakaCodePath, GREEN_AMOUNT, RED_AMOUNT);

      // we get a reference to where io-takamaka-code-1.0.0.jar has been stored
      TransactionReference takamakaCode = node.getTakamakaCode();

      // we get a reference to the gamete
      StorageReference gamete = initialized.gamete();

      // we get the signing algorithm to use for requests
      SignatureAlgorithm<SignedTransactionRequest> signature
        = SignatureAlgorithmForTransactionRequests.mk(node.getNameOfSignatureAlgorithmForRequests());

      // we create a signer that signs with the private key of the gamete
      Signer signerOnBehalfOfGamete = Signer.with
        (signature, initialized.keysOfGamete().getPrivate());

      // we get the nonce of the gamete: we use the gamete as caller and
      // an arbitrary nonce (ZERO in the code) since we are running
      // a @View method of the gamete
      BigInteger nonce = ((BigIntegerValue) node
        .runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
          (gamete, // payer
          BigInteger.valueOf(50_000), // gas limit
          takamakaCode, // class path for the execution of the transaction
          CodeSignature.NONCE, // method
          gamete))) // receiver of the method call
        .value;

      GasHelper gasHelper = new GasHelper(node);

      // we install family-0.0.1-SNAPSHOT.jar in blockchain: the gamete will pay
      TransactionReference family = node
        .addJarStoreTransaction(new JarStoreTransactionRequest
          (signerOnBehalfOfGamete, // an object that signs with the payer's private key
          gamete, // payer
          nonce, // payer's nonce: relevant since this is not a call to a @View method!
          "", // chain identifier: relevant since this is not a call to a @View method!
          BigInteger.valueOf(300_000), // gas limit: enough for this very small jar
          gasHelper.getSafeGasPrice(), // gas price: at least the current gas price of the network
          takamakaCode, // class path for the execution of the transaction
          Files.readAllBytes(familyPath), // bytes of the jar to install
          takamakaCode)); // dependencies of the jar that is being installed

      System.out.println("manifest: " + node.getManifest());
      System.out.println("gamete: " + gamete);
      System.out.println("nonce of gamete: " + nonce);
      System.out.println("family-0.0.1-SNAPSHOT.jar: " + family);

      // we increase to nonce, ready for further transactions having the gamete as payer
      nonce = nonce.add(ONE);
    }
  }
}