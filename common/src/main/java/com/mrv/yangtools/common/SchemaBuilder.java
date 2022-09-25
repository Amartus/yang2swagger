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

import org.opendaylight.yangtools.yang.model.api.EffectiveModelContext;
import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.parser.impl.DefaultReactors;
import org.opendaylight.yangtools.yang.parser.rfc7950.repo.YangStatementStreamSource;
import org.opendaylight.yangtools.yang.parser.spi.meta.ReactorException;
import org.opendaylight.yangtools.yang.parser.stmt.reactor.CrossSourceStatementReactor.BuildAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Yang schema context builder
 * @author cmurch@mrv.com
 * @author bartosz.michalik@amartus.com
 */
public class SchemaBuilder {

    final public static PathMatcher yang = FileSystems.getDefault().getPathMatcher("glob:*.yang");

    private static final Logger log = LoggerFactory.getLogger(SchemaBuilder.class);
    private Predicate<Path> accept;
    private List<Path> yangs;


    public SchemaBuilder() {
        accept = defaultYangMatcher();
        yangs = new ArrayList<>();
    }

    public static Predicate<Path> defaultYangMatcher() {
        return (path) -> yang.matches(path.getFileName());
    }


    public SchemaBuilder accepts(Predicate<Path> accept) {
        Objects.requireNonNull(accept);
        this.accept = accept;
        return this;
    }

    public SchemaBuilder add(Path path) throws IOException {
        if(Files.isDirectory(path)) {
            Files.walk(path)
                    .filter(p -> Files.isRegularFile(p) && accept.test(p))
                    .forEach(p -> {if(!yangs.contains(p)) yangs.add(p); });
        }

        return this;
    }

    EffectiveModelContext build() throws ReactorException {
        final BuildAction reactor = DefaultReactors.defaultReactor().newBuild();
        log.info("Inspecting all defined yangs {}", yangs);
        for (final Path path : yangs) {
            try {
                reactor.addSource(YangStatementStreamSource.create(YangTextSchemaSource.forFile(path.toFile())));
            } catch (final IOException | YangSyntaxErrorException e) {
                throw new IllegalStateException(path + " is not a valid YANG file");
            }
        }
        return reactor.buildEffective();
    }
}
