/*
 *  Copyright (c) 2024 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html

 *  Contributors:
 *       Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen.impl.postprocessor;

/**
 * @author bartosz.michalik@amartus.com
 */
public class Rfc4080PayloadWrapper extends PayloadWrapperProcessor {
    @Override
    protected String toProperty(String path) {
        String[] split = path.split("/");
        String lastSegment = split[split.length - 1];
        split = lastSegment.split("=");
        return split[0];
    }
}
