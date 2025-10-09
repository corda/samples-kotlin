cd /Users/szymon.sztuka/workspace/enterprise

#build
./gradlew solana-aggregator:admin-cli:build
cd solana-aggregator/notary-program
anchor build

#start
solana-test-validator --reset --ledger ../admin-cli/build/test-ledger --bpf-program target/deploy/corda_notary-keypair.json target/deploy/corda_notary.so

#new terminal
cd /Users/szymon.sztuka/workspace/enterprise
solana airdrop -k solana-aggregator/notary-program/dev-keys/DevAD5S5AFhTTCmrD8Jg58bDhbZabSzth7Bu6rG4HFYo.json --commitment confirmed 10
solana airdrop -k solana-aggregator/notary-program/dev-keys/DevNMdtQW3Q4ybKQvxgwpJj84h5mb7JE218qTpZQnoA3.json --commitment confirmed 10
cd solana-aggregator/admin-cli
java -jar build/libs/admin-cli-4.14-SNAPSHOT.jar initialize -u http://localhost:8899 -v
java -jar build/libs/admin-cli-4.14-SNAPSHOT.jar create-network -u http://localhost:8899 -v
java -jar build/libs/admin-cli-4.14-SNAPSHOT.jar authorize --address DevNMdtQW3Q4ybKQvxgwpJj84h5mb7JE218qTpZQnoA3 --network 0 -u http://localhost:8899 -v
