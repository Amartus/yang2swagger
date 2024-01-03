/*
 *  Copyright (c) 2024 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html

 *  Contributors:
 *       Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen.impl.postprocessor;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


/**
 * @author bartosz.michalik@amartus.com
 */
public class Rfc4080PayloadWrapperTest extends AbstractWithSwagger {
    @Test
    public void wrapperTest() {
        Rfc4080PayloadWrapper wrapper = new Rfc4080PayloadWrapper();

        wrapper.accept(swagger);

        long numberOfWrappers = swagger.getDefinitions().keySet().stream()
                .filter(k -> k.endsWith("Wrapper"))
                .count();

        assertEquals(numberOfWrappers, swagger.getPaths().size());
    }
}