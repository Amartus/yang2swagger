package com.mrv.yangutils.codegen;

import io.swagger.codegen.*;
import io.swagger.codegen.languages.JavaJerseyServerCodegen;
import io.swagger.models.*;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.PropertyBuilder;
import io.swagger.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Simple enhancement to JaxRS generator to disable HTML escaping in <code>@Path</code> annotations.
 * @author bartosz.michalik@amartus.com
 */
public class JerseyServerCodegen extends JavaJerseyServerCodegen {

    private static final Logger log = LoggerFactory.getLogger(JerseyServerCodegen.class);

    public JerseyServerCodegen() {
        supportsInheritance = true;
        supportedLibraries.put("mrv", "MRV templates");
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

        final String[] segments = resourcePath.split("/");
        if(segments.length < 3 || !segments[1].equals("data")) {
            super.addOperationToGroup(tag,resourcePath, operation, co, operations);
            return;
        }

        String basePath = segments[1] + "/" + segments[2];

        if(basePath.equals("")) {
            basePath = "default";
        } else {
            if(co.path.startsWith("/" + basePath)) {
                co.path = co.path.substring(("/" + basePath).length());
            }

            co.subresourceOperation = !co.path.isEmpty();
        }

        List<CodegenOperation> opList = operations.get(basePath);
        if(opList == null) {
            opList = new ArrayList<>();
            operations.put(basePath, opList);
        }

        opList.add(co);
        co.baseName = basePath;
    }

    @Override
    public CodegenModel fromModel(String name, Model model, Map<String, Model> allDefinitions) {

        if (!(model instanceof ComposedModel)) {
            return super.fromModel(name, model, allDefinitions);
        }

        List<Model> refModels = ((ComposedModel) model).getAllOf().stream().filter(m -> m instanceof RefModel).collect(Collectors.toList());

        if(refModels.size() == 1) {
            ((ComposedModel) model).setParent(refModels.get(0));
        } else {
            log.warn("Unsupported inheritance schema for {} references to {}", name, refModels.stream()
                    .map(Model::getReference)
                    .collect(Collectors.joining(",")));
        }

        return super.fromModel(name, model, allDefinitions);
    }

    @Override
    public String getName()
    {
        return "jaxrs-mrv";
    }
}
