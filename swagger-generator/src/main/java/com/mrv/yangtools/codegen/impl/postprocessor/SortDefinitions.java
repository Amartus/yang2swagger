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

import io.swagger.models.ComposedModel;
import io.swagger.models.RefModel;
import io.swagger.models.Swagger;

import java.util.function.Consumer;

/**
 * @author bartosz.michalik@amartus.com
 */
public class SortDefinitions implements Consumer<Swagger> {
    @Override
    public void accept(Swagger target) {
        target.getDefinitions().values().stream()
                .filter(d -> d instanceof ComposedModel)
                .forEach(d -> {
                    ComposedModel m = (ComposedModel) d;

                    m.getAllOf().sort((a,b) -> {
                        if(a instanceof RefModel) {
                            if(b instanceof RefModel) {
                                return ((RefModel) a).getSimpleRef().compareTo(((RefModel) b).getSimpleRef());
                            }

                        }
                        if(b instanceof RefModel) return 1;
                        //preserve the order for others
                        return -1;
                    });

                });
    }
}
