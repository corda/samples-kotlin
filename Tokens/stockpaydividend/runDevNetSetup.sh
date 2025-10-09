#Once off
#cd /bridging-flows/src/main/resources
#solana-keygen grind --starts-with Dev:1 --no-bip39-passphrase

java -jar build/libs/admin-cli-4.14-SNAPSHOT.jar create-network -u http://localhost:8899 -v
java -jar build/libs/admin-cli-4.14-SNAPSHOT.jar authorize --address Dev7chG99tLCAny3PNYmBdyhaKEVcZnSTp3p1mKVb5m5 --network 1 -u http://localhost:8899
java -jar build/libs/admin-cli-4.14-SNAPSHOT.jar list-notaries -u http://localhost:8899