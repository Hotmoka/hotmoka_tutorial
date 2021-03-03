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

package io.takamaka.tictactoe;

import static io.hotmoka.beans.Coin.panarea;
import static io.hotmoka.beans.types.BasicTypes.INT;
import static io.hotmoka.beans.types.BasicTypes.LONG;
import static java.math.BigInteger.ONE;
import static java.math.BigInteger.TWO;
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
import io.hotmoka.beans.signatures.NonVoidMethodSignature;
import io.hotmoka.beans.signatures.VoidMethodSignature;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.values.IntValue;
import io.hotmoka.beans.values.LongValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StringValue;
import io.hotmoka.crypto.SignatureAlgorithm;
import io.hotmoka.nodes.ConsensusParams;
import io.hotmoka.nodes.GasHelper;
import io.hotmoka.nodes.Node;
import io.hotmoka.nodes.views.InitializedNode;
import io.hotmoka.nodes.views.NodeWithAccounts;
import io.hotmoka.nodes.views.NodeWithJars;
import io.hotmoka.remote.RemoteNode;
import io.hotmoka.remote.RemoteNodeConfig;

/**
 * Go inside the hotmoka project, run
 * 
 * . set_variables.sh
 * 
 * then move inside this project and run
 * 
 * mvn clean package
 * java --module-path $explicit:$automatic:target/remote-0.0.1-SNAPSHOT.jar -classpath $unnamed"/*" --module blockchain/io.takamaka.tictactoe.Main
 */
public class Main {
  private final static BigInteger _1_000_000_000 = BigInteger.valueOf(1_000_000_000);
  public final static BigInteger GREEN_AMOUNT = _1_000_000_000.multiply(_1_000_000_000);
  public final static BigInteger RED_AMOUNT = ZERO;
  private final static BigInteger _50_000 = BigInteger.valueOf(50_000L);
  private final static ClassType TIC_TAC_TOE
    = new ClassType("io.takamaka.tictactoe.TicTacToe");

  // method void TicTacToe.play(long, int, int)
  private final static VoidMethodSignature TIC_TAC_TOE_PLAY
    = new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT);

  private final static IntValue _1 = new IntValue(1);
  private final static IntValue _2 = new IntValue(2);
  private final static IntValue _3 = new IntValue(3);
  private final static LongValue _0L = new LongValue(0L);
  private final static LongValue _100L = new LongValue(100L);

  public static void main(String[] args) throws Exception {
    ConsensusParams consensus = new ConsensusParams.Builder().build();
    Path takamakaCodePath = Paths.get("../../hotmoka/modules/explicit/io-takamaka-code-1.0.0.jar");
    Path tictactoePath = Paths.get("../tictactoe_improved/target/tictactoe_improved-0.0.1-SNAPSHOT.jar");

    RemoteNodeConfig config = new RemoteNodeConfig.Builder()
   	  //.setURL("ec2-99-80-8-84.eu-west-1.compute.amazonaws.com:8080")
      .setURL("localhost:8080")
   	  .build();

    try (Node node = RemoteNode.of(config)) {
      InitializedNode initialized = InitializedNode.of
        (node, consensus, takamakaCodePath, GREEN_AMOUNT, RED_AMOUNT);
      // install the jar of the TicTacToe contract in the node
      NodeWithJars nodeWithJars = NodeWithJars.of
        (node, initialized.gamete(), initialized.keysOfGamete().getPrivate(),
         tictactoePath);
      NodeWithAccounts nodeWithAccounts = NodeWithAccounts.of
        (node, initialized.gamete(), initialized.keysOfGamete().getPrivate(),
         _1_000_000_000, _1_000_000_000, _1_000_000_000);

      StorageReference creator = nodeWithAccounts.account(0);
      StorageReference player1 = nodeWithAccounts.account(1);
      StorageReference player2 = nodeWithAccounts.account(2);
	  SignatureAlgorithm<SignedTransactionRequest> signature
	    = node.getSignatureAlgorithmForRequests();
	  Signer signerForCreator = Signer.with(signature, nodeWithAccounts.privateKey(0));
      Signer signerForPlayer1 = Signer.with(signature, nodeWithAccounts.privateKey(1));
      Signer signerForPlayer2 = Signer.with(signature, nodeWithAccounts.privateKey(2));
      TransactionReference classpath = nodeWithJars.jar(0);
      GasHelper gasHelper = new GasHelper(node);

      // creation of the TicTacToe contract
      StorageReference ticTacToe = node
        .addConstructorCallTransaction(new ConstructorCallTransactionRequest(
          signerForCreator, // signer of the payer
          creator, // payer of the transaction
          ZERO, // nonce of the payer
          "", // chain identifier
          _50_000, // gas provided to the transaction
          panarea(gasHelper.getSafeGasPrice()), // gas price
          classpath,
          new ConstructorSignature(TIC_TAC_TOE))); /// TicTacToe()

      // player1 plays at (1,1) and bets 100
      node.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
        signerForPlayer1, // signer of the payer
        player1, // payer
        ZERO, // nonce of the payer
        "", // chain identifier
        _50_000, // gas provided to the transaction
        panarea(gasHelper.getSafeGasPrice()), // gas price
        classpath,

        // void TicTacToe.play(long, int, int)
        TIC_TAC_TOE_PLAY,

        ticTacToe, // receiver of the call
        _100L, _1, _1)); // actual parameters

      // player2 plays at (2,1) and bets 100
      node.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
        signerForPlayer2, // signer of the payer
        player2, // this account pays for the transaction
        ZERO, // nonce of the payer
        "", // chain identifier
        _50_000, // gas provided to the transaction
        panarea(gasHelper.getSafeGasPrice()), // gas price
        classpath,
        TIC_TAC_TOE_PLAY, // void TicTacToe.play(long, int, int)
        ticTacToe, // receiver of the call
        _100L, _2, _1)); // actual parameters

      // player1 plays at (1,2)
      node.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
        signerForPlayer1, // signer of the payer
        player1, // this account pays for the transaction
        ONE, // nonce of the payer
        "", // chain identifier
        _50_000, // gas provided to the transaction
        panarea(gasHelper.getSafeGasPrice()), // gas price
        classpath,
        TIC_TAC_TOE_PLAY, // method to call
        ticTacToe, // receiver of the call
        _0L, _1, _2)); // actual parameters

      // player2 plays at (2,2)
      node.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
        signerForPlayer2, // signer of the payer
        player2, // this account pays for the transaction
        ONE, // nonce of the payer
        "", // chain identifier
        _50_000, // gas provided to the transaction
        panarea(gasHelper.getSafeGasPrice()), // gas price
        classpath,
        TIC_TAC_TOE_PLAY, // method to call
        ticTacToe, // receiver of the call
        _0L, _2, _2)); // actual parameters

      // player1 plays at (1,3)
      node.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
        signerForPlayer1, // signer of the payer
        player1, // this account pays for the transaction
        TWO, // nonce of the payer
        "", // chain identifier
        _50_000, // gas provided to the transaction
        panarea(gasHelper.getSafeGasPrice()), // gas price
        classpath,
        TIC_TAC_TOE_PLAY, // method to call
        ticTacToe, // receiver of the call
        _0L, _1, _3)); // actual parameters

      // player1 calls toString() on the TicTacToe contract
      StringValue toString = (StringValue) node.addInstanceMethodCallTransaction
        (new InstanceMethodCallTransactionRequest(
          signerForPlayer1, // signer of the payer
          player1, // this account pays for the transaction
          BigInteger.valueOf(3), // nonce of the payer
          "", // chain identifier
          _50_000, // gas provided to the transaction
          panarea(gasHelper.getSafeGasPrice()), // gas price
          classpath,

          // method String TicTacToe.toString()
          new NonVoidMethodSignature(TIC_TAC_TOE, "toString", ClassType.STRING),

          ticTacToe)); // receiver of the call

      System.out.println(toString);

      // the game is over, but player2 continues playing and will get an exception
      node.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
        signerForPlayer2, // signer of the payer
        player2, // this account pays for the transaction
        TWO, // nonce of the payer
        "", // chain identifier
        _50_000, // gas provided to the transaction
        panarea(gasHelper.getSafeGasPrice()), // gas price
        classpath,
        TIC_TAC_TOE_PLAY, // void TicTacToe.play(long, int, int)
        ticTacToe, // receiver of the call
        _0L, _2, _3)); // actual parameters
    }
  }
}