### Yang2Swagger generator ###

Project is a YANG to Swagger ([OpenAPI Specification](https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md)) generator tool. OpenAPI describes and documents RESTful APIs. The Swagger definition generated with our tool is meant to be compliant with [RESTCONF specification](https://tools.ietf.org/html/rfc8040).
Having the definition you are able to build live documentation services, and generate client or server code using Swagger tools.

Our tool supports:

 * rpc - which are translated into POST operations
 * containers and lists - which are represented in RESTCONF data space URI and Swagger modules.
 * leafs and leaf lists - that are translated into Swagger models' attributes. Generator handles enums as well.
 * leafrefs - which are represented as model attributes with typesUsageTreeBuilder of the referred leafs
 * groupings - which, depending on strategy, are either unpacked into models that use these groupings or optimized model inheritance structures
 * augmentations - which, depending on strategy, are either unpacked into models that use these groupings or optimized model inheritance structures
 * YANG modules documentation - which is added to generated swagger API specification


In this project we use YANG parser from [OpenDaylight](https://www.opendaylight.org/) (ODL) yang-tools project. The generated Swagger specification is available as Java object or serialized either to YAML or JSON file.
The project contains a customized Jersey code-generator that can be use to generate server side scaffolding compatible with API specification.


Contact:

 * Bartosz Michalik bartosz.michalik@amartus.com
 * Christopher Murch cmurch@mrv.com

### How do I get set up? ###

Project is build with standard maven ```maven clean install```. As project depends on ODL components ```settings.xml``` file configuration might be required as explained https://wiki.opendaylight.org/view/GettingStarted:Development_Environment_Setup#Edit_your_.7E.2F.m2.2Fsettings.xml

The main component of the project is ```SwaggerGenerator``` which can be run standalone as well as can be configured as maven plugin. Examples of usage can be found in *examples* directory in the project.

The generated yaml.swagger file might be used in swagger editor or standalone code generator.
As [mustache](https://mustache.github.io/) templates used in original jersey code generator apply HTML escaping to ```@Path``` parameters
we have prepared our own version of the code generator. You might run it standalone or as maven plugin.

### Command-line Execution ###

You can easily run ```SwaggerGenerator``` from the command-line:
```
java -jar ~/.m2/repository/com/mrv/yangtools/swagger-generator-cli/1.0-SNAPSHOT/swagger-generator-cli-1.0-SNAPSHOT-executable.jar
Argument "module ..." is required
 module ...          : List of YANG module names to generate in swagger output
 -output file        : File to generate, containing the output - defaults to stdout
                       (default: )
 -yang-dir path      : Directory to search for YANG modules - defaults to current
                       directory (default: )
 -api-version string : The current version of your API (default: 1.0)
 -format enum        : The output format (options: YAML, JSON) (default: YAML)
 -content-type string: Content type the API generates / consumes (default: application/yang-data+json)
 -simplify-hierarchy : Use it to generate Swagger which with simplified inheritence model which can be used with standard code generators. (default: false)
 -use-namespaces     : Use namespaces in resource URI (default: false)
 -fullCrud           : If the flag is set to false path are generated for GET operations only. (default: true)
 -elements           : Define YANG elements to focus on. (default: DATA + RPC)
 -authentication     : Authentication definition (options: BASIC, NONE) (default: NONE)
```

For example:
```
java -jar ~/.m2/repository/com/mrv/yangtools/swagger-generator-cli/1.0-SNAPSHOT/swagger-generator-cli-1.0-SNAPSHOT-executable.jar \
 -yang-dir examples/build-standalone/src/main/resources \
 -output swagger.yaml \
 mef-services
```

#### Running the CLI in a Docker image ####

To run yang2swagger in a Docker image or to build and extract the Jar file, run
the following in this repo's directory:

```
docker build -t yang2swagger .
```

To use the docker image, gather all the YANG files into a single directory and
from inside that directory run the following:

```
docker run -it -v $(pwd):/usr/src/yang yang2swagger <arguments>
```

Where `<arguments>` can either be the arguments for the jar file, enclosed in
quotes, or `EXTRACT_JAR` if you need to extract the JAR file to the mounted
docker volume.

### Maven integration ###

You can generate ```yaml.swagger``` as part of resource generation step in your maven module. You can also choose the name by editing base-module and swagger-format additionalConfigs. To do so please add following plugin configuration to your project:

```
    <properties>
        <swaggerGeneratorPath>${project.basedir}/target/generated-sources/swagger</swaggerGeneratorPath>
        <swagger.version>1.5.9</swagger.version>
        <yangName>yang</yangName>
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
                <version>1.1.3</version>
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
		                          <outputBaseDir>${project.build.directory}/generated-sources/swagger-maven-api-gen</outputBaseDir>
		                          <resourceBaseDir>${project.basedir}/src/main/yang</resourceBaseDir>
		                          <additionalConfiguration>
		                              <api-version>${project.version}</api-version>
		                              <base-module>${yangName}</base-module>
		                              <swagger-format>yaml</swagger-format>
		                              <use-namespaces>true</use-namespaces>
		                              <path-format>odl</path-format>
		                          </additionalConfiguration>
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

                    <addCompileSourceRoot>false</addCompileSourceRoot>
                    <output>target/generated-sources/jaxRS</output>
                </configuration>
            </execution>
        </executions>
    </plugin>
```
Please note that in this case ```swagger-codegen-jaxrs``` has to be available in your local maven repository.
