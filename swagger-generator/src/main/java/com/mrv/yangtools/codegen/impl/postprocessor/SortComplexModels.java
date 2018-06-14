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
 * Sort all-of in a way code generators are expecting it
 *
 * @author bartosz.michalik@amartus.com
 */
public class SortComplexModels implements Consumer<Swagger> {
    @Override
    public void accept(Swagger swagger) {
        swagger.getDefinitions().entrySet().stream().filter(e -> e.getValue() instanceof ComposedModel)
                .forEach(e -> {
                    ComposedModel m = (ComposedModel) e.getValue();

                    sortModels(m);

                });
    }

    private void sortModels(ComposedModel m) {
        m.getAllOf().sort((a,b) -> {
            if(a instanceof RefModel) {
                if(b instanceof RefModel) {
                    return ((RefModel) a).getSimpleRef().compareTo(((RefModel) b).getSimpleRef());
                }
                return -1;
            }
            return 1;
        });
    }
}
