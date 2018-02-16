/*
 * Copyright (c) 2016 MRV Communications, Inc. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Christopher Murch <cmurch@mrv.com>
 *      Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.example.ioc;

import java.io.File;
import java.io.FileWriter;

import com.google.inject.Guice;
import com.mrv.yangtools.codegen.IoCSwaggerGenerator;
import com.mrv.yangtools.codegen.impl.SegmentTagGenerator;
import com.mrv.yangtools.codegen.impl.postprocessor.SingleParentInheritenceModel;

/**
 * Simple example of swagger generation for TAPI modules using Guice IoC
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class IoCYamlGenerator {

    public static void main(String[] args) throws Exception {
    	Guice.createInjector(new GeneratorInjector());
    	
        IoCSwaggerGenerator generator;
        if(args.length == 1) {
            generator = IoCGeneratorHelper.getGenerator(new File(args[0]),m -> true);
        } else {
            generator = IoCGeneratorHelper.getGenerator(m -> m.getName().startsWith("Tapi"));
        }

        generator
        		.tagGenerator(new SegmentTagGenerator())
                .elements(IoCSwaggerGenerator.Elements.RCP)
                .appendPostProcessor(new SingleParentInheritenceModel());


        generator.generate(new FileWriter("swagger.yaml"));
//        generator.generate(new OutputStreamWriter(System.out));

    }
}
