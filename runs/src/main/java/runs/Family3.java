/*
    A blockchain transactions example with Hotmoka.
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

package runs;

import static io.hotmoka.beans.StorageTypes.INT;
import static io.hotmoka.helpers.Coin.panarea;
import static java.math.BigInteger.ONE;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;

import io.hotmoka.beans.ConstructorSignatures;
import io.hotmoka.beans.MethodSignatures;
import io.hotmoka.beans.StorageTypes;
import io.hotmoka.beans.StorageValues;
import io.hotmoka.beans.TransactionRequests;
import io.hotmoka.beans.api.requests.SignedTransactionRequest;
import io.hotmoka.beans.api.transactions.TransactionReference;
import io.hotmoka.beans.api.types.ClassType;
import io.hotmoka.beans.api.values.BigIntegerValue;
import io.hotmoka.beans.api.values.StorageReference;
import io.hotmoka.beans.api.values.StorageValue;
import io.hotmoka.beans.api.values.StringValue;
import io.hotmoka.crypto.SignatureAlgorithms;
import io.hotmoka.crypto.api.SignatureAlgorithm;
import io.hotmoka.crypto.api.Signer;
import io.hotmoka.helpers.GasHelpers;
import io.hotmoka.helpers.SignatureHelpers;
import io.hotmoka.node.Accounts;
import io.hotmoka.node.api.Node;
import io.hotmoka.node.remote.RemoteNodeConfigBuilders;
import io.hotmoka.node.remote.RemoteNodes;

/**
 * Run in the IDE or go inside this project and run
 * 
 * mvn clean package
 * java --module-path ../../hotmoka/modules/explicit/:../../hotmoka/modules/automatic:target/runs-0.0.1.jar -classpath ../../hotmoka/modules/unnamed"/*" --module runs/runs.Family3
 */
public class Family3 {

  // change this with your account's storage reference
  private final static String
    ADDRESS = "da5ceafc37c8e5fbf01c299b2ccd1deebcad79d1f37e8cd37bd7af0b3df6faf2#0";

  private final static ClassType PERSON = StorageTypes.classNamed("io.takamaka.family.Person");

  public static void main(String[] args) throws Exception {

    // the path of the user jar to install
    var familyPath = Paths.get("../family_exported/target/family_exported-0.0.1.jar");

    var config = RemoteNodeConfigBuilders.defaults()
    	.setURL("panarea.hotmoka.io")
    	.build();

    try (var node = RemoteNodes.of(config)) {
    	// we get a reference to where io-takamaka-code-1.0.1.jar has been stored
        TransactionReference takamakaCode = node.getTakamakaCode();

        // we get the signing algorithm to use for requests
        SignatureAlgorithm signature = SignatureAlgorithms.of(node.getNameOfSignatureAlgorithmForRequests());

        var account = StorageValues.reference(ADDRESS);
        KeyPair keys = loadKeys(node, account);

        // we create a signer that signs with the private key of our account
        Signer<SignedTransactionRequest<?>> signer = signature.getSigner
          (keys.getPrivate(), SignedTransactionRequest::toByteArrayWithoutSignature);

        // we get the nonce of our account: we use the account itself as caller and
        // an arbitrary nonce (ZERO in the code) since we are running
        // a @View method of the account
        BigInteger nonce = ((BigIntegerValue) node
          .runInstanceMethodCallTransaction(TransactionRequests.instanceViewMethodCall
            (account, // payer
            BigInteger.valueOf(50_000), // gas limit
            takamakaCode, // class path for the execution of the transaction
            MethodSignatures.NONCE, // method
            account))) // receiver of the method call
          .getValue();

        // we get the chain identifier of the network
        String chainId = ((StringValue) node
          .runInstanceMethodCallTransaction(TransactionRequests.instanceViewMethodCall
            (account, // payer
            BigInteger.valueOf(50_000), // gas limit
            takamakaCode, // class path for the execution of the transaction
            MethodSignatures.GET_CHAIN_ID, // method
            node.getManifest()))) // receiver of the method call
          .getValue();

        var gasHelper = GasHelpers.of(node);

        // we install family-0.0.1-SNAPSHOT.jar in the node: our account will pay
        TransactionReference family = node
          .addJarStoreTransaction(TransactionRequests.jarStore
            (signer, // an object that signs with the payer's private key
            account, // payer
            nonce, // payer's nonce: relevant since this is not a call to a @View method!
            chainId, // chain identifier: relevant since this is not a call to a @View method!
            BigInteger.valueOf(300_000), // gas limit: enough for this very small jar
            gasHelper.getSafeGasPrice(), // gas price: at least the current gas price of the network
            takamakaCode, // class path for the execution of the transaction
            Files.readAllBytes(familyPath), // bytes of the jar to install
            takamakaCode)); // dependencies of the jar that is being installed

        // we increase our copy of the nonce, ready for further
        // transactions having the account as payer
        nonce = nonce.add(ONE);

        // call the constructor of Person and store in albert the new object in blockchain
        StorageReference albert = node.addConstructorCallTransaction
          (TransactionRequests.constructorCall
            (signer, // an object that signs with the payer's private key
            account, // payer
            nonce, // payer's nonce: relevant since this is not a call to a @View method!
            chainId, // chain identifier: relevant since this is not a call to a @View method!
            BigInteger.valueOf(50_000), // gas limit: enough for a small object
            panarea(gasHelper.getSafeGasPrice()), // gas price, in panareas
            family, // class path for the execution of the transaction

            // constructor Person(String,int,int,int)
            ConstructorSignatures.of(PERSON, StorageTypes.STRING, INT, INT, INT),

            // actual arguments
            StorageValues.stringOf("Albert Einstein"), StorageValues.intOf(14),
            StorageValues.intOf(4), StorageValues.intOf(1879)
        ));

        // we increase our copy of the nonce, ready for further
        // transactions having the account as payer
        nonce = nonce.add(ONE);

        StorageValue s = node.addInstanceMethodCallTransaction
          (TransactionRequests.instanceMethodCall
            (signer, // an object that signs with the payer's private key
             account, // payer
             nonce, // payer's nonce: relevant since this is not a call to a @View method!
             chainId, // chain identifier: relevant since this is not a call to a @View method!
             BigInteger.valueOf(50_000), // gas limit: enough for a small object
             panarea(gasHelper.getSafeGasPrice()), // gas price, in panareas
             family, // class path for the execution of the transaction

      	     // method to call: String Person.toString()
             MethodSignatures.of(PERSON, "toString", StorageTypes.STRING),

        	 // receiver of the method to call
             albert
           ));

        // we increase our copy of the nonce, ready for further
        // transactions having the account as payer
        nonce = nonce.add(ONE);

        // print the result of the call
        System.out.println(s);
    }
  }

  private static KeyPair loadKeys(Node node, StorageReference account) throws Exception {
	  return Accounts.of(account, "..").keys("chocolate", SignatureHelpers.of(node).signatureAlgorithmFor(account));
  }
}