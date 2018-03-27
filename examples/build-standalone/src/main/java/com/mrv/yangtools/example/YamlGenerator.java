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

package com.mrv.yangtools.example;

import com.mrv.yangtools.codegen.SwaggerGenerator;
import com.mrv.yangtools.codegen.impl.SegmentTagGenerator;
import com.mrv.yangtools.codegen.impl.path.odl.ODLPathHandlerBuilder;
import com.mrv.yangtools.codegen.impl.path.rfc8040.PathHandlerBuilder;
import com.mrv.yangtools.codegen.impl.postprocessor.PathPrunner;
import com.mrv.yangtools.codegen.impl.postprocessor.RemoveUnusedDefinitions;
import com.mrv.yangtools.codegen.impl.postprocessor.SingleParentInheritenceModel;

import java.io.*;

/**
 * Simple example of swagger generation for TAPI modules
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class YamlGenerator {

    public static void main(String[] args) throws Exception {
        SwaggerGenerator generator;
        String outputName = "swagger.swagger";
        if(args.length == 1) {
            File file = new File(args[0]);
            outputName = args[0] + ".swagger";
            generator = GeneratorHelper.getGenerator(file, m -> true);
        } else {
            generator = GeneratorHelper.getGenerator(m -> m.getName().startsWith("tapi"));
        }



        generator
                .tagGenerator(new SegmentTagGenerator())
                //if you wish to generate only to specific tree depth
//                .maxDepth(3)
                //define element type
//                .elements(SwaggerGenerator.Elements.RCP);
                //define path handling type
//                .pathHandler(new ODLPathHandlerBuilder().withoutFullCrud())
                .pathHandler(new PathHandlerBuilder().withoutFullCrud())
                //define path pruninng strategy
//                .appendPostProcessor(new PathPrunner("/operations").withType("tapi.common.GlobalClass"))
                //and single inheritence model
//                .appendPostProcessor(new SingleParentInheritenceModel())
                .appendPostProcessor(new RemoveUnusedDefinitions());


        generator.generate(new FileWriter(outputName));

    }
}
