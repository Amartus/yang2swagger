package com.mrv.yangutils.codegen;

import io.swagger.codegen.languages.JavaJerseyServerCodegen;

/**
 * Simple enhancement to JaxRS generator to disable HTML escaping in <code>@Path</code> annotations.
 * @author bartosz.michalik@amartus.com
 */
public class JerseyServerCodegen extends JavaJerseyServerCodegen {

    public JerseyServerCodegen() {
        apiTemplateFiles.put("api-mrv.mustache", ".java");
    }

    @Override
    public String getName()
    {
        return "jaxrs-mrv";
    }
}
