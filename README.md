# devRepo
Kindly add the aem-sdk version as required for cloud / change dependency based on AEM version. For example in cloud add following dependency:

 <dependency>
            <groupId>com.adobe.aem</groupId>
            <artifactId>aem-sdk-api</artifactId>
            <version>2024.11.18598.20241113T125352Z-241100</version>
            <scope>provided</scope>
 </dependency>

 Here version is respective local/ cloud aem version.

 **Build**

 To build repo use mvn clean install -PautoInstallPackage
