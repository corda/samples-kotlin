# Jar Signing Tutorial

This tutorial will demonstrate how to sign a contract jar with your own keystore.

A CorDapp will most likely have two jars, one contract jar and one workflow jar. Since all the data and transactional rules are defined in the contract, when transacting over the Corda Network, we will need to check the hashes of the contract jars. Hence, when speaking of signing a CorDapp, we are most likely talking about signing the contract jar.

The signing option is defined in the `build.gradle` file of the `/workflows` and `/contracts` folder.
```
cordapp {
    targetPlatformVersion corda_platform_version
    minimumPlatformVersion corda_platform_version
    workflow {
        name "4.8LTS Tutorial Flows"
        vendor "Corda Open Source"
        licence "Apache License, Version 2.0"
        versionId 1
    }
    signing {
        enabled false
    }
}
```
In this example, we disable the signing for the workflow jar. And, for the contract jar, we will add custom keystore to use for signing.
```
cordapp {
    targetPlatformVersion corda_platform_version
    minimumPlatformVersion corda_platform_version
    contract {
        name "4.8LTS Tutorial Contracts"
        vendor "Corda Open Source"
        licence "Apache License, Version 2.0"
        versionId 1
    }
    signing {
        enabled true
        options {
            Properties constants = new Properties()
            file("$projectDir/../gradle.properties").withInputStream { constants.load(it) }
            keystore getProperty('jar.sign.keystore')
            alias "cordapp-signer"
            storepass getProperty('jar.sign.password')
            keypass getProperty('jar.sign.password')
            storetype "PKCS12"
        }
    }
}
```
As you can see, we are importing in variables from the gradle.properties file in the root directory. They are the keystore path and the password for the keystore.
```
jar.sign.keystore=/Users/admin/corda/corda4/networkBootstrapper/certificates/jarSignKeystore.pkcs12
jar.sign.password = bootstrapper
```
Once you have edited all the above fields, you can simply run the gradle task build to execute the building and signing of the jar.
```
./gradlew build
```
You will have a signed jar with your keystore. 
