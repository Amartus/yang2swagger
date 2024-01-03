/*
 *   Copyright (c) 2024 Amartus. All rights reserved.
 *  This program and the accompanying materials are made available under the
 *  terms of the Eclipse Public License v1.0 which accompanies this distribution,
 *  and is available at http://www.eclipse.org/legal/epl-v10.html
 *    Contributors:
 *       Bartosz Michalik <bartosz.michalik@amartus.com>
 */

package com.mrv.yangtools.codegen.main;

import io.swagger.models.Swagger;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assert;
import org.junit.Test;

public class  Issue57 {
    private static String path;

    static {
        try {
            path = Paths.get(Issue57.class.getResource("/bug_57/").toURI()).toAbsolutePath().toString();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testRegular() {

        List<String> args = Stream.of(
                "-yang-dir",
                path
        ).collect(Collectors.toList());

        Swagger swagger = Utils.runParser(args);
        assertContainsOnly(swagger, s -> s.endsWith("Input"),
                "objects.createobject.Input","objects.updateobject.Input");
    }

    @Test
    public void testOptimized() {

        List<String> args = Stream.of(
                "-reuse-groupings",
                "-yang-dir",
                path
        ).collect(Collectors.toList());

        Swagger swagger = Utils.runParser(args);

        assertContainsOnly(swagger, s -> s.endsWith("Input"), "objects.createobject.Input");
    }

    private void assertContainsOnly(Swagger swagger, Predicate<String> filterDefs, String... name) {
        Set<String> result = swagger.getDefinitions().keySet().stream()
                .filter(filterDefs)
                .collect(Collectors.toSet());
        Set<String> expected = Stream.of(name)
                .collect(Collectors.toSet());
        Assert.assertEquals(expected, result);
    }
}
