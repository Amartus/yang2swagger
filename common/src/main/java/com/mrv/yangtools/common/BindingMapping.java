package com.mrv.yangtools.common;

// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
// File originates from ODL project

import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Interner;
import com.google.common.collect.Interners;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.common.QNameModule;

public final class BindingMapping {
    public static final Set<String> JAVA_RESERVED_WORDS = ImmutableSet.of(
            "abstract", "assert",
            "boolean", "break", "byte",
            "case", "catch", "char", "class", "const", "continue",
            "default", "double", "do",
            "else", "enum", "extends",
            "false", "final", "finally", "float", "for",
            "goto",
            "if", "implements", "import", "instanceof", "int", "interface",
            "long",
            "native", "new", "null",
            "package", "private", "protected", "public",
            "return",
            "short", "static", "strictfp", "super", "switch", "synchronized",
            "this", "throw", "throws", "transient", "true", "try",
            "void", "volatile",
            "while");
    private static final Splitter CAMEL_SPLITTER = Splitter.on(CharMatcher.anyOf(" _.-").precomputed()).omitEmptyStrings().trimResults();
    private static final Pattern COLON_SLASH_SLASH = Pattern.compile("://", 16);
    private static final String QUOTED_DOT = Matcher.quoteReplacement(".");
    private static final Splitter DOT_SPLITTER = Splitter.on('.');
    private static final ThreadLocal<SimpleDateFormat> PACKAGE_DATE_FORMAT = new ThreadLocal<SimpleDateFormat>() {
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyMMdd");
        }

        public void set(SimpleDateFormat value) {
            throw new UnsupportedOperationException();
        }
    };
    private static final Interner<String> PACKAGE_INTERNER = Interners.newWeakInterner();

    private BindingMapping() {
        throw new UnsupportedOperationException("Utility class should not be instantiated");
    }

    public static String getRootPackageName(QName module) {
        return getRootPackageName(module.getModule());
    }

    public static String getRootPackageName(QNameModule module) {
        Preconditions.checkArgument(module != null, "Module must not be null");
        Preconditions.checkArgument(module.getRevision() != null, "Revision must not be null");
        Preconditions.checkArgument(module.getNamespace() != null, "Namespace must not be null");
        StringBuilder packageNameBuilder = new StringBuilder();
        packageNameBuilder.append("org.opendaylight.yang.gen.v1");
        packageNameBuilder.append('.');
        String namespace = module.getNamespace().toString();
        namespace = COLON_SLASH_SLASH.matcher(namespace).replaceAll(QUOTED_DOT);
        char[] chars = namespace.toCharArray();
        int i = 0;

        while(i < chars.length) {
            switch(chars[i]) {
                case '#':
                case '$':
                case '\'':
                case '*':
                case '+':
                case ',':
                case '-':
                case '/':
                case ':':
                case ';':
                case '=':
                case '@':
                    chars[i] = 46;
                case '%':
                case '&':
                case '(':
                case ')':
                case '.':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                case '<':
                case '>':
                case '?':
                default:
                    ++i;
            }
        }

        packageNameBuilder.append(chars);
        packageNameBuilder.append(".rev");
        packageNameBuilder.append((PACKAGE_DATE_FORMAT.get()).format(module.getRevision()));
        return normalizePackageName(packageNameBuilder.toString());
    }

    public static String normalizePackageName(String packageName) {
        if(packageName == null) {
            return null;
        }
        return PACKAGE_INTERNER.intern(StreamSupport.stream(DOT_SPLITTER.split(packageName.toLowerCase()).spliterator(), false)
                .map(BindingMapping::normalize).collect(Collectors.joining(".")));
    }

    public static String normalize(String name) {
        if(name.isEmpty()) return name;
        if(Character.isDigit(name.charAt(0)) || JAVA_RESERVED_WORDS.contains(name)) {
            return "_" + name;
        }
        return name;
    }

    public static String getMethodName(QName name) {
        Preconditions.checkArgument(name != null, "Name should not be null.");
        return getMethodName(name.getLocalName());
    }

    public static String getClassName(String localName) {
        Preconditions.checkArgument(localName != null, "Name should not be null.");
        return toFirstUpper(toCamelCase(localName));
    }

    public static String getMethodName(String yangIdentifier) {
        Preconditions.checkArgument(yangIdentifier != null, "Identifier should not be null");
        return toFirstLower(toCamelCase(yangIdentifier));
    }

    public static String getClassName(QName name) {
        Preconditions.checkArgument(name != null, "Name should not be null.");


        return toFirstUpper(toCamelCase(name.getLocalName()));
    }

    public static String getGetterSuffix(QName name) {
        Preconditions.checkArgument(name != null, "Name should not be null.");
        String candidate = toFirstUpper(toCamelCase(name.getLocalName()));
        return "Class".equals(candidate)?"XmlClass":candidate;
    }

    public static String getPropertyName(String yangIdentifier) {
        String potential = toFirstLower(toCamelCase(yangIdentifier));
        return "class".equals(potential)?"xmlClass":potential;
    }

    public static String nameToPackageSegment(String rawString) {
//        com.mrv.yangtools.codegen.impl.ModuleUtils
        Preconditions.checkArgument(rawString != null, "String should not be null");

        return StreamSupport.stream(CAMEL_SPLITTER.split(rawString).spliterator(), false)
                    .map(s -> checkNumericPrefix(s.toLowerCase())).collect(Collectors.joining("."));


    }

    private static String toCamelCase(String rawString) {
        Preconditions.checkArgument(rawString != null, "String should not be null");

        String camelString = StreamSupport.stream(CAMEL_SPLITTER.split(rawString).spliterator(), false)
                .map(BindingMapping::toFirstUpper).collect(Collectors.joining());

        return checkNumericPrefix(camelString);
    }

    private static String checkNumericPrefix(String rawString) {
        if(rawString != null && !rawString.isEmpty()) {
            char firstChar = rawString.charAt(0);
            return firstChar >= 48 && firstChar <= 57?"_" + rawString:rawString;
        } else {
            return rawString;
        }
    }

    public static String toFirstUpper(String s) {
        return s != null && s.length() != 0?(Character.isUpperCase(s.charAt(0))?s:(s.length() == 1?s.toUpperCase():s.substring(0, 1).toUpperCase() + s.substring(1))):s;
    }

    private static String toFirstLower(String s) {
        return s != null && s.length() != 0?(Character.isLowerCase(s.charAt(0))?s:(s.length() == 1?s.toLowerCase():s.substring(0, 1).toLowerCase() + s.substring(1))):s;
    }
}
