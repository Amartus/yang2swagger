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
