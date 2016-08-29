### Yang2Swagger generator ###

Project is a YANG to swagger generator tool. The YANG parser is build on top of OpenDaylight (ODL) yang-tools project. 
Yang2Swagger generator is meant to be compliant with [RESTCONF specification  ](https://tools.ietf.org/html/draft-ietf-netconf-restconf-14)


Contact:

 * Bartosz Michalik bartosz.michalik@amartus.com
 * Christopher Murch cmurch@mrv.com 

### How do I get set up? ###

Project is build with standard maven ```maven clean install```. As project depends on ODL components ```settings.xml``` file configuration might be required as explained https://wiki.opendaylight.org/view/GettingStarted:Development_Environment_Setup#Edit_your_.7E.2F.m2.2Fsettings.xml

The main component of the project is ```SwaggerGenerator``` which can be run standalone as well as can be configured as maven plugin. Examples of usage can be found in *examples* directory in the project.

The generated yaml.swagger file might be used in swagger editor or standalone code generator. 
As [mustache](https://mustache.github.io/) templates used in original jersey code generator apply HTML escaping to ```@Path``` paramenters 
we have prepared our own version of the code generator. You might run it standalone or integrate into your maven module.

### Command-line Execution ###

You can easily run ```SwaggerGenerator``` from the command-line:
```
java -jar ~/.m2/repository/com/mrv/yangtools/swagger-generator-cli/1.0-SNAPSHOT/swagger-generator-cli-1.0-SNAPSHOT-executable.jar
Argument "module ..." is required
 module ...     : List of YANG module names to generate in swagger output
 -output file   : File to generate, containing the output - defaults to stdout
                  (default: )
 -yang-dir path : Directory to search for YANG modules - defaults to current
                  directory (default: )
```

For example:
```
java -jar ~/.m2/repository/com/mrv/yangtools/swagger-generator-cli/1.0-SNAPSHOT/swagger-generator-cli-1.0-SNAPSHOT-executable.jar \
 -yang-dir examples/build-standalone/src/main/resources \
 -output swagger.yaml \
 mef-services
```

### Maven integration ###

You can generate ```yaml.swagger``` as part of resource generation step in your maven module.
To do so please add following plugin configuration to your project:

```
    <properties>
        <swaggerGeneratorPath>${project.basedir}/target/generated-sources/swagger</swaggerGeneratorPath>
        <swagger.version>1.5.9</swagger.version>
    </properties>

    ...

   <plugin>
        <groupId>org.opendaylight.yangtools</groupId>
        <artifactId>yang-maven-plugin</artifactId>
        <version>${yangtools.version}</version>
        <dependencies>
            <dependency>
                <groupId>com.mrv.yangtools</groupId>
                <artifactId>swagger-maven-plugin</artifactId>
                <version>1.0-SNAPSHOT</version>
                <type>jar</type>
            </dependency>
        </dependencies>
        <executions>
            <execution>
                <goals>
                    <goal>generate-sources</goal>
                </goals>
                <configuration>
                    <codeGenerators>
                        <generator>
                            <codeGeneratorClass>com.mrv.yangtools.maven.gen.swagger.MavenSwaggerGenerator</codeGeneratorClass>
                            <outputBaseDir>${swaggerGeneratorPath}</outputBaseDir>
                        </generator>
                    </codeGenerators>
                    <inspectDependencies>true</inspectDependencies>
                </configuration>
            </execution>
        </executions>
    </plugin>
```

The swagger specification generator allows for parametrization:
* ```-Dgenerator-mime=xml,json``` to specify mime formats supported in your system
* ```-Dgenerator-elements=DATA,RPC``` to define which elements of yang modules should be consider during swagger definition generation

Please note that ```swagger-maven-plugin``` has to be available in your local maven repository.

You might also consider to plug-in code generator into your model definition:

```
    <plugin>
        <groupId>io.swagger</groupId>
        <artifactId>swagger-codegen-maven-plugin</artifactId>
        <version>2.2.0</version>
        <dependencies>
            <dependency>
                <groupId>com.mrv.yangtools</groupId>
                <artifactId>swagger-codegen-jaxrs</artifactId>
                <version>1.0-SNAPSHOT</version>
                <!--<type>jar</type>-->
            </dependency>
        </dependencies>
        <executions>
            <execution>
                <goals>
                    <goal>generate</goal>
                </goals>
                <configuration>
                    <!-- specify the swagger yaml -->
                    <inputSpec>${swaggerGeneratorPath}/yang.swagger</inputSpec>
    
                    <!-- target to generate -->
                    <language>jaxrs-mrv</language>
    
                    <!-- pass any necessary config options -->
                    <configOptions>
                        <dateLibrary>java8</dateLibrary>
                    </configOptions>
    
                    <!-- override the default library to jersey2 -->
                    <library>jersey2</library>
                    <addCompileSourceRoot>false</addCompileSourceRoot>
                    <output>target/generated-sources/jaxRS</output>
                </configuration>
            </execution>
        </executions>
    </plugin>
```
Please note that in this case ```swagger-codegen-jaxrs``` has to be available in your local maven repository.