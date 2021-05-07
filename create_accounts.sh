#!/bin/bash

java --module-path ../hotmoka/modules/explicit:../hotmoka/modules/automatic --class-path "../hotmoka/modules/unnamed/*" --module io.hotmoka.tools/io.hotmoka.tools.Moka create-account 10000000000000000 --payer faucet --url ec2-54-194-239-91.eu-west-1.compute.amazonaws.com:8080 --non-interactive | grep "A new account"

java --module-path ../hotmoka/modules/explicit:../hotmoka/modules/automatic --class-path "../hotmoka/modules/unnamed/*" --module io.hotmoka.tools/io.hotmoka.tools.Moka create-account 10000000000000000 --payer faucet --url ec2-54-194-239-91.eu-west-1.compute.amazonaws.com:8080 --non-interactive | grep "A new account"

java --module-path ../hotmoka/modules/explicit:../hotmoka/modules/automatic --class-path "../hotmoka/modules/unnamed/*" --module io.hotmoka.tools/io.hotmoka.tools.Moka create-account 10000000000000000 --payer faucet --url ec2-54-194-239-91.eu-west-1.compute.amazonaws.com:8080 --non-interactive | grep "A new account"

