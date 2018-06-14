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

import com.google.common.base.Strings;
import com.mrv.yangtools.common.Tuple;
import com.mrv.yangtools.common.BindingMapping;
import io.swagger.codegen.*;
import io.swagger.codegen.languages.JavaJerseyServerCodegen;
import io.swagger.models.*;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        Set<GeneratorRecord> generators = annotationGenerators.computeIfAbsent(vendorExtensionName, k -> new HashSet<>());
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


        List<CodegenOperation> opList = operations.computeIfAbsent(basePath, k -> new ArrayList<>());

        opList.add(co);
        co.baseName = basePath;
    }

    @Override
    public String toModelFilename(String name) {
        String[] segments = name.split("\\.");
        segments[segments.length-1] = super.toModelFilename(segments[segments.length-1]);
        return Arrays.stream(segments).map(BindingMapping::normalize)
                .collect(Collectors.joining("/"));
    }

    @Override
    public String toModelName(String name) {
        return name;

    }


    private Tuple<String, String> toPkgClass(String datatype) {
        if(datatype == null) return null;
        int classSeparator = datatype.lastIndexOf(".");
        if(classSeparator > 0) {

            return new Tuple<>(datatype.substring(0, classSeparator), datatype.substring(classSeparator + 1));
        }
        return null;
    }

    @Override
    public String getSwaggerType(Property p) {

        if (p instanceof RefProperty) {
            String datatype;
            try {
                RefProperty r = (RefProperty) p;
                datatype = r.get$ref();
                if (datatype.indexOf("#/definitions/") == 0) {
                    datatype = datatype.substring("#/definitions/".length());
                }
            } catch (Exception e) {
                log.warn("Error obtaining the datatype from RefProperty:" + p + ". Datatype default to Object");
                datatype = "Object";
                log.error(e.getMessage(), e);
            }
            return datatype;

        }

        return super.getSwaggerType(p);
    }

    @Override
    protected boolean needToImport(String type) {
        return !defaultIncludes.contains(type)
                && !languageSpecificPrimitives.contains(type);
    }

    protected CodegenModel fromModelInternal(String name, Model model, Map<String, Model> allDefinitions) {
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
        removeAugmentedImports(codegenModel, allDefinitions);
        multiInheritanceSupport(codegenModel, (ComposedModel) model, allDefinitions);
        postprocessProperties(codegenModel);
        return codegenModel;
    }

    @Override
    public CodegenModel fromModel(String name, Model model, Map<String, Model> allDefinitions) {
        CodegenModel cm =  fromModelInternal(name, model,allDefinitions);
        Set<String> imports = cm.imports;
        cm.imports = fixImports(imports.stream()).collect(Collectors.toSet());
        return cm;
    }

    private void postprocessProperties(CodegenModel codegenModel) {
        codegenModel.allVars.forEach(p -> postProcessModelProperty(codegenModel, p));
        codegenModel.vars.forEach(p -> postProcessModelProperty(codegenModel, p));
    }

    private void removeAugmentedImports(CodegenModel codegenModel, Map<String, Model> allDefinitions) {
        codegenModel.interfaces.forEach(i -> {
            Model m = allDefinitions.get(i);
            if(m.getVendorExtensions().get("x-augmentation") != null) {
                codegenModel.imports.remove(i);
            }

        });
    }


    private void multiInheritanceSupport(CodegenModel codegenModel, ComposedModel model, Map<String, Model> allDefinitions) {
        List<CodegenProperty> vars = new ArrayList<>(codegenModel.allVars);
        Set<String> mandatory = new HashSet<>(codegenModel.allMandatory);

        final Set<String> properties = getParentProperties(model.getParent(),allDefinitions);

        //rewrite for #hasMore
        vars = vars.stream().filter(p -> ! properties.contains(p.baseName)).map(p -> {
            CodegenProperty cp = p.clone();
            if (cp.isContainer != null) {
                addImport(codegenModel, typeMapping.get("array"));
            }

            String imp = cp.baseType;

            Tuple<String, String> pkgClass = toPkgClass(imp);
            if(pkgClass != null) {
                imp = BindingMapping.normalizePackageName(pkgClass.first()) + "." + pkgClass.second();
            }
            addImport(codegenModel, imp);
            cp.hasMore = true;
            return cp;
        }).collect(Collectors.toList());

        if(!vars.isEmpty()) {
            vars.get(vars.size() -1).hasMore = null;
            codegenModel.hasVars = true;
        }

        mandatory = mandatory.stream().filter(m -> ! properties.contains(m)).collect(Collectors.toSet());
        codegenModel.vars = vars;
        codegenModel.mandatory = mandatory;
    }



    private Set<String> getParentProperties(Model parent, Map<String, Model> allDefinitions) {
        if(parent == null) return Collections.emptySet();
        if(parent instanceof ModelImpl)
            return parent.getProperties() == null ? Collections.emptySet() : parent.getProperties().keySet();
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
    public CodegenParameter fromParameter(Parameter param, Set<String> imports) {
        return super.fromParameter(param, imports);
    }

    @Override
    public CodegenOperation fromOperation(String path, String httpMethod, Operation operation, Map<String, Model> definitions, Swagger swagger) {
        path = fixPath(path);

        CodegenOperation co = super.fromOperation(path, httpMethod, operation, definitions, swagger);

        final Function<String, String> fixedPkgName = n -> {
            Tuple<String, String> pkgClass = toPkgClass(n);
            if(pkgClass == null) return null;
            return BindingMapping.normalizePackageName(pkgClass.first()) + "." + pkgClass.second();
        };

        final Function<String,String> fullPkgName = p -> {
            String f = fixedPkgName.apply(p);
            if(f == null) return p;
            return this.modelPackage + "." + f;
        };

        Consumer<CodegenParameter> changePkg = p -> {
            if (definitions.keySet().contains(p.dataType)) {
                co.imports.removeIf(x -> x.endsWith(p.dataType));
                p.dataType = fullPkgName.apply(p.dataType);
            }
            if (definitions.keySet().contains(p.baseType)) {
                co.imports.removeIf(x -> x.endsWith(p.baseType));
                p.baseType = fullPkgName.apply(p.baseType);
            }
        };

        if (co.getHasBodyParam()) {
            co.bodyParams.forEach(changePkg);
        }
        if (co.getHasPathParams()) {
            co.pathParams.forEach(changePkg);
        }

        if (co.hasParams != null && co.hasParams) {
            co.allParams.forEach(changePkg);
        }

        Function<String, String> typeFix = t -> {
            if(t == null || "void".equals(t)) return "Void";
            if (definitions.keySet().contains(t))
                return fullPkgName.apply(t);
            return t;
        };

        if (definitions.keySet().contains(co.returnType)) {
            co.imports.removeIf(x -> x.endsWith(co.returnType));
        }

        if (definitions.keySet().contains(co.returnType)) {
            co.imports.removeIf(x -> x.endsWith(co.returnBaseType));
        }


        co.returnType = typeFix.apply(co.returnType);
        co.returnBaseType = typeFix.apply(co.returnBaseType);
        fixImports(co);
        return co;
    }

    private void fixImports(CodegenOperation co) {
        Set<String> imports = co.imports;
        co.imports = fixImports(imports.stream()).collect(Collectors.toSet());
    }

    private Stream<String> fixImports(Stream<String> imports) {
        return imports.map(p -> {
            Tuple<String, String> pkgClass = toPkgClass(p);
            if(pkgClass != null){
                return BindingMapping.normalizePackageName(pkgClass.first())+ "." + pkgClass.second();
            }
            return p;
        });
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Object> postProcessModels(Map<String, Object> objs) {
        String pkgBase = (String) objs.get("package");
        List<HashMap<String, Object>> models = (List<HashMap<String, Object>>) objs.get("models");
        if(models.size() > 1) {
            log.warn("unsupported data - to many codegen models");
            throw new IllegalArgumentException("unsupported data - to many codegen models");
        }
        CodegenModel model = (CodegenModel) models.get(0).get("model");
        String modelPkg =  "";
        Tuple<String, String> pkgClass = toPkgClass(model.name);
        //XXX to support name conficts for imports - finish belove code
//        Map<String, Set<String>> importsByClass = model.imports.stream()
//                .map(this::toPkgClass)
//                .filter(Objects::nonNull)
//                .collect(Collectors.groupingBy(Tuple::second, Collectors.mapping(p -> p.first() + "." + p.second(), Collectors.toSet())));
//        importsByClass = importsByClass.entrySet().stream().filter(e -> e.getValue().size() > 1)
//                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if(pkgClass != null) {
            modelPkg = pkgClass.first();
            model.classname = pkgClass.second();
            if(!Strings.isNullOrEmpty(model.parent)) {
                pkgClass = toPkgClass(model.parent);
                if(pkgClass != null){
                    if(model.classname.equals(pkgClass.second())) {

                        List<HashMap<String, String>> imports = ((List<HashMap<String, String>>) objs.get("imports")).stream()
                                .filter(m -> {
                                    String anImport = m.get("import");
                                    return !anImport.endsWith(model.classname);
                                }).collect(Collectors.toList());
                        objs.put("imports", imports);
                    }
                    model.parent = BindingMapping.normalizePackageName(pkgBase + "." + pkgClass.first())+ "." + pkgClass.second();
                }
            }
        }
        if(modelPkg.isEmpty()) {
            objs.put("package", BindingMapping.normalizePackageName(pkgBase));
        } else {
            objs.put("package", BindingMapping.normalizePackageName(pkgBase + "." + modelPkg));
        }
        return super.postProcessModels(objs);
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty prop) {
        super.postProcessModelProperty(model, prop);
        String type = prop.complexType;
        Tuple<String, String> pkgClass = toPkgClass(type);
        if(pkgClass != null) {
            prop.complexType = pkgClass.second();
            prop.datatypeWithEnum = prop.datatypeWithEnum.replace(type, pkgClass.second());
            prop.datatype = prop.datatype.replace(type, pkgClass.second());
            if(prop.containerType == null) {
                prop.baseType = pkgClass.second();
            }
            if("array".equals(prop.containerType)) {
                prop.defaultValue = prop.defaultValue.replace(type, pkgClass.second());
                postProcessModelProperty(model,prop.items);
            }
        }
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
