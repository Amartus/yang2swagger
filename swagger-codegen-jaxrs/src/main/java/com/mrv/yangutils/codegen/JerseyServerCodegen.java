/*
 * Copyright (c) 2016 MRV Communications, Inc. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Christopher Murch <cmurch@mrv.com>
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangutils.codegen;

import io.swagger.codegen.*;
import io.swagger.codegen.languages.JavaJerseyServerCodegen;
import io.swagger.models.*;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Simple enhancement to JaxRS generator to disable HTML escaping in <code>@Path</code> annotations.
 * In addition several modifications were applied
 * <ul>
 *     <li>changing default throw in API to {@link Exception}</li>
 *     <li>Adding uri info to resource context</li>
 *     <li>Grouping DATA namespace JAX-RS resources by YANG module</li>
 * </ul>
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class JerseyServerCodegen extends JavaJerseyServerCodegen {

    private static final Logger log = LoggerFactory.getLogger(JerseyServerCodegen.class);
    private HashMap<String, Set<GeneratorRecord>> annotationGenerators = new HashMap<>();
    private Set<String> toInterfaces = new HashSet<>();

    public JerseyServerCodegen() {
        supportsInheritance = true;
        supportedLibraries.put("mrv", "MRV templates");
    }

    /**
     * Add annotation
     * @param templateKey key in the template to hook-up the generator result
     * @param vendorExtensionName name of the vendorExtension to hook-up
     * @param generator function to generate the resulting text of annotation
     */
    public void addAnnotation(String templateKey, String vendorExtensionName, Function<String, String> generator) {
        Set<GeneratorRecord> generators = annotationGenerators.get(vendorExtensionName);
        if(generators == null) {
            generators = new HashSet<>();
            annotationGenerators.put(vendorExtensionName, generators);
        }
        generators.add(new GeneratorRecord(templateKey, generator));
    }

    /**
     * Add class name which will not be considered as parent class (TODO in the future code for interface will be generated)
     * @param name class-name
     */
    public void addInterface(String name) {
        toInterfaces.add(name);
    }

    @Override
    public void processOpts() {
        super.processOpts();
        //remove files that we do not need
        supportingFiles = supportingFiles.stream()
                .filter(sf -> ! "NotFoundException.mustache".equals(sf.templateFile))
                .collect(Collectors.toList());
        setLibrary("mrv");
    }

    @Override
    public void addOperationToGroup(String tag, String resourcePath, Operation operation, CodegenOperation co, Map<String, List<CodegenOperation>> operations) {
        resourcePath = fixPath(resourcePath);
        String basePath = resourcePath;
        if(resourcePath.startsWith("/")) {
            basePath = resourcePath.substring(1);
        }
        final String[] segments = basePath.split("/");
        if(segments.length < 2 || !segments[0].equals("data")) {
            super.addOperationToGroup(tag, resourcePath, operation, co, operations);
            return;
        }

        basePath = segments[0] + "/" + segments[1];
        if(co.path.startsWith("/" + basePath)) {
            co.path = fixPath(co.path.substring((basePath).length()+1));
        }

        co.subresourceOperation = !co.path.isEmpty();


        List<CodegenOperation> opList = operations.get(basePath);
        if(opList == null) {
            opList = new ArrayList<>();
            operations.put(basePath, opList);
        }

        opList.add(co);
        co.baseName = basePath;
    }

    @Override
    public String toModelFilename(String name) {
        //TODO this is how it could be fixed
//        String[] segments = name.split("\\.");
//        segments[segments.length-1] = super.toModelFilename(segments[segments.length-1]);
//        return Arrays.stream(segments).collect(Collectors.joining("/"));
        return super.toModelFilename(name);
    }

    @Override
    public CodegenModel fromModel(String name, Model model, Map<String, Model> allDefinitions) {

        if (!(model instanceof ComposedModel)) {
            return super.fromModel(name, model, allDefinitions);
        }

        List<RefModel> refModels = ((ComposedModel) model).getAllOf().stream()
                .filter(m -> m instanceof RefModel)
                .map(x -> (RefModel)x)
                .collect(Collectors.toList());

        final List<RefModel> interfaces = refModels.stream().filter(m -> toInterfaces.contains(m.getSimpleRef())).collect(Collectors.toList());
        refModels = refModels.stream().filter(r -> ! interfaces.contains(r)).collect(Collectors.toList());


        if(refModels.size() == 1) {
            ((ComposedModel) model).setParent(refModels.get(0));
            ((ComposedModel) model).setInterfaces(interfaces);
        } else if(refModels.isEmpty()) {
            log.info("No parent class for {}", name);
        } else {
            log.warn("Unsupported inheritance schema for {} references to {}", name, refModels.stream()
                    .map(Model::getReference)
                    .collect(Collectors.joining(",")));
        }

        CodegenModel codegenModel = super.fromModel(name, model, allDefinitions);
        multiInheritanceSupport(codegenModel, (ComposedModel) model, allDefinitions);
        return codegenModel;
    }

    private void multiInheritanceSupport(CodegenModel codegenModel, ComposedModel model, Map<String, Model> allDefinitions) {
        List<CodegenProperty> vars = new ArrayList<>(codegenModel.allVars);
        Set<String> mandatory = new HashSet<>(codegenModel.allMandatory);

        final Set<String> properties = getParentProperties(model.getParent(),allDefinitions);

        //rewrite for #hasMore
        vars = vars.stream().filter(p -> ! properties.contains(p.baseName)).map(p -> {
            CodegenProperty n = p.clone();
            n.hasMore = true;
            return n;
        }).collect(Collectors.toList());
        if(!vars.isEmpty()) vars.get(vars.size() -1).hasMore = false;

        mandatory = mandatory.stream().filter(m -> ! properties.contains(m)).collect(Collectors.toSet());
        codegenModel.vars = vars;
        codegenModel.mandatory = mandatory;
    }

    private Set<String> getParentProperties(Model parent, Map<String, Model> allDefinitions) {
        if(parent == null) return Collections.emptySet();
        if(parent instanceof ModelImpl) return parent.getProperties().keySet();
        if(parent instanceof RefModel) {
            String ref = ((RefModel) parent).getSimpleRef();
            Model model = allDefinitions.get(ref);
            if(model == null) log.warn("no model found for $ref {}", ref);
            return getParentProperties(model, allDefinitions);
        }
        if(parent instanceof ComposedModel) {
            Set<String> result = new HashSet<>();

            ((ComposedModel) parent).getAllOf().stream().map(m -> getParentProperties(m, allDefinitions))
                    .forEach(result::addAll);
            return result;
        }
        throw new IllegalStateException("Exception type of model " + parent.getClass());
    }

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, Map<String, Model> definitions, Swagger swagger) {
        path = fixPath(path);

        CodegenOperation co = super.fromOperation(path, httpMethod, operation, definitions, swagger);
        if("void".equals(co.returnType)) co.returnType = "Void";
        return co;
    }

    private String fixPath(String path) {
        if(path.endsWith("/")) path = path.substring(0, path.length() -1);
        return path;
    }

    @Override
    public CodegenProperty fromProperty(String name, Property p) {
        CodegenProperty property = super.fromProperty(name, p);
        if(p instanceof ArrayProperty) {
            p = ((ArrayProperty) p).getItems();
        }
        //add annotations for vendor extensions
        Map<String, String> extensions = p.getVendorExtensions().entrySet().stream()
                .filter(entry -> annotationGenerators.containsKey(entry.getKey()))
                .flatMap(entry -> {
                    Set<GeneratorRecord> generatorRecords = annotationGenerators.get(entry.getKey());
                    return generatorRecords.stream()
                            .map(x -> new SimpleEntry<>(x.getKey(), x.getValue().apply((String) entry.getValue())));
                }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        property.vendorExtensions.putAll(extensions);
        return property;
    }

    private class SimpleEntry<K, V> implements Map.Entry<K, V> {

        private K key;
        private V value;

        public SimpleEntry(K key, V value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public K getKey() {
            return key;
        }

        @Override
        public V getValue() {
            return value;
        }

        @Override
        public V setValue(V value) {
            V t = this.value;
            this.value = value;
            return t;
        }
    }

    private class GeneratorRecord extends SimpleEntry<String, Function<String,String>> {
        public GeneratorRecord(String key, Function<String, String> transformer) {
            super(key, transformer);
        }
    }

    @Override
    public String getName()
    {
        return "jaxrs-mrv";
    }
}
