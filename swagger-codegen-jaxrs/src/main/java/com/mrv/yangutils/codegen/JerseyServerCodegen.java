package com.mrv.yangutils.codegen;

import io.swagger.codegen.CodegenOperation;
import io.swagger.codegen.languages.JavaJerseyServerCodegen;
import io.swagger.models.Operation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Simple enhancement to JaxRS generator to disable HTML escaping in <code>@Path</code> annotations.
 * @author bartosz.michalik@amartus.com
 */
public class JerseyServerCodegen extends JavaJerseyServerCodegen {

    public JerseyServerCodegen() {
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
    public String getName()
    {
        return "jaxrs-mrv";
    }
}
