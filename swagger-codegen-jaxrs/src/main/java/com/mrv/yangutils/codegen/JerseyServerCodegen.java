package com.mrv.yangutils.codegen;

import io.swagger.codegen.languages.JavaJerseyServerCodegen;

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
    public String getName()
    {
        return "jaxrs-mrv";
    }
}
