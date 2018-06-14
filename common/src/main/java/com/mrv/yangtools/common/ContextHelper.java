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

package com.mrv.yangtools.common;

import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Simplify context building
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class ContextHelper {
    private static final Logger log = LoggerFactory.getLogger(ContextHelper.class);

    /**
     * Get context for yang files from given directory that are accepted
     * @param dir directory
     * @param accept accept function to be passed to {@link SchemaBuilder}}
     * @return YANG context
     * @throws ReactorException in case of parsing errors
     */
    public static SchemaContext getFromDir(Path dir, Predicate<Path> accept) throws ReactorException {
        return getFromDir(Stream.of(dir), accept);
    }

    public static SchemaContext getFromDir(Stream<Path> dirs, Predicate<Path> accept) throws ReactorException {
        return getCtx(dirs, accept);
    }

    /**
     * Get context for yang from classpath
     * @param accept accept function to be passed to {@link SchemaBuilder}}
     * @return YANG context in case of parsing errors
     * @throws ReactorException in case of problem with YANG modules parsing
     */
    public static SchemaContext getFromClasspath(Predicate<Path> accept) throws ReactorException {
        return getCtx(Arrays.stream(System.getProperty("java.class.path", ".").split(File.pathSeparator)).map(s -> Paths.get(s.replaceFirst("^/(.:/)", "$1"))), accept);
    }

    /**
     * Get context for yang files from given directory that are accepted
     * @param dirs resources directories to be considered
     * @param accept accept function to be passed to {@link SchemaBuilder}}
     * @return YANG context
     * @throws ReactorException in case of parsing errors
     */
    public static SchemaContext getCtx(Stream<Path> dirs, Predicate<Path> accept) throws ReactorException {

        SchemaBuilder builder = new SchemaBuilder().accepts(accept);

        dirs.filter(p -> Files.isDirectory(p)).forEach((path) -> {
            try {
                log.info("adding {}", path);
                builder.add(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        return builder.build();
    }
}
