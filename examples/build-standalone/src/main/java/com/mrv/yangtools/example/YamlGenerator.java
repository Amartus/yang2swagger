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
import com.mrv.yangtools.codegen.impl.path.SegmentTagGenerator;
import com.mrv.yangtools.codegen.impl.path.odl.ODLPathHandlerBuilder;
import com.mrv.yangtools.codegen.impl.path.rfc8040.PathHandlerBuilder;
import com.mrv.yangtools.codegen.impl.postprocessor.*;
import io.swagger.models.auth.BasicAuthDefinition;

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
            generator = GeneratorHelper.getGenerator(file, m -> m.getName().equals("org-openroadm-pm"));
        } else {
            generator = GeneratorHelper.getGenerator(m -> m.getName().startsWith("tapi"));
        }



        generator
                .tagGenerator(new SegmentTagGenerator())
                //if you wish to generate only to specific tree depth
                .maxDepth(2)
                //define element type
//                .elements(SwaggerGenerator.Elements.RPC);
                //define path handling type
//                .pathHandler(new ODLPathHandlerBuilder().withoutFullCrud())
                .pathHandler(new PathHandlerBuilder().withoutFullCrud())
                //define path pruninng strategy
//                .appendPostProcessor(new PathPrunner("/operations").withType("tapi.common.GlobalClass"))
                //define collapse types with the same structure
                .appendPostProcessor(new CollapseTypes())
                .appendPostProcessor(new ShortenName("mef.sdwan.connectivity"))
                // add basic auth definition
//                .appendPostProcessor(new AddSecurityDefinitions().withSecurityDefinition("api_sec", new BasicAuthDefinition()))
                //and single inheritence model
//                .appendPostProcessor(new SingleParentInheritenceModel())
                .appendPostProcessor(new Rfc4080PayloadWrapper())
                .appendPostProcessor(new RemoveUnusedDefinitions());


        generator.generate(new FileWriter(outputName));

    }
}
