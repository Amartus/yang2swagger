### Yang2Swagger generator ###

Project is a YANG to Swagger ([OpenAPI Specification](https://github.com/OAI/OpenAPI-Specification/blob/master/versions/2.0.md)) generator tool. OpenAPI describes and documents RESTful APIs. The Swagger definition generated with our tool is meant to be compliant with [RESTCONF specification](https://tools.ietf.org/html/rfc8040).
Having the definition you are able to build live documentation services, and generate client or server code using Swagger tools.

Our tool supports:

 * rpc - which are translated into POST operations
 * actions - which are translated into POST operations on instance-specific RESTCONF operation paths, analogously to rpc
 * containers and lists - which are represented in RESTCONF data space URI and Swagger modules.
 * leafs and leaf lists - that are translated into Swagger models' attributes. Generator handles enums as well.
 * leafrefs - which are represented as model attributes with typesUsageTreeBuilder of the referred leafs
 * groupings - which, depending on strategy, are either unpacked into models that use these groupings or optimized model inheritance structures
 * augmentations - which, depending on strategy, are either unpacked into models that use these groupings or optimized model inheritance structures
 * YANG modules documentation - which is added to generated swagger API specification
 * mount-point - for which types can be provided during swagger generation


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
java -jar swagger-generator-cli-<<version>>-executable.jar
Argument "module ..." is required
module ...                             : List of YANG module names to generate
                                         in swagger output
 -api-version file                     : Version of api generated - default 1.0
                                         (default: 1.0)
 -authentication [BASIC | NONE]        : Authentication definition (default:
                                         NONE)
 -content-type VAL                     : Content type the API generates /
                                         consumes - default application/yang-dat
                                         a+json (default: application/yang-data+
                                         json)
 -elements [DATA | RPC | DATA_AND_RPC] : Define YANG elements to focus on.
                                         Defaul DATA + RPC (default:
                                         DATA_AND_RPC)
 -format [YAML | JSON]                 : Output format of generated file -
                                         defaults to yaml with options of json
                                         or yaml (default: YAML)
 -fullCrud                             : If the flag is set to false path are
                                         generated for GET operations only.
                                         Default true (default: true)
 -output file                          : File to generate, containing the
                                         output - defaults to stdout (default: )
 -reuse-groupings                      : Use it to generate Swagger which
                                         attempts to reuse structurally
                                         identical grouping types. Default
                                         false (default: false)
 -simplify-hierarchy                   : Use it to generate Swagger which with
                                         simplified inheritence model which can
                                         be used with standard code generators.
                                         Default false (default: false)
 -split-config-state                   : Generate separate '<name>Config'
                                         request-body definitions for write
                                         operations (POST/PUT/PATCH) that omit
                                         'config false' state nodes. Default
                                         false (default: false)
 -use-namespaces                       : Use namespaces in resource URI
                                         (default: false)
 -use-odl-path-format                  : Select to use bierman-02 RESTCONF path
                                         format. Default false (default: false)
 -yang-dir path                        : Directory to search for YANG modules -
                                         defaults to current directory.
                                         Multiple dirs might be separated by
                                         system path separator (default: )
 -mount-point-mappings                 : Mount-point mappings as a JSON string.  
                                         Assigns mount-point labels (from YANG
                                         files) to lists of content types
                                         (can be passed as a specific type or
                                         a whole module) that should be used 
                                         when generating Swagger definitions. 
                                         Expected format:
                                         '{"mount-label": ["module:grouping", ...]}'
                                         or
                                         '{"mount-label": ["module", ...]}'
                                         Example:
                                         '{"mount-point-name": [
                                         "mounted-module-name:top-level-container",
                                         "mounted-module-name:top-level-container"
                                         ]}'
 -mount-point-mappings-json-file file  : JSON file containing mount-point
                                         mappings in the same format as
                                         -mount-point-mappings. Useful when the
                                         mapping is too large to pass directly
                                         on the command line.
```

For example:
```
java -jar swagger-generator-cli-<<version>>-executable.jar \
 -yang-dir examples/build-standalone/src/main/resources \
 -output swagger.yaml \
 mef-services
```

#### Separating config and state in write request bodies ####

By default a Swagger model definition is reused for every HTTP operation on a
given resource. That means the request body of write operations (`POST`, `PUT`,
`PATCH`) also contains YANG `config false` (state-only) nodes, which a
RESTCONF-compliant server rejects because state data is not writable
([issue #74](https://github.com/Amartus/yang2swagger/issues/74)).

The `-split-config-state` flag (programmatic equivalent:
`SwaggerGenerator.splitConfigState(true)`) opts into separate request-body
definitions:

* For every model whose YANG subtree contains at least one `config false`
  descendant, an additional `<name>Config` definition is generated with the
  state nodes pruned out (recursively, at every nesting level).
* Write operations (`POST`, `PUT`, `PATCH`) reference the `<name>Config`
  definition for their request body. The `PATCH` `200` response keeps the full
  model, since the patched resource still reports state.
* `GET` operations and `DELETE` are unchanged — read responses keep the full
  model containing both config and state.
* Models with no `config false` nodes get no extra definition; their write
  operations keep referencing the original model.
* As a secondary safeguard, `config false` leaves are also emitted with the
  OpenAPI `readOnly: true` hint.
* Schema `mount-point` expansion honours the flag: mounted write operations
  reference the state-free `<name>Config` content, and the mount node gets a
  parallel `<name>Config` composed definition attaching only the config part of
  the mounted models. Modules pulled in solely to satisfy a mount-point mapping
  are generated with the flag propagated, so their mounted writes stay
  state-free too.

The flag is **off by default**, so existing output and downstream code
generation pipelines are unaffected unless the flag is set.

Current limitations:

* Write models are emitted as flat, fully inlined definitions — they do not
  reuse shared `grouping` definitions, so deeply nested write bodies are more
  verbose than their read counterparts.
* `config false` leaves placed directly inside a `choice`/`case` are not yet
  pruned from the `<name>Config` model.

#### Running the CLI in a Docker image ####

To run yang2swagger in a Docker image or to build and extract the Jar file, run
the following in this repo's directory:

```
docker build -t yang2swagger .
```

To use the docker image, gather all the YANG files into a single directory and
from inside that directory run the following:

```
docker run -it -v $(pwd):. yang2swagger <arguments>
```

### Maven integration ###

**The maven integration plugins were not thoroughly checked in current release. Please raise any issues as a github tracker.** 

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
                <version>${yang.to.swagger.version}</version>
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
                <version>${yang.to.swagger.version}</version>
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
