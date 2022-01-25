/*
 * Copyright (c) 2018 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *      Damian Mrozowicz <damian.mrozowicz@amartus.com>
 */

package com.mrv.yangtools.example.ioc;

import static com.mrv.yangtools.common.ContextHelper.getFromClasspath;
import static com.mrv.yangtools.common.ContextHelper.getFromDir;

import java.io.File;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.mrv.yangtools.codegen.IoCSwaggerGenerator;

/**
 * Helper utility to simplify {@link IoCSwaggerGenerator} configuration in context of examples
 * @author damian.mrozowicz@amartus.com
 */
public class IoCGeneratorHelper {
    private static final Logger log = LoggerFactory.getLogger(IoCGeneratorHelper.class);
    
    @Inject
    private static SwaggerGeneratorFactory swaggerGeneratorFactory;
    
    public static IoCSwaggerGenerator getGenerator(String... module) throws Exception {
        final List<String> modules = Arrays.asList(module);
        return getGenerator(m-> modules.contains(m.getName()));
    }

    public static IoCSwaggerGenerator getGenerator(Predicate<Module> toSelect) throws Exception {
        return getGenerator(null, toSelect);
    }

    public static IoCSwaggerGenerator getGenerator(File dir, Predicate<Module> toSelect) throws Exception {
        final PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:*.yang");

        Predicate<Path> acc = p ->  matcher.matches(p.getFileName());

        final SchemaContext ctx = dir == null ? getFromClasspath(acc) : getFromDir(dir.toPath(), acc);
        if(ctx.getModules().isEmpty()) throw new IllegalArgumentException(String.format("No YANG modules found in %s", dir == null ? "classpath"  : dir.toString()));
        log.info("Context parsed {}", ctx);

        final Set<Module> toGenerate = ctx.getModules().stream().filter(toSelect).collect(Collectors.toSet());

		return swaggerGeneratorFactory.createSwaggerGenerator(ctx, toGenerate)
                .defaultConfig()
                .format(IoCSwaggerGenerator.Format.YAML)
                .consumes("application/xml")
                .produces("application/xml")
                .host("localhost:1234")
                .elements(IoCSwaggerGenerator.Elements.DATA, IoCSwaggerGenerator.Elements.RPC);
    }
}
