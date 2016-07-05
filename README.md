# README #

### Yang2Swagger generator ###

Project is a YANG to swagger generator tool. The YANG parser is build on top of OpenDaylight (ODL) yang-tools project. 
Yang2Swagger generator is meant to be compliant with [RESTCONF specification  ](https://tools.ietf.org/html/draft-ietf-netconf-restconf-14)


Contact:

 * Bartosz Michalik bartosz.michalik@amartus.com
 * Christopher Murch cmurch@mrv.com 

### How do I get set up? ###

Project is build with standard maven ```maven clean install```. As project depends on ODL components ```settings.xml``` file [configuration might be required](https://wiki.opendaylight.org/view/GettingStarted:Development_Environment_Setup#Edit_your_.7E.2F.m2.2Fsettings.xml). 

The main component of the project is ```SwaggerGenerator``` which can be run standalone as well as can be configured as maven plugin. Examples of usage can be found in *examples* directory in the project.