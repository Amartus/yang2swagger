/*
 *   Copyright (c) 2024 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *    Contributors:
 *       Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen.impl;

import io.swagger.models.Model;

/**
 * @author bartosz.michalik@amartus.com
 */
public final class ModelUtils {
    public static boolean isAugmentation(Model model) {
        if(model.getVendorExtensions() == null) return false;
        return model.getVendorExtensions().get("x-augmentation") != null;
    }
}
