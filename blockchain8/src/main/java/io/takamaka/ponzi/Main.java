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

package io.takamaka.ponzi;

import static io.hotmoka.beans.Coin.panarea;
import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.ConstructorCallTransactionRequest;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest.Signer;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.signatures.VoidMethodSignature;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.values.BigIntegerValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.crypto.SignatureAlgorithm;
import io.hotmoka.memory.MemoryBlockchain;
import io.hotmoka.memory.MemoryBlockchainConfig;
import io.hotmoka.nodes.ConsensusParams;
import io.hotmoka.nodes.GasHelper;
import io.hotmoka.nodes.Node;
import io.hotmoka.nodes.views.InitializedNode;
import io.hotmoka.nodes.views.NodeWithAccounts;
import io.hotmoka.nodes.views.NodeWithJars;

/**
 * Go inside the hotmoka project, run
 * 
 * . set_variables.sh
 * 
 * then move inside this project and run
 * 
 * mvn clean package
 * java --module-path $explicit:$automatic:target/blockchain8-0.0.1-SNAPSHOT.jar -classpath $unnamed"/*" --module blockchain/io.takamaka.ponzi.Main
 */
public class Main {
  private final static BigInteger _1_000_000_000 = BigInteger.valueOf(1_000_000_000);
  public final static BigInteger GREEN_AMOUNT = _1_000_000_000.multiply(_1_000_000_000);
  public final static BigInteger RED_AMOUNT = ZERO;
  private final static BigInteger _100_000 = BigInteger.valueOf(100_000);
  private final static ClassType GRADUAL_PONZI
    = new ClassType("io.takamaka.ponzi.GradualPonzi");
  private final static VoidMethodSignature gradualPonziInvest
    = new VoidMethodSignature(GRADUAL_PONZI, "invest", ClassType.BIG_INTEGER);

  public static void main(String[] args) throws Exception {
    MemoryBlockchainConfig config = new MemoryBlockchainConfig.Builder().build();
    ConsensusParams consensus = new ConsensusParams.Builder().build();
    Path takamakaCodePath = Paths.get("../../hotmoka/modules/explicit/io-takamaka-code-1.0.0.jar");
    Path ponziPath = Paths.get("../ponzi_gradual/target/ponzi_gradual-0.0.1-SNAPSHOT.jar");

    try (Node node = MemoryBlockchain.init(config, consensus)) {
      InitializedNode initialized = InitializedNode.of
        (node, consensus, takamakaCodePath, GREEN_AMOUNT, RED_AMOUNT);
      // install the jar of the Ponzi contracts in the node
      NodeWithJars nodeWithJars = NodeWithJars.of
        (node, initialized.gamete(), initialized.keysOfGamete().getPrivate(),
         ponziPath);
      NodeWithAccounts nodeWithAccounts = NodeWithAccounts.of
        (node, initialized.gamete(), initialized.keysOfGamete().getPrivate(),
        _1_000_000_000, _1_000_000_000, _1_000_000_000);

      StorageReference player1 = nodeWithAccounts.account(0);
      StorageReference player2 = nodeWithAccounts.account(1);
      StorageReference player3 = nodeWithAccounts.account(2);
      SignatureAlgorithm<SignedTransactionRequest> signature
        = node.getSignatureAlgorithmForRequests();
      Signer signerForPlayer1 = Signer.with(signature, nodeWithAccounts.privateKey(0));
      Signer signerForPlayer2 = Signer.with(signature, nodeWithAccounts.privateKey(1));
      Signer signerForPlayer3 = Signer.with(signature, nodeWithAccounts.privateKey(2));
      TransactionReference classpath = nodeWithJars.jar(0);
      GasHelper gasHelper = new GasHelper(node);

      // create the Ponzi contract: player1 becomes its first investor
      StorageReference gradualPonzi = node.addConstructorCallTransaction
        (new ConstructorCallTransactionRequest(
          signerForPlayer1,
          player1, // player1 pays for the transaction
          ZERO, // nonce for player1
          "", // chain identifier
          _100_000, // gas provided to the transaction
          panarea(gasHelper.getSafeGasPrice()), // gas price
          classpath,
          new ConstructorSignature(GRADUAL_PONZI))); /// GradualPonzi()

      // let player2 invest 1200
      node.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
        signerForPlayer2,
        player2, // player2 pays for the transaction
        ZERO, // nonce for player2
        "", // chain identifier
        _100_000, // gas provided to the transaction
        panarea(gasHelper.getSafeGasPrice()), // gas price
        classpath,
        gradualPonziInvest, // method void GradualPonzi.invest(BigInteger)
        gradualPonzi, // receiver of invest()
        new BigIntegerValue(BigInteger.valueOf(1_200)))); // the investment

      // let player3 invest 1500
      node.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
        signerForPlayer3,
        player3, // player3 pays for the transaction
        ZERO, // nonce of player3
        "", // chain identifier
        _100_000, // gas provided to the transaction
        panarea(gasHelper.getSafeGasPrice()), // gas price
        classpath,
        gradualPonziInvest, // method void GradualPonzi.invest(BigInteger)
        gradualPonzi, // receiver of invest()
        new BigIntegerValue(BigInteger.valueOf(1_500)))); // the investment

      // let player1 invest 900, but it is too little and it runs into an exception
      node.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
        signerForPlayer1,
        player1, // player1 pays for the transaction
     	ONE, // nonce of player1
        "", // chain identifier
        _100_000, // gas provided to the transaction
        panarea(gasHelper.getSafeGasPrice()), // gas price
        classpath,
        gradualPonziInvest, // method void GradualPonzi.invest(BigInteger)
        gradualPonzi, // receiver of invest()
        new BigIntegerValue(BigInteger.valueOf(900)))); // the investment
    }
  }
}