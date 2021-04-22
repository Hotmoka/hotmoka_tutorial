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

package io.takamaka.signatures;

import static io.hotmoka.beans.Coin.panarea;
import static java.math.BigInteger.ZERO;

import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.util.Base64;

import io.hotmoka.beans.requests.ConstructorCallTransactionRequest;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest.Signer;
import io.hotmoka.beans.requests.StaticMethodCallTransactionRequest;
import io.hotmoka.beans.signatures.CodeSignature;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.signatures.NonVoidMethodSignature;
import io.hotmoka.beans.types.BasicTypes;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.values.BigIntegerValue;
import io.hotmoka.beans.values.IntValue;
import io.hotmoka.beans.values.LongValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StringValue;
import io.hotmoka.crypto.SignatureAlgorithmForTransactionRequests;
import io.hotmoka.beans.SignatureAlgorithm;
import io.hotmoka.nodes.ConsensusParams;
import io.hotmoka.nodes.GasHelper;
import io.hotmoka.nodes.Node;
import io.hotmoka.views.InitializedNode;
import io.hotmoka.tendermint.TendermintBlockchain;
import io.hotmoka.tendermint.TendermintBlockchainConfig;

/**
 * Go inside the hotmoka project, run
 * 
 * . set_variables.sh
 * 
 * then move inside this project and run
 * 
 * mvn clean package
 * java --module-path $explicit:$automatic:target/signatures-0.0.1-SNAPSHOT.jar -classpath $unnamed"/*" --module blockchain/io.takamaka.signatures.Main
 */
public class Main {
  private final static BigInteger _1_000_000_000 = BigInteger.valueOf(1_000_000_000);
  public final static BigInteger GREEN_AMOUNT = _1_000_000_000.multiply(_1_000_000_000);
  public final static BigInteger RED_AMOUNT = ZERO;

  public static void main(String[] args) throws Exception {
    // the blockhain uses ed25519 as default
    TendermintBlockchainConfig config = new TendermintBlockchainConfig.Builder().build();
    ConsensusParams consensus = new ConsensusParams.Builder().build();

    // the path of the packaged runtime Takamaka classes
    Path takamakaCodePath = Paths.get
      ("../../hotmoka/modules/explicit/io-takamaka-code-1.0.0.jar");

    try (Node node = TendermintBlockchain.init(config, consensus)) {
      // store io-takamaka-code-1.0.0.jar and create manifest and gamete
      InitializedNode initialized = InitializedNode.of
        (node, consensus, takamakaCodePath, GREEN_AMOUNT, RED_AMOUNT);

      // get the algorithm for qtesla-p-I signatures
      SignatureAlgorithm<SignedTransactionRequest> qtesla
        = SignatureAlgorithmForTransactionRequests.qtesla1();

      // create a qtesla keypair
      KeyPair qteslaKeyPair = qtesla.getKeyPair();

      // transform the public qtesla key into a Base64-encoded string
      StringValue qteslaPublicKey = new StringValue
        (Base64.getEncoder().encodeToString(qteslaKeyPair.getPublic().getEncoded()));

      GasHelper gasHelper = new GasHelper(node);

      // we get the nonce of the gamete: we use the gamete as caller and
      // an arbitrary nonce (ZERO in the code) since we are running
      // a @View method of the gamete
      BigInteger nonce = ((BigIntegerValue) node
        .runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
          (initialized.gamete(), // payer
          BigInteger.valueOf(20_000), // gas limit
          node.getTakamakaCode(), // class path for the execution of the transaction
          CodeSignature.NONCE, // method
          initialized.gamete()))) // receiver of the method call
        .value;

      // create an account with 100,000,000 units of coin:
      // it will use the qtesla-p-I algorithm for signing transactions,
      // regardless of the default used for the blockchain
      StorageReference qteslaAccount = node.addConstructorCallTransaction
       (new ConstructorCallTransactionRequest
        // signed with the default algorithm
        (Signer.with(SignatureAlgorithmForTransactionRequests.mk(node.getNameOfSignatureAlgorithmForRequests()),
            initialized.keysOfGamete()),
         initialized.gamete(), // the gamete is the caller
         nonce, // nonce
         "", // chain id
         BigInteger.valueOf(10_000_000), // gas amount: qtesla keys are big!
         panarea(gasHelper.getSafeGasPrice()), // gas cost
         initialized.getTakamakaCode(), // classpath
         // call the constructor of
         // ExternallyOwnedAccountQTESLA1(int amount, String publicKey)
         new ConstructorSignature
           ("io.takamaka.code.lang.ExternallyOwnedAccountQTESLA1",
            BasicTypes.INT, ClassType.STRING),
         new IntValue(100_000_000), // the amount
         qteslaPublicKey)); // the qtesla public key of the account

      // use the qtesla account to call the following static method
      // of the Takamaka library:
      // BigInteger io.takamaka.code.lang.Coin.panarea(long)
      NonVoidMethodSignature callee = new NonVoidMethodSignature
        ("io.takamaka.code.lang.Coin", "panarea",
         ClassType.BIG_INTEGER, BasicTypes.LONG);

      // the next transaction will be signed with the qtesla signature since this is
      // what the qtesla account uses, regardless of the default algorithm of the node
      BigIntegerValue result = (BigIntegerValue) node.addStaticMethodCallTransaction
       (new StaticMethodCallTransactionRequest
        (Signer.with(qtesla, qteslaKeyPair), // signed with the qtesla algorithm
         qteslaAccount, // the caller is the qtesla account
         ZERO, // the nonce of the gtesla account
         "", // the chain id
         BigInteger.valueOf(200_000), // gas amount: qtesla signatures are big!
         panarea(gasHelper.getSafeGasPrice()), // gas cost
         initialized.getTakamakaCode(), // classpath
         callee, // the static method to class
         new LongValue(1973))); // actual argument

      System.out.println("result = " + result);
    }
  }
}