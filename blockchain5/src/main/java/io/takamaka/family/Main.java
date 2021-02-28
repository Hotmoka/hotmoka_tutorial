package io.takamaka.family;

import static io.hotmoka.beans.Coin.panarea;
import static io.hotmoka.beans.types.BasicTypes.INT;
import static java.math.BigInteger.ZERO;

import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.hotmoka.beans.requests.ConstructorCallTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest.Signer;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.values.IntValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StringValue;
import io.hotmoka.memory.MemoryBlockchain;
import io.hotmoka.memory.MemoryBlockchainConfig;
import io.hotmoka.nodes.ConsensusParams;
import io.hotmoka.nodes.GasHelper;
import io.hotmoka.nodes.Node;
import io.hotmoka.nodes.views.InitializedNode;
import io.hotmoka.nodes.views.NodeWithAccounts;
import io.hotmoka.nodes.views.NodeWithJars;


public class Main {
  public final static BigInteger GREEN_AMOUNT = BigInteger.valueOf(100_000_000);
  public final static BigInteger RED_AMOUNT = BigInteger.ZERO;
  private final static ClassType PERSON = new ClassType("io.takamaka.family.Person");

  public static void main(String[] args) throws Exception {
    MemoryBlockchainConfig config = new MemoryBlockchainConfig.Builder().build();
    ConsensusParams consensus = new ConsensusParams.Builder().build();

    // the path of the packaged runtime Takamaka classes
    Path takamakaCodePath = Paths.get
      ("../../hotmoka/modules/explicit/io-takamaka-code-1.0.0.jar");

    // the path of the user jar to install
    Path familyPath = Paths.get("../family_correct/target/family-0.0.1-SNAPSHOT.jar");

    try (Node node = MemoryBlockchain.init(config, consensus)) {
      // first view: store io-takamaka-code-1.0.0.jar and create manifest and gamete
      InitializedNode initialized = InitializedNode.of
        (node, consensus, takamakaCodePath, GREEN_AMOUNT, RED_AMOUNT);

      // second view: store family-0.0.1-SNAPSHOT.jar: the gamete will pay for that
      NodeWithJars nodeWithJars = NodeWithJars.of
        (node, initialized.gamete(), initialized.keysOfGamete().getPrivate(),
        familyPath);

      // third view: create two accounts, the first with 10,000,000 units of green coin
      // and the second with 20,000,000 units of green coin
      NodeWithAccounts nodeWithAccounts = NodeWithAccounts.of
        (node, initialized.gamete(), initialized.keysOfGamete().getPrivate(),
        BigInteger.valueOf(10_000_000), BigInteger.valueOf(20_000_000));

      GasHelper gasHelper = new GasHelper(node);

      // call the constructor of Person and store in albert the new object in blockchain
      StorageReference albert = node.addConstructorCallTransaction
        (new ConstructorCallTransactionRequest(

          // signer on behalf of the first account
          Signer.with(node.getSignatureAlgorithmForRequests(),
            nodeWithAccounts.privateKey(0)),

          // the first account pays for the transaction
          nodeWithAccounts.account(0),

          // nonce: we know this is the first transaction
          // with nodeWithAccounts.account(0)
          ZERO,

          // chain identifier
          "",

          // gas provided to the transaction
          BigInteger.valueOf(10_000),

          // gas price
          panarea(gasHelper.getSafeGasPrice()),

          // reference to family-0.0.1-SNAPSHOT.jar
          // and its dependency io-takamaka-code-1.0.0.jar
          nodeWithJars.jar(0),

          // constructor Person(String,int,int,int)
          new ConstructorSignature(PERSON, ClassType.STRING, INT, INT, INT),

          // actual arguments
          new StringValue("Albert Einstein"), new IntValue(14),
          new IntValue(4), new IntValue(1879)
      ));

      System.out.println("manifest: " + node.getManifest());
      System.out.println("family-0.0.1-SNAPSHOT.jar: " + nodeWithJars.jar(0));
      System.out.println("account #0: " + nodeWithAccounts.account(0) +
                         "\n  with private key " + nodeWithAccounts.privateKey(0));
      System.out.println("account #1: " + nodeWithAccounts.account(1) +
                         "\n  with private key " + nodeWithAccounts.privateKey(1));
    }
  }
}