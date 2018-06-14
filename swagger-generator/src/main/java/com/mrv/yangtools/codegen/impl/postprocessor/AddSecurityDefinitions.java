/*
 * Copyright (c) 2018 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */
package com.mrv.yangtools.codegen.impl.postprocessor;

import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.SecurityRequirement;
import io.swagger.models.Swagger;
import io.swagger.models.auth.SecuritySchemeDefinition;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.function.Consumer;

/**
 * @author bartosz.michalik@amartus.com
 */
public class AddSecurityDefinitions implements Consumer<Swagger> {

    private SecuritySchemeDefinition securityDefinition;
    private String securityDefinitionName;

    public AddSecurityDefinitions withSecurityDefinition(String name, SecuritySchemeDefinition def) {
        this.securityDefinition = def;
        this.securityDefinitionName = name;
        return this;
    }

    @Override
    public void accept(Swagger swagger) {
        swagger.securityDefinition(securityDefinitionName, securityDefinition);

        for(Path p : swagger.getPaths().values()) {
            for(Operation o : p.getOperations()) {
                o.addSecurity(securityDefinitionName, Collections.emptyList());
            }
        }
    }
}
