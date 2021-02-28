package io.takamaka.family;

import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.hotmoka.memory.MemoryBlockchain;
import io.hotmoka.memory.MemoryBlockchainConfig;
import io.hotmoka.nodes.ConsensusParams;
import io.hotmoka.nodes.Node;
import io.hotmoka.nodes.views.InitializedNode;
import io.hotmoka.nodes.views.NodeWithAccounts;
import io.hotmoka.nodes.views.NodeWithJars;

public class Main {
  public final static BigInteger GREEN_AMOUNT = BigInteger.valueOf(100_000_000);
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
      // first view: store io-takamaka-code-1.0.0.jar and create manifest and gamete
      InitializedNode initialized = InitializedNode.of
        (node, consensus, takamakaCodePath, GREEN_AMOUNT, RED_AMOUNT);

      // second view: store family-0.0.1-SNAPSHOT.jar: the gamete will pay for that
      NodeWithJars nodeWithJars = NodeWithJars.of
        (node, initialized.gamete(), initialized.keysOfGamete().getPrivate(),
        familyPath);

      // third view: create two accounts, the first with 100,000 units of green coin
      // and the second with 200,000 units of green coin
      NodeWithAccounts nodeWithAccounts = NodeWithAccounts.of
        (node, initialized.gamete(), initialized.keysOfGamete().getPrivate(),
        BigInteger.valueOf(100_000), BigInteger.valueOf(200_000));

      System.out.println("manifest: " + node.getManifest());
      System.out.println("family-0.0.1-SNAPSHOT.jar: " + nodeWithJars.jar(0));
      System.out.println("account #0: " + nodeWithAccounts.account(0) +
                         "\n  with private key " + nodeWithAccounts.privateKey(0));
      System.out.println("account #1: " + nodeWithAccounts.account(1) +
                         "\n  with private key " + nodeWithAccounts.privateKey(1));
    }
  }
}